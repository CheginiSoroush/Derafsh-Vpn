package com.v2ray.ang.ui.license

import com.v2ray.ang.R
import com.v2ray.ang.handler.LicenseManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM tests for the activation screen's pure helpers: the license-api error-key →
 * localized-message table and the code input shaping.
 */
class LicenseViewModelTest {

    @Test
    fun `every license-api error key maps to a localized message`() {
        assertEquals(R.string.license_error_bad_request, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_BAD_REQUEST))
        assertEquals(R.string.license_error_invalid_code, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_INVALID_CODE))
        assertEquals(R.string.license_error_expired_code, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_EXPIRED_CODE))
        assertEquals(R.string.license_error_device_mismatch, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_DEVICE_MISMATCH))
        assertEquals(R.string.license_error_revoked, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_REVOKED))
        assertEquals(R.string.license_error_rate_limited, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_RATE_LIMITED))
        assertEquals(R.string.license_error_network, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_NETWORK))
        assertEquals(R.string.license_error_server, LicenseViewModel.errorKeyToRes(LicenseManager.ERR_SERVER_ERROR))
    }

    @Test
    fun `unknown error keys fall back to the generic server error`() {
        assertEquals(R.string.license_error_server, LicenseViewModel.errorKeyToRes("SOMETHING_NEW"))
        assertEquals(R.string.license_error_server, LicenseViewModel.errorKeyToRes(""))
    }

    @Test
    fun `code input is upper-cased and stripped to code characters`() {
        assertEquals("DRF-ABCDE-FGHIJ-KLMNO-PQRST", LicenseViewModel.filterCode("drf-abcde-fghij-klmno-pqrst"))
        assertEquals("DRFABCDEFGHIJ", LicenseViewModel.filterCode("drf abcde fghij"))
        assertEquals("A1B2C3D4E5F6", LicenseViewModel.filterCode("a1b2c3d4e5f6"))
    }

    @Test
    fun `code input drops non-ASCII and separator characters`() {
        assertEquals("DRFABCDE", LicenseViewModel.filterCode("drf، abcde!"))
        assertEquals("FABDE", LicenseViewModel.filterCode("در f ab de"))
        assertEquals("", LicenseViewModel.filterCode("کد فعال‌سازی"))
    }

    @Test
    fun `code input is capped at the maximum length`() {
        val long = "A".repeat(100)
        assertEquals(27, LicenseViewModel.filterCode(long).length)
    }

    @Test
    fun `a full 27-char 4-group code survives the filter untouched`() {
        // DRF prefix + 20-char body + 3 separators = 27 chars (MAX_CODE_LENGTH);
        // the old cap of 24 silently cut the last group.
        val fullCode = "DRF-ABCDE-FGHIJ-KLMNO-PQRST"
        assertEquals(fullCode, LicenseViewModel.filterCode(fullCode.lowercase()))
    }
}
