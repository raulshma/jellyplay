package com.raulshma.jellyplay.feature.requests

/**
 * wasmJs actuals: strict-regex + integer-math re-implementations of the two
 * java.time reads. See [RequestTime] KDoc for the equivalence contract and
 * the documented degrades. Parse failures return `null` exactly where the
 * JVM's DateTimeParseException path did.
 */
internal actual fun requestAgeMinutes(dateStr: String): Long? {
    val trimmed = dateStr.trim()
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

internal actual fun formatRequestedDate(dateStr: String): String? {
    // ISO-8601 LOCAL date-time (java.time LocalDateTime.parse + ISO_DATE_TIME
    // shape): the offset and trailing bracket zone are optional and DISCARDED
    // — identical to the JVM read, which kept only the local fields.
    val stripped = bracketZoneRegex.replace(dateStr.trim(), "")
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

    // ofPattern("MMM d, yyyy"): no zero-padding on either field; the month
    // abbreviation is fixed English on web (documented locale degrade).
    return "${ENGLISH_MONTH_ABBREVS[m - 1]} $d, $y"
}

private val offsetStampRegex = Regex(
    """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?([+\-])(\d{2}):(\d{2})"""
)

private val localStampRegex = Regex(
    """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:[+\-]\d{2}:\d{2})?"""
)

private val bracketZoneRegex = Regex("""\[[^\]]*]$""")

private val ENGLISH_MONTH_ABBREVS =
    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

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
 * expression of a top-level function (same pattern as core:network's
 * WasmClock).
 */
private fun jsNowMillis(): Double = js("Date.now()")

private fun nowEpochMillis(): Long = jsNowMillis().toLong()
