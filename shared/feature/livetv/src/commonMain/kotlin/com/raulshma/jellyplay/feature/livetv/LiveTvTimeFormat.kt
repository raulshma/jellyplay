package com.raulshma.jellyplay.feature.livetv

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Shared "h:mm a" wall-clock formatting for Live-TV start/end times, built
 * once instead of per row per recomposition (the EpgGridLayout top-level-
 * formatter pattern). Both the channel-detail program timeline and the
 * recording schedule render the same ISO-offset timestamps.
 */
internal val LIVE_TV_TIME_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
internal val LIVE_TV_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Lenient start/end-time formatting: offset parse first, then a naive-local
 * fallback (Jellyfin occasionally emits offset-less timestamps), else the raw
 * input so the row still shows something identifiable. Null/blank input maps
 * to null so callers choose their own placeholder ("" for timer rows, "--"
 * for program times).
 */
internal fun formatLiveTvTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso, LIVE_TV_TIME_PARSER).format(LIVE_TV_TIME_FORMATTER)
    }.recoverCatching {
        LocalDateTime.parse(
            iso.replace("Z", "").replace("T", " ").substringBefore('+').trim()
        ).format(LIVE_TV_TIME_FORMATTER)
    }.getOrElse { iso }
}
