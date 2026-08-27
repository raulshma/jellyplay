package com.raulshma.jellyplay.feature.calendar

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaYearMonth

/**
 * JVM/android actuals: the verbatim pre-wasm java.time bodies of
 * [calendarDayHeaderLabel] / [calendarMonthYearLabel] (moved out of
 * CalendarGrouping.kt unchanged, including the Locale.getDefault() resolution),
 * so android + desktop behavior is byte-identical — pinned by
 * CalendarGroupingTest's locale-pinned assertions.
 */
private val dayHeaderFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private val monthYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

internal actual fun calendarDayHeaderLabel(date: LocalDate): String =
    date.toJavaLocalDate().format(dayHeaderFormatter)

internal actual fun calendarMonthYearLabel(month: YearMonth): String =
    month.toJavaYearMonth().format(monthYearFormatter)
