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
    UNKNOWN,
}

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
)

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

@Immutable
@Serializable
data class NameGuidPair(
    val name: String,
    val id: String,
)
