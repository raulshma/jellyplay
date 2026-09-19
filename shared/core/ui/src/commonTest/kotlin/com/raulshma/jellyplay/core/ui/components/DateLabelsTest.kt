package com.raulshma.jellyplay.core.ui.components

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Platform-neutral pins for the DateLabels seam (this commonTest suite runs
 * on the jvmTest lane of every target that executes tests). Only assertions
 * that hold byte-for-byte on BOTH actuals live here: the Today/Yesterday
 * ladder (fixed-English everywhere), the strict-offset/no-parse null
 * contracts of the ISO parses, and parse-failure passthrough. The
 * locale-sensitive month/day SHAPES are pinned per-platform: jvmTest pins
 * the JVM host-locale output under a pinned locale, and the jvmTest
 * source-scan contract pins the wasmJs fixed-English degrade.
 */
class DateLabelsTest {

    private val today: LocalDate
        get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // ── the Today/Yesterday ladder ────────────────────────────────────────

    @Test
    fun relativeDayLabelLadder() {
        val today = today
        assertEquals("Today", relativeDayLabel(today, today))
        assertEquals("Yesterday", relativeDayLabel(today.minus(DatePeriod(days = 1)), today))
        // Future/older entries fall to the short-date shape — asserted
        // self-consistently (the exact string is locale-sensitive on the JVM).
        assertEquals(shortMonthDay(today.minus(DatePeriod(days = 3))), relativeDayLabel(today.minus(DatePeriod(days = 3)), today))
        assertEquals(shortMonthDay(today.minus(DatePeriod(days = 40))), relativeDayLabel(today.minus(DatePeriod(days = 40)), today))
    }

    @Test
    fun relativeInstantDateLabelResolvesTheSystemZoneTodayAndYesterday() {
        val zone = TimeZone.currentSystemDefault()
        val today = today
        // Midnight and a same-day non-midnight time are both "Today".
        assertEquals("Today", relativeInstantDateLabel(today.atStartOfDayIn(zone).toString()))
        assertEquals("Today", relativeInstantDateLabel(today.atStartOfDayIn(zone).plus(13_977.seconds).toString()))
        assertEquals("Yesterday", relativeInstantDateLabel(today.minus(DatePeriod(days = 1)).atStartOfDayIn(zone).toString()))
    }

    @Test
    fun relativeInstantDateLabelReturnsTheRawStringWhenParsingFails() {
        assertEquals("not-a-date", relativeInstantDateLabel("not-a-date"))
        assertEquals("", relativeInstantDateLabel(""))
    }

    @Test
    fun relativeInstantDateLabelFallsBackToTheShortDateShapeForOlderEntries() {
        val zone = TimeZone.currentSystemDefault()
        val threeDaysAgo = today.minus(DatePeriod(days = 3))
        val label = relativeInstantDateLabel(threeDaysAgo.atStartOfDayIn(zone).toString())
        assertEquals(shortMonthDay(threeDaysAgo), label)
    }

    // ── isoOffsetMinutesAgo: strict-offset + parse contracts ─────────────

    @Test
    fun offsetStampsWithoutAZoneOffsetReturnNull() {
        // Strict-offset on BOTH platforms (the JVM's OffsetDateTime.parse
        // throws; the wasm regex requires the offset) — the relative-time
        // buckets' null contract.
        assertNull(isoOffsetMinutesAgo("2024-01-05T14:30:00"))
        assertNull(isoOffsetMinutesAgo("not a timestamp"))
        assertNull(isoOffsetMinutesAgo(""))
        // Impossible civil date — java.time rejects it; so does the wasm table.
        assertNull(isoOffsetMinutesAgo("2024-02-30T12:00:00Z"))
    }

    @Test
    fun offsetStampsParseToPositiveMinutesAgoAndAreOffsetIndependent() {
        val utc = assertNotNull(isoOffsetMinutesAgo("2024-01-05T12:30:00Z"))
        assertTrue(utc > 0, "a fixed past instant must be a positive age")
        // The same instant in +02:00 — equal up to the one minute the two
        // separate "now" reads can straddle.
        val plus2 = assertNotNull(isoOffsetMinutesAgo("2024-01-05T14:30:00+02:00"))
        assertTrue(kotlin.math.abs(utc - plus2) <= 1)
    }

    // ── localDateFromIsoTimestamp: local-fields + discard contracts ───────

    @Test
    fun localStampParsesToLocalFieldsDiscardingOffsetAndBracketSuffixes() {
        assertEquals(LocalDate(2024, 1, 5), localDateFromIsoTimestamp("2024-01-05T14:30:00"))
        assertEquals(LocalDate(2023, 12, 31), localDateFromIsoTimestamp("2023-12-31T23:59:59.123456789"))
        assertEquals(LocalDate(2024, 1, 5), localDateFromIsoTimestamp("2024-01-05T14:30:00+02:00"))
        assertEquals(LocalDate(2024, 1, 5), localDateFromIsoTimestamp("2024-01-05T14:30:00+02:00[Europe/Paris]"))
        assertEquals(LocalDate(2024, 1, 5), localDateFromIsoTimestamp("2024-01-05T14:30:00Z"))
    }

    @Test
    fun localStampFailuresReturnNull() {
        assertNull(localDateFromIsoTimestamp("not a timestamp"))
        assertNull(localDateFromIsoTimestamp(""))
        assertNull(localDateFromIsoTimestamp("2024-02-30T12:00:00"))
    }
}
