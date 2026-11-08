package com.v2ray.ang.handler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Derafsh premium renewal reminder (task 7).
 *
 * A single named periodic WorkManager task (24h, network-connected constraint) that:
 *  1. re-validates the stored license against the license API whenever the last server
 *     confirmation is older than [AppConfig.LICENSE_REFRESH_INTERVAL_MS] — this is the
 *     background arm of design §7.3 (the license screen only refreshes on manual open).
 *     The decision is deliberately NOT gated on the locally computed active flag: when
 *     the 72 h grace window has lapsed, active is false precisely because the last
 *     confirmation is old, and this refresh is the only automatic path that can either
 *     restore premium (server still active) or cleanly revert the premium source
 *     (server answers inactive → [LicenseManager] clears + [DerafshSourceManager].onLicenseLost).
 *  2. posts a renewal notification once the remaining license window drops below
 *     [AppConfig.LICENSE_REMIND_MS] (3 days) and clears it again when the license is
 *     renewed, revoked (server answered inactive) or simply not near expiry.
 *
 * With no license stored the run cancels any stale reminder and exits silently, so the
 * schedule is harmless for free users. Pure MMKV + HTTP work — safe in the :bg process
 * (same multiprocess pattern as [WebDavAutoBackup]).
 */
object LicenseReminder {

    /**
     * Ensure the periodic reminder task exists (KEEP policy preserves the schedule).
     * Call from app startup; the worker self-skips when there is nothing to remind about.
     */
    fun sync(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderTask>(24L, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(AppConfig.LICENSE_REMINDER_TASK_NAME)
            .build()
        RemoteWorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AppConfig.LICENSE_REMINDER_TASK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        LogUtil.i(AppConfig.TAG, "LicenseReminder: periodic task ensured (24h, network connected)")
    }

    /**
     * Pure decision (JVM-testable): should this background run re-validate the stored
     * license? True whenever a server confirmation exists (lastOk > 0 — every persisted
     * license has one, written by activate) and is older than the 24h cadence. The
     * locally computed active flag is deliberately NOT an input: it is false exactly
     * when the grace window has lapsed, which is when re-validation matters most (A31).
     */
    internal fun shouldRevalidateInBackground(lastOk: Long, now: Long): Boolean =
        lastOk > 0L && now - lastOk >= AppConfig.LICENSE_REFRESH_INTERVAL_MS

    class ReminderTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val context = applicationContext
            try {
                // Force-update config re-check (v1.2.53): keeps the cached minimum fresh so
                // the start-path gates and the main-screen blocker act on current data even
                // if the app process stays alive for days. Failure keeps the cached value.
                ForceUpdateManager.refresh()

                // Nothing stored (never activated / cleared): make sure no stale reminder survives.
                val storedCode = MmkvManager.decodeSettingsString(AppConfig.PREF_LICENSE_CODE)
                if (storedCode.isNullOrBlank()) {
                    NotificationHelper.cancel(NotificationChannelType.LICENSE_REMINDER, context)
                    return Result.success()
                }

                // Background re-validation (design §7.3): refresh whenever the last server
                // confirmation is older than the 24h cadence, so the reminder (and the app)
                // judge on the freshest state. Network failure keeps the cached state (grace).
                // NOT gated on state.active — a lapsed grace window (active=false exactly
                // because lastOk is old) is the case that needs this refresh the most (A31).
                val lastOk = MmkvManager.decodeSettingsLong(AppConfig.PREF_LICENSE_LAST_OK, 0L)
                var state = LicenseManager.current(context)
                if (shouldRevalidateInBackground(lastOk, System.currentTimeMillis())) {
                    state = LicenseManager.refresh(context)
                }

                // Expired, revoked (server reachable and answered inactive) or device-mismatch:
                // nothing left to remind about.
                val expiresAt = state.expiresAt
                if (!state.active || expiresAt == null) {
                    NotificationHelper.cancel(NotificationChannelType.LICENSE_REMINDER, context)
                    return Result.success()
                }

                // Not within the reminder window (fresh license or just renewed): keep the
                // notification shade clean.
                val remaining = expiresAt - System.currentTimeMillis()
                if (remaining > AppConfig.LICENSE_REMIND_MS) {
                    NotificationHelper.cancel(NotificationChannelType.LICENSE_REMINDER, context)
                    return Result.success()
                }

                NotificationHelper.notify(
                    NotificationChannelType.LICENSE_REMINDER,
                    context,
                    context.getString(R.string.license_reminder_title),
                    context.getString(R.string.license_reminder_content, formatDate(expiresAt)),
                    contentIntent = mainActivityIntent(context)
                )
                LogUtil.i(AppConfig.TAG, "LicenseReminder: posted (expires in ${remaining / (60L * 60L * 1000L)}h)")
            } catch (e: Exception) {
                // A reminder must never crash the worker; the next periodic run retries.
                LogUtil.e(AppConfig.TAG, "LicenseReminder: run failed", e)
            }
            return Result.success()
        }

        /** Tapping the reminder opens the app; renewal continues on the license screen. */
        private fun mainActivityIntent(context: Context): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        }

        /** Same display format as the license screen ([com.v2ray.ang.ui.license.LicenseViewModel.formatExpiry]). */
        private fun formatDate(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    }
}
