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
}
