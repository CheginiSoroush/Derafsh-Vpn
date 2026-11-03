package com.v2ray.ang.handler

import android.app.Activity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil

/**
 * Iranian ad leg of the Derafsh monetization waterfall (Tapsell — rial-settled inventory).
 *
 * Waterfall contract at every placement: Tapsell first (rial), then AdMob (dollar), then the
 * Derafsh house ad as the always-fill fallback. Any leg that is unconfigured, unloaded or
 * failed simply falls through — a missing fill never blocks the user.
 *
 * THIS FILE IS THE SDK SEAM: the Tapsell Plus SDK is intentionally not added yet because no
 * app key exists (nothing would serve without one). Once the panel account is ready, put the
 * app key + slot ids into the TAPSELL_* BuildConfig fields in app/build.gradle.kts, add the
 * SDK dependency, and implement the load/show bodies here — nothing else in the app changes.
 */
object TapsellAds {

    /** False until a real app key is configured; the waterfall then skips this leg. */
    val isConfigured: Boolean
        get() = BuildConfig.TAPSELL_APP_KEY.isNotBlank()

    /** True when the ad was shown; false = leg unavailable → caller falls to the next one. */
    fun showAppOpen(activity: Activity): Boolean {
        if (!isConfigured) return false
        // TODO(Tapsell SDK): show the preloaded TAPSELL app-open/interstitial here.
        LogUtil.i(AppConfig.TAG, "TapsellAds: app-open requested but SDK not wired yet")
        return false
    }

    fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean {
        if (!isConfigured) return false
        // TODO(Tapsell SDK): show TAPSELL_INTERSTITIAL_ID and call onClosed on dismissal.
        LogUtil.i(AppConfig.TAG, "TapsellAds: interstitial requested but SDK not wired yet")
        return false
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onClosed: () -> Unit): Boolean {
        if (!isConfigured) return false
        // TODO(Tapsell SDK): show TAPSELL_REWARDED_ID; grant onRewarded on completion.
        LogUtil.i(AppConfig.TAG, "TapsellAds: rewarded requested but SDK not wired yet")
        return false
    }
}
