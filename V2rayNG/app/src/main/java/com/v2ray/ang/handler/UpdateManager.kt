package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
 * - The check is silent: on private repos / offline it just resolves to no update.
 * - Releases are published as prereleases, so the list endpoint (`?per_page=1`) is used
 *   instead of `releases/latest`, which excludes them.
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

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Latest release considered for the update prompt (kept for retry logic). */
    @Volatile
    private var pendingInfo: UpdateInfo? = null

    /**
     * Silently checks for a newer release. Safe to call on every app start; any failure
     * (offline, private repo, rate limit) leaves the state at [UpdateState.Idle].
     */
    suspend fun checkForUpdate() = withContext(Dispatchers.IO) {
        if (BuildConfig.DISTRIBUTION == "F-Droid") return@withContext
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Ready) return@withContext

        try {
            val request = Request.Builder()
                .url(AppConfig.DERAFSH_UPDATE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Derafsh-VPN")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LogUtil.i(AppConfig.TAG, "Update check skipped: HTTP ${response.code}")
                    return@withContext
                }
                val body = response.body?.string() ?: return@withContext
                val info = parseLatestRelease(JSONArray(body)) ?: return@withContext
                if (!isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) return@withContext

                pendingInfo = info
                _state.value = UpdateState.Available(info)
                LogUtil.i(
                    AppConfig.TAG,
                    "Update available: ${info.versionName} (${info.tag}) — ${info.apkUrl}"
                )
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Update check failed", e)
        }
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

    /** Numeric semantic-version compare, ignoring any -build.N suffix. */
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
