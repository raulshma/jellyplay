package com.raulshma.jellyplay.core.ui.util

/**
 * Formats a byte count into a human-readable file-size string (B/KB/MB/GB),
 * always with one decimal place and SI units.
 *
 * Extracted from duplicate private copies previously inlined in
 * `ManageSeriesScreen` and `MediaInfoScreen`.
 */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
