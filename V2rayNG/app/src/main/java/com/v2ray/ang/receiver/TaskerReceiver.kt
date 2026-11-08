package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil

class TaskerReceiver : BroadcastReceiver() {

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It retrieves the bundle from the intent and checks the switch and guid values.
     * Depending on the switch value, it starts or stops the V2Ray service.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID).orEmpty()

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else if (switch) {
                if (guid == AppConfig.TASKER_DEFAULT_GUID) {
                    LauncherManager.startServiceFromToggle(context)
                } else if (MmkvManager.decodeServerConfig(guid) == null) {
                    // A26: unknown/stale guid (profile deleted after the Tasker scene was
                    // created, or a foreign FIRE_SETTING broadcast — the receiver is
                    // exported). LauncherManager.startService persists the guid via
                    // setSelectServer BEFORE validating it, which poisons the
                    // selected-server key and silently breaks every later start
                    // (tile/shortcut/boot) until the user re-selects a profile.
                    LogUtil.w(AppConfig.TAG, "TaskerReceiver: unknown profile guid, ignoring")
                } else {
                    LauncherManager.startService(context, guid)
                }
            } else {
                LauncherManager.stopService(context)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing Tasker broadcast", e)
        }
    }
}
