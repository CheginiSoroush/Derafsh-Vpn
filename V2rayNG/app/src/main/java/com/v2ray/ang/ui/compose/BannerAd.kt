package com.v2ray.ang.ui.compose

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.AdsManager
import com.v2ray.ang.util.LogUtil

/** Horizontal padding the host screen applies around the banner; used to derive the ad width. */
private const val BANNER_HORIZONTAL_PADDING_DP = 48

/** Below this width the anchored adaptive API is unreliable; fall back to the 320x50 banner. */
private const val MIN_ADAPTIVE_WIDTH_DP = 320

/**
 * Anchored adaptive banner for the main connect screen.
 *
 * Banner waterfall: Tapsell (rial) is the seam for when the SDK lands; AdMob (dollar) is the
 * network leg; the branded house banner is the always-fill fallback — a no-fill or load
 * failure falls through instead of leaving an empty gap.
 *
 * The ad view is created once and kept across recompositions; it collapses to zero height
 * until an ad is loaded, and a load failure swaps in the house banner. The view is destroyed
 * when the composable leaves the composition. Composed only in the main (UI) process —
 * MainScreen lives there.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (BuildConfig.ADS_PROVIDER.equals("house", ignoreCase = true)) {
        DerafshHouseBanner(modifier)
        return
    }
    val context = LocalContext.current
    val adaptiveWidthDp = (LocalConfiguration.current.screenWidthDp - BANNER_HORIZONTAL_PADDING_DP)
        .coerceAtLeast(0)

    val isLoaded = remember { mutableStateOf(false) }
    val loadFailed = remember { mutableStateOf(false) }
    val adView = remember(adaptiveWidthDp) {
        createBannerAdView(context, adaptiveWidthDp, isLoaded, loadFailed)
    }

    DisposableEffect(adView) {
        try {
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "BannerAd: loadAd failed", e)
            loadFailed.value = true
        }
        onDispose {
            try {
                adView.destroy()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "BannerAd: destroy failed", e)
            }
        }
    }

    when {
        isLoaded.value -> AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth(),
        )
        // Network leg came up empty: the house banner keeps the slot warm (and clickable)
        // instead of leaving a hole in the layout.
        loadFailed.value -> DerafshHouseBanner(modifier)
    }
}

private fun createBannerAdView(
    context: Context,
    adaptiveWidthDp: Int,
    isLoaded: MutableState<Boolean>,
    loadFailed: MutableState<Boolean>,
): AdView {
    val adSize = if (adaptiveWidthDp >= MIN_ADAPTIVE_WIDTH_DP) {
        try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adaptiveWidthDp)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "BannerAd: adaptive size failed, falling back to 320x50", e)
            AdSize.BANNER
        }
    } else {
        AdSize.BANNER
    }

    return AdView(context).apply {
        setAdSize(adSize)
        adUnitId = AdsManager.BANNER_AD_UNIT
        // AdListener callbacks are invoked on the main thread, so writing snapshot state here
        // is safe.
        adListener = object : AdListener() {
            override fun onAdLoaded() {
                isLoaded.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoaded.value = false
                loadFailed.value = true
                LogUtil.w(AppConfig.TAG, "BannerAd: failed to load: ${error.message}")
            }
        }
    }
}
