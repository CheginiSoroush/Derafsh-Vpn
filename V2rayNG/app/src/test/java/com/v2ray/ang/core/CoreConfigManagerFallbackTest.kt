package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the policy-group balancer fallback decision in [CoreConfigManager]:
 * which fallback tag a balancer gets, and whether a standard observatory is attached.
 * These encode xray semantics — dead probes must never route traffic to the default
 * outbound, and random/roundRobin only health-filter when a fallbackTag exists.
 */
class CoreConfigManagerFallbackTest {

    @Test
    fun `leastPing always falls back to the first member`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.LEAST_PING,
            profile = policyGroup(fallbackTag = "custom", testOutbounds = false),
            firstMemberTag = "first",
        )

        assertEquals("first", result)
    }

    @Test
    fun `leastLoad defers to the burst observatory and falls back to the first member`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.LEAST_LOAD,
            profile = policyGroup(fallbackTag = "custom", testOutbounds = true),
            firstMemberTag = "first",
        )

        assertEquals("first", result)
    }

    @Test
    fun `random with testing enabled uses the configured fallback tag`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.RANDOM,
            profile = policyGroup(fallbackTag = "member-2", testOutbounds = true),
            firstMemberTag = "first",
        )

        assertEquals("member-2", result)
    }

    @Test
    fun `random with testing enabled by default uses the configured fallback tag`() {
        // policyGroupTestOutbounds == null means the toggle was never touched = enabled.
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.ROUND_ROBIN,
            profile = policyGroup(fallbackTag = "member-2", testOutbounds = null),
            firstMemberTag = "first",
        )

        assertEquals("member-2", result)
    }

    @Test
    fun `proxy fallback tag is rejected in favor of the first member`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.RANDOM,
            profile = policyGroup(fallbackTag = AppConfig.TAG_PROXY, testOutbounds = true),
            firstMemberTag = "first",
        )

        assertEquals("first", result)
    }

    @Test
    fun `empty fallback tag falls back to the first member`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.RANDOM,
            profile = policyGroup(fallbackTag = "", testOutbounds = true),
            firstMemberTag = "first",
        )

        assertEquals("first", result)
    }

    @Test
    fun `random with testing disabled gets no fallback at all`() {
        val result = CoreConfigManager.resolvePolicyGroupFallbackTag(
            strategyType = BalancerStrategyType.RANDOM,
            profile = policyGroup(fallbackTag = "member-2", testOutbounds = false),
            firstMemberTag = "first",
        )

        assertEquals(null, result)
    }

    @Test
    fun `standard observatory follows the strategy and fallback tag`() {
        assertTrue(
            CoreConfigManager.shouldUseStandardObservatory(BalancerStrategyType.LEAST_PING, null)
        )
        assertFalse(
            CoreConfigManager.shouldUseStandardObservatory(BalancerStrategyType.LEAST_LOAD, "first")
        )
        assertTrue(
            CoreConfigManager.shouldUseStandardObservatory(BalancerStrategyType.RANDOM, "first")
        )
        assertFalse(
            CoreConfigManager.shouldUseStandardObservatory(BalancerStrategyType.RANDOM, null)
        )
        assertFalse(
            CoreConfigManager.shouldUseStandardObservatory(BalancerStrategyType.ROUND_ROBIN, null)
        )
    }

    private fun policyGroup(
        fallbackTag: String?,
        testOutbounds: Boolean?,
    ) = ProfileItem.create(EConfigType.VMESS).apply {
        remarks = "group"
        policyGroupType = "2"
        policyGroupTestOutbounds = testOutbounds
        policyGroupFallbackTag = fallbackTag
    }
}
