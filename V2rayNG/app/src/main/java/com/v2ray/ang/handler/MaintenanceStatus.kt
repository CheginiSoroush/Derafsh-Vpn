package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/**
 * Cross-process maintenance progress store.
 *
 * [com.v2ray.ang.service.SubscriptionUpdateService] runs in the separate ":tasks" process,
 * so its live progress (subscription refresh + real-ping speed test) is published through
 * the multi-process MMKV settings store; the main screen polls it with a cheap ticker and
 * renders the maintenance status card. All numeric values are encoded as strings because
 * the writer and the reader only share the raw key-value store.
 */
object MaintenanceStatus {

    enum class Phase { IDLE, UPDATING, TESTING, DONE }

    /** Immutable view of the maintenance progress, read by the UI. */
    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val subLabel: String = "",
        val done: Int = 0,
        val total: Int = 0,
        val doneAt: Long = 0L,
    ) {
        /** 0f..1f test progress; 0 when the total is not known yet. */
        val fraction: Float
            get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private val IDLE_SNAPSHOT = Snapshot()

    // Writer-side session state — only touched inside the ":tasks" process. Test batches
    // run sequentially, so a simple base offset turns per-batch completed counts into a
    // single session-wide "done" figure.
    @Volatile
    private var doneBase = 0

    // ------------------------------------------------------------------
    // Writer side — called from SubscriptionUpdateService (":tasks")
    // ------------------------------------------------------------------

    /** Starts a fresh maintenance session: subscription refresh first, then speed tests. */
    fun beginSession() {
        doneBase = 0
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_PHASE, AppConfig.MAINT_PHASE_UPDATING)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_SUB_LABEL, "")
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE, 0)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_TOTAL, 0)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE_AT, 0L)
    }

    /** Announces which subscription profile is currently being refreshed/tested. */
    fun setSubLabel(label: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_SUB_LABEL, label)
    }

    /** A speed-test batch of [batchTotal] servers is starting; accumulates the total. */
    fun beginTestBatch(batchTotal: Int) {
        if (batchTotal <= 0) return
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_PHASE, AppConfig.MAINT_PHASE_TESTING)
        val total = MmkvManager.decodeSettingsInt(AppConfig.PREF_MAINT_TOTAL, 0) + batchTotal
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_TOTAL, total)
    }

    /** Per-batch completed count arrives cumulatively; expose the session-wide figure. */
    fun reportBatchProgress(completedInBatch: Int) {
        if (completedInBatch < 0) return
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE, doneBase + completedInBatch)
    }

    /** A batch fully finished; fold its servers into the completed base. */
    fun endTestBatch(batchTotal: Int) {
        if (batchTotal <= 0) return
        doneBase += batchTotal
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE, doneBase)
    }

    /**
     * Publishes absolute test progress — used by [com.v2ray.ang.service.CoreTestService],
     * which sums the live per-worker counts (settled batches + running batches) and pushes
     * the session-wide figure. Phase always becomes TESTING, so a standalone bulk test
     * (servers screen, fastest-pick) is announced on the main screen too.
     */
    fun publishTestProgress(done: Int, total: Int) {
        if (total <= 0) return
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_PHASE, AppConfig.MAINT_PHASE_TESTING)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_TOTAL, total)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE, done.coerceIn(0, total))
    }

    /**
     * Ends the session quietly (test cancelled or service torn down mid-flight): the card
     * hides immediately instead of showing the celebratory "all done" state.
     */
    fun abandon() {
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_PHASE, AppConfig.MAINT_PHASE_DONE)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE_AT, 0L)
    }

    /** Every task of the session is finished (or the service is going away). */
    fun finish() {
        MmkvManager.encodeSettings(
            AppConfig.PREF_MAINT_DONE,
            MmkvManager.decodeSettingsInt(AppConfig.PREF_MAINT_TOTAL, 0)
        )
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_PHASE, AppConfig.MAINT_PHASE_DONE)
        MmkvManager.encodeSettings(AppConfig.PREF_MAINT_DONE_AT, System.currentTimeMillis())
    }

    // ------------------------------------------------------------------
    // Reader side — called from the main process UI
    // ------------------------------------------------------------------

    /** Reads the latest published snapshot; never throws, defaults to idle. */
    fun read(): Snapshot {
        return runCatching {
            val phase = when (MmkvManager.decodeSettingsString(AppConfig.PREF_MAINT_PHASE)) {
                AppConfig.MAINT_PHASE_UPDATING -> Phase.UPDATING
                AppConfig.MAINT_PHASE_TESTING -> Phase.TESTING
                AppConfig.MAINT_PHASE_DONE -> Phase.DONE
                else -> return IDLE_SNAPSHOT
            }
            Snapshot(
                phase = phase,
                subLabel = MmkvManager.decodeSettingsString(AppConfig.PREF_MAINT_SUB_LABEL).orEmpty(),
                done = MmkvManager.decodeSettingsInt(AppConfig.PREF_MAINT_DONE, 0),
                total = MmkvManager.decodeSettingsInt(AppConfig.PREF_MAINT_TOTAL, 0),
                doneAt = MmkvManager.decodeSettingsLong(AppConfig.PREF_MAINT_DONE_AT, 0L),
            )
        }.getOrDefault(IDLE_SNAPSHOT)
    }
}
