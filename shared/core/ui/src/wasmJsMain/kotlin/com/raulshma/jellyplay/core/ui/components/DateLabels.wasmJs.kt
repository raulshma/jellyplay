package com.raulshma.jellyplay.core.ui.components

import kotlinx.datetime.LocalDate

/**
 * wasmJs actuals for the [DateLabels] seam: the SAME fixed-English degrade
 * the per-feature actuals shipped, consolidated here onto ONE table set (the
 * documented locale degrade lives on the seam's commonMain KDoc — the web
 * serves English headers regardless of browser locale; no ICU/CLDR data table
 * ships in a wasm bundle). Pattern equivalents at en-US, with kotlinx
 * day-of-week running MONDAY-first (the array order below):
 *  - "MMM d" / "MMM d, yyyy" / "MMMM d, yyyy" / "MMMM yyyy" / "EEE, MMM d".
 */

/** en-US "MMM" abbreviations, January-first. */
private val ENGLISH_MONTH_ABBREVS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** en-US "MMMM" full names, January-first. */
private val ENGLISH_MONTHS =
    listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

/** Indexed by [kotlinx.datetime.DayOfWeek.ordinal] (MONDAY = 0 .. SUNDAY = 6), en-US "EEE". */
private val ENGLISH_DAY_ABBREVS =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

actual fun shortMonthDay(date: LocalDate): String =
    "${ENGLISH_MONTH_ABBREVS[date.monthNumber - 1]} ${date.dayOfMonth}"

actual fun shortMonthDayYear(date: LocalDate): String =
    "${ENGLISH_MONTH_ABBREVS[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"

actual fun longMonthDayYear(date: LocalDate): String =
    "${ENGLISH_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"

actual fun monthYear(year: Int, monthNumber: Int): String =
    "${ENGLISH_MONTHS[monthNumber - 1]} $year"

actual fun weekdayShortMonthDay(date: LocalDate): String =
    "${ENGLISH_DAY_ABBREVS[date.dayOfWeek.ordinal]}, ${ENGLISH_MONTH_ABBREVS[date.monthNumber - 1]} ${date.dayOfMonth}"

// ── The strict-regex + integer-math ISO parses (requests' wasm bodies,
//    moved verbatim; java.time has no wasm twin) ────────────────────────────

actual fun isoOffsetMinutesAgo(stamp: String): Long? {
    val trimmed = stamp.trim()
    // Normalize the UTC designator so one offset shape remains (java.time
    // accepts `Z`/`z` and `±HH:MM`; both become `±HH:MM` here).
    val normalized = if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
        trimmed.dropLast(1) + "+00:00"
    } else {
        trimmed
    }
    // ISO-8601 OFFSET date-time (java.time OffsetDateTime.parse shape):
    // extended calendar date, 'T', mandatory minutes, optional seconds +
    // fraction, then a REQUIRED zone offset — a stamp without one throws on
    // the JVM too, so `null` here matches.
    val match = offsetStampRegex.matchEntire(normalized) ?: return null
    val (year, month, day, hour, minute, second, offsetSign, offsetHour, offsetMinute) = match.destructured
    val y = year.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    val h = hour.toIntOrNull() ?: return null
    val min = minute.toIntOrNull() ?: return null
    val sec = second.ifEmpty { "0" }.toIntOrNull() ?: return null
    val offH = offsetHour.toIntOrNull() ?: return null
    val offM = offsetMinute.toIntOrNull() ?: return null
    if (!isValidCivilDate(y, m, d)) return null
    if (h > 23 || min > 59 || sec > 59) return null
    if (offH > 18 || offM > 59) return null // java.time ZoneOffset caps at ±18:00

    // Civil-date → epoch-days (Hinnant's days_from_civil), then the offset
    // correction: `local - offset = UTC`.
    val epochDays = daysFromCivil(y, m, d)
    val localSeconds = epochDays * 86_400L + h * 3_600L + min * 60L + sec
    val offsetSeconds = signOf(offsetSign) * offH * 3_600L + offM * 60L
    val instantSeconds = localSeconds - offsetSeconds
    val diffSeconds = nowEpochMillis() / 1_000L - instantSeconds
    return diffSeconds / 60L
}

actual fun localDateFromIsoTimestamp(stamp: String): LocalDate? {
    // ISO-8601 LOCAL date-time (java.time LocalDateTime.parse + ISO_DATE_TIME
    // shape): the offset and trailing bracket zone are optional and DISCARDED
    // — identical to the JVM read, which kept only the local fields.
    val stripped = bracketZoneRegex.replace(stamp.trim(), "")
    val normalized = if (stripped.endsWith("Z") || stripped.endsWith("z")) {
        stripped.dropLast(1) + "+00:00"
    } else {
        stripped
    }
    val match = localStampRegex.matchEntire(normalized) ?: return null
    val (year, month, day, hour, minute, second) = match.destructured
    val y = year.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    val h = hour.toIntOrNull() ?: return null
    val min = minute.toIntOrNull() ?: return null
    val sec = second.ifEmpty { "0" }.toIntOrNull() ?: return null
    if (!isValidCivilDate(y, m, d)) return null
    if (h > 23 || min > 59 || sec > 59) return null
    return LocalDate(y, m, d)
}

private val offsetStampRegex = Regex(
    """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?([+\-])(\d{2}):(\d{2})"""
)

private val localStampRegex = Regex(
    """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:[+\-]\d{2}:\d{2})?"""
)

private val bracketZoneRegex = Regex("""\[[^\]]*]$""")

/** Real-calendar day validation (java.time rejects e.g. Feb 30; so do we). */
private fun isValidCivilDate(year: Int, month: Int, day: Int): Boolean {
    if (month !in 1..12 || day < 1) return false
    val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    val monthLengths = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    return day <= monthLengths[month - 1]
}

private fun signOf(sign: String): Long = if (sign == "-") -1L else 1L

/** Days since 1970-01-01 for a valid civil date (Hinnant's days_from_civil). */
private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val yy = if (m <= 2) (y - 1).toLong() else y.toLong()
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val mp = (m + 9) % 12
    val doy = (153L * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

/**
 * Current epoch milliseconds (`Date.now()`). Only self-contained `js()`
 * expressions touch the platform — wasm requires each to be the single
 * expression of a top-level function.
 */
private fun jsNowMillis(): Double = js("Date.now()")

private fun nowEpochMillis(): Long = jsNowMillis().toLong()
