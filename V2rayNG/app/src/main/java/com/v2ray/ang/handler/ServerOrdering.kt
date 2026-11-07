package com.v2ray.ang.handler

/**
 * Pure decision logic for server ordering by speed-test result. Kept free of storage
 * dependencies so the ordering semantics can be unit-tested without MMKV.
 */
internal object ServerOrdering {

    /**
     * Stable sort of a group's server guids by measured delay: tested configs first in
     * ascending delay order, then everything untested (0) or failed (< 0), each group
     * preserving its original relative order (stable sort — no re-shuffling of entries
     * the user may have ordered deliberately).
     *
     * @param serverList The group's current server guid list.
     * @param delayOf The measured delay for a guid (0 = never tested, < 0 = failed).
     * @return The reordered guid list.
     */
    fun byTestResult(serverList: List<String>, delayOf: (String) -> Long): List<String> {
        return serverList
            .map { guid -> guid to delayOf(guid) }
            .sortedWith(compareBy { if (it.second <= 0L) Long.MAX_VALUE else it.second })
            .map { it.first }
    }

    /**
     * Merges a UI drag order onto the CURRENT storage index without destroying entries.
     *
     * The UI snapshot (the row order a drag produced) can be stale relative to storage:
     * a subscription refresh or pool import may commit new configs and a concurrent
     * removal may delete some between the snapshot and the persist. A full-replace
     * write would drop every entry committed after the snapshot (the stale-snapshot
     * class fixed for removals as A2/A6). Merge semantics instead:
     *  - entries present in BOTH lists keep the UI's relative order (the drag wins);
     *  - entries only in the current index (committed after the snapshot) are preserved
     *    at the end, in their storage order;
     *  - entries only in the UI order (removed after the snapshot, or foreign guids
     *    from a merged view) are dropped — the index never gains entries it did not
     *    already own;
     *  - duplicates in the UI order collapse to their first occurrence.
     *
     * @param currentIndex The group's storage index at write time (inside the lock).
     * @param uiOrder The UI snapshot order to apply.
     * @return The merged index.
     */
    fun applyUiOrder(currentIndex: List<String>, uiOrder: List<String>): List<String> {
        if (uiOrder.isEmpty()) return currentIndex
        val storageSet = currentIndex.toHashSet()
        val seen = HashSet<String>(currentIndex.size)
        val result = ArrayList<String>(currentIndex.size)
        for (guid in uiOrder) {
            if (guid in storageSet && seen.add(guid)) result += guid
        }
        for (guid in currentIndex) {
            if (guid !in seen) result += guid
        }
        return result
    }
}
