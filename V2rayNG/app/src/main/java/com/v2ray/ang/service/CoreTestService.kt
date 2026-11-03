package com.v2ray.ang.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MaintenanceStatus
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CoreTestService : Service() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    // manage active batch workers so each batch is independent and cancellable
    private val activeWorkers = ConcurrentHashMap<RealPingWorkerService, String>()

    // Live test progress for the main-screen maintenance card (":tasks" -> main process via
    // multi-process MMKV): per-worker (done, total) pairs plus the sums of already settled
    // batches, so overlapping workers and back-to-back batches never move the bar backwards.
    private val workerProgress = ConcurrentHashMap<RealPingWorkerService, Pair<Int, Int>>()
    private val settledDone = AtomicInteger(0)
    private val settledTotal = AtomicInteger(0)
    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java).putExtra(
            "content",
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            R.drawable.ic_stop_24dp,
            getString(R.string.action_cancel),
            pendingIntent
        ).build()
    }

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed, cancelling ${activeWorkers.size} active workers")
        // Safety net: a mid-flight teardown must not leave the main-screen card hanging.
        val hadActiveWorkers = activeWorkers.isNotEmpty()
        cancelWorkers()
        if (hadActiveWorkers) MaintenanceStatus.abandon()
        workerProgress.clear()
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server),
            cancelAction
        )
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(
                message, startId, intent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            )
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel()
            else -> {
                NotificationHelper.stopForeground(this); stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int, requestId: String) {
        LogUtil.i(AppConfig.TAG, "CoreTestService starting worker   subscription ${message.subscriptionId}")

        val guidsList = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }

        if (guidsList.isNotEmpty()) {
            lateinit var worker: RealPingWorkerService
            worker = RealPingWorkerService(
                context = this,
                guids = guidsList,
                onlyTcp = message.onlyTcp,
                onEvent = { event ->
                    if (activeWorkers.containsKey(worker)) {
                        handleWorkerEvent(event, worker, message, requestId) { activeWorkers.remove(worker) }
                    }
                }
            )
            // A brand-new test session resets the settled sums; announce the scope so the
            // main-screen card flips to the testing phase with a 0% bar right away.
            if (activeWorkers.isEmpty()) {
                settledDone.set(0)
                settledTotal.set(0)
            }
            activeWorkers[worker] = requestId
            workerProgress[worker] = 0 to guidsList.size
            MaintenanceStatus.publishTestProgress(0, guidsList.size)
            worker.start()
        } else {
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_CANCEL, "", requestId)
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
        }
    }

    private fun handleWorkerEvent(
        event: RealPingEvent,
        worker: RealPingWorkerService,
        message: TestServiceMessage,
        requestId: String,
        onWorkerDone: () -> Unit,
    ) {
        when (event) {
            is RealPingEvent.Progress -> {
                // Feed the main-screen maintenance card with the session-wide percentage.
                if (event.batchTotal > 0) {
                    workerProgress[worker] = event.completedInBatch.coerceAtLeast(0) to event.batchTotal
                    publishMaintenanceTestProgress()
                }
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    title = getString(R.string.app_name),
                    content = getString(R.string.connection_running_task_left, event.text)
                )
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text, requestId)
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageHelper.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS,
                    RealPingResult(event.guid, event.delayMillis),
                    requestId,
                )
            }

            is RealPingEvent.Finish -> {
                // Settle this batch into the session sums before dropping the worker entry.
                workerProgress.remove(worker)?.let { (done, total) ->
                    if (total > 0) {
                        settledDone.addAndGet(done.coerceIn(0, total))
                        settledTotal.addAndGet(total)
                    }
                }
                if (message.subscriptionId.isNotEmpty()) {
                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                        AngConfigManager.removeInvalidServer(message.subscriptionId)
                    }

                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
                        AngConfigManager.sortByTestResultsForSub(message.subscriptionId)
                    }
                }

                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status, requestId)
                onWorkerDone()
                if (activeWorkers.isEmpty()) {
                    MaintenanceStatus.finish()
                    NotificationHelper.stopForeground(this)
                    stopSelf()
                }
            }
        }
    }

    /** Sums settled batches plus the running workers and publishes the session-wide figure. */
    private fun publishMaintenanceTestProgress() {
        var done = settledDone.get()
        var total = settledTotal.get()
        workerProgress.values.forEach { (d, t) ->
            if (t > 0) {
                done += d.coerceIn(0, t)
                total += t
            }
        }
        MaintenanceStatus.publishTestProgress(done, total)
    }

    private fun handleMeasureCancel() {
        LogUtil.i(AppConfig.TAG, "CoreTestService received cancel message, cancelling ${activeWorkers.size} active workers")
        cancelWorkers()
        // A cancelled test ends the announced session quietly (no "all done" fanfare).
        if (activeWorkers.isEmpty()) {
            workerProgress.clear()
            MaintenanceStatus.abandon()
        }
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun cancelWorkers() {
        // Reply for each cancelled request, never for a newer UI request that has not started here.
        activeWorkers.entries.toList().forEach { (worker, requestId) ->
            if (activeWorkers.remove(worker, requestId)) {
                // Cancelled workers never emit Finish, so drop their progress entry here.
                workerProgress.remove(worker)
                worker.cancel()
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_CANCEL, "", requestId)
            }
        }
    }
}
