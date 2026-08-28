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
 * The formatters are built PER CALL over `Locale.getDefault()` (review
 * round): static `val` formatters would capture the locale at CLASS-LOAD
 * time, so an in-process locale switch (Android per-app locales /
 * `Locale.setDefault` without process death) would keep serving stale-locale
 * labels until restart — a silent drift from the pre-wasm bodies, which
 * resolved the locale on every call.
 */
internal actual fun calendarDayHeaderLabel(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

internal actual fun calendarMonthYearLabel(month: YearMonth): String =
    month.toJavaYearMonth().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
