package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the O4 chain-reference scan: proxy-chain members are stored BY REMARK
 * (proxyChainProfiles, comma-joined), so renaming or deleting a member profile
 * dangles every chain holding it. The scan must mirror connect-time resolution
 * exactly (split(",") + exact token equality via getServerViaRemarks — no
 * trimming, blank tokens never resolve) so a reported reference is exactly a
 * reference that would drop a hop at connect.
 */
class ChainReferencesTest {

    private fun chain(remarks: String, members: String?) = ProfileItem(
        configType = EConfigType.PROXYCHAIN,
        remarks = remarks,
        proxyChainProfiles = members,
    )

    private fun plain(remarks: String, configType: EConfigType = EConfigType.VMESS) = ProfileItem(
        configType = configType,
        remarks = remarks,
        proxyChainProfiles = remarks, // data oddity: non-chains must never be scanned
    )

    @Test
    fun `exact member token finds the holding chain by name`() {
        val chains = listOf(chain("MyChain", "a,b,Target"))

        assertEquals(listOf("MyChain"), chainNamesReferencing("Target", chains))
    }

    @Test
    fun `match requires exact token equality like connect-time resolution`() {
        val chains = listOf(chain("MyChain", "a, Target ,b"))

        // " Target " tokens never resolve via getServerViaRemarks("Target") —
        // the dangle is real, but the scan reports only TRUE exact-token refs.
        assertTrue(chainNamesReferencing("Target", chains).isEmpty())
    }

    @Test
    fun `blank remark matches nothing`() {
        val chains = listOf(chain("MyChain", "a,b"), chain("Blanky", ""))

        assertTrue(chainNamesReferencing("", chains).isEmpty())
    }

    @Test
    fun `non-chain profiles are never scanned even with the field set`() {
        val profiles = listOf(
            plain("Target"),
            plain("TargetGroup", EConfigType.POLICYGROUP),
            plain("TargetCustom", EConfigType.CUSTOM),
        )

        assertTrue(chainNamesReferencing("Target", profiles).isEmpty())
        assertTrue(chainNamesReferencing("TargetGroup", profiles).isEmpty())
    }

    @Test
    fun `every holding chain is named in input order without duplicates`() {
        val chains = listOf(
            chain("First", "Target,x"),
            chain("Second", "y"),
            chain("Third", "z,Target"),
            chain("First", "Target,y"), // duplicate chain remark — dedup by name
        )

        assertEquals(listOf("First", "Third"), chainNamesReferencing("Target", chains))
    }

    @Test
    fun `blank or missing member strings hold no members`() {
        val chains = listOf(
            chain("NullMembers", null),
            chain("EmptyMembers", ""),
            chain("BlankMembers", "  "),
            chain("OnlyCommas", ",,,"),
        )

        assertTrue(chainNamesReferencing("Target", chains).isEmpty())
    }

    @Test
    fun `multi-hop chain holding the remark twice still reports once`() {
        val chains = listOf(chain("MyChain", "Target,middle,Target"))

        assertEquals(listOf("MyChain"), chainNamesReferencing("Target", chains))
    }

    @Test
    fun `no reference yields an empty list`() {
        val chains = listOf(chain("MyChain", "a,b,c"))

        assertTrue(chainNamesReferencing("Unrelated", chains).isEmpty())
    }
}
