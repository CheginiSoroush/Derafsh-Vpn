package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Daily automatic WebDAV backup.
 *
 * A single named periodic WorkManager task (24h, network-connected constraint)
 * uploads the exact same backup artifact as the manual WebDAV backup
 * (MMKV snapshot zipped, uploaded as [AppConfig.WEBDAV_BACKUP_FILE_NAME] under
 * the configured remote base path). Runs in the background process; the network
 * is touched only inside the worker.
 */
object WebDavAutoBackup {

    private const val INTERVAL_HOURS = 24L

    /**
     * Sync the periodic task with the stored toggle:
     * toggle on ⇒ enqueue (KEEP policy preserves the existing schedule),
     * toggle off ⇒ cancel.
     * Call from app startup and whenever the toggle changes.
     */
    fun sync(context: Context = AngApplication.application) {
        val rw = RemoteWorkManager.getInstance(context)
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_WEBDAV_AUTO_BACKUP, false)) {
            val request = PeriodicWorkRequestBuilder<AutoBackupTask>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(AppConfig.WEBDAV_AUTO_BACKUP_TASK_NAME)
                .build()
            rw.enqueueUniquePeriodicWork(
                AppConfig.WEBDAV_AUTO_BACKUP_TASK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            LogUtil.i(AppConfig.TAG, "WebDavAutoBackup: periodic task ensured (24h, network connected)")
        } else {
            rw.cancelUniqueWork(AppConfig.WEBDAV_AUTO_BACKUP_TASK_NAME)
            LogUtil.i(AppConfig.TAG, "WebDavAutoBackup: periodic task cancelled (toggle off)")
        }
    }

    class AutoBackupTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val context = applicationContext

            // No WebDAV credentials configured: skip silently.
            val config = MmkvManager.decodeWebDavConfig()
            if (config == null || config.baseUrl.isEmpty()) {
                LogUtil.i(AppConfig.TAG, "WebDavAutoBackup: no WebDAV credentials, skipping silently")
                return Result.success()
            }

            var tempFile: File? = null
            try {
                // Same artifact pipeline as the manual backup (BackupViewModel):
                // timestamped MMKV snapshot folder → single zip → upload.
                val appName = context.getString(R.string.app_name)
                val dateFormatted = SimpleDateFormat(
                    "yyyy-MM-dd-HH-mm-ss",
                    Locale.getDefault()
                ).format(System.currentTimeMillis())
                val backupDir = "${context.cacheDir.absolutePath}/${appName}_$dateFormatted"
                val outputZipFilePath = "${context.cacheDir.absolutePath}/${appName}_$dateFormatted.zip"

                val count = MMKV.backupAllToDirectory(backupDir)
                if (count <= 0) {
                    recordOutcome(false)
                    return Result.success()
                }

                val zipped = ZipUtil.zipFromFolder(backupDir, outputZipFilePath)
                // The intermediate folder is a plaintext copy of the whole MMKV
                // storage (credentials included); only the zip is consumed, so
                // remove the folder and any partial zip artifact right away.
                File(backupDir).deleteRecursively()
                if (!zipped) {
                    File(outputZipFilePath).delete()
                    recordOutcome(false)
                    return Result.success()
                }
                tempFile = File(outputZipFilePath)

                WebDavManager.init(config)
                val ok = WebDavManager.uploadFile(tempFile, AppConfig.WEBDAV_BACKUP_FILE_NAME)

                if (ok) {
                    MmkvManager.encodeSettings(
                        AppConfig.PREF_WEBDAV_LAST_AUTO_BACKUP,
                        System.currentTimeMillis()
                    )
                }
                recordOutcome(ok)
                LogUtil.i(AppConfig.TAG, "WebDavAutoBackup: run finished ok=$ok")
                return Result.success()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDavAutoBackup: run failed", e)
                recordOutcome(false)
                return Result.success()
            } finally {
                tempFile?.delete()
            }
        }

        /** Records the outcome (success/failure) of the last automatic run. */
        private fun recordOutcome(ok: Boolean) {
            MmkvManager.encodeSettings(AppConfig.PREF_WEBDAV_LAST_AUTO_BACKUP_SUCCESS, ok)
        }
    }
}
