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

    // ---------- applyUiOrder (A13: stale UI drag snapshot must not drop live entries) ----------

    @Test
    fun `ui order wins for entries present in both lists`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "b", "c", "d"),
            uiOrder = listOf("c", "a", "b", "d"),
        )

        assertEquals(listOf("c", "a", "b", "d"), result)
    }

    @Test
    fun `configs committed after the snapshot are preserved at the end`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "new1", "b", "new2"),
            uiOrder = listOf("b", "a"),
        )

        assertEquals(listOf("b", "a", "new1", "new2"), result)
    }

    @Test
    fun `configs removed after the snapshot are dropped`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "b"),
            uiOrder = listOf("a", "b", "deleted"),
        )

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `empty ui order leaves the index untouched`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "b"),
            uiOrder = emptyList(),
        )

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `duplicate guids in the ui order collapse to their first occurrence`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "b", "c"),
            uiOrder = listOf("b", "b", "a", "b"),
        )

        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `foreign guids never enter the index`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("a", "b"),
            uiOrder = listOf("foreign", "b", "a"),
        )

        assertEquals(listOf("b", "a"), result)
    }

    @Test
    fun `appended survivors keep their storage relative order`() {
        val result = ServerOrdering.applyUiOrder(
            currentIndex = listOf("x", "a", "y", "b", "z"),
            uiOrder = listOf("b", "a"),
        )

        assertEquals(listOf("b", "a", "x", "y", "z"), result)
    }
}
