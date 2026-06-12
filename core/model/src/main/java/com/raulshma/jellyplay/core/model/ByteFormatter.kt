package com.raulshma.jellyplay.core.model

fun Long.formatBytes(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.1f GB".format(this / (1024.0 * 1024 * 1024))
}

fun Long.formatSpeed(): String = when {
    this <= 0 -> ""
    this < 1024 -> "$this B/s"
    this < 1024 * 1024 -> "%.1f KB/s".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB/s".format(this / (1024.0 * 1024))
    else -> "%.1f GB/s".format(this / (1024.0 * 1024 * 1024))
}

fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String {
    if (totalBytes <= 0 || speedBytesPerSec <= 0) return ""
    val remainingBytes = totalBytes - downloadedBytes
    if (remainingBytes <= 0) return ""
    val secondsRemaining = remainingBytes / speedBytesPerSec
    return when {
        secondsRemaining < 60 -> "${secondsRemaining}s left"
        secondsRemaining < 3600 -> "${secondsRemaining / 60}m ${secondsRemaining % 60}s left"
        else -> {
            val hours = secondsRemaining / 3600
            val minutes = (secondsRemaining % 3600) / 60
            "${hours}h ${minutes}m left"
        }
    }
}
