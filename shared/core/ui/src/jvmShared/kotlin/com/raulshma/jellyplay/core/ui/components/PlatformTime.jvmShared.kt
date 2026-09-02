package com.raulshma.jellyplay.core.ui.components

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/**
 * JVM actuals for the [PlatformTime] seam. Bodies are the original call-site
 * code, moved verbatim out of commonMain when the wasmJs target arrived —
 * Android/desktop outputs are unchanged by construction and stay pinned by
 * the jvmTest suites (FormatFileSize/DurationFormatter/YearRangePresets).
 */
internal actual fun formatOneDecimal(value: Double): String = "%.1f".format(value)

internal actual fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

internal actual fun hourOfDayAt(epochMillis: Long?): Int {
    val calendar = Calendar.getInstance()
    if (epochMillis != null && epochMillis > 0) calendar.timeInMillis = epochMillis
    return calendar.get(Calendar.HOUR_OF_DAY)
}

internal actual fun isoDateIsAfterToday(dateStr: String): Boolean {
    return try {
        val now = LocalDate.now()
        val releaseDate = LocalDate.parse(dateStr)
        releaseDate.isAfter(now)
    } catch (_: Exception) {
        false
    }
}

/** Parses the three ISO forms the offline/playback code produces into epoch-millis. */
internal actual fun parseIsoTimestampToEpochMillis(value: String?): Long? {
    value ?: return null
    // Offset-aware first (OfflineRepositoryImpl stamps OffsetDateTime.now().toString()).
    runCatching {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
    // Fall back to bare LocalDateTime (Jellyfin SDK mapper form) in system zone.
    return runCatching {
        LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
