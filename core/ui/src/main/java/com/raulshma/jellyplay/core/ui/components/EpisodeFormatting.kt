package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Single source of truth for the `SxxExx · Series` context line shown under
 * episode titles in list-style rows (downloads list, library list view, etc.).
 *
 * For non-episodes this returns `null` so callers can fall back to their own
 * type-specific subtitle. The SxxExx tag is bold to draw the eye to the index,
 * matching the downloads list and the library list view.
 *
 * Used by the downloads list, the resync sheets, and the library list view so a
 * format change edits one place.
 */
fun episodeContextLine(
    mediaType: MediaType?,
    seriesName: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
): AnnotatedString? {
    if (mediaType != MediaType.EPISODE) return null
    val tag = seasonNumber?.let { s ->
        episodeNumber?.let { e ->
            "S${s.toString().padStart(2, '0')}E${e.toString().padStart(2, '0')}"
        }
    }
    val series = seriesName?.takeIf { it.isNotBlank() } ?: return tag?.let { plainTag ->
        buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(plainTag) } }
    }
    return buildAnnotatedString {
        if (tag != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tag) }
            append(" · ")
        }
        append(series)
    }
}

/**
 * Subtitle for a library list row: an episode context line for episodes (see
 * [episodeContextLine]), otherwise a `Year · TypeLabel` string. Single source
 * of truth for the library list view so the list path in [LibraryScreen] and
 * the grouped list path in [GroupedLibraryContent] stay in sync — previously
 * this block was duplicated verbatim in both call sites.
 *
 * The type label mirrors the labels used elsewhere in the library list; it is
 * intentionally a hard-coded string rather than the localized
 * `mediaTypeDisplayName` because the list view has always shown these short
 * fixed labels and this preserves that behavior.
 */
fun MediaItem.libraryListSubtitle(): AnnotatedString? =
    episodeContextLine(mediaType, seriesName, seasonNumber, episodeNumber)
        ?: buildString {
            if (year != null) append("$year")
            val typeLabel = when (mediaType) {
                MediaType.EPISODE -> "Episode"
                MediaType.SERIES -> "Series"
                MediaType.MOVIE -> "Movie"
                MediaType.AUDIO -> "Audio"
                MediaType.MUSIC -> "Music"
                MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
                else -> null
            }
            if (typeLabel != null) {
                if (isNotEmpty()) append(" · ")
                append(typeLabel)
            }
        }.let(::AnnotatedString)

/**
 * Season card title context: `Sxx - SeriesName`. A season's own name (e.g.
 * "Season 1") doesn't identify the show in flat season lists (library filtered
 * by type, search results), so card titles render the series instead.
 *
 * The season number is read from [indexNumber] first, then [seasonNumber].
 * Jellyfin season items carry their number in `IndexNumber` (mapped to
 * [indexNumber]); `ParentIndexNumber` (mapped to [seasonNumber]) is null for
 * seasons — it's only populated on episodes, where it means "the season this
 * episode belongs to". The [seasonNumber] fallback covers non-Jellyfin sources
 * that might populate it for seasons. Returns null for non-seasons, seasons
 * without a series name, or seasons whose number is unknown.
 */
fun MediaItem.seasonContextTitle(): String? {
    if (mediaType != MediaType.SEASON) return null
    val series = seriesName?.takeIf { it.isNotBlank() } ?: return null
    val number = indexNumber ?: seasonNumber ?: return null
    return "S${number.toString().padStart(2, '0')} - $series"
}

/**
 * Card/list title with season context: seasons render as `Sxx - SeriesName`
 * (see [seasonContextTitle]), everything else keeps its plain name. Single
 * source of truth for title rendering across card components (PosterCard,
 * ThumbCard, WideMediaCard) and list rows.
 */
fun MediaItem.displayTitle(): String = seasonContextTitle() ?: name
