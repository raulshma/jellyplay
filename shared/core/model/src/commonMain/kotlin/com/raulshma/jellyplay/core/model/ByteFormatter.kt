package com.raulshma.jellyplay.core.model

/** The storage display band a byte count scales into (÷1024 house convention). */
enum class StorageBytesUnit(val suffix: String) {
    B("B"),
    KB("KB"),
    MB("MB"),
    GB("GB"),
}

/** A storage size already scaled into its [StorageBytesUnit] band. */
data class StorageBytesValue(val value: Double, val unit: StorageBytesUnit)

/**
 * The ONE storage-byte band ladder (the ÷1024 house convention): below 1024
 * stays in bytes, each larger band divides by another 1024. [formatBytes] and
 * localized wrappers that need the number+unit parts separately both read
 * this, so the divisor policy lives exactly here. Declared delta:
 * the four drifted storage formatters (admin Logs, PhotoViewer,
 * DetailDownloadDialog, ArrQueue) now route through this table — PhotoViewer
 * and DetailDownloadDialog switch from ÷1000 to ÷1024 conventions (small
 * visible number change, unifying drift), and the sub-band formatting quirks
 * (integer KB, `%.0f KB`, missing GB band, the ÷1000 pair's sub-KB funnel
 * where 500 B rendered "0.5 KB") collapse onto the one table.
 */
fun Long.toStorageBytesValue(): StorageBytesValue = when {
    this < 1024 -> StorageBytesValue(toDouble(), StorageBytesUnit.B)
    this < 1024 * 1024 -> StorageBytesValue(this / 1024.0, StorageBytesUnit.KB)
    this < 1024 * 1024 * 1024 -> StorageBytesValue(this / (1024.0 * 1024), StorageBytesUnit.MB)
    else -> StorageBytesValue(this / (1024.0 * 1024 * 1024), StorageBytesUnit.GB)
}

/** The band's rendered "number unit" pair; [suffix] carries the "/s" speed tail. */
private fun StorageBytesValue.render(suffix: String = ""): String {
    val number = if (unit == StorageBytesUnit.B) value.toLong().toString() else formatFixed(value, 1)
    return "$number ${unit.suffix}$suffix"
}

fun Long.formatBytes(): String = toStorageBytesValue().render()

fun Long.formatSpeed(): String = if (this <= 0) "" else toStorageBytesValue().render("/s")

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
