package com.raulshma.jellyplay.feature.calendar

import com.raulshma.jellyplay.core.ui.components.monthYear
import com.raulshma.jellyplay.core.ui.components.weekdayShortMonthDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Thin façade over the core/ui date-label seam — the module-internal names
 * the calendar's call sites use, kept so churn stays at the seams' edges.
 * The formatting bodies (java.time DateTimeFormatter on android/desktop)
 * and the month/day tables live ONLY in
 * core:ui's DateLabels.
 */
internal fun calendarDayHeaderLabel(date: LocalDate): String = weekdayShortMonthDay(date)

/** Full month + year label, e.g. "July 2026" — see [calendarDayHeaderLabel]. */
internal fun calendarMonthYearLabel(month: YearMonth): String =
    // Month is an enum, JANUARY-first — ordinal + 1 is its 1-based number
    // (kotlinx keeps `number` internal in this version line).
    monthYear(year = month.year, monthNumber = month.month.ordinal + 1)
