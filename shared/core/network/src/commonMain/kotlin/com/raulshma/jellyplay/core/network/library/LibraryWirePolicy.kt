package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The wire-level library policy tables that are canonical here in commonMain
 * (no Jellyfin SDK types): the compound sort-key parser, the parental-rating
 * table + client-side filter, and the [MediaType] → wire-kind naming. Both
 * request-shape consumers ([LibraryItemsQuerySpec], [ResumeRowFilter]) and
 * the jvmShared mappers (`JellyfinDtoMappers.parseItemSortList` /
 * `.toFilteredMediaItems`) resolve against these tables so the two dialects
 * can never drift.
 *
 * Formerly mixed into `LibraryWireMappers.kt` beside the wire-DTO → model
 * twins; the twins (and the hand-rolled `LibraryWireDto` set) were deleted
 * with the wasmJs target — only the SDK-independent policy tables remain.
 */

/**
 * [MediaType] → wire `BaseItemKind` serial name; mirrors
 * `JellyfinDtoMappers.toBaseItemKind` (null for UNKNOWN, MUSIC folds to
 * "Audio"). Null means "do not constrain the query by item type".
 */
internal fun MediaType.toWireItemKind(): String? = when (this) {
    MediaType.MOVIE -> "Movie"
    MediaType.SERIES -> "Series"
    MediaType.SEASON -> "Season"
    MediaType.EPISODE -> "Episode"
    MediaType.ALBUM -> "MusicAlbum"
    MediaType.AUDIO -> "Audio"
    MediaType.ARTIST -> "MusicArtist"
    MediaType.MUSIC_VIDEO -> "MusicVideo"
    MediaType.COLLECTION -> "BoxSet"
    MediaType.PHOTO -> "Photo"
    MediaType.PHOTO_FOLDER -> "PhotoAlbum"
    MediaType.BOOK -> "Book"
    MediaType.FOLDER -> "Folder"
    MediaType.CHANNEL -> "LiveTvChannel"
    MediaType.LIVE_TV -> "LiveTvProgram"
    MediaType.MUSIC -> "Audio"
    MediaType.UNKNOWN -> null
}

/**
 * The canonical rating→age table (unknown ratings map to null = "no
 * opinion"). The parental-filter tails resolve ratings through it
 * (jvmShared: the SDK-typed `toFilteredMediaItems` mapper tail over
 * [filterByParentalRating]).
 */
internal fun parentalRatingAge(rating: String): Int? = when (rating.uppercase()) {
    "G", "TV-Y", "TV-G" -> 0
    "PG", "TV-Y7", "TV-PG" -> 7
    "PG-13", "TV-14" -> 13
    "R", "TV-MA" -> 17
    "NC-17" -> 18
    else -> null
}

/**
 * The client-side parental-rating filter, verbatim semantics: no max rating →
 * unfiltered; an unrated/unknown-rating item passes (`!= false` keeps it).
 */
internal fun <T : MediaItem> List<T>.filterByParentalRating(maxParentalRating: Int?): List<T> {
    val max = maxParentalRating ?: return this
    return mapNotNull { item ->
        if (item.officialRating?.let { rating ->
                parentalRatingAge(rating)?.let { age -> age <= max }
            } != false) item else null
    }
}

/**
 * Parses a compound sort key ("ProductionYear,SortName") into wire sort
 * tokens, dropping unknown tokens — the canonical table behind
 * `JellyfinDtoMappers.parseItemSortList`: the 9 exact-match hot tokens, then
 * a static lowercase lookup over every ItemSortBy serial name (plus the
 * enum-name aliases the JVM map registers).
 */
internal fun parseItemSortList(sortBy: String): List<String> {
    if (sortBy.isBlank()) return emptyList()
    return sortBy.split(",").mapNotNull { token -> parseItemSortToken(token.trim()) }
}

private fun parseItemSortToken(trimmed: String): String? = when (trimmed) {
    "SortName", "DatePlayed", "DateCreated", "DateLastContentAdded", "PlayCount",
    "Random", "PremiereDate", "ProductionYear", "CommunityRating",
    -> trimmed
    else -> ITEM_SORT_BY_TOKENS[trimmed.lowercase()]
}

/** All `ItemSortBy` serial names (jellyfin-model 1.8.12), for token lookup. */
private val ITEM_SORT_BY_SERIAL_NAMES = listOf(
    "Default", "AiredEpisodeOrder", "Album", "AlbumArtist", "Artist",
    "DateCreated", "OfficialRating", "DatePlayed", "PremiereDate", "StartDate",
    "SortName", "Name", "Random", "Runtime", "CommunityRating",
    "ProductionYear", "PlayCount", "CriticRating", "IsFolder", "IsUnplayed",
    "IsPlayed", "SeriesSortName", "VideoBitRate", "AirTime", "Studio",
    "IsFavoriteOrLiked", "DateLastContentAdded", "SeriesDatePlayed",
    "ParentIndexNumber", "IndexNumber",
)

private val ITEM_SORT_BY_TOKENS: Map<String, String> = buildMap {
    for (serial in ITEM_SORT_BY_SERIAL_NAMES) {
        put(serial.lowercase(), serial)
    }
    // The JVM lookup also registers the SCREAMING_SNAKE enum-name aliases
    // (e.g. "sort_name" → "SortName"); reproduce them from the same serial
    // list. Underscore goes before an uppercase letter preceded by a
    // lowercase one — checking the ORIGINAL previous char, not the already
    // uppercased builder content (ItemSortBy serials carry no acronyms, so
    // this yields exactly the SDK enum constant names).
    for (serial in ITEM_SORT_BY_SERIAL_NAMES) {
        val enumName = buildString {
            for ((index, ch) in serial.withIndex()) {
                if (ch.isUpperCase() && index > 0 && !serial[index - 1].isUpperCase()) append('_')
                append(ch.uppercase())
            }
        }
        put(enumName.lowercase(), serial)
    }
}
