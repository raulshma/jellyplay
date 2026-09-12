package com.raulshma.jellyplay.feature.newsletter

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * The java.time actuals for the newsletter date-label seam — the verbatim
 * bodies the header/digest carried in commonMain before the wasmJs target
 * (android + desktop output is unchanged, locale included; the reflection
 * test in jvmTest still pins the digest branch shape).
 */
private val HEADER_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

// Built once at class-load rather than per digest entry.
private val ACTIVITY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d")

internal actual fun newsletterHeaderDateLabel(date: LocalDate): String =
    date.toJavaLocalDate().format(HEADER_DATE_FORMATTER)

internal actual fun newsletterRelativeDateLabel(dateStr: String): String {
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val entryDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = java.time.LocalDate.now()
        when {
            entryDate == today -> "Today"
            entryDate == today.minusDays(1) -> "Yesterday"
            else -> entryDate.format(ACTIVITY_DATE_FORMATTER)
        }
    } catch (_: Exception) {
        dateStr
    }
}

internal actual fun formatOneDecimal(value: Double): String = "%.1f".format(value)
