package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * O4 cheap guard: proxy-chain members are stored BY REMARK (proxyChainProfiles,
 * comma-joined), so renaming or deleting a member profile silently dangles every
 * chain holding it — the dangling hop is dropped at connect time with no user
 * signal (CoreConfigContextBuilder.resolveProxyChainProfiles mapNotNull over
 * SettingsManager.getServerViaRemarks). These helpers find the affected chains
 * so callers can warn around renames/deletes. Matching mirrors connect-time
 * resolution exactly: split(",") with exact token equality (getServerViaRemarks
 * compares it.remarks == remarks, no trimming) — a token that would not resolve
 * at connect is exactly the dangle worth warning about. Full guid-based member
 * storage remains the future data-model decision (OBSERVATION-O4).
 */

/**
 * Pure scan: names of the chains in [chains] whose proxyChainProfiles holds
 * [remark] as an exact comma-token, in input order, deduplicated.
 * Blank remarks match nothing — a blank token is not a resolvable member.
 */
internal fun chainNamesReferencing(remark: String, chains: List<ProfileItem>): List<String> {
    if (remark.isEmpty()) return emptyList()
    return chains.asSequence()
        .filter { it.configType == EConfigType.PROXYCHAIN }
        .filter { chain -> chain.proxyChainProfiles.orEmpty().split(",").any { it == remark } }
        .map { it.remarks }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}

/**
 * Storage-backed scan over every stored chain profile; names of the chains
 * referencing [remark] as a member.
 */
fun getChainNamesReferencing(remark: String): List<String> {
    if (remark.isBlank()) return emptyList()
    val chains = MmkvManager.decodeAllServerList()
        .mapNotNull { MmkvManager.decodeServerConfig(it) }
        .filter { it.configType == EConfigType.PROXYCHAIN }
    return chainNamesReferencing(remark, chains)
}
