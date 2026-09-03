package com.raulshma.jellyplay.core.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the JVM (android/desktop) actuals of the [PlatformTime] seam — the
 * bodies moved verbatim out of commonMain when the wasmJs target arrived, so
 * these tests freeze the desktop/Android behavior contract:
 *
 *  - `formatOneDecimal` keeps the `%.1f` HALF_UP rounding contract (locale
 *    normalization applied so the pinned digits hold under any host locale);
 *  - `currentYear` is the system-calendar year;
 *  - `hourOfDayAt` resolves the given epoch in the system zone and falls back
 *    to "now" for null / non-positive ticks;
 *  - `isoDateIsAfterToday` is strictly-after and false for unparseable input;
 *  - `parseIsoTimestampToEpochMillis` accepts offset-aware (Z / ±HH:mm) and
 *    bare-local (system zone) forms, null for anything else.
 */
class PlatformTimeJvmTest {

    /** `%.1f` renders with the host-default decimal separator — normalize it. */
    private fun String.normalized() = replace(',', '.')

    @Test
    fun formatOneDecimal_halfUpRoundingAtFirstDecimal() {
        assertEquals("1.0", formatOneDecimal(1.04).normalized())
        assertEquals("1.0", formatOneDecimal(0.96).normalized())
        assertEquals("1.0", formatOneDecimal(1.0).normalized())
        // HALF_UP: 0.25 rounds away from the 0.24999... boundary.
        assertEquals("0.3", formatOneDecimal(0.25).normalized())
        assertEquals("2.0", formatOneDecimal(1.96).normalized())
    }

    @Test
    fun formatOneDecimal_rendersStrayNegativeSignSymmetrically() {
        // "%.1f" of a small negative is "-0.0" — pinned as the documented
        // ("sign rendered symmetrically") contract, not silently fixed.
        assertEquals("-0.0", formatOneDecimal(-0.04).normalized())
        assertEquals("-1.5", formatOneDecimal(-1.54).normalized())
    }

    @Test
    fun currentYear_isTheSystemCalendarYear() {
        val expected = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        assertEquals(expected, currentYear())
    }

    @Test
    fun hourOfDayAt_resolvesEpochInSystemZone() {
        val zone = ZoneId.systemDefault()
        // 2024-01-15T15:30:00Z expressed in the system zone.
        val epoch = LocalDateTime.of(2024, 1, 15, 15, 30)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        val expectedHour = Instant.ofEpochMilli(epoch).atZone(zone).hour

        assertEquals(expectedHour, hourOfDayAt(epoch))
    }

    @Test
    fun hourOfDayAt_nullOrNonPositive_fallsBackToNow() {
        val expectedNow = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        assertEquals(expectedNow, hourOfDayAt(null))
        assertEquals(expectedNow, hourOfDayAt(0L))
        assertEquals(expectedNow, hourOfDayAt(-12345L))
    }

    @Test
    fun isoDateIsAfterToday_isStrictlyAfter() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1).toString()
        val yesterday = today.minusDays(1).toString()

        assertTrue(isoDateIsAfterToday(tomorrow), "tomorrow must be after today")
        assertFalse(isoDateIsAfterToday(today.toString()), "today is not strictly after today")
        assertFalse(isoDateIsAfterToday(yesterday), "yesterday is before today")
    }

    @Test
    fun isoDateIsAfterToday_unparseableInputIsFalse() {
        assertFalse(isoDateIsAfterToday("not-a-date"))
        assertFalse(isoDateIsAfterToday(""))
        assertFalse(isoDateIsAfterToday("2024-13-45"))
    }

    @Test
    fun parseIsoTimestamp_offsetAwareForms_yieldExactEpochMillis() {
        // Z form.
        assertEquals(
            1705314600000L,
            parseIsoTimestampToEpochMillis("2024-01-15T10:30:00Z"),
        )
        // Explicit +HH:mm offset form (OfflineRepositoryImpl's OffsetDateTime.now()).
        assertEquals(
            1705307400000L, // 10:30+02:00 == 08:30Z
            parseIsoTimestampToEpochMillis("2024-01-15T10:30:00+02:00"),
        )
        assertEquals(
            1705334400000L, // 10:30-05:30 == 16:00Z
            parseIsoTimestampToEpochMillis("2024-01-15T10:30:00-05:30"),
        )
    }

    @Test
    fun parseIsoTimestamp_bareLocal_resolvesInSystemZone() {
        val expected = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, parseIsoTimestampToEpochMillis("2024-01-15T10:30:00"))
    }

    @Test
    fun parseIsoTimestamp_nullBlankOrGarbage_isNull() {
        assertNull(parseIsoTimestampToEpochMillis(null))
        assertNull(parseIsoTimestampToEpochMillis(""))
        assertNull(parseIsoTimestampToEpochMillis("   "))
        assertNull(parseIsoTimestampToEpochMillis("yesterday"))
        assertNull(parseIsoTimestampToEpochMillis("2024-01-15")) // date only: not a LocalDateTime
    }
}
