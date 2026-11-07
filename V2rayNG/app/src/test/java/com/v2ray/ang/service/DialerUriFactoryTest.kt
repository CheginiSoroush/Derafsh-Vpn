package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pinning DialerUriFactory (A9): every builder must fail CLOSED (null) instead of throwing.
 * Before the factory existed, URI(...) built from a REMOTE server's control token threw
 * URISyntaxException inside a handler-less coroutine and crashed the app at VPN start.
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
    fun `buildDialerProbeUrl maps secure schemes to https`() {
        assertEquals("https://example.com/", DialerUriFactory.buildDialerProbeUrl("wss://example.com:8443"))
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
    fun `buildControlWsUrl with an illegal token character returns null instead of crashing (A9)`() {
        // The token regex admits | ` ^ { } < > etc.; the old inline URI(...) construction
        // threw URISyntaxException on these and the handler-less launch crashed the app.
        val illegalTokens = listOf("a|b", "a`b", "a^b", "a{b}", "a<b>", "a\\b", "a[b]")

        illegalTokens.forEach { token ->
            assertNull(DialerUriFactory.buildControlWsUrl("1.2.3.4:8080", token), "token: $token")
        }
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
