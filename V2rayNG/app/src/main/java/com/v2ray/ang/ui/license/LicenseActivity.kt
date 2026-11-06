package com.v2ray.ang.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.LicenseManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.Utils

/**
 * Derafsh premium activation screen (task 5): shows the Device ID (copyable), the current
 * license status, the code input with the activate action and the server re-check; opens
 * the Telegram bot for buying a code when [AppConfig.DERAFSH_BOT_URL] is configured.
 */
class LicenseActivity : BaseComponentActivity() {

    private val viewModel: LicenseViewModel by viewModels()

    private fun copyDeviceId(deviceId: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Derafsh Device ID", deviceId))
        viewModel.toast(R.string.license_copied)
    }

    @Composable
    override fun ScreenContent() {
        LicenseScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onCopyDeviceId = ::copyDeviceId,
        )
    }
}

@Composable
fun LicenseScreen(
    viewModel: LicenseViewModel,
    onBackClick: () -> Unit,
    onCopyDeviceId: (String) -> Unit,
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
            )

            Button(
                onClick = { viewModel.activate(codeInput) },
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
