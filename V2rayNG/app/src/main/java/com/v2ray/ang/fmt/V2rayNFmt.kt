package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayNShareItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

object V2rayNFmt : FmtBase() {

    /** Reserved namespace for synthesized keys of blank-IndexId records (O3).
     *  A real v2rayN indexId is a GUID and can never start with this. */
    internal const val BLANK_ID_PREFIX = "blank#"

    fun parse(list: List<String>, subId: String): List<ProfileItem> {
        val idMap = buildIdMap(list.mapNotNull(::parseShareItem))

        return convertShareItems(idMap, subId) { item, e ->
            LogUtil.e(AppConfig.TAG, "Failed to convert V2rayN share item (ConfigType=${item.ConfigType})", e)
        }
    }

    /**
     * O3: keys the parsed share items for conversion. A real (non-blank) IndexId —
     * what every real v2rayN export carries — is used VERBATIM with
     * first-occurrence-wins on true duplicates, byte-for-byte identical to the old
     * keying. A null/blank IndexId (the field is nullable only for parser
     * robustness) used to collapse all such records onto the "" key, where
     * putIfAbsent silently dropped all but the first from the import; each one now
     * gets a synthesized key in the reserved [BLANK_ID_PREFIX] namespace, numbered
     * past any key already present, so no record is ever silently dropped.
     * Insertion (input) order is preserved.
     */
    internal fun buildIdMap(items: List<V2rayNShareItem>): Map<String, V2rayNShareItem> {
        val idMap = mutableMapOf<String, V2rayNShareItem>()
        var blankSeq = 0
        items.forEach { item ->
            val id = item.IndexId
            val key = if (id.isNullOrBlank()) {
                do {
                    ++blankSeq
                } while (idMap.containsKey("$BLANK_ID_PREFIX$blankSeq"))
                "$BLANK_ID_PREFIX$blankSeq"
            } else {
                id
            }
            idMap.putIfAbsent(key, item)
        }
        return idMap
    }

    /**
     * Converts every share item in isolation: one unsupported entry — e.g. a real
     * v2rayN export carrying a Wireguard (7) or TUIC (8) node this client cannot map
     * (the share numbering mirrors v2rayN's EConfigType: Hysteria2 is 9 and IS mapped;
     * 7/8 have no branch and error) —
     * is reported to [onError] and skipped, instead of throwing through the caller and
     * aborting the WHOLE batch (including every plain-URI profile already parsed next
     * to it). Same one-bad-item failure class [AngConfigManager.parseBatchProfiles]
     * guards against for the URI branch via [com.v2ray.ang.handler.SubscriptionBatch].
     *
     * @param idMap Parsed share items keyed by real IndexId (first occurrence wins);
     *        blank-IndexId records ride synthesized unique keys — see [buildIdMap].
     * @param subId Subscription id stamped on "self" policy-group items.
     * @param onError Invoked with the failing item and its exception.
     * @return The converted profiles, in idMap insertion order, minus failed items.
     */
    internal fun convertShareItems(
        idMap: Map<String, V2rayNShareItem>,
        subId: String,
        onError: (V2rayNShareItem, Exception) -> Unit = { _, _ -> },
    ): List<ProfileItem> {
        return idMap.values.mapNotNull { item ->
            try {
                convertShareItem(item, subId, idMap)
            } catch (e: Exception) {
                onError(item, e)
                null
            }
        }
    }

    /**
     * Pure conversion of one share item into a [ProfileItem]. Throws for a
     * [V2rayNShareItem.ConfigType] this client cannot represent — the caller isolates
     * that failure per item.
     */
    internal fun convertShareItem(
        item: V2rayNShareItem,
        subId: String,
        idMap: Map<String, V2rayNShareItem>,
    ): ProfileItem {
        return item.toProfileItem().apply {
            val proto = item.ProtoExtraObj
            policyGroupSubscriptionId = if (proto?.SubChildItems == "self") {
                subId
            } else {
                null
            }
            proto?.ChildItems?.takeIf { it.isNotNullEmpty() }?.let { ids ->
                val remarks = ids.split(",")
                    .mapNotNull { idMap[it]?.Remarks }
                    .filter { it.isNotNullEmpty() }

                if (remarks.isNotEmpty()) {
                    when (item.ConfigType) {
                        101 -> policyGroupFilter = remarks.joinToString("|", "^(", ")$") { Regex.escape(it) }
                        102 -> proxyChainProfiles = remarks.joinToString(",")
                    }
                }
            }
        }
    }

    private fun parseShareItem(str: String): V2rayNShareItem? = try {
        JsonUtil.fromJson(Utils.decode(str.substringAfterLast('/')), V2rayNShareItem::class.java)
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN share item", e)
        null
    }
}