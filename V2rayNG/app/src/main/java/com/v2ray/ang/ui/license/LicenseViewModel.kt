package com.v2ray.ang.ui.license

import android.app.Application
import androidx.annotation.StringRes
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.LicenseManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backing state owner of the activation screen (task 5, premium-design.md §6).
 *
 * All license work goes through [LicenseManager] — the screen itself stays a pure
 * presenter and owns the uncommitted code input as transient `rememberSaveable` state.
 * The helper tables ([errorKeyToRes], [filterCode]) are JVM-testable so every
 * license-api error key is guaranteed a localized message and the input shaping is
 * pinned down.
 */
class LicenseViewModel(application: Application) : BaseViewModel(application) {

    /** Formatted Device ID (`XXXX-XXXX-XXXX-XXXX`) shown on the screen and copied around. */
    private val _deviceId = MutableStateFlow(LicenseManager.getDeviceId(application))
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    /** The last known license state, seeded from MMKV (no network on open). */
    private val _licenseState = MutableStateFlow(LicenseManager.current(application))
    val licenseState: StateFlow<LicenseManager.LicenseState> = _licenseState.asStateFlow()

    /** Activates [code]; success refreshes the state card, failure toasts the mapped message. */
    fun activate(code: String) {
        if (code.isBlank()) return
        launchLoading {
            try {
                LicenseManager.activate(getApplication(), code)
                    .onSuccess { state ->
                        _licenseState.value = state
                        toastSuccess(R.string.license_activated)
                    }
                    .onFailure { failure ->
                        val errorKey = (failure as? LicenseManager.LicenseException)?.errorKey
                            ?: LicenseManager.ERR_SERVER_ERROR
                        LogUtil.i(AppConfig.TAG, "Activate failed: $errorKey")
                        toastError(errorKeyToRes(errorKey))
                    }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Activate crashed", e)
                toastError(R.string.license_error_server)
            }
        }
    }

    /** Re-checks the license with the server (design §7.3); network failure keeps the cached state. */
    fun refresh() {
        launchLoading {
            val state = LicenseManager.refresh(getApplication())
            _licenseState.value = state
            if (state.lastError == LicenseManager.ERR_NETWORK) {
                toastError(R.string.license_error_network)
            }
        }
    }

    companion object {

        private const val MAX_CODE_LENGTH = 24

        /**
         * Shapes raw user input into a code-form string: upper-cased, only A-Z / 0-9 /
         * dashes kept, capped at [MAX_CODE_LENGTH] (23 = DRF prefix + 20-char body).
         */
        fun filterCode(raw: String): String =
            raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }.take(MAX_CODE_LENGTH)

        /** Expiry formatter used by the status card (`yyyy/MM/dd` in the device timezone). */
        fun formatExpiry(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

        /**
         * Maps a license-api error key (or [LicenseManager.ERR_NETWORK]) to its localized
         * message; unknown keys fall back to the generic server error.
         */
        @StringRes
        fun errorKeyToRes(errorKey: String): Int = when (errorKey) {
            LicenseManager.ERR_BAD_REQUEST -> R.string.license_error_bad_request
            LicenseManager.ERR_INVALID_CODE -> R.string.license_error_invalid_code
            LicenseManager.ERR_EXPIRED_CODE -> R.string.license_error_expired_code
            LicenseManager.ERR_DEVICE_MISMATCH -> R.string.license_error_device_mismatch
            LicenseManager.ERR_REVOKED -> R.string.license_error_revoked
            LicenseManager.ERR_RATE_LIMITED -> R.string.license_error_rate_limited
            LicenseManager.ERR_UPDATE_REQUIRED -> R.string.license_error_update_required
            LicenseManager.ERR_NETWORK -> R.string.license_error_network
            else -> R.string.license_error_server
        }
    }
}
