package com.raulshma.jellyplay.feature.calendar

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaYearMonth

/**
 * JVM/android actuals: the verbatim pre-wasm java.time bodies of
 * [calendarDayHeaderLabel] / [calendarMonthYearLabel] — android + desktop
 * behavior is byte-identical, pinned by CalendarGroupingTest's locale-pinned
 * assertions.
 *
 * Formatters are cached per pattern+locale via [cachedFormatter] so repeated
 * calendar renders do not rebuild DateTimeFormatter on each item (bd956 perf).
 * Locale is resolved per call (not captured at class-load) so an in-process
 * locale switch (Android per-app locales / `Locale.setDefault` without process
 * death) is reflected immediately, matching the pre-wasm bodies.
 */
private val labelFormatters = java.util.concurrent.ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

private fun cachedFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    labelFormatters.computeIfAbsent(pattern to locale) { DateTimeFormatter.ofPattern(pattern, it.second) }

internal actual fun calendarDayHeaderLabel(date: LocalDate): String =
    date.toJavaLocalDate().format(cachedFormatter("EEE, MMM d", Locale.getDefault()))

internal actual fun calendarMonthYearLabel(month: YearMonth): String =
    month.toJavaYearMonth().format(cachedFormatter("MMMM yyyy", Locale.getDefault()))
