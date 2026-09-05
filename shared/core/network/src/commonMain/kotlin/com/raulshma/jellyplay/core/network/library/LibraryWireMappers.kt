package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.ImageBlurHashes
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.TrickplayInfo

/**
 * Wire → core.model mappers for the Phase W wasm library/playback clients.
 * Every mapped field, fallback and tick conversion mirrors the jvmShared
 * `JellyfinDtoMappers` + the inline mappings in `LibraryApiClientImpl`
 * field-for-field; deviations are limited to the two documented wasm deltas:
 *  - DATE FIELDS stay the raw wire ISO strings. The SDK deserializes dates
 *    into a zone-shifted LocalDateTime and the JVM mappers re-emit them via
 *    `.toString()` (no offset); wasm keeps the server's original string.
 *  - `MediaSource.id` keeps the SDK's `id.toString()` fallback literally
 *    (a missing server Id maps to the string "null"), byte-parity with the
 *    JVM mapper.
 */
internal fun BaseItemDtoWire.toMediaItem() = MediaItem(
    id = id ?: "",
    name = name ?: "",
    originalTitle = originalTitle,
    overview = overview,
    mediaType = type.toMediaType(),
    year = productionYear,
    communityRating = communityRating?.toFloat(),
    officialRating = officialRating,
    runTimeTicks = runTimeTicks,
    playbackPositionTicks = userData?.playbackPositionTicks,
    isPlayed = userData?.played == true,
    isFavorite = userData?.isFavorite == true,
    // Server-computed width/height of the Primary image; drives the masonry
    // view's per-card height. Falls back to the poster 2:3 default when the
    // server omits it.
    posterAspectRatio = primaryImageAspectRatio?.toFloat()?.takeIf { it > 0f } ?: (2f / 3f),
    premiereDate = premiereDate,
    genres = genres ?: emptyList(),
    studios = studios?.mapNotNull { it.name } ?: emptyList(),
    tags = tags ?: emptyList(),
    parentId = parentId,
    seriesId = seriesId,
    seasonId = seasonId ?: (if (type == "Episode") parentId else null),
    seriesName = seriesName,
    seasonNumber = parentIndexNumber,
    episodeNumber = indexNumber,
    indexNumber = indexNumber,
    childCount = childCount,
    albumArtist = albumArtist,
    album = album,
    blurHashes = ImageBlurHashes(
        primary = imageBlurHashes?.get("Primary")?.values?.firstOrNull(),
        backdrop = imageBlurHashes?.get("Backdrop")?.values?.firstOrNull(),
    ),
    normalizationGain = normalizationGain?.toFloat(),
    playCount = userData?.playCount ?: 0,
    lastPlayedDate = userData?.lastPlayedDate,
    unplayedItemCount = userData?.unplayedItemCount,
)

/**
 * Wire `BaseItemKind` serial name → [MediaType]; mirrors
 * `JellyfinDtoMappers.toMediaType` incl. the TvChannel/TvProgram aliases and
 * the `else -> UNKNOWN` catch-all (note: "Playlist" is UNKNOWN there too).
 */
internal fun String?.toMediaType(): MediaType = when (this) {
    "Movie" -> MediaType.MOVIE
    "Series" -> MediaType.SERIES
    "Season" -> MediaType.SEASON
    "Episode" -> MediaType.EPISODE
    "MusicAlbum" -> MediaType.ALBUM
    "Audio" -> MediaType.AUDIO
    "MusicArtist" -> MediaType.ARTIST
    "MusicVideo" -> MediaType.MUSIC_VIDEO
    "BoxSet" -> MediaType.COLLECTION
    "Photo" -> MediaType.PHOTO
    "PhotoAlbum" -> MediaType.PHOTO_FOLDER
    "LiveTvChannel", "TvChannel" -> MediaType.CHANNEL
    "LiveTvProgram", "TvProgram" -> MediaType.LIVE_TV
    else -> MediaType.UNKNOWN
}

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
    MediaType.CHANNEL -> "LiveTvChannel"
    MediaType.LIVE_TV -> "LiveTvProgram"
    MediaType.MUSIC -> "Audio"
    MediaType.UNKNOWN -> null
}

/** Mirrors `TrickplayInfoDto.toTrickplayInfo` (all fallbacks). */
internal fun TrickplayInfoDtoWire.toTrickplayInfo() = TrickplayInfo(
    width = width ?: 320,
    height = height ?: 180,
    tileWidth = tileWidth ?: 10,
    tileHeight = tileHeight ?: 1,
    thumbnailCount = thumbnailCount ?: 0,
    interval = interval ?: 10_000,
    bandwidth = bandwidth ?: 0,
)

