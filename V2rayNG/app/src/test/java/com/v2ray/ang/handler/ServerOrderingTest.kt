package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerOrderingTest {

    private fun delayOf(map: Map<String, Long>): (String) -> Long = { map[it] ?: 0L }

    @Test
    fun `tested configs sort ascending by delay`() {
        val result = ServerOrdering.byTestResult(
            serverList = listOf("a", "b", "c"),
            delayOf = delayOf(mapOf("a" to 300L, "b" to 100L, "c" to 200L)),
        )

        assertEquals(listOf("b", "c", "a"), result)
    }

    @Test
    fun `untested and failed configs go after every real result`() {
        val result = ServerOrdering.byTestResult(
            serverList = listOf("untested", "slow", "failed", "fast"),
            delayOf = delayOf(mapOf("untested" to 0L, "slow" to 900L, "failed" to -1L, "fast" to 50L)),
        )

        assertEquals(listOf("fast", "slow", "untested", "failed"), result)
    }

    @Test
    fun `sort is stable among equal delays and among untested`() {
        val result = ServerOrdering.byTestResult(
            serverList = listOf("x1", "y1", "x2", "y2", "x3"),
            delayOf = delayOf(mapOf("x1" to 100L, "y1" to 100L, "x2" to 0L, "y2" to -5L, "x3" to 0L)),
        )

        assertEquals(listOf("x1", "y1", "x2", "y2", "x3"), result)
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<String>(), ServerOrdering.byTestResult(emptyList()) { 0L })
    }

    @Test
    fun `missing affiliation data counts as untested`() {
        val result = ServerOrdering.byTestResult(
            serverList = listOf("unknown", "known"),
            delayOf = delayOf(mapOf("known" to 42L)),
        )

        assertEquals(listOf("known", "unknown"), result)
    }
}
