package com.raulshma.jellyplay.core.model

/** Common replacement for `"%.1f".format(v)` — always one decimal, '.' separator. */
private fun formatOneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}

fun Long.formatBytes(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${formatOneDecimal(this / 1024.0)} KB"
    this < 1024 * 1024 * 1024 -> "${formatOneDecimal(this / (1024.0 * 1024))} MB"
    else -> "${formatOneDecimal(this / (1024.0 * 1024 * 1024))} GB"
}

fun Long.formatSpeed(): String = when {
    this <= 0 -> ""
    this < 1024 -> "$this B/s"
    this < 1024 * 1024 -> "${formatOneDecimal(this / 1024.0)} KB/s"
    this < 1024 * 1024 * 1024 -> "${formatOneDecimal(this / (1024.0 * 1024))} MB/s"
    else -> "${formatOneDecimal(this / (1024.0 * 1024 * 1024))} GB/s"
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
