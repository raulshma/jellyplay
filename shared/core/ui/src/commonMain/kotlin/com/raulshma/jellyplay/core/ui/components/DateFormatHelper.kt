package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.DateFormatPreference

/**
 * Formats a wall-clock timestamp using the user's [DateFormatPreference].
 *
 * The JVM pipeline (per-thread cached formatters, ICU/SHORT-date-derived
 * SYSTEM patterns via [getDateFormat]) lives in the jvmShared actual, so
 * android/desktop outputs stay byte-identical (pinned by
 * DateFormatHelperTest).
 */
expect fun formatDate(
    timestamp: Long,
    preference: DateFormatPreference = DateFormatPreference.SYSTEM,
): String

/**
 * ISO yyyy-MM-dd regardless of user preference (timestamp grids, log lines).
 * Equivalent-by-construction to formatting with [DateFormatPreference.ISO]:
 * the original ISO_DATE_PATTERN/Locale.US formatter produced exactly the
 * ISO preference's output.
 */
fun formatDateIso(timestamp: Long): String =
    formatDate(timestamp, DateFormatPreference.ISO)
