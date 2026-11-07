package com.v2ray.ang.ui.server

import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType

/** Removes one draft member and its row key together, resolving its current position at confirmation. */
internal fun withoutProxyChainMember(
    members: List<String>,
    memberKeys: List<String>,
    memberKey: String,
): Pair<List<String>, List<String>> {
    val index = memberKeys.indexOf(memberKey)
    if (index < 0) return members to memberKeys

    return members.toMutableList().also { it.removeAt(index) } to
        memberKeys.toMutableList().also { it.removeAt(index) }
}

/**
 * Returns the chain members that cannot be persisted as-is.
 *
 * Members are stored comma-joined in proxyChainProfiles (a remark-based encoding
 * shared with the v2rayN "102" import format), so a remark containing ',' cannot
 * survive a save/reload round-trip: split(",") would shatter it into unrelated
 * fragments that either dangle (hop silently dropped at connect) or rebind to a
 * different same-named profile. Unresolvable remarks and complex-typed profiles
 * are equally unchainable. Blank entries are NOT reported here — the caller
 * treats them as "unselected" rows with their own message.
 */
internal fun findInvalidProxyChainMembers(
    members: List<String>,
    resolveConfigType: (String) -> EConfigType?,
): List<String> =
    members.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { member ->
            if (member.indexOf(',') >= 0) {
                true
            } else {
                val type = resolveConfigType(member)
                type == null || type.isComplexType()
            }
        }
        .toList()
