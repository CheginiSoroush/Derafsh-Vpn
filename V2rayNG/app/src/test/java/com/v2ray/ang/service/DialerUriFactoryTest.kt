package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pinning DialerUriFactory. EMPIRICAL BASELINE (verified on JDK 21, recorded in audit
 * STATE as A9-REFUTED): java.net.URI's multi-argument constructors QUOTE illegal
 * component characters (token=| -> token=%7C) instead of throwing — the old inline code
 * was already crash-free for remote token characters. These tests pin the exact quoting
 * (so a JDK/refactor change stays visible), the fail-closed nulls for structural
 * problems, and the never-throw guarantee.
 */
class DialerUriFactoryTest {

    // ---- parseDialerUri ----

    @Test
    fun `parseDialerUri defaults scheme-less addresses to http`() {
        val uri = DialerUriFactory.parseDialerUri("1.2.3.4:8080")

        assertEquals("http", uri?.scheme)
        assertEquals("1.2.3.4", uri?.host)
        assertEquals(8080, uri?.port)
    }

    @Test
    fun `parseDialerUri keeps an explicit scheme and path`() {
        val uri = DialerUriFactory.parseDialerUri("https://example.com/sub?x=1")

        assertEquals("https", uri?.scheme)
        assertEquals("example.com", uri?.host)
        assertEquals("/sub", uri?.path)
    }

    @Test
    fun `parseDialerUri trims whitespace`() {
        val uri = DialerUriFactory.parseDialerUri("  example.com  ")

        assertEquals("example.com", uri?.host)
    }

    @Test
    fun `parseDialerUri returns null instead of throwing on malformed input`() {
        assertNull(DialerUriFactory.parseDialerUri(""))
        assertNull(DialerUriFactory.parseDialerUri("   "))
        assertNull(DialerUriFactory.parseDialerUri("http://[::1"))
        assertNull(DialerUriFactory.parseDialerUri("ht tp://bad"))
    }

    // ---- buildDialerProbeUrl ----

    @Test
    fun `buildDialerProbeUrl maps secure schemes to https and keeps the port`() {
        assertEquals("https://example.com:8443/", DialerUriFactory.buildDialerProbeUrl("wss://example.com:8443"))
        assertEquals("https://example.com/", DialerUriFactory.buildDialerProbeUrl("https://example.com/sub/page"))
    }

    @Test
    fun `buildDialerProbeUrl maps plain schemes to http`() {
        assertEquals("http://1.2.3.4:8080/", DialerUriFactory.buildDialerProbeUrl("1.2.3.4:8080"))
        assertEquals("http://1.2.3.4:8080/", DialerUriFactory.buildDialerProbeUrl("ws://1.2.3.4:8080"))
    }

    @Test
    fun `buildDialerProbeUrl fails closed on empty or host-less input`() {
        assertNull(DialerUriFactory.buildDialerProbeUrl(""))
        assertNull(DialerUriFactory.buildDialerProbeUrl("  "))
        assertNull(DialerUriFactory.buildDialerProbeUrl("/path/only"))
    }

    // ---- extractControlToken ----

    @Test
    fun `extractControlToken reads the token out of the endpoint HTML`() {
        val html = """<script>new WebSocket("ws://x/websocket?token=abc123-DEF_456")</script>"""

        assertEquals("abc123-DEF_456", DialerUriFactory.extractControlToken(html))
    }

    @Test
    fun `extractControlToken returns null when there is no token or it is blank`() {
        assertNull(DialerUriFactory.extractControlToken("<html>nothing here</html>"))
        assertNull(DialerUriFactory.extractControlToken("/websocket?token="))
    }

    // ---- buildControlWsUrl ----

    @Test
    fun `buildControlWsUrl builds the control endpoint with the token`() {
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=tok",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "tok"),
        )
        assertEquals(
            "ws://example.com/websocket?token=tok",
            DialerUriFactory.buildControlWsUrl("example.com", "tok"),
        )
    }

    @Test
    fun `buildControlWsUrl percent-encodes characters that are illegal in a URI query (A9 refutation)`() {
        // The token regex admits | ` ^ { } < > etc.; the JDK multi-arg URI constructor
        // quotes them into %XX form instead of throwing, so the URL is valid and the
        // control channel keeps working with the encoded token.
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%7Cb",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a|b"),
        )
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%60b",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a`b"),
        )
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%5Eb",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a^b"),
        )
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%7Bb%7D",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a{b}"),
        )
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%3Cb%3E",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a<b>"),
        )
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a%5Cb",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a\\b"),
        )
        // RFC 2732: brackets stay literal in a query (JDK behavior, empirically verified)
        assertEquals(
            "ws://1.2.3.4:8080/websocket?token=a[b]",
            DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "a[b]"),
        )
    }

    @Test
    fun `buildControlWsUrl fails closed on a blank token`() {
        assertNull(DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", ""))
        assertNull(DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", "   "))
    }

    @Test
    fun `buildControlWsUrl keeps query-legal special characters verbatim`() {
        // & and = are legal in a URI query, so the token is embedded unchanged (documented
        // behavior; the server produced this token and expects it back as-is).
        assertEquals(
            "ws://example.com/websocket?token=a=b&c",
            DialerUriFactory.buildControlWsUrl("example.com", "a=b&c"),
        )
    }
}
