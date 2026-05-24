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
