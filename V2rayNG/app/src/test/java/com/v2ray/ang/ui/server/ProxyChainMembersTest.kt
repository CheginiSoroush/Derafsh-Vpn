package com.v2ray.ang.ui.server

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyChainMembersTest {
    @Test
    fun removalFollowsThePendingKeyAfterReordering() {
        val pendingKey = "two"
        val members = listOf("Two", "One", "Three")
        val keys = listOf("two", "one", "three")

        val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, keys, pendingKey)

        assertEquals(listOf("One", "Three"), remainingMembers)
        assertEquals(listOf("one", "three"), remainingKeys)
        assertEquals(listOf("Two", "One", "Three"), members)
        assertEquals(listOf("two", "one", "three"), keys)
    }

    @Test
    fun duplicateNamesDoNotChangeWhichMemberIsRemoved() {
        val (members, keys) = withoutProxyChainMember(
            listOf("Same", "Same", "Other"),
            listOf("first", "second", "third"),
            "second",
        )

        assertEquals(listOf("Same", "Other"), members)
        assertEquals(listOf("first", "third"), keys)
    }

    @Test
    fun missingMemberLeavesBothListsUnchanged() {
        val members = listOf("One", "Three")
        val keys = listOf("one", "three")

        val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, keys, "two")

        assertSame(members, remainingMembers)
        assertSame(keys, remainingKeys)
    }

    @Test
    fun emptyChainRemainsEmpty() {
        assertEquals(
            emptyList<String>() to emptyList<String>(),
            withoutProxyChainMember(emptyList(), emptyList(), "missing"),
        )
    }

    @Test
    fun blankMemberIsRemovedWithItsOwnKey() {
        assertEquals(
            listOf("One") to listOf("one"),
            withoutProxyChainMember(listOf("One", ""), listOf("one", "blank"), "blank"),
        )
    }

    // ---- findInvalidProxyChainMembers ----

    private fun resolverOf(vararg validPlain: String): (String) -> EConfigType? {
        val valid = validPlain.toSet()
        return { remark -> if (remark in valid) EConfigType.SOCKS else null }
    }

    @Test
    fun commaRemarkIsRejectedEvenWhenItResolves() {
        val invalid = findInvalidProxyChainMembers(
            listOf("US, 2", "plain"),
            resolveConfigType = resolverOf("US, 2", "plain"),
        )

        assertEquals(listOf("US, 2"), invalid)
    }

    @Test
    fun commaAloneIsEnoughToRejectWithoutConsultingResolver() {
        var consulted = false
        val invalid = findInvalidProxyChainMembers(
            listOf("a,b"),
            resolveConfigType = { consulted = true; EConfigType.SOCKS },
        )

        assertEquals(listOf("a,b"), invalid)
        assertTrue(!consulted, "resolver must not be needed to reject a comma remark")
    }

    @Test
    fun unresolvedRemarkIsRejected() {
        val invalid = findInvalidProxyChainMembers(
            listOf("gone", "plain"),
            resolveConfigType = resolverOf("plain"),
        )

        assertEquals(listOf("gone"), invalid)
    }

    @Test
    fun complexTypeProfileIsRejected() {
        val invalid = findInvalidProxyChainMembers(
            listOf("chainy", "plain"),
            resolveConfigType = { remark ->
                when (remark) {
                    "chainy" -> EConfigType.PROXYCHAIN
                    "groupy" -> EConfigType.POLICYGROUP
                    "custom" -> EConfigType.CUSTOM
                    "plain" -> EConfigType.SOCKS
                    else -> null
                }
            },
        )

        assertEquals(listOf("chainy"), invalid)
        assertTrue(
            findInvalidProxyChainMembers(
                listOf("groupy", "custom", "plain"),
                resolveConfigType = {
                    when (it) {
                        "groupy" -> EConfigType.POLICYGROUP
                        "custom" -> EConfigType.CUSTOM
                        else -> EConfigType.SOCKS
                    }
                },
            ).let { it == listOf("groupy", "custom") },
        )
    }

    @Test
    fun plainRemarksPassAndBlanksAreNotReported() {
        val invalid = findInvalidProxyChainMembers(
            listOf("", "   ", "one", "two"),
            resolveConfigType = resolverOf("one", "two"),
        )

        assertTrue(invalid.isEmpty(), "blanks are the caller's unselected-row concern, not invalid members")
    }

    @Test
    fun trimmingHappensBeforeValidationAndReportedTrimmed() {
        val invalid = findInvalidProxyChainMembers(
            listOf("  one  ", " x,y "),
            resolveConfigType = resolverOf("one"),
        )

        assertEquals(listOf("x,y"), invalid)
    }

    @Test
    fun everyCommaFragmentOfAStoredChainWouldBeCaughtOnReload() {
        // Round-trip: "US, 2,plain" (a saved chain holding the remark "US, 2") reloads
        // through split(",") as three fragments; the validator must reject the two
        // shatter fragments so the corruption is surfaced at edit time.
        val reloaded = "US, 2,plain".split(",").map { it.trim() }

        val invalid = findInvalidProxyChainMembers(reloaded, resolveConfigType = resolverOf("plain"))

        assertEquals(listOf("US", "2"), invalid)
    }
}
