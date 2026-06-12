package com.raulshma.jellyplay.core.ui.components

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
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
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
