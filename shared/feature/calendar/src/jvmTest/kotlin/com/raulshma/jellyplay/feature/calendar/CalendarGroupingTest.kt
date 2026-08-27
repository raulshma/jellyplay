package com.raulshma.jellyplay.feature.calendar

import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-function coverage for the calendar grouping helpers (no legacy suite
 * existed — written fresh at the V3 conveyor move): day-bucket ordering +
 * filtering, the ISO-prefix release-date parse, the relative-label boundary
 * window, and the month-membership check.
 *
 * Wave 16A: the helpers run kotlinx.datetime.LocalDate/YearMonth now (wasmJs
 * purification); `plus`/`minus` take a DateTimeUnit since kotlinx 0.8 dropped
 * the plusDays/minusDays sugar. The header-label test pins the JVM default
 * locale for the CalendarDateLabels jvmShared actual (Locale.getDefault()
 * resolution, unchanged from the pre-wasm java.time bodies).
 */
class CalendarGroupingTest {

    private fun item(
        title: String,
        mediaType: ArrMediaType = ArrMediaType.MOVIE,
        airDateUtc: String? = "2026-07-14T09:30:00Z",
        tmdbId: Int? = null,
    ) = ArrCalendarItem(
        tmdbId = tmdbId,
        title = title,
        mediaType = mediaType,
        airDateUtc = airDateUtc,
    )

    // ── groupByDay ─────────────────────────────────────────────────────────

    @Test
    fun groupsByDayAscendingAndSortsWithinDay() {
        val items = listOf(
            item("Zeta Series", ArrMediaType.SERIES, "2026-07-13T00:00:00Z"),
            item("Beta Movie", ArrMediaType.MOVIE, "2026-07-14T00:00:00Z"),
            item("Alpha Movie", ArrMediaType.MOVIE, "2026-07-13T00:00:00Z"),
            item("gamma movie", ArrMediaType.MOVIE, "2026-07-15T00:00:00Z"),
        )

        val days = groupByDay(items, CalendarFilter.ALL)

        assertEquals(listOf(LocalDate(2026, 7, 13), LocalDate(2026, 7, 14), LocalDate(2026, 7, 15)), days.map { it.date })
        // Movies first, then alphabetical ignoring case, within a day.
        assertEquals(listOf("Alpha Movie", "Zeta Series"), days[0].items.map { it.title })
        assertEquals(listOf("Beta Movie"), days[1].items.map { it.title })
        assertEquals(listOf("gamma movie"), days[2].items.map { it.title })
    }

    @Test
    fun filterDropsOtherTypesAndNowEmptyDays() {
        val items = listOf(
            item("Only Movie", ArrMediaType.MOVIE, "2026-07-13T00:00:00Z"),
            item("Series A", ArrMediaType.SERIES, "2026-07-14T00:00:00Z"),
            item("Series B", ArrMediaType.SERIES, "2026-07-14T00:00:00Z"),
        )

        val seriesDays = groupByDay(items, CalendarFilter.SERIES)
        assertEquals(1, seriesDays.size)
        assertEquals(LocalDate(2026, 7, 14), seriesDays.single().date)
        assertEquals(setOf("Series A", "Series B"), seriesDays.single().items.map { it.title }.toSet())

        val movieDays = groupByDay(items, CalendarFilter.MOVIES)
        assertEquals(1, movieDays.size)
        assertEquals("Only Movie", movieDays.single().items.single().title)
    }

    @Test
    fun itemsWithoutParseableAirDateAreDropped() {
        val items = listOf(
            item("Has date", ArrMediaType.MOVIE, "2026-07-14T00:00:00Z"),
            item("No date", ArrMediaType.MOVIE, null),
            item("Bad date", ArrMediaType.MOVIE, "not-a-date!"),
        )

        val days = groupByDay(items, CalendarFilter.ALL)

        assertEquals(1, days.size)
        assertEquals("Has date", days.single().items.single().title)
    }

    // ── releaseDate ────────────────────────────────────────────────────────

    @Test
    fun releaseDateParsesIsoDatePrefixOfFullTimestamp() {
        assertEquals(LocalDate(2026, 7, 14), item("m", airDateUtc = "2026-07-14T23:59:59.999Z").releaseDate())
        assertNull(item("m", airDateUtc = null).releaseDate())
        assertNull(item("m", airDateUtc = "garbage!!").releaseDate())
    }

    // ── toRelativeLabel ────────────────────────────────────────────────────

    @Test
    fun relativeLabelCoversTheHumanWindow() {
        val today = LocalDate(2026, 7, 14)
        assertEquals("Today", today.plus(0, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("Tomorrow", today.plus(1, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("Yesterday", today.minus(1, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("In 2 days", today.plus(2, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("In 6 days", today.plus(6, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("2 days ago", today.minus(2, DateTimeUnit.DAY).toRelativeLabel(today))
        assertEquals("6 days ago", today.minus(6, DateTimeUnit.DAY).toRelativeLabel(today))
        // Outside ±6 the label is null so the header falls back to the
        // absolute date label.
        assertNull(today.plus(7, DateTimeUnit.DAY).toRelativeLabel(today))
        assertNull(today.minus(7, DateTimeUnit.DAY).toRelativeLabel(today))
    }

    // ── isInMonth ──────────────────────────────────────────────────────────

    @Test
    fun isInMonthMatchesYearAndMonthOnly() {
        val july = YearMonth(2026, 7)
        assertTrue(LocalDate(2026, 7, 1).isInMonth(july))
        assertTrue(LocalDate(2026, 7, 31).isInMonth(july))
        assertFalse(LocalDate(2026, 8, 1).isInMonth(july))
        assertFalse(LocalDate(2025, 7, 1).isInMonth(july))
    }

    // ── header labels (locale pinned for determinism) ─────────────────────

    @Test
    fun headerLabelsFormatWithPinnedLocale() {
        // The jvmShared actual formats through the DEFAULT locale (the
        // verbatim pre-wasm behavior) — pin it to English around the
        // assertions for determinism, as the old `Locale.ENGLISH` parameter
        // argument did.
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            // 2026-07-13 is a Monday.
            assertEquals("Mon, Jul 13", LocalDate(2026, 7, 13).toDayHeaderLabel())
            assertEquals("July 2026", YearMonth(2026, 7).toMonthYearLabel())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
