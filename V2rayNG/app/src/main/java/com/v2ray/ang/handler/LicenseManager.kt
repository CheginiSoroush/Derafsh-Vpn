package com.v2ray.ang.handler

import android.content.Context
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Derafsh premium license client (premium-design.md §6).
 *
 * Owns everything premium-unlock related on the device: the stable Device ID, code
 * activation against the license API, periodic re-validation and the offline grace
 * window. Pure logic + MMKV storage — no UI here (the license screens of tasks 5-7
 * build on these calls).
 *
 * Security model:
 *  - The APK contains no premium content — only a user-entered code and this device's ID.
 *  - A code binds to the FIRST device that activates it (enforced server-side, where the
 *    HMAC secret lives); every later activation from another device gets DEVICE_MISMATCH.
 *  - Server-side revocation propagates through the periodic [refresh]; until it lands,
 *    the [AppConfig.LICENSE_GRACE_MS] offline window is the worst-case exposure.
 *
 * Time model (anti clock-shift): the reference "now" is max(device time, last
 * server-confirmed time). Rolling the device clock back cannot extend a license; rolling
 * it forward can only lock it early, and one successful refresh fixes that.
 *
 * Failure policy: network failures NEVER lock the app. [current] is pure MMKV + math;
 * [refresh] falls back to the cached state on any error; premium is only ever taken away
 * by an explicit server answer (revoked / inactive) or by the grace window expiring.
 */
object LicenseManager {

    /** Machine-readable error keys (in [LicenseException.errorKey]) — aligned with the license-api contract. */
    const val ERR_BAD_REQUEST = "BAD_REQUEST"
    const val ERR_INVALID_CODE = "INVALID_CODE"
    const val ERR_EXPIRED_CODE = "EXPIRED_CODE"
    const val ERR_DEVICE_MISMATCH = "DEVICE_MISMATCH"
    const val ERR_REVOKED = "REVOKED"
    const val ERR_RATE_LIMITED = "RATE_LIMITED"
    const val ERR_SERVER_ERROR = "SERVER_ERROR"

    /** The worker refused this outdated/legacy build (force-update gate, v1.2.53). */
    const val ERR_UPDATE_REQUIRED = "UPDATE_REQUIRED"

    /** Client-side only (never produced by the server): the API is unreachable. */
    const val ERR_NETWORK = "NETWORK"

    /** Snapshot of the license status for the UI. */
    data class LicenseState(
        val active: Boolean,
        val expiresAt: Long?,
        val inGrace: Boolean,
        val lastError: String?,
    )

    /** Carries a machine-readable error key from [activate] to the UI. */
    class LicenseException(val errorKey: String) : Exception(errorKey)

    // v1.2.54: relaxed timeouts — on Iranian networks the tunnel handshake plus the
    // TLS setup to the Cloudflare edge routinely eats the old 8s budget on the first
    // attempt, which surfaced as spurious "network error" toasts during activation.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Canonical code charset (base32 RFC 4648 without padding) and length after normalization. */
    private val CODE_REGEX = Regex("^[A-Z2-7]{20}$")

    // ------------------------------------------------------------------ device id

    /**
     * The stable, user-visible Device ID in `XXXX-XXXX-XXXX-XXXX` form.
     *
     * Derived once from ANDROID_ID (SHA-256, first 16 hex chars, upper-cased) and cached in
     * MMKV so it stays fixed for the whole install even if ANDROID_ID stops resolving later
     * (rare devices return null — a random fallback is hashed in and cached on first use).
     */
    fun getDeviceId(context: Context): String = formatDeviceId(getDeviceIdCanonical(context))

