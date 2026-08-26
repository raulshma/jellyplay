package com.raulshma.jellyplay.core.ui.components

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class DurationFormatterTest {

    @Test
    fun `formatDurationFromTicks with zero`() {
        assertEquals("0m", formatDurationFromTicks(0))
    }

    @Test
    fun `formatDurationFromTicks with minutes only`() {
        assertEquals("45m", formatDurationFromTicks(45 * 60 * 10_000_000L))
    }

    @Test
    fun `formatDurationFromTicks with hours and minutes`() {
        assertEquals("2h 30m", formatDurationFromTicks((2 * 3600 + 30 * 60) * 10_000_000L))
    }

    @Test
    fun `formatDurationFromTicks with exact hours`() {
        assertEquals("1h 0m", formatDurationFromTicks(3600 * 10_000_000L))
    }

    @Test
    fun `formatDurationFromTicks with single minute`() {
        assertEquals("1m", formatDurationFromTicks(60 * 10_000_000L))
    }

    @Test
    fun `formatDurationFromTicks ignores remaining seconds`() {
        assertEquals("1m", formatDurationFromTicks((90) * 10_000_000L))
    }

    @Test
    fun `formatDurationFromTicks with negative ticks`() {
        val result = formatDurationFromTicks(-1)
        assertEquals("0m", result)
    }

    @Test
    fun `formatRemainingTimeFromTicks with valid remaining`() {
        val runtime = 2 * 3600 * 10_000_000L
        val position = 30 * 60 * 10_000_000L
        assertEquals("1h 30m", formatRemainingTimeFromTicks(runtime, position))
    }

    @Test
    fun `formatRemainingTimeFromTicks returns null when zero runtime`() {
        assertNull(formatRemainingTimeFromTicks(0, 0))
    }

    @Test
    fun `formatRemainingTimeFromTicks returns null when negative runtime`() {
        assertNull(formatRemainingTimeFromTicks(-1, 0))
    }

    @Test
    fun `formatRemainingTimeFromTicks returns null when playback exceeds runtime`() {
        assertNull(formatRemainingTimeFromTicks(100L, 200L))
    }

    @Test
    fun `formatRemainingTimeFromTicks returns null when equal`() {
        assertNull(formatRemainingTimeFromTicks(100L, 100L))
    }

    @Test
    fun `formatDurationMs with zero`() {
        assertEquals("0:00", formatDurationMs(0))
    }

    @Test
    fun `formatDurationMs with seconds only`() {
        assertEquals("0:45", formatDurationMs(45_000))
    }

    @Test
    fun `formatDurationMs with minutes and seconds`() {
        assertEquals("5:30", formatDurationMs((5 * 60 + 30) * 1000L))
    }

    @Test
    fun `formatDurationMs with hours minutes and seconds`() {
        assertEquals("1:02:03", formatDurationMs((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `formatDurationMs with exact hours`() {
        assertEquals("2:00:00", formatDurationMs(2 * 3600 * 1000L))
    }

    @Test
    fun `formatDurationMs with large value`() {
        assertEquals("10:00:00", formatDurationMs(10 * 3600 * 1000L))
    }

    @Test
    fun `formatRelativeTime buckets real ISO stamps`() {
        val now = java.time.OffsetDateTime.now()
        assertNull(formatRelativeTime(null))
        assertNull(formatRelativeTime(""))
        assertEquals("just now", formatRelativeTime(now.minusSeconds(30).toString()))
        assertEquals("5m ago", formatRelativeTime(now.minusMinutes(5).toString()))
        assertEquals("3h ago", formatRelativeTime(now.minusHours(3).toString()))
        assertEquals("2d ago", formatRelativeTime(now.minusDays(2).toString()))
    }

    @Test
    fun `formatDurationApproxSeconds pins the one-decimal hour rounding`() {
        // Pins the `%.1f` JVM contract ahead of the wasmJs commonMain split:
        // the hour branch is the only String.format-dependent path.
        assertEquals("1.0h", formatDurationApproxSeconds(3_660))   // 1.01666 -> 1.0
        assertEquals("5.5h", formatDurationApproxSeconds(19_800))  // exact half -> HALF_UP stays .5
        assertEquals("25.8h", formatDurationApproxSeconds(92_880)) // 25.8
        assertEquals("59m", formatDurationApproxSeconds(3_599))    // minute branch, no decimal
        assertEquals("45s", formatDurationApproxSeconds(45))       // seconds branch
        assertEquals("0s", formatDurationApproxSeconds(0))
    }
}
