package com.raulshma.jellyplay.feature.details

/**
 * Seerr-style date formatting. Parses an ISO date ("yyyy-MM-dd") and renders a
 * short locale-friendly form; falls back to the first 10 chars on parse failure.
 *
 * Extracted verbatim from `SeerrDetailScreen.kt`.
 */
internal fun formatDate(dateStr: String): String {
    return try {
        val input = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val output = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
        input.parse(dateStr)?.let { output.format(it) } ?: dateStr.take(10)
    } catch (_: Exception) {
        dateStr.take(10)
    }
}

/**
 * Runtime formatting (minutes → "Xh Ym" / "Xh" / "Ym").
 *
 * Extracted verbatim from `SeerrDetailScreen.kt`.
 */
internal fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/**
 * Converts a 2-letter ISO country code into its flag emoji.
 *
 * Extracted verbatim from `SeerrDetailScreen.kt`.
 */
internal fun getFlagEmoji(countryCode: String): String? {
    if (countryCode.length != 2) return null
    val firstChar = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}
