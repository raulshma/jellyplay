package com.raulshma.jellyplay.core.ui.components

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class DurationFormatterTest {

    private val originalLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun pinUsLocale() {
        // The hour branch routes through the "%.1f"-shaped formatOneDecimal
        // seam (pinned directly below, too), whose JVM actual is
        // default-locale-sensitive.
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

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
    fun `formatRuntimeLabelFromTicks with null ticks`() {
        assertNull(formatRuntimeLabelFromTicks(null))
    }

    @Test
    fun `formatRuntimeLabelFromTicks hides sub-minute runtimes`() {
        // Books and other non-playback items report a null, zero or tiny
        // RunTimeTicks; all three must render as "no runtime", never "0m".
        assertNull(formatRuntimeLabelFromTicks(0L))
        assertNull(formatRuntimeLabelFromTicks(599_999_999L))
    }

    @Test
    fun `formatRuntimeLabelFromTicks formats valid runtimes`() {
        assertEquals("1m", formatRuntimeLabelFromTicks(600_000_000L))
        assertEquals("1h 35m", formatRuntimeLabelFromTicks(95 * 60 * 10_000_000L))
        assertEquals("2h 30m", formatRuntimeLabelFromTicks((2 * 3600 + 30 * 60) * 10_000_000L))
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
    fun `formatDurationMs paddedMinutes zero-pads the under-hour minutes`() {
        // Trickplay's deliberate padding (the plain variant renders "5:07"):
        // the overlay label must not jitter between the 9- and 10-minute
        // marks while scrubbing.
        assertEquals("05:07", formatDurationMs((5 * 60 + 7) * 1000L, paddedMinutes = true))
        assertEquals("09:59", formatDurationMs((9 * 60 + 59) * 1000L, paddedMinutes = true))
        assertEquals("10:00", formatDurationMs(10 * 60 * 1000L, paddedMinutes = true))
        assertEquals("00:45", formatDurationMs(45_000, paddedMinutes = true))
        assertEquals("00:00", formatDurationMs(0, paddedMinutes = true))
    }

    @Test
    fun `formatDurationMs paddedMinutes leaves the hours branch unpadded`() {
        // Only the under-hour minutes pad; hours render bare, exactly the
        // shape the trickplay overlay shipped before the fold.
        assertEquals("1:02:03", formatDurationMs((3600 + 2 * 60 + 3) * 1000L, paddedMinutes = true))
        assertEquals("2:00:00", formatDurationMs(2 * 3600 * 1000L, paddedMinutes = true))
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
        // Pins the `%.1f` JVM contract: the hour branch is the only
        // String.format-dependent path.
        assertEquals("1.0h", formatDurationApproxSeconds(3_660))   // 1.01666 -> 1.0
        // 8100 s = 2.25 h, a binary-exact true tie: HALF_UP prints 2.3 where
        // HALF_EVEN would print 2.2 — this line actually discriminates modes.
        assertEquals("2.3h", formatDurationApproxSeconds(8_100))
        assertEquals("5.5h", formatDurationApproxSeconds(19_800))  // ordinary .5 bucket coverage
        assertEquals("25.8h", formatDurationApproxSeconds(92_880)) // 25.8
        assertEquals("59m", formatDurationApproxSeconds(3_599))    // minute branch, no decimal
        assertEquals("45s", formatDurationApproxSeconds(45))       // seconds branch
        assertEquals("0s", formatDurationApproxSeconds(0))
    }

    // ── the formatOneDecimal seam, directly ──────────────────────────────
    // Migrated here when the expect left PlatformTime.kt for this file's
    // subject: DateLabelsJvmTest and PlatformTimeJvmTest both pinned it
    // through the old homes; the assertions below are their union.

    /** `%.1f` renders with the host-default decimal separator — normalize it. */
    private fun String.normalized() = replace(',', '.')

    @Test
    fun `formatOneDecimal keeps the percent-one-f contract`() {
        assertEquals("4.0", formatOneDecimal(4.0).normalized())
        assertEquals("12.3", formatOneDecimal(12.34).normalized())
    }

    @Test
    fun `formatOneDecimal rounds half-up at the first decimal`() {
        assertEquals("1.0", formatOneDecimal(1.04).normalized())
        assertEquals("1.0", formatOneDecimal(0.96).normalized())
        assertEquals("1.0", formatOneDecimal(1.0).normalized())
        // HALF_UP: 0.25 rounds away from the 0.24999... boundary.
        assertEquals("0.3", formatOneDecimal(0.25).normalized())
        assertEquals("1.3", formatOneDecimal(1.25).normalized())
        assertEquals("2.0", formatOneDecimal(1.96).normalized())
    }

    @Test
    fun `formatOneDecimal renders stray negative signs symmetrically`() {
        // "%.1f" of a small negative is "-0.0" — pinned as the documented
        // ("sign rendered symmetrically") contract, not silently fixed.
        assertEquals("-0.0", formatOneDecimal(-0.04).normalized())
        assertEquals("-1.5", formatOneDecimal(-1.54).normalized())
        assertEquals("-1.3", formatOneDecimal(-1.25).normalized())
    }
}
