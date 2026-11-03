package com.v2ray.ang.handler

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.ui.HouseAdActivity
import com.v2ray.ang.util.LogUtil

/**
 * Central AdMob owner for the app (monetization task). All unit IDs are Google's official
 * TEST ad unit IDs; replace them with Derafsh production IDs before release.
 *
 * Process contract: the Google Mobile Ads SDK must only ever be touched from the main (UI)
 * process. [init] refuses to run elsewhere, mirroring how AngApplication keeps
 * process-specific initialization out of the :daemon/:tasks/:bg processes.
 *
 * Robustness contract: no ad call may ever crash the app. Every load/show and every callback
 * is guarded, and failures are only logged through LogUtil.
 */
object AdsManager {

    // Ad unit IDs come from BuildConfig (app/build.gradle.kts defaultConfig — the single
    // source of truth, see VAULT.md §4). Currently Google's official TEST ad units.
    private val APP_OPEN_AD_UNIT = BuildConfig.ADMOB_APP_OPEN_UNIT
    private val INTERSTITIAL_AD_UNIT = BuildConfig.ADMOB_INTERSTITIAL_UNIT
    private val REWARDED_AD_UNIT = BuildConfig.ADMOB_REWARDED_UNIT

    /** Banner unit, shared with the compose banner (ui/compose/BannerAd.kt). */
    val BANNER_AD_UNIT: String = BuildConfig.ADMOB_BANNER_UNIT

    /**
     * Derafsh Kaviani frequency cap: interstitials are natural-pause placements, not a tax on
     * every tap. Two connect attempts closer than this are served without a second ad.
     */
    const val INTERSTITIAL_MIN_INTERVAL_MS = 180_000L

    /**
     * House-ads mode (ADS_PROVIDER = "house"): all full-screen slots route to the first-party
     * Derafsh house ad instead of AdMob, and the Mobile Ads SDK is never initialized. Flip
     * ADS_PROVIDER in app/build.gradle.kts to "admob" (with real IDs) to restore network ads.
     */
    val isHouseAds: Boolean
        get() = BuildConfig.ADS_PROVIDER.equals("house", ignoreCase = true)

    // Pending house-ad callbacks (single-flight: one house ad on screen at a time).
    @Volatile
    private var housePendingReward: (() -> Unit)? = null

    @Volatile
    private var housePendingClosed: (() -> Unit)? = null

    /**
     * Called by HouseAdActivity when the house ad closes. Fires the pending reward (house ads
     * always grant, e.g. session extension) and then the close callback, mirroring AdMob order.
     */
    fun onHouseAdDismissed() {
        val reward = housePendingReward
        housePendingReward = null
        val closed = housePendingClosed
        housePendingClosed = null
        try {
            reward?.invoke()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: house reward callback failed", e)
        }
        try {
            closed?.invoke()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: house close callback failed", e)
        }
    }

    @Volatile
    private var lastInterstitialShownAt = 0L

    @Volatile
    private var initialized = false

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var rewardedAd: RewardedAd? = null

    // In-flight markers so a slow load is not started twice from rapid entry points.
    @Volatile
    private var isLoadingAppOpen = false

    @Volatile
    private var isLoadingInterstitial = false

    @Volatile
    private var isLoadingRewarded = false

    /**
     * Initializes the Google Mobile Ads SDK and eagerly preloads every full-screen format.
     * Must be called from the main process; a no-op everywhere else. Safe to call repeatedly.
     */
    fun init(context: Context) {
        if (isHouseAds) return // house mode: never touch the Google Mobile Ads SDK
        if (initialized || !isMainProcess()) return

        val appContext = context.applicationContext
        try {
            // Loading may begin immediately after initialize() returns; the SDK queues work
            // internally until initialization completes.
            MobileAds.initialize(appContext)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: MobileAds initialization failed", e)
            return
        }
        initialized = true

        preloadAppOpen(appContext)
        preloadInterstitial(appContext)
        preloadRewarded(appContext)
    }

    /**
     * Monetization waterfall: Iranian Tapsell leg first (rial), then AdMob (dollar), then the
     * house ad as the always-fill fallback. An unconfigured/failed leg never blocks the user.
     */
    fun showAppOpen(activity: Activity) {
        if (TapsellAds.showAppOpen(activity)) return
        if (isHouseAds) {
            HouseAdActivity.launch(activity)
            return
        }
        val ad = appOpenAd
        if (ad == null) {
            LogUtil.i(AppConfig.TAG, "AdsManager: app open ad not ready")
            return
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: app open ad show failed", e)
            if (appOpenAd === ad) appOpenAd = null
            preloadAppOpen(activity.applicationContext)
        }
    }