/**
 * Maps a wire [MediaSourceInfoWire] to the domain [MediaSource]; mirrors
 * `MediaSourceInfo.toMediaSource`. Shared by the item-detail fetch and the
 * `PlaybackInfo` fetch so playability flags and stream lists parse
 * identically. [trickplayInfo] is item-scoped so callers that have it
 * (detail) pass it in; `PlaybackInfo` callers pass `null`.
 */
internal fun MediaSourceInfoWire.toMediaSource(
    trickplayInfo: TrickplayInfo? = null,
) = MediaSource(
    id = id.toString(),
    name = name ?: "",
    container = container,
    size = size,
    bitrate = bitrate?.toLong(),
    runTimeTicks = runTimeTicks,
    supportsTranscoding = supportsTranscoding ?: false,
    supportsDirectStream = supportsDirectStream ?: false,
    supportsDirectPlay = supportsDirectPlay ?: false,
    transcodeUrl = transcodingUrl,
    liveStreamId = liveStreamId,
    requiresOpening = requiresOpening ?: false,
    path = path,
    mediaStreams = mediaStreams?.map { it.toMediaStream() } ?: emptyList(),
    trickplayInfo = trickplayInfo,
)

/** Mirrors `MediaStream.toMediaStream` (type table + `else -> EMBEDDED_IMAGE`). */
internal fun MediaStreamDtoWire.toMediaStream() = MediaStream(
    index = index,
    type = when (type) {
        "Video" -> StreamType.VIDEO
        "Audio" -> StreamType.AUDIO
        "Subtitle" -> StreamType.SUBTITLE
        else -> StreamType.EMBEDDED_IMAGE
    },
    codec = codec,
    language = language,
    title = title,
    displayTitle = displayTitle,
    isDefault = isDefault ?: false,
    isForced = isForced ?: false,
    isHearingImpaired = isHearingImpaired ?: false,
    isExternal = isExternal ?: false,
    width = width,
    height = height,
    bitRate = bitRate?.toLong(),
    sampleRate = sampleRate,
    channels = channels,
    deliveryUrl = deliveryUrl,
    videoRange = videoRange,
    videoRangeType = videoRangeType,
    realFrameRate = realFrameRate?.toFloat(),
    videoDoViTitle = videoDoViTitle,
)

/**
 * Maps a detail item to [MediaDetail]; the construction mirrors
 * `LibraryApiClientImpl.getMediaDetail`'s inline mapping (people distinctBy
 * id, chapters, per-source trickplay projection, provider-id lowercase,
 * relatedItems left empty — similar items fetch separately).
 */
internal fun BaseItemDtoWire.toMediaDetail(): MediaDetail {
    val people = (people?.map { person ->
        PersonInfo(
            id = person.id ?: "",
            name = person.name ?: "",
            role = person.role,
            type = person.type ?: "",
            primaryImageTag = person.primaryImageTag,
        )
    } ?: emptyList()).distinctBy { it.id }
    val chapters = chapters?.map { chapter ->
        ChapterInfo(
            name = chapter.name ?: "",
            startPositionTicks = chapter.startPositionTicks ?: 0L,
            imageDateModified = chapter.imageDateModified,
            imageTag = chapter.imageTag,
        )
    } ?: emptyList()
    val mediaSources = mediaSources?.map { source ->
        source.toMediaSource(
            trickplayInfo = trickplay
                ?.get(source.id ?: "")
                ?.values
                ?.maxByOrNull { it.width ?: 0 }
                ?.toTrickplayInfo(),
        )
    } ?: emptyList()
    val externalUrls = externalUrls?.map { url ->
        ExternalUrl(
            name = url.name ?: "",
            url = url.url ?: "",
        )
    } ?: emptyList()
    val providerIds = providerIds
        ?.mapNotNull { (k, v) -> v?.let { k.lowercase() to it } }
        ?.toMap() ?: emptyMap()
    return MediaDetail(
        item = toMediaItem(),
        sortName = forcedSortName,
        customRating = customRating,
        criticRating = criticRating?.toFloat(),
        taglines = taglines ?: emptyList(),
        productionLocations = productionLocations ?: emptyList(),
        lockData = lockData ?: false,
        lockedFields = lockedFields ?: emptyList(),
        status = status,
        airDays = airDays ?: emptyList(),
        airTime = airTime,
        displayOrder = displayOrder,
        preferredMetadataLanguage = preferredMetadataLanguage,
        preferredMetadataCountryCode = preferredMetadataCountryCode,
        dateCreated = dateCreated,
        people = people,
        relatedItems = emptyList(),
        chapters = chapters,
        mediaSources = mediaSources,
        externalUrls = externalUrls,
        providerIds = providerIds,
    )
}

