package com.raulshma.jellyplay.feature.calendar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * wasmJs actual: fixed-English formatting of the two calendar labels. See
 * [CalendarDateLabels] for the seam; this is the same documented locale
 * degrade as requests' RequestTime.wasmJs English month abbreviations: the
 * web serves ENGLISH headers regardless of the browser locale (no ICU/CLDR
 * data table ships in a wasm bundle — the host-locale DateTimeFormatter the
 * JVM actual uses has no multiplatform equivalent in kotlinx-datetime).
 *
 * Pattern equivalents (java.time "EEE, MMM d" / "MMMM yyyy" at en-US):
 *  - "EEE" → en-US short day-of-week "Mon".."Sun" (kotlinx DayOfWeek runs
 *    MONDAY-first, the same order as the array below).
 *  - "d" → day-of-month, no zero padding (kotlinx dayOfMonth is 1..31).
 *  - "MMMM" → en-US full month name ("MMMM yyyy" → "July 2026").
 */
internal actual fun calendarDayHeaderLabel(date: LocalDate): String =
    "${ENGLISH_DAY_ABBREVS[date.dayOfWeek.ordinal]}, ${ENGLISH_MONTH_ABBREVS[date.monthNumber - 1]} ${date.dayOfMonth}"

internal actual fun calendarMonthYearLabel(month: YearMonth): String =
    "${ENGLISH_MONTHS.getValue(month.month)} ${month.year}"

/** Indexed by [DayOfWeek.ordinal] (MONDAY = 0 .. SUNDAY = 6), en-US "EEE". */
private val ENGLISH_DAY_ABBREVS =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** en-US "MMM" abbreviations, January-first. */
private val ENGLISH_MONTH_ABBREVS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Keyed by the [Month] enum entry, en-US "MMMM" full names. */
private val ENGLISH_MONTHS =
    mapOf(
        Month.JANUARY to "January",
        Month.FEBRUARY to "February",
        Month.MARCH to "March",
        Month.APRIL to "April",
        Month.MAY to "May",
        Month.JUNE to "June",
        Month.JULY to "July",
        Month.AUGUST to "August",
        Month.SEPTEMBER to "September",
        Month.OCTOBER to "October",
        Month.NOVEMBER to "November",
        Month.DECEMBER to "December",
    )
