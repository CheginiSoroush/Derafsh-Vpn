package com.v2ray.ang.fmt

import com.v2ray.ang.dto.V2rayNShareItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the per-item isolation of the V2rayN share conversion (A30): one unsupported
 * item — a real v2rayN export legitimately carries Wireguard (7) / TUIC (8) nodes
 * this client cannot map — must be skipped with an [onError] report, never abort the
 * whole batch (which previously dropped every plain-URI profile parsed next to it).
 */
class V2rayNConvertTest {

    private fun protoExtra(
        childItems: String? = null,
        subChildItems: String? = null,
        filter: String? = null,
    ): V2rayNShareItem.V2rayNProtocolExtraShareItem =
        V2rayNShareItem.V2rayNProtocolExtraShareItem(
            AlterId = null, VmessSecurity = null, Flow = null, VlessEncryption = null,
            SsMethod = null, WgPublicKey = null, WgPresharedKey = null,
            WgInterfaceAddress = null, WgReserved = null, WgMtu = null,
            SalamanderPass = null, UpMbps = null, DownMbps = null, Ports = null,
            HopInterval = null, GroupType = null, ChildItems = childItems,
            SubChildItems = subChildItems, Filter = filter, MultipleLoad = null,
        )

    private fun shareItem(
        indexId: String? = "idx",
        configType: Int? = 1,
        remarks: String? = "srv",
        address: String? = "example.com",
        port: Int? = 443,
        network: String? = "ws",
        proto: V2rayNShareItem.V2rayNProtocolExtraShareItem? = null,
    ): V2rayNShareItem = V2rayNShareItem(
        IndexId = indexId, ConfigType = configType, ConfigVersion = 2, Subid = "sub",
        IsSub = true, PreSocksPort = null, Remarks = remarks, Address = address,
        Port = port, Password = null, Username = null, Network = network,
        StreamSecurity = null, AllowInsecure = null, Sni = null, Alpn = null,
        Fingerprint = null, PublicKey = null, ShortId = null, SpiderX = null,
        Mldsa65Verify = null, CertSha = null, EchConfigList = null,
        VerifyPeerCertByName = null, Finalmask = null, ProtoExtraObj = proto,
        TransportExtraObj = null,
    )

    private fun idMapOf(vararg items: V2rayNShareItem): Map<String, V2rayNShareItem> =
        items.associateBy { it.IndexId.orEmpty() }

    @Test
    fun `one unsupported config type is isolated and the rest of the batch survives`() {
        val good = shareItem(indexId = "good", configType = 1, remarks = "vmess-node")
        val wireguard = shareItem(indexId = "wg", configType = 7, remarks = "wg-node")
        val tuic = shareItem(indexId = "tuic", configType = 8, remarks = "tuic-node")
        val failures = mutableListOf<Pair<V2rayNShareItem, Exception>>()

        val result = V2rayNFmt.convertShareItems(
            idMapOf(good, wireguard, tuic),
            subId = "sub",
            onError = { item, e -> failures.add(item to e) },
        )

        assertEquals(listOf("vmess-node"), result.map { it.remarks })
        assertEquals(listOf("wg", "tuic"), failures.map { it.first.IndexId })
        assertTrue(failures.all { it.second is IllegalStateException })
    }

    @Test
    fun `isolation preserves the relative order of surviving items`() {
        val good1 = shareItem(indexId = "a", configType = 1, remarks = "first")
        val bad = shareItem(indexId = "b", configType = 99, remarks = "bad")
        val good2 = shareItem(indexId = "c", configType = 3, remarks = "third")

        val result = V2rayNFmt.convertShareItems(idMapOf(good1, bad, good2), subId = "sub")

        assertEquals(listOf("first", "third"), result.map { it.remarks })
    }

    @Test
    fun `all items unsupported yields an empty list with one error per item`() {
        val failures = mutableListOf<V2rayNShareItem>()

        val result = V2rayNFmt.convertShareItems(
            idMapOf(
                shareItem(indexId = "x", configType = 7),
                shareItem(indexId = "y", configType = 8),
            ),
            subId = "sub",
            onError = { item, _ -> failures.add(item) },
        )

        assertEquals(0, result.size)
        assertEquals(listOf("x", "y"), failures.map { it.IndexId })
    }

    @Test
    fun `empty id map converts to nothing`() {
        val failures = mutableListOf<V2rayNShareItem>()

        val result = V2rayNFmt.convertShareItems(emptyMap(), subId = "sub") { item, _ ->
            failures.add(item)
        }

        assertEquals(0, result.size)
        assertEquals(0, failures.size)
    }

    @Test
    fun `valid vmess conversion contract is preserved`() {
        val item = shareItem(
            indexId = "v",
            configType = 1,
            remarks = "node",
            address = "host.example",
            port = 8443,
            network = "ws",
        )

        val profile = V2rayNFmt.convertShareItem(item, subId = "sub", idMap = idMapOf(item))

        assertEquals(EConfigType.VMESS, profile.configType)
        assertEquals("node", profile.remarks)
        assertEquals("host.example", profile.server)
        assertEquals("8443", profile.serverPort)
        assertEquals("ws", profile.network)
        assertEquals(null, profile.policyGroupSubscriptionId)
    }

    @Test
    fun `hysteria2 share forces the hysteria network`() {
        val item = shareItem(indexId = "h", configType = 9, network = null)

        val profile = V2rayNFmt.convertShareItem(item, subId = "sub", idMap = idMapOf(item))

        assertEquals(EConfigType.HYSTERIA2, profile.configType)
        assertEquals("hysteria", profile.network)
    }

    @Test
    fun `self sub-child stamps the subscription id on a policy group`() {
        val item = shareItem(
            indexId = "pg",
            configType = 101,
            proto = protoExtra(subChildItems = "self"),
        )

        val profile = V2rayNFmt.convertShareItem(item, subId = "sub-42", idMap = idMapOf(item))

        assertEquals("sub-42", profile.policyGroupSubscriptionId)
    }

    @Test
    fun `proxy chain child items resolve to remarks through the id map`() {
        val member1 = shareItem(indexId = "m1", configType = 1, remarks = "Alpha")
        val member2 = shareItem(indexId = "m2", configType = 1, remarks = "Beta")
        val chain = shareItem(
            indexId = "ch",
            configType = 102,
            proto = protoExtra(childItems = "m1,m2,m3"),
        )

        val profile = V2rayNFmt.convertShareItem(
            chain, subId = "sub", idMap = idMapOf(member1, member2, chain),
        )

        assertEquals("Alpha,Beta", profile.proxyChainProfiles)
    }

    @Test
    fun `policy group filter regex-escapes every remark`() {
        val member1 = shareItem(indexId = "m1", configType = 1, remarks = "A|B")
        val member2 = shareItem(indexId = "m2", configType = 1, remarks = "C")
        val group = shareItem(
            indexId = "pg",
            configType = 101,
            proto = protoExtra(childItems = "m1,m2"),
        )

        val profile = V2rayNFmt.convertShareItem(
            group, subId = "sub", idMap = idMapOf(member1, member2, group),
        )

        assertEquals("^(\\QA|B\\E|\\QC\\E)$", profile.policyGroupFilter)
    }

    @Test
    fun `unknown config type throws from the pure mapper`() {
        val item = shareItem(indexId = "wg", configType = 7)

        val error = try {
            V2rayNFmt.convertShareItem(item, subId = "sub", idMap = idMapOf(item))
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue(error?.message?.contains("7") == true)
    }
}
