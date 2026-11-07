package com.v2ray.ang.ui.license

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.LicenseManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Derafsh premium activation screen (task 5): shows the Device ID (copyable), the current
 * license status, the code input with the activate action and the server re-check; opens
 * the Telegram bot for buying a code when [AppConfig.DERAFSH_BOT_URL] is configured.
 */
class LicenseActivity : BaseComponentActivity() {

    private val viewModel: LicenseViewModel by viewModels()

    /** v1.2.52 auto-connect: the code held while the VPN permission prompt is on screen. */
    private var pendingCode: String? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val code = pendingCode
            pendingCode = null
            when {
                result.resultCode == RESULT_OK && code != null -> activateAfterTunnel(code)
                code != null -> viewModel.activate(code) // denied — try the direct call anyway
            }
        }

    private fun copyDeviceId(deviceId: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Derafsh Device ID", deviceId))
        viewModel.toast(R.string.license_copied)
    }

    /**
     * v1.2.52 (user feedback): activation must ride the tunnel — the worker sits behind
     * Cloudflare and some Iranian networks only reach it through the VPN. Make sure the
     * VPN is up before calling the API; the permission prompt (once per install) is part
     * of that flow. If the user declines, activation still proceeds directly.
     */
    private fun activateClicked(code: String) {
        pendingCode = code
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
                return
            }
        }
        activateAfterTunnel(code)
    }

    private fun activateAfterTunnel(code: String) {
        lifecycleScope.launch {
            ensureTunnelRunning()
            // v1.2.54: on a network failure the VPN bring-up runs once more before the
            // single automatic retry (cold tunnels often miss the first window).
            viewModel.activate(code) { ensureTunnelRunning() }
        }
    }

    /**
     * Best-effort tunnel bootstrap: ask the daemon its state (MSG_REGISTER_CLIENT →
     * RUNNING / NOT_RUNNING); when nothing answers or the answer is "not running", run the
     * regular service start flow and wait briefly for the start broadcast. NEVER blocks
     * activation: on any failure the API call proceeds anyway (imsoroush.top is reachable
     * without the tunnel on most networks).
     *
     * v1.2.54 (user feedback: "network error" on activate): a fresh install has no server
     * selected, and the start flow refuses to run without one — on networks where the
     * worker is only reachable through the tunnel that deadlocked activation into a
     * guaranteed network error. When the selection is empty, the first profile of the
     * library is auto-selected so the bootstrap can actually bring the tunnel up.
     */
    private suspend fun ensureTunnelRunning(): Boolean {
        val appContext = applicationContext
        val listener = ServiceStateListener()
        try {
            ContextCompat.registerReceiver(
                appContext, listener, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags()
            )
            MessageHelper.sendMsg2Service(appContext, AppConfig.MSG_REGISTER_CLIENT, "")
            if (listener.awaitState(1_500L) == true) return true
            if (MmkvManager.getSelectServer().isNullOrEmpty()) {
                val first = MmkvManager.decodeAllServerList().firstOrNull() ?: return false
                MmkvManager.setSelectServer(first)
            }
            listener.reset()
            LauncherManager.startService(this)
            return listener.awaitState(15_000L) == true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "License: auto-connect before activation failed", e)
            return false
        } finally {
            runCatching { appContext.unregisterReceiver(listener) }
        }
    }

    /** Catches the daemon's state broadcasts: RUNNING / START_SUCCESS → true, else false. */
    private class ServiceStateListener : BroadcastReceiver() {
        private val state = MutableStateFlow<Boolean?>(null)

        fun reset() {
            state.value = null
        }

        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> state.value = true
                AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE -> state.value = false
            }
        }

        suspend fun awaitState(timeoutMs: Long): Boolean? = withTimeoutOrNull(timeoutMs) {
            state.filterNotNull().first()
        }
    }

    @Composable
    override fun ScreenContent() {
        LicenseScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onCopyDeviceId = ::copyDeviceId,
            onActivate = ::activateClicked,
        )
    }
}

@Composable
fun LicenseScreen(
    viewModel: LicenseViewModel,
    onBackClick: () -> Unit,
    onCopyDeviceId: (String) -> Unit,
    onActivate: (String) -> Unit,
) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val licenseState by viewModel.licenseState.collectAsStateWithLifecycle()
    var codeInput by rememberSaveable { mutableStateOf("") }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_license_activation),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            StatusCard(state = licenseState)

            DeviceIdCard(
                deviceId = deviceId,
                onCopy = { onCopyDeviceId(deviceId) }
            )

            FormTextField(
                label = stringResource(R.string.license_code_label),
                value = codeInput,
                onValueChange = { codeInput = LicenseViewModel.filterCode(it) },
                keyboardType = KeyboardType.Ascii,
                placeholder = stringResource(R.string.license_code_placeholder),
                maxLines = 1,
                // v1.2.52: the 24-char code must fit and read clearly — monospace, sized
                // to the box, centered, with the expected shape spelled out below it.
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
                supportingText = stringResource(R.string.license_code_format_hint),
            )

            Button(
                onClick = { onActivate(codeInput) },
                enabled = codeInput.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.license_activate))
            }

            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_check_update_24dp),
                title = stringResource(R.string.license_recheck),
                onClick = { viewModel.refresh() }
            )

            if (AppConfig.DERAFSH_BOT_URL.isNotBlank()) {
                SettingsMenuItem(
                    title = stringResource(R.string.license_get_code),
                    subtitle = stringResource(R.string.license_get_code_summary),
                    onClick = { Utils.openUri(context, AppConfig.DERAFSH_BOT_URL) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun StatusCard(state: LicenseManager.LicenseState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.active && state.expiresAt != null) {
                Text(
                    text = stringResource(R.string.license_status_active_until, LicenseViewModel.formatExpiry(state.expiresAt)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.inGrace) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.license_status_grace),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.license_status_inactive),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.lastError?.let { errorKey ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(LicenseViewModel.errorKeyToRes(errorKey)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIdCard(
    deviceId: String,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.license_device_id),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.license_device_id_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.license_copy)
                    )
                }
            }
        }
    }
}
