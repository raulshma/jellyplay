package com.raulshma.jellyplay.core.ui.components

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The ONE date-label seam: the fixed-shape human date labels every UI family
 * renders, promoted here from the per-feature expect/actual twins this file
 * replaces (calendar's CalendarDateLabels, requests' RequestTime formats,
 * newsletter's NewsletterDateLabels, editor's one-decimal). The features keep
 * their module-internal function names as thin delegating façades — the logic
 * bodies and hand-rolled month tables live here only.
 *
 * LOCALE BEHAVIOR (stated once, here — the feature façades point at
 * this KDoc instead of re-documenting it): android/desktop render through
 * java.time DateTimeFormatter with `Locale.getDefault()` (host locale),
 * pinned by the Locale-pinned JVM shape tests.
 *
 * Declared JVM pairing of every shape (jvmShared actual ← verbatim
 * java.time bodies the features shipped):
 *  - [shortMonthDay]        "MMM d"
 *  - [shortMonthDayYear]    "MMM d, yyyy"
 *  - [longMonthDayYear]     "MMMM d, yyyy"
 *  - [monthYear]            "MMMM yyyy"
 *  - [weekdayShortMonthDay] "EEE, MMM d"
 *  - [oneDecimal]           the "%.1f" contract (see [formatOneDecimal])
 */

/** Short month + day, e.g. "Jul 13" (digest entries). */
expect fun shortMonthDay(date: LocalDate): String

/** Abbreviated month, day and year, e.g. "Jan 5, 2024" (requests' requested-date row). */
expect fun shortMonthDayYear(date: LocalDate): String

/** Full month, day and year, e.g. "January 5, 2026" (newsletter header line). */
expect fun longMonthDayYear(date: LocalDate): String

/** Full month + year, e.g. "July 2026" (calendar month-nav header). */
expect fun monthYear(year: Int, monthNumber: Int): String

/** Short day-of-week + short month + day, e.g. "Mon, Jul 13" (calendar day headers). */
expect fun weekdayShortMonthDay(date: LocalDate): String

/**
 * One-decimal fixed notation for ratings/counts — the public route to the
 * internal [formatOneDecimal] machinery ("%.1f" contract: HALF_UP rounding at
 * the first decimal; separator follows the platform's %.1f behavior — see its
 * KDoc). Formerly duplicated by the newsletter and editor seams.
 */
fun oneDecimal(value: Double): String = formatOneDecimal(value)

/**
 * The Today/Yesterday ladder, pure and clock-free: the exact "Today" /
 * "Yesterday" fixed-English labels (identical strings on every platform —
 * they were never localized) and the short-date shape for everything else.
 * `today` is passed in rather than read from the clock so callers and tests
 * stay deterministic.
 */
fun relativeDayLabel(entryDate: LocalDate, today: LocalDate): String = when {
    entryDate == today -> "Today"
    entryDate == today.minus(DatePeriod(days = 1)) -> "Yesterday"
    else -> shortMonthDay(entryDate)
}

/**
 * The digest-entry relative label for an ISO-8601 timestamp:
 * [relativeDayLabel] resolved in the system zone (Today / Yesterday / the
 * [shortMonthDay] shape), or [stamp] itself when it does not parse — the
 * exact contract the newsletter's private `formatRelativeDate` pinned.
 * Both platforms parse through kotlinx-datetime (which delegates to
 * java.time on the JVM), so this needs no actual.
 */
fun relativeInstantDateLabel(stamp: String): String = try {
    val zone = TimeZone.currentSystemDefault()
    relativeDayLabel(
        entryDate = Instant.parse(stamp).toLocalDateTime(zone).date,
        today = Clock.System.todayIn(zone),
    )
} catch (_: IllegalArgumentException) {
    stamp
}

/**
 * Whole minutes between an ISO-8601 OFFSET timestamp and now — negative when
 * the stamp is in the future, null when it does not parse. Strict-offset by
 * contract: a stamp with no zone offset returns null (the JVM's
 * `OffsetDateTime.parse` throws on offset-less stamps), which is what the
 * requests' relative-time buckets rely on.
 *
 * This is the parse transport behind the "relative time ago" family (whose
 * label templates are localized resource strings, not platform seams).
 */
expect fun isoOffsetMinutesAgo(stamp: String): Long?

/**
 * The civil date of an ISO-8601 LOCAL date-time stamp — offset and trailing
 * bracket-zone suffixes are accepted and DISCARDED (exactly what the JVM's
 * `LocalDateTime.parse(ISO_DATE_TIME)` kept: the local fields); null when the
 * stamp is malformed or names an impossible date. The parse transport behind
 * [shortMonthDayYear] for server-stamped strings.
 */
expect fun localDateFromIsoTimestamp(stamp: String): LocalDate?
