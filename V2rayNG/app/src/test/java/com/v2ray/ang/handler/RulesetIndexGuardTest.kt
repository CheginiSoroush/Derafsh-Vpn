package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * JVM tests for the routing-ruleset index guard in [SettingsManager]: a stale UI
 * position (list shrank meanwhile) must resolve to null instead of throwing or
 * touching the wrong entry.
 */
class RulesetIndexGuardTest {

    @Test
    fun `valid index resolves to itself`() {
        assertEquals(0, SettingsManager.resolveRulesetIndex(0, 3))
        assertEquals(2, SettingsManager.resolveRulesetIndex(2, 3))
    }

    @Test
    fun `negative index resolves to null`() {
        assertNull(SettingsManager.resolveRulesetIndex(-1, 3))
    }

    @Test
    fun `index equal to size resolves to null`() {
        assertNull(SettingsManager.resolveRulesetIndex(3, 3))
    }

    @Test
    fun `index beyond size resolves to null`() {
        assertNull(SettingsManager.resolveRulesetIndex(10, 3))
    }

    @Test
    fun `any index resolves to null on an empty list`() {
        assertNull(SettingsManager.resolveRulesetIndex(0, 0))
    }
}
