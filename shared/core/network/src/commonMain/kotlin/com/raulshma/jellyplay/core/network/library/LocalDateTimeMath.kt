package com.raulshma.jellyplay.core.network.library

/**
 * Pure UTC-millis → local ISO-8601 offset-date-time formatting for the wasm
 * NextUp `nextUpDateCutoff` parameter. The JVM path computes
 * `LocalDateTime.now().minusDays(n)` and lets the SDK serialize it as an
 * `ISO_OFFSET_DATE_TIME` string in the system zone; wasm has no java.time, so
 * the civil-date math (Howard Hinnant's `civil_from_days`) lives here pure
 * and the platform file supplies only "now" and the zone offset.
 */
fun isoLocalFromUtcMillis(utcMillis: Long, offsetBehindMinutes: Int): String {
    // Local civil time = UTC shifted forward by (−offsetBehind): JS's
    // getTimezoneOffset() reports minutes UTC is AHEAD of local (west
    // positive), so local = utc − offsetBehind.
    val localMillis = utcMillis - offsetBehindMinutes * 60_000L
    val days = localMillis.floorDiv(86_400_000L)
    val secsOfDay = (localMillis.mod(86_400_000L) / 1_000L).toInt()
    val (year, month, day) = civilFromDays(days)
    val hour = secsOfDay / 3_600
    val minute = (secsOfDay % 3_600) / 60
    val second = secsOfDay % 60

    val sign = if (offsetBehindMinutes <= 0) '+' else '-'
    val absOffset = kotlin.math.abs(offsetBehindMinutes)
    return buildString(25) {
        append4(year); append('-'); append2(month); append('-'); append2(day)
        append('T'); append2(hour); append(':'); append2(minute); append(':'); append2(second)
        append(sign); append2(absOffset / 60); append(':'); append2(absOffset % 60)
    }
}

/** Civil (year, month [1-12], day [1-31]) from days since 1970-01-01. */
private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val z = daysSinceEpoch + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097 // [0, 146096]
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365 // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
    val mp = (5 * doy + 2) / 153 // [0, 11]
    val d = doy - (153 * mp + 2) / 5 + 1 // [1, 31]
    val m = mp + if (mp < 10) 3 else -9 // [1, 12]
    val year = (y + if (m <= 2) 1 else 0).toInt()
    return Triple(year, m.toInt(), d.toInt())
}

private fun StringBuilder.append2(v: Int) {
    if (v < 10) append('0')
    append(v)
}

private fun StringBuilder.append4(v: Int) {
    if (v < 0) {
        append('-')
        append3(-v)
    } else {
        append2(v / 100)
        append2(v % 100)
    }
}

private fun StringBuilder.append3(v: Int) {
    if (v < 10) append("00") else if (v < 100) append('0')
    append(v)
}