    /** Canonical (dash-less) Device ID, the form compared against and sent in status queries. */
    fun getDeviceIdCanonical(context: Context): String {
        MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_DEVICE_ID)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val source = if (!androidId.isNullOrBlank()) androidId else UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        val canonical = digest.joinToString(separator = "") { "%02X".format(it) }.take(16)
        MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_DEVICE_ID, canonical)
        return canonical
    }

    private fun formatDeviceId(canonical: String): String = canonical.chunked(4).joinToString("-")

    // ------------------------------------------------------------------ activate

    /**
     * Activates [code] for this device: POST /v1/activate.
     *
     * Normalizes the user input first (separators and a DRF prefix are stripped, upper-cased,
     * strict base32 shape check) so obviously malformed codes never hit the network. On
     * success the license is persisted (code, expiry, server-confirmed time, bound device)
     * and the fresh state is returned. Server error keys pass through unchanged in
     * [LicenseException.errorKey]; unreachable servers map to [ERR_NETWORK].
     */
    suspend fun activate(context: Context, code: String): Result<LicenseState> = withContext(Dispatchers.IO) {
        val normalized = normalizeCode(code)
        if (!CODE_REGEX.matches(normalized)) {
            return@withContext Result.failure(LicenseException(ERR_BAD_REQUEST))
        }
        val deviceIdCanonical = getDeviceIdCanonical(context)
        try {
            val payload = JSONObject()
                .put("device_id", formatDeviceId(deviceIdCanonical))
                .put("code", normalized)
            val request = Request.Builder()
                .url("${AppConfig.LICENSE_API_BASE.trimEnd('/')}/v1/activate")
                .header("User-Agent", "Derafsh-VPN")
                .header(AppConfig.LICENSE_APP_VERSION_HEADER, BuildConfig.VERSION_CODE.toString())
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val serverTimeMs = response.headers.getDate("Date")?.time ?: 0L
                val json = JSONObject(response.body?.string().orEmpty())
                if (response.isSuccessful && json.optBoolean("ok")) {
                    val expiresAt = parseIsoMillis(json.optString("expires_at"))
                    if (expiresAt == null || expiresAt <= 0L) {
                        return@use Result.failure(LicenseException(ERR_SERVER_ERROR))
                    }
                    persistLicense(deviceIdCanonical, normalized, expiresAt, serverTimeMs)
                    Result.success(current(context))
                } else {
                    val errorKey = json.optString("error", ERR_SERVER_ERROR).ifBlank { ERR_SERVER_ERROR }
                    LogUtil.i(AppConfig.TAG, "License activate rejected: $errorKey (HTTP ${response.code})")
                    Result.failure(LicenseException(errorKey))
                }
            }
        } catch (e: JSONException) {
            LogUtil.e(AppConfig.TAG, "License activate: malformed server response", e)
            Result.failure(LicenseException(ERR_SERVER_ERROR))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "License activate failed", e)
            Result.failure(LicenseException(ERR_NETWORK))
        }
    }

    // ------------------------------------------------------------------ refresh

    /**
     * Re-validates the stored license with GET /v1/status (design §7.3: run every 24 h).
     *
     *  - Server reachable, license active → refresh the expiry and the server-confirmed time.
     *  - Server reachable, license NOT active (revoked or expired server-side) → clear the
     *    local license immediately: the server IS reachable, so no grace is due.
     *  - Any failure → the cached state is returned untouched (grace keeps premium alive),
     *    with [LicenseState.lastError] set to [ERR_NETWORK] / [ERR_SERVER_ERROR] for the UI.
     *  - Nothing stored → no request at all.
     */
    suspend fun refresh(context: Context): LicenseState = withContext(Dispatchers.IO) {
        val cached = current(context)
        val code = MmkvManager.decodeSettingsString(AppConfig.PREF_LICENSE_CODE)
        if (code.isNullOrBlank()) return@withContext cached
        try {
            val request = Request.Builder()
                .url("${AppConfig.LICENSE_API_BASE.trimEnd('/')}/v1/status?device_id=${getDeviceIdCanonical(context)}")
                .header("User-Agent", "Derafsh-VPN")
                .header(AppConfig.LICENSE_APP_VERSION_HEADER, BuildConfig.VERSION_CODE.toString())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val serverTimeMs = response.headers.getDate("Date")?.time ?: 0L
                val json = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful || !json.optBoolean("ok")) {
                    // 426 UPDATE_REQUIRED: the worker force-update gate refused this
                    // build; the UI surfaces the dedicated message instead of a generic
                    // server error, and the cached state (grace) is kept untouched.
                    if (response.code == 426) {
                        return@use cached.copy(lastError = ERR_UPDATE_REQUIRED)
                    }
                    return@use cached.copy(lastError = ERR_SERVER_ERROR)
                }
                if (json.optBoolean("active")) {
                    val expiresAt = parseIsoMillis(json.optString("expires_at"))
                    if (expiresAt != null && expiresAt > 0L) {
                        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_EXPIRES, expiresAt)
                    }
                    if (serverTimeMs > 0L) {
                        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_LAST_OK, serverTimeMs)
                    }
                    current(context)
                } else {
                    clearLicense()
                    // Premium pool access dies with the license: if the built-in
                    // subscription points at the locked premium endpoint, revert it to
                    // the last free source (task 9).
                    DerafshSourceManager.onLicenseLost(context)
                    LicenseState(active = false, expiresAt = null, inGrace = false, lastError = null)
                }
            }
        } catch (e: JSONException) {
            LogUtil.e(AppConfig.TAG, "License refresh: malformed server response", e)
            cached.copy(lastError = ERR_SERVER_ERROR)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "License refresh failed — keeping cached state (grace)", e)
            cached.copy(lastError = ERR_NETWORK)
        }
    }

    // ------------------------------------------------------------------ pure state

    /**
     * The current license state, computed ONLY from MMKV — no network, never blocks.
     *
     * Premium is unlocked iff a license is stored, belongs to this device, has not expired
     * against the reference time, and was confirmed by the server within the grace window.
     * A never-confirmed license (no successful activate yet) is never unlocked.
     */
    fun current(context: Context): LicenseState {
        val expiresAt = MmkvManager.decodeSettingsLong(AppConfig.PREF_LICENSE_EXPIRES, 0L)
        if (expiresAt <= 0L) return LicenseState(false, null, false, null)

        val bound = MmkvManager.decodeSettingsString(AppConfig.PREF_LICENSE_DEVICE)
        if (bound.isNullOrBlank() || bound != getDeviceIdCanonical(context)) {
            return LicenseState(false, expiresAt, false, ERR_DEVICE_MISMATCH)
        }

        val lastOk = MmkvManager.decodeSettingsLong(AppConfig.PREF_LICENSE_LAST_OK, 0L)
        if (lastOk <= 0L) return LicenseState(false, expiresAt, false, null)

        // Anti clock-shift reference time (see class doc).
        val now = maxOf(System.currentTimeMillis(), lastOk)
        if (expiresAt <= now) return LicenseState(false, expiresAt, false, null)

        val staleMs = now - lastOk
        val unlocked = staleMs <= AppConfig.LICENSE_GRACE_MS
        val inGrace = unlocked && staleMs > AppConfig.LICENSE_REFRESH_INTERVAL_MS
        return LicenseState(active = unlocked, expiresAt = expiresAt, inGrace = inGrace, lastError = null)
    }

    /** Convenience gate for premium content (design §6). */
    fun isPremiumUnlocked(context: Context): Boolean = current(context).active

    /**
     * Locked premium-pool subscription URL (task 9): embeds the stored license code and
     * the canonical device id, so only the licensed device can fetch the pool from the
     * license API. Null when no license code is stored.
     */
    fun premiumSubUrl(context: Context): String? {
        val code = MmkvManager.decodeSettingsString(AppConfig.PREF_LICENSE_CODE) ?: return null
        val base = AppConfig.LICENSE_API_BASE.trimEnd('/')
        return "$base/v1/sub/$code?device=${getDeviceIdCanonical(context)}&${AppConfig.LICENSE_APP_VERSION_PARAM}=${BuildConfig.VERSION_CODE}"
    }

    /**
     * v1.2.55 (user report: pools went empty after the v1.2.54 release): the worker's
     * force-update gate rejects every request that carries no version declaration with
     * 426 UPDATE_REQUIRED — header or `av=` query param. License API calls and the
     * premium sub URL declare it, but the generic subscription pipeline (free pool
     * included) never did, so once a minimum was first pinned server-side every
     * worker-served pool fetch 426'd and the subscription silently went empty. This
     * appends `av=<versionCode>` to license-worker URLs; anything else passes through
     * untouched, and a URL that already declares `av=` is left alone.
     */
    fun decorateWorkerSubUrl(url: String): String {
        val base = AppConfig.LICENSE_API_BASE.trimEnd('/')
        if (!url.startsWith(base)) return url
        if (url.contains("${AppConfig.LICENSE_APP_VERSION_PARAM}=")) return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator${AppConfig.LICENSE_APP_VERSION_PARAM}=${BuildConfig.VERSION_CODE}"
    }

    /** Wipes the stored license; the Device ID cache is intentionally kept. */
    fun clearLicense() {
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_CODE, null as String?)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_EXPIRES, 0L)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_LAST_OK, 0L)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_DEVICE, null as String?)
    }

    // ------------------------------------------------------------------ helpers

    /** Persists a successful activation. */
    private fun persistLicense(deviceIdCanonical: String, code: String, expiresAt: Long, serverTimeMs: Long) {
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_CODE, code)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_EXPIRES, expiresAt)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_DEVICE, deviceIdCanonical)
        MmkvManager.encodeSettings(AppConfig.PREF_LICENSE_LAST_OK, if (serverTimeMs > 0L) serverTimeMs else System.currentTimeMillis())
    }

    /**
     * Normalizes a user-entered code: non-alphanumerics stripped (dashes, spaces, Persian
     * separators), upper-cased, optional `DRF` prefix removed only when the residue is a
     * full 20-char body (so a canonical code that happens to start with D-R-F survives).
     */
    private fun normalizeCode(raw: String): String {
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return if (cleaned.length == 23 && cleaned.startsWith("DRF")) cleaned.substring(3) else cleaned
    }

    /** Parses an ISO-8601 UTC timestamp ("2026-12-06T00:00:00Z") to epoch millis; null on garbage. */
    private fun parseIsoMillis(iso: String): Long? = try {
        Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
