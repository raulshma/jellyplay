package com.raulshma.jellyplay.core.notification.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests: the quiet-hours logic in [NewMediaCheckWorker] (now extracted to
 * a pure companion helper) must correctly handle the overnight wraparound case (e.g.
 * 22:00 → 07:00) that previously lived inline and was untested.
 */
class NewMediaCheckWorkerQuietHoursTest {

    @Test
    fun `returns false when quiet hours are disabled`() {
        val result = NewMediaCheckWorker.isInQuietHours(
            currentMinutes = 22 * 60,
            quietHoursEnabled = false,
            start = 22 * 60,
            end = 7 * 60,
        )
        assertFalse(result)
    }

    @Test
    fun `overnight window - returns true at midnight`() {
        // 22:00 → 07:00; midnight (00:00) is inside.
        assertTrue(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 0,
                quietHoursEnabled = true,
                start = 22 * 60,
                end = 7 * 60,
            )
        )
    }

    @Test
    fun `overnight window - returns true at start boundary`() {
        // 22:00 should be inside the window (inclusive at start).
        assertTrue(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 22 * 60,
                quietHoursEnabled = true,
                start = 22 * 60,
                end = 7 * 60,
            )
        )
    }

    @Test
    fun `overnight window - returns false just before start`() {
        // 21:59 should be outside the window.
        assertFalse(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 21 * 60 + 59,
                quietHoursEnabled = true,
                start = 22 * 60,
                end = 7 * 60,
            )
        )
    }

    @Test
    fun `overnight window - returns false at end boundary`() {
        // 07:00 is the exclusive end of the window.
        assertFalse(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 7 * 60,
                quietHoursEnabled = true,
                start = 22 * 60,
                end = 7 * 60,
            )
        )
    }

    @Test
    fun `overnight window - returns false at noon`() {
        assertFalse(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 12 * 60,
                quietHoursEnabled = true,
                start = 22 * 60,
                end = 7 * 60,
            )
        )
    }

    @Test
    fun `same-day window - returns true in the middle`() {
        // 13:00 → 14:00; 13:30 is inside.
        assertTrue(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 13 * 60 + 30,
                quietHoursEnabled = true,
                start = 13 * 60,
                end = 14 * 60,
            )
        )
    }

    @Test
    fun `same-day window - returns false outside`() {
        // 13:00 → 14:00; 15:00 is outside.
        assertFalse(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 15 * 60,
                quietHoursEnabled = true,
                start = 13 * 60,
                end = 14 * 60,
            )
        )
    }

    @Test
    fun `same-day window - returns false at start boundary`() {
        // start is inclusive on overnight path but exclusive on same-day path
        // (Kotlin's `until` is end-exclusive, start-inclusive).
        assertTrue(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 13 * 60,
                quietHoursEnabled = true,
                start = 13 * 60,
                end = 14 * 60,
            )
        )
    }

    @Test
    fun `same-day window - returns false at end boundary`() {
        assertFalse(
            NewMediaCheckWorker.isInQuietHours(
                currentMinutes = 14 * 60,
                quietHoursEnabled = true,
                start = 13 * 60,
                end = 14 * 60,
            )
        )
    }
}
