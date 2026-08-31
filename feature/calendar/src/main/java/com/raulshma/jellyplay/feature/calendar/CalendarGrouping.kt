package com.raulshma.jellyplay.feature.calendar

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Filter applied client-side to the merged calendar stream. Kept in the UI
 * state (not in the repository query) so toggling it is instant — the data is
 * already loaded for the visible month.
 */
enum class CalendarFilter { ALL, MOVIES, SERIES }

/**
 * A single day bucket produced by [groupByDay]. Days are returned in ascending
 * [LocalDate] order; items within a day are sorted movies-first then by title.
 */
@Immutable
data class CalendarDay(
    val date: LocalDate,
    val items: List<ArrCalendarItem>,
)

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Parses an [ArrCalendarItem]'s effective release date. Mirrors the parse in
 * `ArrRepositoryImpl.matchesWindow` (`airDateUtc` is a full ISO-8601 UTC
 * timestamp; only the leading `yyyy-MM-dd` is meaningful for day grouping).
 */
fun ArrCalendarItem.releaseDate(): LocalDate? =
    airDateUtc?.take(10)?.let { runCatching { LocalDate.parse(it, ISO_DATE) }.getOrNull() }

/**
 * Groups items by their release day, applies [filter], and sorts. Days with no
 * matching items after filtering are omitted so the LazyColumn only renders
 * non-empty sections.
 */
fun groupByDay(
    items: List<ArrCalendarItem>,
    filter: CalendarFilter,
): List<CalendarDay> {
    val filtered = when (filter) {
        CalendarFilter.ALL -> items
        CalendarFilter.MOVIES -> items.filter { it.mediaType == ArrMediaType.MOVIE }
        CalendarFilter.SERIES -> items.filter { it.mediaType == ArrMediaType.SERIES }
    }
    return filtered
        .mapNotNull { item -> item.releaseDate()?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .map { (date, dayItems) -> CalendarDay(date = date, items = dayItems.sortedWith(dayComparator())) }
        .sortedBy { it.date }
}

/** Movies first (released media surfaces higher), then alphabetical by title. */
private fun dayComparator(): Comparator<ArrCalendarItem> = Comparator { a, b ->
    val typeRank = typeRank(a.mediaType).compareTo(typeRank(b.mediaType))
    if (typeRank != 0) typeRank else a.title.compareTo(b.title, ignoreCase = true)
}

private fun typeRank(mediaType: ArrMediaType): Int = when (mediaType) {
    ArrMediaType.MOVIE -> 0
    ArrMediaType.SERIES -> 1
}

/**
 * Relative-to-today label for a day header, or `null` when the day is outside
 * the ±1-day "human" window (callers fall back to an absolute date label).
 * Pure and testable; `today` is passed in rather than read from the clock so
 * tests are deterministic.
 */
fun LocalDate.toRelativeLabel(today: LocalDate): String? {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, this)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days in 2L..6L -> "In $days days"
        days in -6L..-2L -> "${-days} days ago"
        else -> null
    }
}

/** `true` when [date] falls inside the calendar month [month]. */
fun LocalDate.isInMonth(month: YearMonth): Boolean =
    year == month.year && monthValue == month.monthValue

/**
 * Day-of-week + day-of-month label, e.g. "Mon, Jul 14". Locale-aware so the
 * app follows the device locale (matching [java.time] usage elsewhere).
 */
fun LocalDate.toDayHeaderLabel(locale: Locale = Locale.getDefault()): String =
    format(cachedFormatter("EEE, MMM d", locale))

/** Full month + year label, e.g. "July 2026", used by the month nav header. */
fun YearMonth.toMonthYearLabel(locale: Locale = Locale.getDefault()): String =
    format(cachedFormatter("MMMM yyyy", locale))

/** [DateTimeFormatter]s are immutable and thread-safe; cache one per pattern+locale. */
private val labelFormatters = java.util.concurrent.ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

private fun cachedFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    labelFormatters.computeIfAbsent(pattern to locale) { DateTimeFormatter.ofPattern(pattern, it.second) }
