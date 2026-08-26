package com.raulshma.jellyplay.core.ui.util

import com.raulshma.jellyplay.core.ui.components.formatOneDecimal

/**
 * Formats a byte count into a human-readable file-size string (B/KB/MB/GB),
 * always with one decimal place and SI units.
 *
 * Extracted from duplicate private copies previously inlined in
 * `ManageSeriesScreen` and `MediaInfoScreen`.
 */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${formatOneDecimal(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${formatOneDecimal(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${formatOneDecimal(bytes / 1_000.0)} KB"
    else -> "$bytes B"
}
