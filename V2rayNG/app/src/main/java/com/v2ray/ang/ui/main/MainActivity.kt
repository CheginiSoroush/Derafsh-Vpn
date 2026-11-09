package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AdsManager
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.IntegrityManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.OnboardingManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.onboarding.OnboardingActivity
import com.v2ray.ang.ui.server.ProfileEditorResult
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHttpActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        /** App-open ads show once per process: cold app open only, never on resume from background. */
        private var wasAppOpenShownThisSession = false

        /** Derafsh brand moment: total time the splash stays on screen before the content shows. */
        private const val SPLASH_HOLD_MILLIS = 3000L
        private const val SPLASH_EXIT_MILLIS = 450L

        /** v1.2.61 pool heal: settle delay after the tunnel starts before the forced
         *  live refetch of the built-in source (lets routing actually carry traffic). */
        private const val POOL_HEAL_DELAY_MS = 4_000L
    }

    /** Kept true until [SPLASH_HOLD_MILLIS] elapsed; read by the splash keep-on-screen condition. */
    private var splashVisible = true

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    data.getStringExtra(ProfileEditorResult.EXTRA_GUID) == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { splashVisible }
        splashScreen.setOnExitAnimationListener { provider ->
            // Branded exit: the splash view gently zooms toward the viewer while fading out.
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(SPLASH_EXIT_MILLIS)
                .withEndAction { provider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)

        // First launch: the onboarding carousel introduces Derafsh and collects privacy-
        // policy consent BEFORE anything else (ads, maintenance, UI) runs; MainActivity
        // relaunches itself fresh once onboarding completes.
        if (OnboardingManager.needsOnboarding()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Ads start preloading behind the splash so the app-open ad is ready when it lifts.
        AdsManager.init(this)
        checkAppIntegrity()
        mainViewModel.onAction(MainAction.Initialize)
        // v1.2.56 (user report): the built-in source pool must refresh when the app
        // opens — the 4h background cadence alone left users on stale configs.
        mainViewModel.refreshBuiltInSourceOnOpen()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        // Hold the branded splash long enough to register, then reveal content and gate it
        // with the app-open ad (preloaded while the splash was showing). The ad waterfall
        // (Tapsell → AdMob → house) runs inside AdsManager.
        lifecycleScope.launch {
            delay(SPLASH_HOLD_MILLIS)
            splashVisible = false
            if (!wasAppOpenShownThisSession) {
                wasAppOpenShownThisSession = true
                AdsManager.showAppOpen(this@MainActivity)
            }
        }

        // Monetization: the interstitial fires once per successful connection — never on
        // boot/auto-start (those sessions are already running at first observation), and
        // the frequency cap inside AdsManager keeps repeats polite.
        // O2: the uiState collector runs under repeatOnLifecycle(RESUMED), so
        // AdsManager.showInterstitial is never attempted while the activity is
        // backgrounded (e.g. a QS-tile tunnel start while the app sits in recents):
        // the collection pauses below RESUMED and reseeds from the current state on
        // each re-entry, so a background-started session never triggers the ad.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var wasRunning = mainViewModel.uiState.value.isRunning
                mainViewModel.uiState.collect { state ->
                    val running = state.isRunning
                    if (running && !wasRunning) {
                        AdsManager.showInterstitial(this@MainActivity) { }
                        // v1.2.61 (pool-server toast): when the last on-open refresh fell
                        // back to the bundled snapshot, the worker was unreachable — on
                        // SNI-filtering ISPs it only becomes reachable THROUGH the tunnel
                        // that just came up. Refetch once over it (forced: the bundled
                        // "success" had stamped the success-only on-open throttle), after
                        // a short settle delay so routing is actually in place.
                        if (AngConfigManager.lastImportUsedBundledBackup) {
                            lifecycleScope.launch {
                                delay(POOL_HEAL_DELAY_MS)
                                mainViewModel.refreshBuiltInSourceOnOpen(force = true)
                            }
                        }
                    }
                    wasRunning = running
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // v1.2.57: refresh the built-in pool on foreground returns too, not only on a cold
        // onCreate — users keep the app in recents for days and onCreate never re-runs.
        // The success-only throttle inside refreshBuiltInSourceOnOpen keeps this cheap and
        // lets a previously failed attempt heal on the very next entry.
        mainViewModel.refreshBuiltInSourceOnOpen()
    }

    /**
     * Anti-tamper gate: warn on modified/rooted installs. Monetization hardening only —
     * the app remains fully functional for VPN use.
     */
    private fun checkAppIntegrity() {
        val report = IntegrityManager.check(this)
        if (!report.isTampered) return
        if (report.signatureMismatch) {
            LogUtil.w(AppConfig.TAG, "Integrity: unofficial signature — patched build?")
            toast(R.string.toast_integrity_modified)
        } else if (report.isRooted) {
            toast(R.string.toast_integrity_root)
        } else if (report.isHooked) {
            LogUtil.w(AppConfig.TAG, "Integrity: instrumentation environment detected")
            toast(R.string.toast_integrity_hooking)
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        MainScreen(
            mainViewModel = mainViewModel,
            onAction = { action ->
                when (action) {
                    MainAction.ToggleService -> handleFabAction()
                    MainAction.TestCurrentServer -> handleLayoutTestClick()
                    MainAction.ImportQRcode -> importQRcode()
                    MainAction.ImportClipboard -> importClipboard()
                    MainAction.ImportConfigLocal -> importConfigLocal()
                    is MainAction.ImportManually -> importManually(action.type)
                    MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
                    MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
                    is MainAction.SelectServer -> setSelectServer(action.guid)
                    is MainAction.EditServer -> editServer(action.guid, action.profile)
                    is MainAction.ShareClipboard -> shareToClipboard(action.guid)
                    is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
                    is MainAction.OpenSubscriptionLink -> openSubscriptionLink(action.url)
                    else -> mainViewModel.onAction(action)
                }
            },
            onNavigate = { route -> navigateTo(route) },
        )
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun openSubscriptionLink(url: String) {
        // Links come from a remote panel, so only http(s) and tg:// are ever handed to ACTION_VIEW.
        val safeUrl = SubscriptionInfoParser.sanitizeLink(url) ?: return
        Utils.openUri(this, safeUrl)
    }

    private fun navigateTo(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            // Monetization: connect starts immediately — the interstitial moved to AFTER a
            // successful connection (see the uiState collector in onCreate), so the tunnel
            // is never gated behind an ad.
            requestServiceStart()
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        if (mainViewModel.isAutoPickFastestEnabled()) {
            // Presentation-only phases while the bulk real-ping test runs and the winner's
            // tunnel is brought up — the status label tracks them honestly.
            mainViewModel.markStartRequested(ConnectPhase.TestingServers)
            mainViewModel.pickFastestServer { guid ->
                if (guid == null) {
                    // Storage holds no connectable profile at all; keep the classic toast.
                    toast(R.string.title_file_chooser)
                    return@pickFastestServer
                }
                // Update the server card before the service start so the picked server is shown.
                mainViewModel.updateSelectedGuid(guid)
                mainViewModel.markTunnelStarting()
                LauncherManager.startService(this, guid)
            }
        } else {
            if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
                toast(R.string.title_file_chooser)
                return
            }
            // Presentation-only connecting phase; start/stop mechanics are unchanged.
            mainViewModel.markStartRequested(ConnectPhase.EstablishingTunnel)
            LauncherManager.startService(this)
        }
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            // A SAF document provider can block for seconds (cloud-backed files:
            // Drive/OneDrive/nextcloud), so the read must never run on the main
            // thread — the ActivityResult callback always does. A14 (audit cycle 7).
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: return@launch
                    withContext(Dispatchers.Main) {
                        mainViewModel.onAction(MainAction.ImportBatchConfig(text))
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
                }
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
