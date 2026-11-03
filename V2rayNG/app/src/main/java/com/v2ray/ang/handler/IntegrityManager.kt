package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.security.MessageDigest

/**
 * Lightweight client-side integrity layer (anti-tamper task).
 *
 * What it can and cannot do: Derafsh is open source, so anyone may rebuild it from source
 * under GPL-3.0. What this manager protects against is *binary patching of the official
 * release APK* — the classic "remove the ads / kill the session timer" crack. Any bytecode
 * patch forces a re-sign, which changes the signing certificate and is detected here.
 * Deep client hardening (Play Integrity, server-side config gating) is planned for the
 * release phase.
 *
 * The result is advisory: the app still runs, but the monetization flow degrades
 * (interstitial on every connect, reward fast-paths disabled) and the user is warned.
 */
object IntegrityManager {

    enum class TamperFlag { NONE, DEBUGGABLE, SIGNATURE, ROOT, HOOKING }

    data class Report(
        val flags: Set<TamperFlag>,
    ) {
        val isTampered: Boolean get() = flags.isNotEmpty()
        val signatureMismatch: Boolean get() = TamperFlag.SIGNATURE in flags
        val isRooted: Boolean get() = TamperFlag.ROOT in flags
        val isHooked: Boolean get() = TamperFlag.HOOKING in flags
    }

    @Volatile
    private var cached: Report? = null

    fun check(context: Context): Report {
        cached?.let { return it }
        val flags = mutableSetOf<TamperFlag>()

        try {
            if (isDebuggable(context)) flags += TamperFlag.DEBUGGABLE
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Integrity: debuggable check failed", e)
        }

        try {
            if (!signatureMatches(context)) flags += TamperFlag.SIGNATURE
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Integrity: signature check failed", e)
            flags += TamperFlag.SIGNATURE
        }

        try {
            if (isRooted()) flags += TamperFlag.ROOT
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Integrity: root check failed", e)
        }

        try {
            if (isHookingFrameworkPresent(context) || isDebuggerAttached()) flags += TamperFlag.HOOKING
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Integrity: hooking check failed", e)
        }

        val report = Report(flags)
        cached = report
        if (report.isTampered) {
            LogUtil.w(AppConfig.TAG, "Integrity: tamper flags detected: $flags")
        }
        return report
    }

    /** SHA-256 of the APK signing certificate must equal the embedded official release hash. */
    private fun signatureMatches(context: Context): Boolean {
        val signerSha256 = getSigningCertSha256(context) ?: return false
        return signerSha256.equals(AppConfig.RELEASE_CERT_SHA256, ignoreCase = true)
    }

    private fun getSigningCertSha256(context: Context): String? {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info: SigningInfo? = packageInfo.signingInfo
            val chain = info?.apkContentsSigners ?: return null
            chain.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        val digest = MessageDigest.getInstance("SHA-256").digest(signatureBytes)
        return digest.joinToString(separator = "") { "%02X".format(it) }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun isRooted(): Boolean {
        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/su/bin/su", "/vendor/bin/su",
        )
        if (suPaths.any { File(it).exists() }) return true

        return try {
            // `which su` succeeding on a production build is a strong root signal.
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val found = process.inputStream.bufferedReader().use { it.readLine() != null }
            process.waitFor()
            found
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detects dynamic-instrumentation tooling used to hook the monetization/session flow at
     * runtime: Xposed-family manager packages, Frida's default TCP port and its common
     * on-disk artifacts. Advisory only — flag sets the same degraded path as a re-sign.
     */
    private fun isHookingFrameworkPresent(context: Context): Boolean {
        val hookPackages = arrayOf(
            "de.robv.android.xposed.installer",
            "org.lsposed.manager",
            "com.solohsu.android.edxp.manager",
            "me.weishu.exp",
            "org.meowmate.manager",
        )
        for (pkg in hookPackages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // not installed — keep scanning
            }
        }

        // Frida server's default port and its usual staging paths.
        if (isFridaPortOpen()) return true
        if (File("/data/local/tmp/frida-server").exists()) return true
        if (File("/data/local/tmp/re.frida.server").exists()) return true
        return false
    }

    private fun isFridaPortOpen(): Boolean = try {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 150)
            true
        }
    } catch (e: Exception) {
        false
    }

    /** TracerPid != 0 in /proc/self/status means a debugger/instrumenter is attached. */
    private fun isDebuggerAttached(): Boolean = try {
        File("/proc/self/status").bufferedReader().useLines { lines ->
            lines.any {
                it.startsWith("TracerPid") && it.substringAfter(':').trim().toIntOrNull() != 0
            }
        }
    } catch (e: Exception) {
        false
    }
}
