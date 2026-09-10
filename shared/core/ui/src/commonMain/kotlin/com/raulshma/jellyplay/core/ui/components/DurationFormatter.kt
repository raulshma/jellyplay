package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.wallNowMillis

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

/**
 * The no-hours clock variant of [formatDurationMs]: durations past an hour
 * roll into plain minutes (`61:01`, the Now Playing widget's convention)
 * instead of switching to `h:mm:ss`. Same zero/negative handling — `0:00`.
 */
fun formatDurationMsNoHours(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
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
    seconds >= 3600 -> "${formatOneDecimal(seconds / 3600.0)}h"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

/**
 * A self-refreshing wall-clock display string, realigned to minute
 * boundaries. Pre-wasm the body lived here atop `SimpleDateFormat` /
 * `System.currentTimeMillis`; it moved verbatim into the jvmShared actual so
 * android/desktop are unchanged, while wasm reads the browser's local time.
 */
@Composable
expect fun rememberWallClockTimeString(): String

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
    val epochMillis = parseIsoTimestampToEpochMillis(isoTimestamp) ?: return null
    val deltaMillis = wallNowMillis() - epochMillis
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

/**
 * Whether the platform's time convention is 24-hour. Android reads the system
 * setting; desktop derives it from the locale's short time pattern.
 */
@Composable
internal expect fun rememberIs24HourFormat(): Boolean
