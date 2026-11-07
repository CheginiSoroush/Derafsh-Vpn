package com.v2ray.ang.root

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A16 regression pins for RootProxyManager's pure decision seams.
 *
 * A16 has two halves:
 * 1. The "__unknown_app__" (unidentified) pseudo-app from the per-app picker was
 *    mapped to uid "-1" and emitted as `--uid-owner -1`, but iptables rejects that
 *    literal (libxt_owner owner_parse_range validates 0..UINT32_MAX-1) and the
 *    marking rules run under `set -e` — one rejected rule aborted the whole setup
 *    script, so root mode failed to start whenever the pseudo-app was selected.
 *    The filter must keep it out of every uid rule.
 * 2. The root-side oom re-pin loop ran `while true` forever: after the app died it
 *    leaked until reboot and, on pid reuse, blindly pinned an unrelated process to
 *    -1000. The loop must verify the pid's cmdline identity and break on loss.
 */
class RootProxyManagerTest {

    @Test
    fun unidentifiedPseudoPackageIsFiltered() {
        assertEquals(
            emptyList<String>(),
            RootProxyManager.iptablesRepresentablePackages(listOf(AppConfig.UNIDENTIFIED_PACKAGE))
        )
    }

    @Test
    fun unidentifiedIsFilteredFromMixedSelectionInOrder() {
        assertEquals(
            listOf("com.example.one", "com.example.two"),
            RootProxyManager.iptablesRepresentablePackages(
                listOf("com.example.one", AppConfig.UNIDENTIFIED_PACKAGE, "com.example.two")
            )
        )
    }

    @Test
    fun identifiedPackagesAreKept() {
        val pkgs = listOf("com.example.one", "com.example.two")
        assertEquals(pkgs, RootProxyManager.iptablesRepresentablePackages(pkgs))
    }

    @Test
    fun emptySelectionYieldsEmpty() {
        assertEquals(emptyList<String>(), RootProxyManager.iptablesRepresentablePackages(emptyList()))
    }

    @Test
    fun oomGuardPinsAppScoreWhileIdentityHolds() {
        val cmd = RootProxyManager.buildOomGuardCommand(4242, "com.derafsh.vpn")

        assertTrue(cmd.contains("tr \"\\0\" \"\\n\" < /proc/4242/cmdline"))
        assertTrue(cmd.contains("grep -qs \"^com.derafsh.vpn\$\" || break"))
        assertTrue(cmd.contains("echo -1000 > /proc/4242/oom_score_adj 2>/dev/null"))
        assertTrue(cmd.contains("sleep 5"))
        assertTrue(cmd.startsWith("nohup sh -c '"))
        assertTrue(cmd.endsWith("' >/dev/null 2>&1 &"))
    }

    @Test
    fun oomGuardChecksIdentityBeforePinning() {
        val cmd = RootProxyManager.buildOomGuardCommand(4242, "com.derafsh.vpn")

        val breakAt = cmd.indexOf("|| break")
        val pinAt = cmd.indexOf("echo -1000")
        assertTrue(breakAt in 0 until pinAt, "identity check must gate the pin write")
    }

    @Test
    fun oomGuardPayloadHasNoSingleQuotes() {
        val cmd = RootProxyManager.buildOomGuardCommand(4242, "com.derafsh.vpn")
        val payload = cmd.removePrefix("nohup sh -c '").substringBefore("' >/dev/null 2>&1 &")

        assertFalse(payload.contains('\''), "sh -c payload must stay single-quote-free")
    }

    @Test
    fun hevConfigQuotesCredentialsSafely() {
        val config = RootProxyManager.buildHevConfig(
            socksUsername = "it's",
            socksPassword = "a\"b",
            socksPort = 10808,
            ipv6 = false,
            mtu = 8500,
            lanSharing = false
        )

        assertTrue(config.contains("  username: 'it''s'"))
        assertTrue(config.contains("  password: 'a\"b'"))
    }

    @Test
    fun hevConfigOmitsCredentialsWhenEitherIsNull() {
        val noUser = RootProxyManager.buildHevConfig(null, "pw", 10808, false, 8500, false)
        val noPass = RootProxyManager.buildHevConfig("user", null, 10808, false, 8500, false)

        assertFalse(noUser.contains("username:"))
        assertFalse(noUser.contains("password:"))
        assertFalse(noPass.contains("username:"))
        assertFalse(noPass.contains("password:"))
    }

    @Test
    fun hevConfigShapeWithIpv6Disabled() {
        val config = RootProxyManager.buildHevConfig(null, null, 10808, false, 8500, false)

        assertTrue(config.contains("  name: 'utun7788'"))
        assertTrue(config.contains("  mtu: 8500"))
        assertTrue(config.contains("  port: 10808"))
        assertTrue(config.contains("  address: '127.0.0.1'"))
        assertTrue(config.contains("  ipv4: '198.18.0.1'"))
        assertFalse(config.contains("ipv6:"), "no v6 tunnel address when ipv6 disabled")
        assertFalse(config.contains("offload:"), "offload only for LAN sharing")
    }

    @Test
    fun hevConfigIpv6AndLanSharingOffload() {
        val sharing = RootProxyManager.buildHevConfig(null, null, 10808, true, 8500, true)

        assertTrue(sharing.contains("  ipv6: 'fdfe:dcba:9876::1'"))
        assertTrue(sharing.contains("    jumbo: false"))
        assertTrue(sharing.contains("    tso: false"))

        val rootCapture = RootProxyManager.buildHevConfig(null, null, 10808, true, 8500, false)
        assertTrue(rootCapture.contains("  ipv6: 'fdfe:dcba:9876::1'"))
        assertFalse(rootCapture.contains("offload:"))
    }
}
