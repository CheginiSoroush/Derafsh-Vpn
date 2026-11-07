package com.v2ray.ang

import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the segment-based IPv6 validation (A18). The old alternation regex wrongly
 * accepted malformed forms — "1:::2" (triple colon), "1:2:3:4:5:6:7::8" and
 * "1:2:3:4:5:6:7:8::9" (over-full compressed forms, 8/9 explicit groups plus "::").
 * Every form accepted by the old validator that is VALID IPv6 must stay accepted.
 */
class UtilsIpv6Test {

    @Test
    fun malformedTripleColonIsRejected() {
        assertFalse(Utils.isPureIpAddress("1:::2"))
        assertFalse(Utils.isPureIpAddress(":::"))
        assertFalse(Utils.isPureIpAddress("1:::"))
        assertFalse(Utils.isPureIpAddress("fe80:::1"))
    }

    @Test
    fun overFullCompressedFormsAreRejected() {
        // "::" must stand in for at least one group: 8 explicit groups + "::" is invalid.
        assertFalse(Utils.isPureIpAddress("1:2:3:4:5:6:7::8"))
        assertFalse(Utils.isPureIpAddress("1:2:3:4:5:6:7:8::"))
        assertFalse(Utils.isPureIpAddress("1:2:3:4:5:6:7:8::9"))
        assertFalse(Utils.isPureIpAddress("::1:2:3:4:5:6:7:8"))
    }

    @Test
    fun trailingSingleColonIsRejected() {
        assertFalse(Utils.isPureIpAddress("1::2:"))
        assertFalse(Utils.isPureIpAddress("1:2:3:4:5:6:7:8:"))
    }

    @Test
    fun emptyAndNonHexGroupsAreRejected() {
        assertFalse(Utils.isPureIpAddress(":1:2"))
        assertFalse(Utils.isPureIpAddress("1::::2"))
        assertFalse(Utils.isPureIpAddress("1::2::3"))
        assertFalse(Utils.isPureIpAddress("g::1"))
        assertFalse(Utils.isPureIpAddress("12345::1"))
        assertFalse(Utils.isPureIpAddress("::ffff:1.2.3.4"))
    }

    @Test
    fun validFullAndCompressedFormsAreAccepted() {
        assertTrue(Utils.isPureIpAddress("::"))
        assertTrue(Utils.isPureIpAddress("::1"))
        assertTrue(Utils.isPureIpAddress("1::"))
        assertTrue(Utils.isPureIpAddress("1::2"))
        assertTrue(Utils.isPureIpAddress("fe80::1"))
        assertTrue(Utils.isPureIpAddress("2001:4321::1"))
        assertTrue(Utils.isPureIpAddress("1:2:3:4:5:6:7:8"))
        assertTrue(Utils.isPureIpAddress("0:0:0:0:0:0:0:0"))
        // "::" covers exactly one group here — 7 explicit groups total is valid.
        assertTrue(Utils.isPureIpAddress("1:2:3:4:5:6:7::"))
        assertTrue(Utils.isPureIpAddress("::2:3:4:5:6:7:8"))
        assertTrue(Utils.isPureIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isPureIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isPureIpAddress("0001:0000:0000:0000:0000:0000:0000:0001"))
    }

    @Test
    fun bracketedAndCidrFormsKeepWorking() {
        // isIpAddress keeps its existing lenient bracket handling ("[addr]:port").
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
        // ::ffff:-mapped IPv4 is handled by isIpAddress's special case, not the v6 validator.
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
    }

    @Test
    fun getIpv6AddressBracketsOnlyValidAddresses() {
        assertEquals("[fe80::1]", Utils.getIpv6Address("fe80::1"))
        assertEquals("[::1]", Utils.getIpv6Address("::1"))
        // Malformed input must not be presented to the core as a bracketed address.
        assertEquals("1:::2", Utils.getIpv6Address("1:::2"))
        assertEquals("1:2:3:4:5:6:7::8", Utils.getIpv6Address("1:2:3:4:5:6:7::8"))
    }
}
