package com.v2ray.ang.handler

/**
 * Pure decision logic for removing profiles from a group index. Kept free of any
 * storage dependency so the removal semantics can be unit-tested without MMKV.
 */
internal object ProfileRemoval {

    /**
     * Plans the group-index edit for removing [targets] from one group's list.
     * Order of the surviving entries is preserved.
     *
     * @param currentList The group's current server guid list.
     * @param targets The guids to remove.
     * @return The updated guid list.
     */
    fun updatedIndex(currentList: List<String>, targets: Collection<String>): List<String> {
        if (targets.isEmpty()) return currentList
        return currentList.filterNot { it in targets }
    }

    /**
     * True when removing [targets] invalidates the current selection, so the
     * SELECTED_SERVER pointer must be cleared. A null selection is never invalidated.
     *
     * @param selectedServer The current selected server guid, or null.
     * @param targets The guids being removed.
     * @return Whether the selection points at a removed profile.
     */
    fun selectionInvalidated(selectedServer: String?, targets: Collection<String>): Boolean {
        return selectedServer != null && selectedServer in targets
    }
}
