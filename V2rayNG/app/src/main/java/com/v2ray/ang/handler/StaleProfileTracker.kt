package com.v2ray.ang.handler

import com.google.gson.JsonPrimitive
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil

/**
 * Derafsh stale-profile tracking: counts consecutive real-delay test failures per profile
 * GUID and derives a "stale" set. Mark-only — profiles are never deleted or reordered by
 * this tracker; the stale set only biases the fastest-server pick and adds a dimmed badge
 * in the server picker.
 *
 * Storage (settings MMKV):
 *  - [AppConfig.PREF_GUID_FAIL_STREAK]: JSON map guid → consecutive failure count (capped at
 *    [STREAK_CAP]). Any successful test resets the count to 0 (the entry is dropped).
 *  - [AppConfig.PREF_STALE_GUID_SET]: the set of GUIDs whose streak reached
 *    [STALE_FAIL_THRESHOLD]. A successful test removes the GUID again.
 *
 * A delay result of 0 (never tested / cleared) never touches the streak, and GUIDs that were
 * removed from storage in the same beat are pruned so removed profiles leave no residue.
 */
internal object StaleProfileTracker {

    /** Consecutive failures needed before a profile is considered stale. */
    internal const val STALE_FAIL_THRESHOLD = 5

    /** The streak counter never grows beyond this cap. */
    internal const val STREAK_CAP = 10

    /**
     * Pure decision core (JVM-testable): applies drained guid → delay results and the set of
     * guids removed from storage in the same beat to the current streak map and stale set.
     *
     * Positive delays are successes (reset), negative delays are failures (+1, capped);
     * zero or null delays never touch the streak. Removed GUIDs are pruned from both stores.
     */
    internal fun applyResults(
        streaks: Map<String, Int>,
        stale: Set<String>,
        results: Map<String, Long>,
        removedGuids: Set<String>,
    ): Pair<Map<String, Int>, Set<String>> {
        if (results.isEmpty() && removedGuids.isEmpty()) return streaks to stale
        val nextStreaks = streaks.toMutableMap()
        val nextStale = stale.toMutableSet()
        for (guid in removedGuids) {
            nextStreaks.remove(guid)
            nextStale.remove(guid)
        }
        for ((guid, delayMillis) in results) {
            if (guid.isBlank()) continue
            if (delayMillis > 0L) {
                nextStreaks.remove(guid)
                nextStale.remove(guid)
            } else if (delayMillis < 0L) {
                val next = ((nextStreaks[guid] ?: 0) + 1).coerceAtMost(STREAK_CAP)
                nextStreaks[guid] = next
                if (next >= STALE_FAIL_THRESHOLD) nextStale.add(guid)
            }
        }
        return nextStreaks to nextStale
    }

    /**
     * Records one drained batch of test results (and optional same-beat removals) into MMKV.
     * Writes are skipped when nothing changed, so healthy passes cost no storage traffic.
     *
     * @param results The drained guid → delay results (positive = success, negative = failure).
     * @param removedGuids GUIDs removed from profile storage in the same beat.
     */
    fun recordTestResults(results: Map<String, Long>, removedGuids: Set<String> = emptySet()) {
        if (results.isEmpty() && removedGuids.isEmpty()) return
        val streaks = decodeStreaks()
        val stale = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_STALE_GUID_SET) ?: emptySet()
        val (nextStreaks, nextStale) = applyResults(streaks, stale, results, removedGuids)
        if (nextStreaks == streaks && nextStale == stale) return
        MmkvManager.encodeSettings(AppConfig.PREF_GUID_FAIL_STREAK, JsonUtil.toJson(nextStreaks))
        MmkvManager.encodeSettings(AppConfig.PREF_STALE_GUID_SET, nextStale.toMutableSet())
    }

    /**
     * The current stale GUID set (empty when nothing is stale).
     */
    fun staleGuidSet(): Set<String> =
        MmkvManager.decodeSettingsStringSet(AppConfig.PREF_STALE_GUID_SET) ?: emptySet()

    private fun decodeStreaks(): Map<String, Int> {
        val json = MmkvManager.decodeSettingsString(AppConfig.PREF_GUID_FAIL_STREAK)
            ?: return emptyMap()
        val obj = JsonUtil.parseString(json) ?: return emptyMap()
        val map = mutableMapOf<String, Int>()
        for ((guid, element) in obj.entrySet()) {
            val primitive = element as? JsonPrimitive ?: continue
            if (!primitive.isNumber) continue
            map[guid] = primitive.asInt
        }
        return map
    }
}
