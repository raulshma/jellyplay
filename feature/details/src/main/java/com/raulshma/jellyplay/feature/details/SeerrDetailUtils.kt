package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCast
import com.raulshma.jellyplay.core.model.seerr.SeerrCast
import com.raulshma.jellyplay.core.ui.components.formatDurationFromMinutes

/**
 * Neutral cast-member view used by the detail UI. The Seerr API exposes cast
 * two ways — [SeerrAggregateCast] (TV, role-based) and [SeerrCast] (movie,
 * single character) — with no shared interface. This value type is the single
 * shape the cast row renders, mapped at the call site, so the composable no
 * longer takes `List<Any>` and `when`-branches on the source type.
 */
internal data class SeerrCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
)

/** Maps a TV aggregate-cast list to the neutral [SeerrCastMember] view. */
internal fun List<SeerrAggregateCast>.toAggregateCastMembers(): List<SeerrCastMember> =
    map { member ->
        SeerrCastMember(
            id = member.id,
            name = member.name,
            character = member.roles.firstOrNull()?.character ?: "",
            profileUrl = member.profileUrl,
        )
    }

/** Maps a movie cast list to the neutral [SeerrCastMember] view. */
internal fun List<SeerrCast>.toCastMembers(): List<SeerrCastMember> =
    map { member ->
        SeerrCastMember(
            id = member.id,
            name = member.name,
            character = member.character ?: "",
            profileUrl = member.profileUrl,
        )
    }

/**
 * Seerr-style date formatting. Parses an ISO date ("yyyy-MM-dd") and renders a
 * short locale-friendly form; falls back to the first 10 chars on parse failure.
 *
 * Extracted verbatim from `SeerrDetailScreen.kt`.
 */
private val ISO_INPUT_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val SHORT_OUTPUT_FORMAT = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)

internal fun formatDate(dateStr: String): String {
    return try {
        java.time.LocalDate.parse(dateStr, ISO_INPUT_FORMAT).format(SHORT_OUTPUT_FORMAT)
    } catch (_: Exception) {
        dateStr.take(10)
    }
}

/**
 * Runtime formatting (minutes → "Xh Ym" / "Xh" / "Ym").
 *
 * Delegates to the shared [formatDurationFromMinutes] in core/ui — one
 * implementation instead of the copy that previously lived here.
 */
internal fun formatRuntime(minutes: Int): String = formatDurationFromMinutes(minutes.toLong())

/**
 * Builds a YouTube thumbnail URL for a related video, or null when the video is
 * not hosted on YouTube (no thumbnail source is available for other sites).
 * Centralized here so the media-detail and Seerr video rows share one builder.
 */
internal fun youTubeThumbnailUrl(site: String?, key: String?): String? =
    if (site?.equals("youtube", ignoreCase = true) == true && !key.isNullOrBlank()) {
        "https://img.youtube.com/vi/$key/mqdefault.jpg"
    } else null

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
