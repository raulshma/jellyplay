package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private const val TICKS_PER_SECOND = 10_000_000L
private const val TICKS_PER_MINUTE = TICKS_PER_SECOND * 60
private const val TICKS_PER_HOUR = TICKS_PER_MINUTE * 60

fun formatDurationFromTicks(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return if (hours > 0) {
        "$hours${'h'} $minutes${'m'}"
    } else {
        "$minutes${'m'}"
    }
}

fun formatRemainingTimeFromTicks(runTimeTicks: Long, playbackPositionTicks: Long): String? {
    if (runTimeTicks <= 0) return null
    
    val remainingTicks = runTimeTicks - playbackPositionTicks
    if (remainingTicks <= 0) return null
    
    return formatDurationFromTicks(remainingTicks)
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    // String templates avoid the Formatter + StringBuilder + boxed-varargs
    // allocation that String.format makes per call — this runs per-frame
    // during trickplay scrubbing.
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun formatDurationFromMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

fun formatDurationApproxSeconds(seconds: Long): String = when {
    seconds >= 3600 -> String.format("%.1fh", seconds / 3600.0)
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

@Composable
fun rememberWallClockTimeString(): String {
    val is24Hour = rememberIs24HourFormat()
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var time by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        val initialDelay = 60_000 - (System.currentTimeMillis() % 60_000)
        kotlinx.coroutines.delay(initialDelay)
        time = formatter.format(Date())
        while (true) {
            kotlinx.coroutines.delay(60_000)
            time = formatter.format(Date())
        }
    }
    return time
}

/**
 * Formats an ISO-8601 timestamp (with or without offset, e.g.
 * `2026-07-20T12:34:56Z`, `2026-07-20T12:34:56+01:00`, or the bare
 * `2026-07-20T12:34:56` produced by the Jellyfin SDK's mappers) into a
 * relative-time string like "2d ago", "3h ago", "5m ago", or "just now".
 *
 * Returns `null` when [isoTimestamp] is blank or cannot be parsed, so the
 * caller can fall back to a placeholder rather than showing a malformed date.
 *
 * The bare-local form is interpreted in the system zone — the same zone the
 * SDK used to produce it (see JellyfinDtoMappers).
 */
fun formatRelativeTime(isoTimestamp: String?): String? {
    if (isoTimestamp.isNullOrBlank()) return null
    val epochMillis = parseIsoToEpochMillis(isoTimestamp) ?: return null
    val deltaMillis = System.currentTimeMillis() - epochMillis
    if (deltaMillis < 60_000L) return "just now"
    val seconds = deltaMillis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> "${days}d ago"
        hours >= 1 -> "${hours}h ago"
        minutes >= 1 -> "${minutes}m ago"
        else -> "just now"
    }
}

/** Parses the three ISO forms the offline/playback code produces into epoch-millis. */
private fun parseIsoToEpochMillis(value: String): Long? {
    // Offset-aware first (OfflineRepositoryImpl stamps OffsetDateTime.now().toString()).
    runCatching {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
    // Fall back to bare LocalDateTime (Jellyfin SDK mapper form) in system zone.
    return runCatching {
        LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

/**
 * Whether the platform's time convention is 24-hour. Android reads the system
 * setting; desktop derives it from the locale's short time pattern.
 */
@Composable
internal expect fun rememberIs24HourFormat(): Boolean
