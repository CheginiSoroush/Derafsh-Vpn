package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object WebDavManager {
    private var cfg: WebDavConfig? = null
    private var client: OkHttpClient? = null

    /**
     * Initialize the WebDAV manager with a configuration and build an OkHttp client.
     * Note: upload/download reject insecure plain-http base URLs (see [isSecureRemoteUrl]).
     *
     * @param config WebDavConfig containing baseUrl, credentials, remoteBasePath and timeoutSeconds.
     */
    fun init(config: WebDavConfig) {
        cfg = config
        client = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Upload a local file to a remote file name under the configured remoteBasePath.
     * The provided `remoteFileName` should be a file name (e.g. "backup_ng.zip").
     * The method will attempt to create parent directories via MKCOL before PUT.
     * Plain `http://` remote URLs are rejected (Basic-auth credentials would be sent
     * in cleartext), except for loopback/private LAN hosts used for local testing.
     *
     * @param localFile File to upload.
     * @param remoteFileName Remote file name relative to configured remoteBasePath.
     * @return true if upload succeeded (HTTP 2xx), false otherwise.
     */
    suspend fun uploadFile(localFile: File, remoteFileName: String): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            // Fail fast before Basic-auth credentials are sent to an insecure server
            if (!isSecureRemoteUrl(remote)) return@withContext false

            val cl = client ?: return@withContext false

            // Ensure parent directories exist
            val dirPath = remote.substringBeforeLast('/')
            if (dirPath != remote) {
                ensureRemoteDirs(dirPath)
            }

            // Determine content type based on file extension
            val mediaType = when (localFile.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()

            val body = localFile.asRequestBody(mediaType)
            val req = applyAuth(Request.Builder().url(remote).put(body)).build()
            cl.newCall(req).execute().use { resp ->
                val success = resp.isSuccessful
                if (success) {
                    LogUtil.i(AppConfig.TAG, "WebDAV upload success: ${redactUrlCredentials(remote)}")
                } else {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload failed: ${redactUrlCredentials(remote)} (HTTP ${resp.code})")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV upload exception: ${redactUrlCredentials(remote)}", e)
            return@withContext false
        }
    }

    /**
     * Download a remote file (relative to configured remoteBasePath) into a local file.
     * Plain `http://` remote URLs are rejected (Basic-auth credentials would be sent
     * in cleartext), except for loopback/private LAN hosts used for local testing.
     *
     * @param remoteFileName Remote file name relative to configured remoteBasePath.
     * @param destFile Local destination file to write to.
     * @return true if download and write succeeded, false otherwise.
     */
    suspend fun downloadFile(remoteFileName: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            // Fail fast before Basic-auth credentials are sent to an insecure server
            if (!isSecureRemoteUrl(remote)) return@withContext false

            val cl = client ?: return@withContext false
            val req = applyAuth(Request.Builder().url(remote).get()).build()
            cl.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogUtil.e(AppConfig.TAG, "WebDAV download failed: ${redactUrlCredentials(remote)} (HTTP ${resp.code})")
                    return@withContext false
                }

                resp.body.byteStream().use { input ->
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        input.copyTo(fos)
                    }
                }

                LogUtil.i(AppConfig.TAG, "WebDAV download success: ${redactUrlCredentials(remote)}")
                return@withContext true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV download exception: ${redactUrlCredentials(remote)}", e)
            return@withContext false
        }
    }

    /**
     * Build a full remote URL by combining the configured base URL, the configured
     * remote base path and a file name provided by the caller.
     *
     * Example: baseUrl="https://example.com/remote.php/dav", remoteBasePath="backups",
     * remoteFileName="backup_ng.zip" => "https://example.com/remote.php/dav/backups/backup_ng.zip"
     *
     * @param remoteFileName A file name relative to the configured remoteBasePath (no leading slash required).
     * @return Full URL string used for HTTP operations.
     */
    private fun buildRemoteUrl(remoteFileName: String): String {
        val base = cfg?.baseUrl?.trimEnd('/') ?: ""
        // Use configured remoteBasePath when not empty; otherwise fallback to AppConfig.WEBDAV_BACKUP_DIR
        val basePathConfigured = cfg?.remoteBasePath?.trim('/')?.takeIf { it.isNotEmpty() }
        val basePath = basePathConfigured ?: AppConfig.WEBDAV_BACKUP_DIR
        val rel = remoteFileName.trimStart('/')
        return if (basePath.isEmpty()) "$base/$rel" else "$base/$basePath/$rel"
    }

    /**
     * Check that the remote URL will not leak Basic-auth credentials in cleartext.
     *
     * Plain `http://` URLs send credentials unencrypted and are rejected, except for
     * loopback or private LAN hosts (localhost, 127.x, 10.x, 172.16-31.x, 192.168.x)
     * which are allowed for local testing.
     *
     * @param remoteUrl Full remote URL to check.
     * @return true if the URL is https, or plain http to a loopback/private address, false otherwise.
     */
    private fun isSecureRemoteUrl(remoteUrl: String): Boolean {
        return try {
            val url = URL(remoteUrl)
            if (url.protocol.equals("https", ignoreCase = true)) return true
            if (url.protocol.equals("http", ignoreCase = true)) {
                val host = url.host?.trimEnd('.')?.lowercase().orEmpty()
                if (host == "localhost") return true
                if (Utils.isIpAddress(host)) {
                    AppConfig.PRIVATE_IP_LIST.forEach {
                        if (Utils.isIpInCidr(host, it)) return true
                    }
                }
            }
            LogUtil.e(AppConfig.TAG, "WebDAV insecure URL rejected (use https or a loopback/private address): ${redactUrlCredentials(remoteUrl)}")
            false
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV invalid remote URL", e)
            false
        }
    }

    /**
     * Mask the password portion of credentials embedded in a URL (e.g.
     * "https://user:pass@host") so URLs can be logged without leaking secrets.
     *
     * @param url The URL string to redact.
     * @return The URL with any embedded password replaced by "***".
     */
    private fun redactUrlCredentials(url: String): String {
        return url.replace(Regex("(?<=//)([^/@]+)@")) { m ->
            val userInfo = m.groupValues[1]
            val username = userInfo.substringBefore(':')
            if (':' in userInfo) "${username}:***@" else "$username@"
        }
    }

    /**
     * Apply HTTP Basic authentication headers to the given request builder when
     * username is configured in `cfg`.
     *
     * @param builder OkHttp Request.Builder to modify.
     * @return The same builder instance with Authorization header applied if credentials exist.
     */
    private fun applyAuth(builder: Request.Builder): Request.Builder {
        val username = cfg?.username
        val password = cfg?.password
        if (!username.isNullOrEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password ?: ""))
        }
        return builder
    }

    /**
     * Ensure that each directory segment in the given directory URL exists on the
     * WebDAV server. This issues MKCOL requests for each segment in a best-effort
     * manner and ignores errors for segments that already exist.
     *
     * @param dirUrl Absolute URL to the directory that should exist (e.g. https://.../backups)
     */
    private fun ensureRemoteDirs(dirUrl: String) {
        try {
            val cl = client ?: return
            val url = URL(dirUrl)
            val segments = url.path.split("/").filter { it.isNotEmpty() }
            var accum = ""
            for (seg in segments) {
                accum += "/$seg"
                val mkUrl = URL(url.protocol, url.host, if (url.port == -1) -1 else url.port, accum).toString()
                try {
                    val req = applyAuth(Request.Builder().url(mkUrl).method("MKCOL", null)).build()
                    cl.newCall(req).execute().use { resp ->
                        // 201 Created or 405 Method Not Allowed (already exists) are acceptable
                        if (resp.code != 201 && resp.code != 405 && resp.code != 409) {
                            LogUtil.w(AppConfig.TAG, "WebDAV MKCOL ${redactUrlCredentials(mkUrl)} returned ${resp.code}")
                        }
                    }
                } catch (_: Exception) {
                    // best-effort, continue
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV ensureRemoteDirs error", e)
        }
    }
}