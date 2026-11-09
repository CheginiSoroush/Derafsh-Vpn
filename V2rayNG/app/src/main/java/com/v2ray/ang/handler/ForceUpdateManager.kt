package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Derafsh force-update gate (v1.2.53).
 *
 * The license worker exposes GET /v1/config with the server-mandated minimum supported
 * versionCode. This manager caches that number in MMKV (throttled to one request per
 * [AppConfig.FORCE_UPDATE_CHECK_INTERVAL_MS]) and answers the two questions the rest of
 * the app asks:
 *
 *  - [required] — is this build outdated? The main screen then replaces itself with a
 *    non-dismissable update blocker and every service start path (main toggle, quick
 *    tile, shortcut, widget) refuses to run: an outdated build is dead until updated.
 *  - the announced version — every license API call carries X-App-Version (and the
 *    premium sub URL an av= parameter), so the worker itself rejects outdated/legacy
 *    clients with 426 UPDATE_REQUIRED regardless of what the client does locally.
 *
 * Failure policy: network errors NEVER unlock anything — the
 * cached minimum stays in force (0 = never checked / nothing pinned means "allow").
 */
object ForceUpdateManager {

    /** Snapshot of the cached server configuration for the UI. */
    data class ForceUpdateState(
        val minVersionCode: Int,
        val checkedAt: Long,
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Pure decision (JVM-testable): a build is dead iff a minimum is pinned and older. */
    fun isUpdateRequired(versionCode: Int, minVersionCode: Int): Boolean =
        minVersionCode > 0 && versionCode in 1 until minVersionCode

    /** The cached state (pure MMKV — never blocks, safe from any process). */
    fun snapshot(): ForceUpdateState = ForceUpdateState(
        minVersionCode = MmkvManager.decodeSettingsString(AppConfig.PREF_FORCE_UPDATE_MIN)?.toIntOrNull() ?: 0,
        checkedAt = MmkvManager.decodeSettingsLong(AppConfig.PREF_FORCE_UPDATE_CHECKED_AT, 0L),
    )

    /** Convenience gate for start paths: is THIS build below the cached minimum? */
    fun required(): Boolean = isUpdateRequired(BuildConfig.VERSION_CODE, snapshot().minVersionCode)

    /**
     * Re-reads /v1/config from the worker and persists the minimum. Throttled to once
     * per [AppConfig.FORCE_UPDATE_CHECK_INTERVAL_MS] unless [force]; on any failure the
     * cached state is returned untouched (the pinned minimum, if any, stays in force).
     */
    suspend fun refresh(force: Boolean = false): ForceUpdateState = withContext(Dispatchers.IO) {
        val cached = snapshot()
        val now = System.currentTimeMillis()
        if (!force && now - cached.checkedAt < AppConfig.FORCE_UPDATE_CHECK_INTERVAL_MS) {
            return@withContext cached
        }
        try {
            val request = Request.Builder()
                .url("${AppConfig.LICENSE_API_BASE.trimEnd('/')}/v1/config")
                .header("User-Agent", "Derafsh-VPN")
                .header(AppConfig.LICENSE_APP_VERSION_HEADER, BuildConfig.VERSION_CODE.toString())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (response.isSuccessful && json.optBoolean("ok")) {
                    val min = json.optInt("min_version_code", 0).coerceAtLeast(0)
                    MmkvManager.encodeSettings(AppConfig.PREF_FORCE_UPDATE_MIN, min.toString())
                    MmkvManager.encodeSettings(AppConfig.PREF_FORCE_UPDATE_CHECKED_AT, now)
                    LogUtil.i(AppConfig.TAG, "ForceUpdate: min_version_code=$min (this=${BuildConfig.VERSION_CODE})")
                    ForceUpdateState(min, now)
                } else {
                    LogUtil.w(AppConfig.TAG, "ForceUpdate: config rejected (HTTP ${response.code}) — keeping cached minimum")
                    cached
                }
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ForceUpdate: refresh failed — keeping cached minimum", e)
            cached
        }
    }
}
