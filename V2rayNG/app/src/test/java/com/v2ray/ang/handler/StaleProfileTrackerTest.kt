package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaleProfileTrackerTest {

    @Test
    fun `failures increment the streak one per test`() {
        val (streaks, stale) = StaleProfileTracker.applyResults(
            streaks = emptyMap(),
            stale = emptySet(),
            results = mapOf("a" to -1L),
            removedGuids = emptySet(),
        )
        assertEquals(mapOf("a" to 1), streaks)
        assertTrue(stale.isEmpty())

        val (streaks2, stale2) = StaleProfileTracker.applyResults(
            streaks = streaks,
            stale = stale,
            results = mapOf("a" to -1L),
            removedGuids = emptySet(),
        )
        assertEquals(mapOf("a" to 2), streaks2)
        assertTrue(stale2.isEmpty())
    }

    @Test
    fun `streak reaches the threshold and marks the guid stale`() {
        var streaks: Map<String, Int> = emptyMap()
        var stale: Set<String> = emptySet()
        repeat(StaleProfileTracker.STALE_FAIL_THRESHOLD) {
            val (s, g) = StaleProfileTracker.applyResults(
                streaks = streaks,
                stale = stale,
                results = mapOf("dead" to -1L),
                removedGuids = emptySet(),
            )
            streaks = s
            stale = g
        }

        assertEquals(StaleProfileTracker.STALE_FAIL_THRESHOLD, streaks["dead"])
        assertEquals(setOf("dead"), stale)
    }

    @Test
    fun `streak is capped at ten`() {
        val (streaks, _) = StaleProfileTracker.applyResults(
            streaks = mapOf("dead" to StaleProfileTracker.STREAK_CAP),
            stale = emptySet(),
            results = mapOf("dead" to -1L),
            removedGuids = emptySet(),
        )

        assertEquals(StaleProfileTracker.STREAK_CAP, streaks["dead"])
    }

    @Test
    fun `success resets the streak and clears the stale mark`() {
        val (streaks, stale) = StaleProfileTracker.applyResults(
            streaks = mapOf("revived" to 7),
            stale = setOf("revived"),
            results = mapOf("revived" to 250L),
            removedGuids = emptySet(),
        )

        assertTrue(streaks.isEmpty())
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `zero delay never touches the streak`() {
        val (streaks, stale) = StaleProfileTracker.applyResults(
            streaks = mapOf("fresh" to 4),
            stale = emptySet(),
            results = mapOf("fresh" to 0L, "other" to 100L),
            removedGuids = emptySet(),
        )

        assertEquals(mapOf("fresh" to 4), streaks)
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `removed guids are pruned from both stores`() {
        val (streaks, stale) = StaleProfileTracker.applyResults(
            streaks = mapOf("gone" to 9, "kept" to 2),
            stale = setOf("gone"),
            results = emptyMap(),
            removedGuids = setOf("gone"),
        )

        assertFalse(streaks.containsKey("gone"))
        assertEquals(mapOf("kept" to 2), streaks)
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `empty input is a no-op`() {
        val streaks = mapOf("a" to 1)
        val stale = setOf("b")

        val (nextStreaks, nextStale) = StaleProfileTracker.applyResults(
            streaks = streaks,
            stale = stale,
            results = emptyMap(),
            removedGuids = emptySet(),
        )

        assertEquals(streaks, nextStreaks)
        assertEquals(stale, nextStale)
    }

    @Test
    fun `blank guids are ignored`() {
        val (streaks, stale) = StaleProfileTracker.applyResults(
            streaks = emptyMap(),
            stale = emptySet(),
            results = mapOf("" to -1L),
            removedGuids = emptySet(),
        )

        assertTrue(streaks.isEmpty())
        assertTrue(stale.isEmpty())
    }
}
