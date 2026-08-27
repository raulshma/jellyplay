package com.raulshma.jellyplay.feature.requests

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * JVM/android actuals: the verbatim pre-15B java.time bodies of
 * [requestAgeMinutes] / [formatRequestedDate] (moved out of RequestListItem.kt
 * and RequestDetailBottomSheet.kt unchanged), so android + desktop behavior
 * is byte-identical — pinned by jvmTest.
 */
internal actual fun requestAgeMinutes(dateStr: String): Long? = try {
    val date = OffsetDateTime.parse(dateStr)
    val now = OffsetDateTime.now()
    Duration.between(date, now).toMinutes()
} catch (_: Exception) {
    null
}

private val requestedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy")

internal actual fun formatRequestedDate(dateStr: String): String? = try {
    LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        .format(requestedDateFormatter)
} catch (_: Exception) {
    null
}
