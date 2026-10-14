package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class MediaType {
    MOVIE,
    SERIES,
    SEASON,
    EPISODE,
    MUSIC,
    AUDIO,
    ALBUM,
    ARTIST,
    MUSIC_VIDEO,
    COLLECTION,
    PHOTO,
    PHOTO_FOLDER,
    LIVE_TV,
    CHANNEL,
    BOOK,
    // Generic wire "Folder" — a container folder inside a library (e.g. a
    // series/volume folder in a books library; the server has no BookFolder
    // kind, plain folders serialize as "Folder"). Not playable itself: item
    // clicks drill into its children (resolveItemDestination).
    FOLDER,
    UNKNOWN,
}

/**
 * The content types offered as media-type filter chips. FOLDER is a
 * container, not a content type, and UNKNOWN names nothing — neither is ever
 * offered as a filter (library chips, library sheet, search sheet).
 */
val filterableMediaTypes: List<MediaType> =
    MediaType.entries.filter { it != MediaType.UNKNOWN && it != MediaType.FOLDER }

val MediaType.isAudioType: Boolean
    get() = this == MediaType.AUDIO || this == MediaType.MUSIC || this == MediaType.ALBUM || this == MediaType.ARTIST

val MediaType.isVideoType: Boolean
    get() = this == MediaType.MOVIE || this == MediaType.EPISODE || this == MediaType.MUSIC_VIDEO

val MediaType.isMusicTrack: Boolean
    get() = this == MediaType.AUDIO || this == MediaType.MUSIC

// NOTE: User-facing display labels for MediaType previously lived here as
// `displayName` / `displayNamePlural`. They were hardcoded English and could
// not read string resources, so they were untranslatable. They have moved to
// `core:ui` — see `mediaTypeDisplayName` / `mediaTypeDisplayNamePlural`
// (and their `@StringRes` variants) in
// com.raulshma.jellyplay.core.ui.model.MediaTypeNames.

val MediaType.isPhotoType: Boolean
    get() = this == MediaType.PHOTO || this == MediaType.PHOTO_FOLDER

@Immutable
@Serializable
data class ImageBlurHashes(
    val primary: String? = null,
    val backdrop: String? = null,
)

@Immutable
@Serializable
data class MediaItem(
    val id: String,
    val name: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val mediaType: MediaType,
    val year: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val playbackPositionTicks: Long? = null,
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false,
    val posterAspectRatio: Float = 2f / 3f,
    val backdropAspectRatio: Float = 16f / 9f,
    val premiereDate: String? = null,
    val genres: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val parentId: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val indexNumber: Int? = null,
    val childCount: Int? = null,
    val recursiveItemCount: Int? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val artistItems: List<NameGuidPair> = emptyList(),
    val container: String? = null,
    val videoType: String? = null,
    val blurHashes: ImageBlurHashes = ImageBlurHashes(),
    val normalizationGain: Float? = null,
    val normalizationPeak: Float? = null,
    val playCount: Int = 0,
    val lastPlayedDate: String? = null,
    val unplayedItemCount: Int? = null,
    /**
     * True when the item is a server-side placeholder with no file behind it
     * (Jellyfin `LocationType = Virtual`) — a missing or unaired episode.
     * Rendered as a dimmed row with a badge; never a play/download target.
     */
    val isVirtual: Boolean = false,
    /** Why the item is virtual; non-null exactly when [isVirtual] is true. */
    val missingReason: MissingEpisodeReason? = null,
    /**
     * Compact stream summary (D11) for the season-episode rows' subtitle /
     * audio badges. Populated only where the fetch actually projected
     * `MediaStreams` — the per-season [getEpisodes] call; the all-episodes
     * path (and any listing that carries no sources) maps to null so the
     * rows render nothing. Derived at map time in JellyfinDtoMappers.
     */
    val episodeStreamInfo: EpisodeStreamInfo? = null,
    /**
     * When the item entered the library (epoch millis; H12 NEW-badge input).
     * Populated only where the fetch projected `DateCreated` (the
     * HOME_ROW_PROJECTION_FIELDS rows — resume / next-up / latest); every
     * other listing maps to null and the badge predicate treats it as
     * "never new". Derived at map time in JellyfinDtoMappers.
     */
    val dateCreatedEpochMs: Long? = null,
    /**
     * Compact resolution/audio quality summary (H11) for the cards'
     * [ItemQualityBadge] row, in render-priority order (resolution badge
     * first, then format badges — see [toQualityBadges] / QualityBadgeRow).
     * Populated only where the fetch projected `MediaStreams` (the home-row
     * projections); null means "not fetched", so the cards render nothing —
     * never a badge-less lie. The full MediaStreams never reach the UI: this
     * tiny list is the only stream summary a home item carries.
     */
    val qualityBadges: List<ItemQualityBadge>? = null,
    /**
     * The item's credited People (D18) — the raw wire list retained so the
     * person screen extracts the per-item role ("as {Role}") and role group
     * from the entry matching the queried person. Populated only where the
     * fetch projected the `People` field (the getItemsByPerson filmography
     * rows, bounded at 50); every other listing maps to emptyList so
     * role-bearing surfaces degrade to the role-less render.
     */
    val people: List<PersonInfo> = emptyList(),
    /**
     * Compact trickplay sprite-sheet summary (H13) — everything a preview
     * overlay needs to request and crop the right tile: the tile-cell pixel
     * size ([TrickplayInfo.width]/[TrickplayInfo.height]), the per-sheet grid
     * shape ([TrickplayInfo.tileWidth]/[TrickplayInfo.tileHeight] — tiles per
     * row/column), the total [TrickplayInfo.thumbnailCount] and the per-tile
     * [TrickplayInfo.interval] in ms. Populated only where the fetch projected
     * `Trickplay` (the HOME_ROW_PROJECTION_FIELDS resume/next-up rows); every
     * other listing — and older servers that never generate the element — maps
     * to null, so preview surfaces degrade to nothing rather than guess.
     * Derived at map time in JellyfinDtoMappers.
     */
    val trickplayInfo: TrickplayInfo? = null,
)