/** Mirrors the `getUserViews` folder mapping in `LibraryApiClientImpl`. */
internal fun BaseItemDtoWire.toLibraryFolder() = LibraryFolder(
    id = id ?: "",
    name = name ?: "",
    collectionType = collectionType,
    type = type,
)

/** Mirrors the `getGenres` mapping. */
internal fun BaseItemDtoWire.toGenre() = Genre(
    id = id ?: "",
    name = name ?: "",
)

/** Mirrors the `getStudios` mapping. */
internal fun BaseItemDtoWire.toStudio() = Studio(
    id = id ?: "",
    name = name ?: "",
)

/**
 * Mirrors the `getPlaylists` mapping. Flags not carried by the wire query
 * (`IsReadOnly`/`IsPublic`/`CanEdit`) keep the same hardcoded JVM values.
 */
internal fun BaseItemDtoWire.toPlaylist(currentUserId: String?) = Playlist(
    id = id ?: "",
    name = name ?: "",
    overview = overview,
    itemCount = childCount ?: 0,
    imageTag = imageTags?.get("Primary"),
    userId = currentUserId,
    isReadOnly = false,
    isPublic = false,
    canEdit = true,
    canDelete = canDelete ?: true,
    createdAt = dateCreated,
)

/** Mirrors the `getPlaylistItems` mapping (artist falls back to artistItems). */
internal fun BaseItemDtoWire.toPlaylistItem() = PlaylistItem(
    id = id ?: "",
    playlistItemId = playlistItemId,
    name = name ?: "",
    artist = albumArtist ?: artistItems?.firstOrNull()?.name,
    album = album,
    mediaType = type.toMediaType(),
    runTimeTicks = runTimeTicks,
)

/** Mirrors the `getCollections` mapping (CHILD_COUNT projection). */
internal fun BaseItemDtoWire.toCollectionSummary() = CollectionSummary(
    id = id ?: "",
    name = name ?: "",
    itemCount = childCount ?: 0,
    imageTag = imageTags?.get("Primary"),
)

/**
 * The canonical rating→age table (unknown ratings map to null = "no
 * opinion"). `JellyfinApiEngine.ratingToAge` delegates here; formerly a
 * verbatim twin lived in the engine.
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
 * The engine's client-side parental-rating filter, verbatim semantics
 * (`JellyfinApiEngine.filterByParentalRating`): no max rating → unfiltered;
 * an unrated/unknown-rating item passes (`!= false` keeps it).
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
 * tokens, dropping unknown tokens — mirrors `JellyfinDtoMappers
 * .parseItemSortList`: the 9 exact-match hot tokens, then a static
 * lowercase lookup over every ItemSortBy serial name (plus the enum-name
 * aliases the JVM map registers).
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

/**
 * Maps the wire lyric DTO to [LyricsResult]: per-line start/end times derived
 * from the next line (clamped non-negative), per-word cues sliced out of the
 * line text, and [LyricsSource.UNKNOWN] exactly when no lines parsed. Formerly
 * a private twin of this exact body inside `KtorWasmLibraryApiClient`; the
 * jvmShared `LyricsApi` keeps its own SDK-typed copy because its input is the
 * deserialized `org.jellyfin.sdk.model.api.LyricDto`, which commonMain cannot
 * see.
 */
internal fun LyricsDtoWire.toLyricsResult(): LyricsResult {
    val lines = lyrics.mapIndexedNotNull { idx, line ->
        val startMs = line.start?.let { it / 10_000 } ?: 0L
        val nextStartMs = if (idx + 1 < lyrics.size) {
            lyrics[idx + 1].start?.div(10_000) ?: startMs
        } else startMs
        val text = line.text
        val words = line.cues?.map { cue ->
            // Wire cue offsets are server-authored and not trusted: clamp
            // both ends into the line text (end below start degrades to an
            // empty slice) instead of letting substring throw on out-of-
            // range positions.
            val start = cue.position.coerceIn(0, text.length)
            val end = cue.endPosition.coerceIn(start, text.length)
            LyricsWord(
                timeMs = cue.start / 10_000,
                text = text.substring(start, end),
                durationMs = ((cue.end ?: cue.start) - cue.start) / 10_000,
            )
        }.orEmpty()
        LyricsLine(
            timeMs = startMs,
            text = text,
            durationMs = (nextStartMs - startMs).coerceAtLeast(0L),
            words = words,
        )
    }
    val source = if (lines.isEmpty()) LyricsSource.UNKNOWN else LyricsSource.EXTERNAL
    return LyricsResult(lines = lines, source = source)
}
