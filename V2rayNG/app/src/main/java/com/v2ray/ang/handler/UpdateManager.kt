package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Derafsh in-app auto-update.
 *
 * Checks the repo's GitHub Releases for a newer version, downloads the matching APK with
 * progress reporting and hands it to the system package installer through FileProvider.
 *
 * - F-Droid flavor never self-updates (store policy).
 * - The check is silent and fast: 4s/6s timeouts so the prompt lands during the splash
 *   window on healthy networks and fails quickly on dead ones; offline / private repo
 *   simply resolves to no update.
 * - Primary source is the api.github.com releases list (`?per_page=1`, newest first, so
 *   prereleases are caught too). api.github.com caps unauthenticated traffic at 60
 *   requests/hour/IP — routinely exhausted behind carrier-grade NAT (whole countries
 *   behind a handful of IPs) — so on any API failure the check retries through the
 *   quota-free github.com web endpoints: the releases atom feed for the newest tag,
 *   then the per-tag `expanded_assets` fragment for the APK download path.
 */
object UpdateManager {

    /** Current lifecycle state of the update flow, observed by the main screen. */
    sealed class UpdateState {
        data object Idle : UpdateState()
        data class Available(val info: UpdateInfo) : UpdateState()
        data class Downloading(val percent: Int) : UpdateState()
        data class Ready(val file: File, val versionName: String) : UpdateState()
        data class Failed(val message: String?) : UpdateState()
    }

