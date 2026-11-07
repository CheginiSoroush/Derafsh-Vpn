package com.v2ray.ang.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * A15 regression: FmtBase.getQueryParam must never throw on malformed untrusted
 * query segments (trailing '&', '&&', bare flags, empty keys) — the legacy
 * split+destructure threw IndexOutOfBoundsException and the per-line catch in
 * AngConfigManager then dropped the WHOLE profile. Well-formed queries must
 * parse exactly as before.
 */
class FmtBaseTest {

    private val fmtBase = FmtBase()

    @Test
    fun trailingAmpersandParsesWholeProfile() {
        // Legacy code threw here: rawQuery "type=ws&" -> empty last segment -> crash -> profile dropped.
        val profile = VlessFmt.parse("vless://uuid@example.com:443?type=ws&")

        assertEquals("example.com", profile?.server)
        assertEquals("443", profile?.serverPort)
        assertEquals("ws", profile?.network)
    }

    @Test
    fun doubleAmpersandParsesPrecedingAndFollowingPairs() {
        val profile = VlessFmt.parse("vless://uuid@example.com:443?type=ws&&host=example.org")

        assertEquals("ws", profile?.network)
        assertEquals("example.org", profile?.host)
    }

    @Test
    fun bareFlagSegmentIsSkipped() {
        val params = fmtBase.getQueryParam(URI("vless://uuid@example.com:443?security&type=ws"))

        assertFalse(params.containsKey("security"))
        assertEquals("ws", params["type"])
    }

    @Test
    fun emptyKeySegmentIsSkipped() {
        val params = fmtBase.getQueryParam(URI("vless://uuid@example.com:443?=leak&type=ws"))

        assertFalse(params.containsKey(""))
        assertEquals("ws", params["type"])
    }

    @Test
    fun valueWithUnencodedEqualsIsFullyPreserved() {
        // Legacy single-split truncated to "abc" (everything from the first '=' on was dropped
        // for values containing raw '='). The fixed parser keeps everything after the FIRST '='.
        val params = fmtBase.getQueryParam(URI("vless://uuid@example.com:443?pbk=abc=def"))

        assertEquals("abc=def", params["pbk"])
    }

    @Test
    fun wellFormedQueryUnchanged() {
        val params = fmtBase.getQueryParam(URI("vless://uuid@example.com:443?type=ws&path=%2Fabc&host=ex.com"))

        assertEquals(
            mapOf("type" to "ws", "path" to "/abc", "host" to "ex.com"),
            params
        )
    }

    @Test
    fun duplicateKeysLastWins() {
        // Pins the legacy associate() semantics: later pair overwrites earlier one.
        val params = fmtBase.getQueryParam(URI("vless://uuid@example.com:443?type=kcp&type=ws"))

        assertEquals("ws", params["type"])
    }

    @Test
    fun queryOfOnlyMalformedSegmentsYieldsEmptyMap() {
        // Callers guard isNullOrEmpty(rawQuery) before invoking; a defined-but-degenerate
        // query must still yield an empty map, not an exception.
        assertTrue(fmtBase.getQueryParam(URI("vless://uuid@example.com:443?&")).isEmpty())
    }

    @Test
    fun hysteria2WithTrailingAmpersandParses() {
        val profile = Hysteria2Fmt.parse("hysteria2://test@example.com:443?sni=example.org&")

        assertEquals("example.org", profile.sni)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun trojanWithBareFlagParses() {
        val profile = TrojanFmt.parse("trojan://pw@example.com:443?sni=example.org&insecure")

        assertEquals("example.org", profile.sni)
        // "insecure" is a bare flag: no "insecure=1" pair, so it must NOT enable insecurity.
        assertEquals(false, profile.insecure)
    }
}
