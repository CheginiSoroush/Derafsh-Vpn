package com.v2ray.ang.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.handler.AdsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.DerafshHouseInterstitial

/**
 * Full-screen Derafsh house ad host (ADS_PROVIDER = "house"). AdsManager routes app-open,
 * interstitial and rewarded slots here; when the screen closes, pending callbacks stored in
 * [AdsManager] are invoked so caller flows (connect / extend session) continue unchanged.
 */
class HouseAdActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        // The Continue button and the auto-dismiss timer both route through
        // AdsManager.onHouseAdDismissed(); the system back press (or any other
        // finishing destroy) does not. Without this, pending reward/close
        // callbacks would survive as stale state and fire on a LATER, unrelated
        // house ad (e.g. a backed-out "extend session" reward auto-connecting
        // the VPN on the next app-open ad). House policy: closing the screen
        // always resolves the pending callbacks. Rotation/recreation is not
        // finishing, so the pending callbacks stay with the replacement screen.
        if (isFinishing) {
            AdsManager.onHouseAdDismissed()
        }
        super.onDestroy()
    }

    @Composable
    override fun ScreenContent() {
        DerafshHouseInterstitial(onClosed = {
            AdsManager.onHouseAdDismissed()
            finish()
        })
    }

    companion object {
        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, HouseAdActivity::class.java))
        }
    }
}
