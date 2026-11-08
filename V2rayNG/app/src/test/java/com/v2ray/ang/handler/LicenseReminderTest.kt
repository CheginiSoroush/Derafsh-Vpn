package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the background reminder's re-validation decision (A31 fix).
 *
 * The old gate also required `state.active`, which is false exactly when the 72h
 * grace window has lapsed — so the one state that needed background re-validation
 * the most was the one it skipped forever, leaving premium locked (and the built-in
 * subscription pointing at the locked premium endpoint) until a manual license-screen
 * refresh. The decision must depend only on the age of the last server confirmation.
 */
class LicenseReminderTest {

    private val hour = 60L * 60L * 1000L

    @Test
    fun `fresh confirmation inside the 24h cadence does not revalidate`() {
        val now = 1_000_000_000_000L
        assertFalse(LicenseReminder.shouldRevalidateInBackground(lastOk = now, now = now))
        assertFalse(LicenseReminder.shouldRevalidateInBackground(lastOk = now - 23 * hour, now = now))
    }

    @Test
    fun `confirmation older than the cadence revalidates while still in grace`() {
        val now = 1_000_000_000_000L
        // 25h old: cadence exceeded, still inside the 72h grace window.
        assertTrue(LicenseReminder.shouldRevalidateInBackground(lastOk = now - 25 * hour, now = now))
    }

    @Test
    fun `lapsed grace window still revalidates - the A31 regression pin`() {
        val now = 1_000_000_000_000L
        // 100h old: grace (72h) has lapsed, so current() reports active=false.
        // This is exactly the state the old `state.active` gate skipped forever.
        assertTrue(LicenseReminder.shouldRevalidateInBackground(lastOk = now - 100 * hour, now = now))
    }

    @Test
    fun `never-confirmed license does not trigger a network call`() {
        val now = 1_000_000_000_000L
        assertFalse(LicenseReminder.shouldRevalidateInBackground(lastOk = 0L, now = now))
        assertFalse(LicenseReminder.shouldRevalidateInBackground(lastOk = -1L, now = now))
    }

    @Test
    fun `boundary - confirmation exactly the cadence age revalidates`() {
        val now = 1_000_000_000_000L
        assertTrue(
            LicenseReminder.shouldRevalidateInBackground(
                lastOk = now - AppConfig.LICENSE_REFRESH_INTERVAL_MS,
                now = now,
            )
        )
    }
}
