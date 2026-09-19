package com.raulshma.jellyplay.feature.livetv

import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDateTime

/**
 * wasm actuals for the Live-TV wall-clock/date-label renderers: fixed-English
 * component derivation — kotlinx-datetime ships no CLDR data on wasm, so
 * localized names are unavailable there (the newsletter's web actual took the
 * same degradation). The JVM actual keeps localized output via java.time.
 */

/** "Mon"-style day abbreviations behind [formatDateLabel] (java 'EEE', US). */
private val DAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Jan"-style month abbreviations behind [formatDateLabel] (java 'MMM', US). */
private val MONTH_ABBREVIATIONS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

internal actual fun formatWallClockTime(local: LocalDateTime): String {
    val hour12 = ((local.hour + 11) % 12) + 1
    val marker = if (local.hour < 12) "AM" else "PM"
    return "$hour12:${local.minute.toString().padStart(2, '0')} $marker"
}

internal actual fun formatDateLabel(local: LocalDateTime): String =
    "${DAY_ABBREVIATIONS[local.dayOfWeek.isoDayNumber - 1]}, " +
        "${MONTH_ABBREVIATIONS[local.monthNumber - 1]} ${local.dayOfMonth}"
