package com.raulshma.jellyplay.core.ui.components

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

/**
 * JVM/android actuals for the [DateLabels] seam: the verbatim java.time
 * pattern-formatting bodies the feature seams carried before the promotion —
 * android + desktop output is byte-identical (Locale.getDefault() resolved
 * per call; jvmTest pins the shapes under a pinned locale).
 *
 * Formatters are cached per pattern+locale so repeated renders do not rebuild
 * DateTimeFormatter per item (the calendar seam's bd956 perf note, carried
 * over); locale is resolved per call (not captured at class-load) so an
 * in-process locale switch is reflected immediately.
 */
private val labelFormatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

private fun cachedFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    labelFormatters.computeIfAbsent(pattern to locale) { DateTimeFormatter.ofPattern(pattern, it.second) }

private fun LocalDate.formatWith(pattern: String): String =
    toJavaLocalDate().format(cachedFormatter(pattern, Locale.getDefault()))

actual fun shortMonthDay(date: LocalDate): String = date.formatWith("MMM d")

actual fun shortMonthDayYear(date: LocalDate): String = date.formatWith("MMM d, yyyy")

actual fun longMonthDayYear(date: LocalDate): String = date.formatWith("MMMM d, yyyy")

actual fun monthYear(year: Int, monthNumber: Int): String =
    YearMonth.of(year, monthNumber).format(cachedFormatter("MMMM yyyy", Locale.getDefault()))

actual fun weekdayShortMonthDay(date: LocalDate): String = date.formatWith("EEE, MMM d")

/** The verbatim `OffsetDateTime.parse` + `Duration.between` body requests shipped. */
actual fun isoOffsetMinutesAgo(stamp: String): Long? = try {
    val date = OffsetDateTime.parse(stamp)
    val now = OffsetDateTime.now()
    Duration.between(date, now).toMinutes()
} catch (_: Exception) {
    null
}

/**
 * The verbatim `LocalDateTime.parse(ISO_DATE_TIME)` read requests shipped:
 * offset/bracket suffixes parse and are discarded (ISO_DATE_TIME keeps the
 * local fields); garbage and impossible dates return null like the old
 * catch path.
 */
actual fun localDateFromIsoTimestamp(stamp: String): LocalDate? = try {
    LocalDateTime.parse(stamp, DateTimeFormatter.ISO_DATE_TIME).toLocalDate().toKotlinLocalDate()
} catch (_: Exception) {
    null
}
