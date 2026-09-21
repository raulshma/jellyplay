package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The ONE plain-string `SxxExx` derivation — the core under
 * [episodeContextLine] (which is [AnnotatedString]-typed and therefore could
 * not serve plain-string consumers across the seam; eleven sites had
 * re-derived the ladder by hand and drifted on padding and null-leg
 * handling).
 *
 * Shape, by null-leg:
 *  - both numbers present: `S{season}{separator}E{episode}` — [separator]
 *    covers the house styles: the tight `S01E01` context line and the
 *    spaced `S1 E01` card chip.
 *  - episode only: `E{episode}`.
 *  - season only: `S{season}`.
 *  - neither: null.
 *
 * Padding is UNIFORM here — [padded] zero-pads both numbers (`S01E01`) or
 * neither (`S1E1`). The card-chip family's MIXED style (bare season, padded
 * episode: `S1 E01`) is its own derivation, [episodeCardCode], sharing this
 * function's legs. Callers whose ladder skips a leg the core would render
 * (e.g. the wide card's subtitle only shows a code when a season is
 * present) keep their leg guard at the call site and call this with the
 * guarded inputs.
 */
fun episodeCode(
    seasonNumber: Int?,
    episodeNumber: Int?,
    padded: Boolean = true,
    separator: String = "",
): String? {
    fun num(n: Int): String = if (padded) n.toString().padStart(2, '0') else n.toString()
    return when {
        seasonNumber != null && episodeNumber != null -> "S${num(seasonNumber)}${separator}E${num(episodeNumber)}"
        episodeNumber != null -> "E${num(episodeNumber)}"
        seasonNumber != null -> "S${num(seasonNumber)}"
        else -> null
    }
}

/**
 * The card style of the SxxExx derivation: BARE season, PADDED episode
 * (`S1 E01` spaced on the chip and poster footer, `S1E01` tight in the wide
 * card's subtitle), with [episodeCode]'s single-number legs (`E01`, `S1`)
 * — every leg of the former hand copies, byte for byte. [separator] goes
 * between the season and episode parts of the pair.
 */
fun episodeCardCode(
    seasonNumber: Int?,
    episodeNumber: Int?,
    separator: String = "",
): String? = when {
    seasonNumber != null && episodeNumber != null ->
        "S${seasonNumber}${separator}E${episodeNumber.toString().padStart(2, '0')}"
    episodeNumber != null -> "E${episodeNumber.toString().padStart(2, '0')}"
    seasonNumber != null -> "S${seasonNumber}"
    else -> null
}

/**
 * The player chrome's episode subtitle — `Series · S1E5` (unpadded; a
 * "Series" suffix alone when the episode numbers are absent, the code alone
 * when the series name is missing/blank). The single derivation the player
 * session's subtitle builder and the next-episode overlay both render;
 * previously the VM and the overlay each carried a near-identical
 * `buildString` and had already split on the blank-name edge (the VM
 * suppressed blank names, the overlay rendered a stray separator after
 * them). Null when neither a series name nor a full season+episode pair
 * exists — callers fall back (the VM to a trimmed overview, the overlay to
 * not rendering the line at all).
 */
fun episodePlayerSubtitle(
    seriesName: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String? {
    val series = seriesName?.takeIf { it.isNotBlank() }
    val code =
        if (seasonNumber != null && episodeNumber != null) {
            episodeCode(seasonNumber, episodeNumber, padded = false)
        } else null
    return listOfNotNull(series, code).joinToString(" · ").ifEmpty { null }
}

/**
 * Single source of truth for the `SxxExx · Series` context line shown under
 * episode titles in list-style rows (downloads list, library list view, etc.).
 *
 * For non-episodes this returns `null` so callers can fall back to their own
 * type-specific subtitle. The SxxExx tag is bold to draw the eye to the index,
 * matching the downloads list and the library list view. The tag itself is
 * [episodeCode]'s padded tight form — this function is the styled wrapper,
 * the string shape lives in the plain core.
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
    val tag =
        if (seasonNumber != null && episodeNumber != null) {
            episodeCode(seasonNumber, episodeNumber, padded = true)
        } else null
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
