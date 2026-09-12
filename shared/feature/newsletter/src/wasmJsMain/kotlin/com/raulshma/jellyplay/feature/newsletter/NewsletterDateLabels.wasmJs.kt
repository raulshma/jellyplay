package com.raulshma.jellyplay.feature.newsletter

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The wasmJs actuals for the newsletter date-label seam. The browser has no
 * java.time and kotlinx-datetime ships no pattern formatter, so the labels are
 * hand-rolled fixed-English — the documented locale degrade of the calendar
 * seam (the JVM actuals keep Locale.getDefault() output; the web shell is
 * English-only today, so the visible behavior matches).
 */
private val FULL_MONTHS =
    listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

private val SHORT_MONTHS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal actual fun newsletterHeaderDateLabel(date: LocalDate): String =
    "${FULL_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"

internal actual fun newsletterRelativeDateLabel(dateStr: String): String {
    return try {
        val instant = Instant.parse(dateStr)
        val zone = TimeZone.currentSystemDefault()
        val entryDate = instant.toLocalDateTime(zone).date
        val today = Clock.System.todayIn(zone)
        when {
            entryDate == today -> "Today"
            entryDate == today.minus(DatePeriod(days = 1)) -> "Yesterday"
            else -> "${SHORT_MONTHS[entryDate.monthNumber - 1]} ${entryDate.dayOfMonth}"
        }
    } catch (_: IllegalArgumentException) {
        dateStr
    }
}

internal actual fun formatOneDecimal(value: Double): String {
    // HALF_UP at the first decimal through integer math ("%.1f" replacement,
    // core:ui PlatformTime shape), sign applied symmetrically.
    val magnitude = kotlin.math.round(kotlin.math.abs(value) * 10).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}