    data class UpdateInfo(
        val versionName: String,
        val tag: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Hard ceiling for a downloaded update APK (mirrors ZipUtil's archive budget). */
    private const val APP_DOWNLOAD_LIMIT_BYTES = 256L * 1024 * 1024

    /** Minimum spacing between startup checks: onCreate and onStart both fire on a cold open. */
    private const val CHECK_MIN_INTERVAL_MS = 30_000L

    /** Atom feed entry links: .../releases/tag/v1.2.39 (served from github.com, no API quota). */
    private val FEED_TAG_REGEX = Regex("""releases/tag/(v[^"&<\s]+)""")

    /** expanded_assets fragment: href="/<owner>/<repo>/releases/download/<tag>/<file>.apk" */
    private val FEED_ASSET_REGEX = Regex("""href="([^"]+releases/download/[^"]+\.apk)"""")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Latest release considered for the update prompt (kept for retry logic). */
    @Volatile
    private var pendingInfo: UpdateInfo? = null

    /** [SystemClock.elapsedRealtime] of the last check start, backing [CHECK_MIN_INTERVAL_MS]. */
    @Volatile
    private var lastCheckAtMs = 0L

    /**
     * Silently checks for a newer release. Safe to call on every app open (onCreate and
     * onStart): the internal throttle collapses the cold-open double-fire and rate-limits
     * hot re-opens; any failure (offline, rate limit, private repo) leaves the state at
     * [UpdateState.Idle].
     */
    suspend fun checkForUpdate() {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Ready) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckAtMs < CHECK_MIN_INTERVAL_MS) return
        lastCheckAtMs = now

        withContext(Dispatchers.IO) {
            if (BuildConfig.DISTRIBUTION == "F-Droid") return@withContext

            var info: UpdateInfo? = null
            try {
                info = fetchLatestViaApi()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Update check via API failed, falling back to feed", e)
            }
            if (info == null) {
                try {
                    info = fetchLatestViaFeed()
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Update check via feed failed", e)
                }
            }
            if (info == null || !isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
                return@withContext
            }

            pendingInfo = info
            _state.value = UpdateState.Available(info)
            LogUtil.i(
                AppConfig.TAG,
                "Update available: ${info.versionName} (${info.tag}) — ${info.apkUrl}"
            )
        }
    }

    /**
     * API path — rich metadata (exact asset size) in one request, but subject to the
     * api.github.com unauthenticated quota (see class doc).
     */
    private fun fetchLatestViaApi(): UpdateInfo? {
        val request = Request.Builder()
            .url(AppConfig.DERAFSH_UPDATE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Derafsh-VPN")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                LogUtil.i(AppConfig.TAG, "Update API skipped: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            return parseLatestRelease(JSONArray(body))
        }
    }

    /**
     * Rate-limit-proof fallback through github.com web endpoints instead of api.github.com:
     * the releases atom feed names every published tag (newest entries first), then the
     * per-tag `expanded_assets` fragment lists the APK download paths. Asset size is
     * unknown here — the downloader falls back to the HTTP Content-Length.
     */
    private fun fetchLatestViaFeed(): UpdateInfo? {
        val tag = newestTagFromFeed(fetchBody(AppConfig.DERAFSH_UPDATE_FEED_URL) ?: return null)
            ?: return null
        val versionName = versionIn(tag)
        if (versionName.isBlank()) return null
        val assetsHtml = fetchBody("${AppConfig.DERAFSH_UPDATE_ASSETS_URL}/$tag") ?: return null
        val apkUrl = pickFeedAssetUrl(assetsHtml) ?: return null
        return UpdateInfo(versionName = versionName, tag = tag, apkUrl = apkUrl, sizeBytes = 0L)
    }

    /** GET helper shared by the feed path; any non-2xx yields null (the check stays silent). */
    private fun fetchBody(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Derafsh-VPN")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                LogUtil.i(AppConfig.TAG, "Update check skipped: HTTP ${response.code} ($url)")
                return null
            }
            return response.body?.string()
        }
    }

    /**
     * Picks the newest valid tag mentioned in the feed. Entries are ordered by update time
     * and release notes can reference older tags, so every tag is a candidate and the max
     * version wins (a stale note can never trigger a downgrade or a phantom prompt).
     */
    private fun newestTagFromFeed(feed: String): String? {
        val tags = FEED_TAG_REGEX.findAll(feed)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        if (tags.isEmpty()) return null
        return tags.reduce { best, next ->
            if (isNewerVersion(versionIn(next), versionIn(best))) next else best
        }
    }

    /** "v1.2.39-build.7" -> "1.2.39" — the form the version comparator expects. */
    private fun versionIn(tag: String): String = tag.removePrefix("v").substringBefore("-build.")

    /** Chooses the APK download path from the expanded_assets fragment: ABI → universal → first, never fdroid. */
    private fun pickFeedAssetUrl(assetsHtml: String): String? {
        val paths = FEED_ASSET_REGEX.findAll(assetsHtml)
            .map { it.groupValues[1] }
            .distinct()
            .filter { !it.contains("fdroid", ignoreCase = true) }
            .toList()
        if (paths.isEmpty()) return null
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val chosen = paths.firstOrNull { it.substringAfterLast('/').contains(deviceAbi, ignoreCase = true) }
            ?: paths.firstOrNull { it.substringAfterLast('/').contains("universal", ignoreCase = true) }
            ?: paths.first()
        // hrefs are repo-rooted ("/CheginiSoroush/Derafsh-Vpn/releases/download/…"), so the
        // scheme+host is the only prefix needed.
        return if (chosen.startsWith("http")) chosen else AppConfig.GITHUB_URL + chosen
    }

    /** Starts the APK download for the available update, reporting progress via [state]. */
    fun downloadUpdate(context: Context) {
        val info = pendingInfo ?: return
        // Check-and-set atomically on the caller thread: setting Downloading inside the launched
        // coroutine instead let a rapid double-tap pass this guard twice and start two concurrent
        // downloads racing on the same destination file (each also wiping the other's partial).
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(0)

        scope.launch {
            try {
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val dest = File(dir, "Derafsh_${info.versionName}.apk")

                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "Derafsh-VPN")
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed: HTTP ${response.code}")
                    }
                    val source = response.body ?: throw IllegalStateException("Empty body")
                    val total = info.sizeBytes.takeIf { it > 0 } ?: source.contentLength()
                    // Size caps in the same threat model as the signature gate (compromised
                    // release channel): never accept more than the declared asset size, and
                    // never more than the app-wide 256 MiB budget (mirrors ZipUtil).
                    val sizeLimit = if (total > 0) minOf(total, APP_DOWNLOAD_LIMIT_BYTES) else APP_DOWNLOAD_LIMIT_BYTES
                    var done = 0L
                    var lastPercent = -1

                    source.byteStream().use { input ->
                        FileOutputStream(dest).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                done += read
                                if (done > sizeLimit) {
                                    throw IllegalStateException("Download exceeds expected size ($done > $sizeLimit)")
                                }
                                output.write(buffer, 0, read)
                                if (total > 0) {
                                    val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        _state.value = UpdateState.Downloading(percent)
                                        lastPercent = percent
                                    }
                                }
                            }
                        }
                    }
                }

                // Anti-hijack gate: never hand an APK signed with a foreign certificate to
                // the user, even if the release channel itself were ever compromised.
                if (!verifyApkSignature(context, dest)) {
                    dest.delete()
                    LogUtil.e(AppConfig.TAG, "Update blocked: downloaded APK signature does not match the official Derafsh certificate")
                    _state.value = UpdateState.Failed(null)
                    return@launch
                }

                _state.value = UpdateState.Ready(dest, info.versionName)
                LogUtil.i(AppConfig.TAG, "Update downloaded to ${dest.absolutePath}")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Update download failed", e)
                _state.value = UpdateState.Failed(e.message)
            }
        }
    }

    /** Hands a downloaded APK to the system installer. */
    fun installApk(context: Context, file: File) {
        try {
            // Second gate (defense in depth): re-verify the certificate at install time.
            if (!verifyApkSignature(context, file)) {
                LogUtil.e(AppConfig.TAG, "Install blocked: APK signature does not match the official Derafsh certificate")
                file.delete()
                _state.value = UpdateState.Failed(null)
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.cache", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to launch installer", e)
            _state.value = UpdateState.Failed(e.message)
        }
    }

    /** Dismisses transient states (available / failed) back to idle. */
    fun dismiss() {
        val current = _state.value
        if (current is UpdateState.Available || current is UpdateState.Failed) {
            _state.value = UpdateState.Idle
        }
    }

    /** Retry an available-update check after a failure. */
    suspend fun retryCheck() {
        dismiss()
        checkForUpdate()
    }

    // ---------- helpers ----------

    /** Picks the newest release entry and its best APK asset for this device. */
    private fun parseLatestRelease(array: JSONArray): UpdateInfo? {
        if (array.length() == 0) return null
        val release = array.getJSONObject(0)
        if (release.optBoolean("draft")) return null

        val tag = release.optString("tag_name").orEmpty()
        val versionName = tag.removePrefix("v").substringBefore("-build.")
        if (versionName.isBlank()) return null

        val assets: List<JSONObject> = buildList {
            val arr = release.optJSONArray("assets") ?: return@buildList
            for (i in 0 until arr.length()) {
                val asset = arr.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true) &&
                    !name.contains("fdroid", ignoreCase = true) &&
                    asset.optString("state") == "uploaded"
                ) {
                    add(asset)
                }
            }
        }
        if (assets.isEmpty()) return null

        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val chosen = assets.firstOrNull { it.optString("name").contains(deviceAbi) }
            ?: assets.firstOrNull { it.optString("name").contains("universal") }
            ?: assets.first()

        return UpdateInfo(
            versionName = versionName,
            tag = tag,
            apkUrl = chosen.optString("browser_download_url"),
            sizeBytes = chosen.optLong("size", 0L),
        )
    }

    /**
     * Verifies a downloaded APK is signed with the official Derafsh certificate before it
     * is offered to the user. Blocks poisoned releases (compromised channel/account) from
     * silently replacing the app.
     */
    private fun verifyApkSignature(context: Context, file: File): Boolean {
        return try {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            }
            val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures?.firstOrNull()?.toByteArray()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(signatureBytes ?: return false)
            digest.joinToString(separator = "") { "%02X".format(it) }
                .equals(AppConfig.RELEASE_CERT_SHA256, ignoreCase = true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Update: APK signature verification failed", e)
            false
        }
    }

    /** Numeric semantic-version compare of two version names, ignoring any -build.N suffix. */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        fun parts(version: String): List<Int> =
            version.split("-").first().split(".")
                .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

        val remoteParts = parts(remote)
        val localParts = parts(local)
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