/**
 * The card-quality-badge vocabulary (H11). Derived client-side from the
 * projected MediaStreams in JellyfinDtoMappers — one resolution tier
 * ([FOUR_K] / [HD] / [SD]) plus independent format badges ([HDR] /
 * [DOLBY_VISION] / [ATMOS]). Only the first four are badge-worthy on the
 * cards ([QualityBadgeRow]'s render set); HD/SD exist so the derivation is
 * total and callers can tell "just HD" from "nothing notable" without
 * re-deriving from widths.
 */
@Immutable
@Serializable
enum class ItemQualityBadge { FOUR_K, HDR, DOLBY_VISION, ATMOS, HD, SD }

/**
 * The compact per-episode stream summary behind the season rows' stream
 * badges (D11): whether any subtitle track exists ([hasSubtitles] → the CC
 * chip), the best audio track's layout label ([audioLayout] — "Atmos" when
 * the codec/profile advertises it, else "7.1"/"5.1" from the channel count,
 * null for lesser layouts), and the count of distinct audio languages
 * ([audioLanguageCount], rendered only when > 1). Null [MediaItem.episodeStreamInfo]
 * means "unknown" — never rendered as an empty badge row.
 */
@Immutable
@Serializable
data class EpisodeStreamInfo(
    val hasSubtitles: Boolean,
    val audioLayout: String?,
    val audioLanguageCount: Int,
)

/**
 * Why a virtual ([MediaItem.isVirtual]) episode has no playable file: it
 * hasn't aired yet ([UNAIRED], premiere date in the future), or the file is
 * simply absent from the library ([MISSING_FILE]). Derived at map time from
 * the wire `LocationType` + `PremiereDate`.
 */
@Immutable
@Serializable
enum class MissingEpisodeReason { UNAIRED, MISSING_FILE }

/**
 * True when the user has a non-zero playback position saved. Use for surfacing
 * a "resume" indicator or progress bar without the `playbackPositionTicks != null
 * && playbackPositionTicks!! > 0` repeat at every call site.
 */
val MediaItem.hasPlaybackPosition: Boolean
    get() = playbackPositionTicks != null && playbackPositionTicks > 0

/**
 * True when the user is mid-playback and hasn't finished — i.e. there is a
 * non-zero position AND the item isn't marked played. Use for the "time
 * remaining" badge; [hasPlaybackPosition] is the broader predicate for just
 * showing a progress bar.
 */
val MediaItem.hasWatchProgress: Boolean
    get() = hasPlaybackPosition && !isPlayed

/**
 * True when the item carries a runtime worth rendering as time: a non-zero
 * [runTimeTicks] that is neither a SERIES container (carries no runtime of
 * its own) nor a BOOK (RunTimeTicks is absent or meaningless there — the
 * position ticks encode reading progress, not time, and the video runtime
 * math on that data produced bogus "0m left" labels).
 *
 * The card footers and the newsletter meta rows previously re-derived this
 * ladder per site and had drifted: the card footers excluded SERIES + BOOK,
 * the newsletter rows only SERIES (or neither), so a book with stray runtime
 * ticks rendered a duration in some sections and not others. Route every
 * "show a duration" gate through this predicate; the sub-minute floor lives
 * one step down in `formatRuntimeLabelFromTicks`.
 */
val MediaItem.hasMeaningfulRuntime: Boolean
    get() = (runTimeTicks ?: 0L) > 0 && mediaType != MediaType.SERIES && mediaType != MediaType.BOOK

/**
 * The series id this detail entry resolves to for series-scoped operations
 * (seasons/episodes load, smart-play, playlist expansion): a series resolves to
 * itself, an episode/season to its parent [seriesId], anything else to null.
 * Lifts the repeated `when (mediaType)` fork out of the provider and ViewModel.
 */
val MediaItem.seriesIdForDetail: String?
    get() = when (mediaType) {
        MediaType.SERIES -> id
        MediaType.EPISODE, MediaType.SEASON -> seriesId
        else -> null
    }

/**
 * H12's NEW-badge predicate in its pure form: the item entered the library
 * strictly after the user's last home visit. Both failure modes degrade to
 * false — a null [dateCreatedEpochMs] (the fetch didn't project DateCreated)
 * and a zero/never-stamped [lastVisitEpochMs] (first visit — everything in
 * the library predates no stamp, and stamping happens ~10s into the visit,
 * so the first session would otherwise badge the entire library).
 */
fun isNewForHome(dateCreatedEpochMs: Long?, lastVisitEpochMs: Long): Boolean =
    dateCreatedEpochMs != null && lastVisitEpochMs > 0L && dateCreatedEpochMs > lastVisitEpochMs

/** [isNewForHome] against the item's own [MediaItem.dateCreatedEpochMs]. */
fun MediaItem.isNewForHome(lastVisitEpochMs: Long): Boolean =
    isNewForHome(dateCreatedEpochMs = dateCreatedEpochMs, lastVisitEpochMs = lastVisitEpochMs)

@Immutable
@Serializable
data class NameGuidPair(
    val name: String,
    val id: String,
)