    /**
     * Waterfall: Tapsell (rial) → AdMob (dollar) → house. Shows the winning leg and calls
     * [onClosed] after it is dismissed (or immediately when no ad is ready or the frequency
     * cap is active), so callers can continue their flow in both cases.
     */
    fun showInterstitial(activity: Activity, onClosed: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS) {
            LogUtil.i(AppConfig.TAG, "AdsManager: interstitial skipped by frequency cap, continuing")
            onClosed()
            return
        }
        if (TapsellAds.showInterstitial(activity, onClosed)) {
            lastInterstitialShownAt = now
            return
        }
        if (isHouseAds) {
            lastInterstitialShownAt = now
            housePendingReward = null
            housePendingClosed = onClosed
            HouseAdActivity.launch(activity)
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            LogUtil.i(AppConfig.TAG, "AdsManager: interstitial not ready, continuing directly")
            onClosed()
            return
        }
        // Null out before show so a second entry point cannot show the same instance twice.
        interstitialAd = null
        lastInterstitialShownAt = now
        try {
            ad.fullScreenContentCallback = buildShowCallback(
                onDismiss = {
                    preloadInterstitial(activity.applicationContext)
                    onClosed()
                },
            )
            ad.show(activity)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: interstitial show failed", e)
            preloadInterstitial(activity.applicationContext)
            onClosed()
        }
    }

    /**
     * Waterfall: Tapsell (rial) → AdMob (dollar) → house. [onRewarded] fires when the user
     * earns the reward and [onClosed] fires afterwards on dismissal; if no leg is ready only
     * [onClosed] runs (house ads always grant the reward when the screen closes).
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onClosed: () -> Unit) {
        if (TapsellAds.showRewarded(activity, onRewarded, onClosed)) return
        if (isHouseAds) {
            // House ads always grant the reward when the screen closes.
            housePendingReward = onRewarded
            housePendingClosed = onClosed
            HouseAdActivity.launch(activity)
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            LogUtil.i(AppConfig.TAG, "AdsManager: rewarded ad not ready")
            onClosed()
            return
        }
        rewardedAd = null
        try {
            ad.fullScreenContentCallback = buildShowCallback(
                onDismiss = {
                    preloadRewarded(activity.applicationContext)
                    onClosed()
                },
            )
            ad.show(activity) {
                try {
                    onRewarded()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "AdsManager: rewarded callback failed", e)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AdsManager: rewarded show failed", e)
            preloadRewarded(activity.applicationContext)
            onClosed()
        }
    }

    /**
     * Shared full-screen callback: hops nothing across threads (the SDK invokes these on the
     * main thread) and always reloads the next ad after the current one leaves the screen.
     */
    private fun buildShowCallback(onDismiss: () -> Unit): FullScreenContentCallback =
        object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                try {
                    onDismiss()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "AdsManager: dismiss callback failed", e)
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                LogUtil.w(AppConfig.TAG, "AdsManager: full-screen ad failed to show: ${adError.message}")
                try {
                    onDismiss()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "AdsManager: show-failure callback failed", e)
                }
            }
        }

    private fun preloadAppOpen(context: Context) {
        if (isLoadingAppOpen || appOpenAd != null) return
        isLoadingAppOpen = true
        try {
            AppOpenAd.load(
                context,
                APP_OPEN_AD_UNIT,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isLoadingAppOpen = false
                        appOpenAd = ad
                        ad.fullScreenContentCallback = buildShowCallback(
                            onDismiss = {
                                appOpenAd = null
                                preloadAppOpen(context.applicationContext)
                            },
                        )
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingAppOpen = false
                        LogUtil.w(AppConfig.TAG, "AdsManager: app open ad failed to load: ${error.message}")
                    }
                },
            )
        } catch (e: Exception) {
            isLoadingAppOpen = false
            LogUtil.e(AppConfig.TAG, "AdsManager: app open ad load failed", e)
        }
    }

    private fun preloadInterstitial(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingInterstitial = false
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingInterstitial = false
                        LogUtil.w(AppConfig.TAG, "AdsManager: interstitial failed to load: ${error.message}")
                    }
                },
            )
        } catch (e: Exception) {
            isLoadingInterstitial = false
            LogUtil.e(AppConfig.TAG, "AdsManager: interstitial load failed", e)
        }
    }

    private fun preloadRewarded(context: Context) {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        try {
            RewardedAd.load(
                context,
                REWARDED_AD_UNIT,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewarded = false
                        rewardedAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingRewarded = false
                        LogUtil.w(AppConfig.TAG, "AdsManager: rewarded ad failed to load: ${error.message}")
                    }
                },
            )
        } catch (e: Exception) {
            isLoadingRewarded = false
            LogUtil.e(AppConfig.TAG, "AdsManager: rewarded load failed", e)
        }
    }

    /**
     * Ads are main-process only: :daemon, :tasks and :bg must never load the SDK.
     *
     * Platform boundary: Application.getProcessName() exists since API 28; below that the same
     * value is read from ActivityThread.currentProcessName(), which is what the framework call
     * delegates to and is stable on every supported API level. Remove the reflection fallback
     * when minSdk reaches 28.
     */
    private fun isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentProcessName = activityThread.getDeclaredMethod("currentProcessName")
                currentProcessName.isAccessible = true
                currentProcessName.invoke(null) as? String
            }.getOrNull()
        }
        return processName == BuildConfig.APPLICATION_ID
    }
}
