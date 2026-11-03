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
