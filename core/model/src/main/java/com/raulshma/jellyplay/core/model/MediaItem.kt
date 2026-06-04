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
    COLLECTION,
    LIVE_TV,
    CHANNEL,
    UNKNOWN,
}

val MediaType.isAudioType: Boolean
    get() = this == MediaType.AUDIO || this == MediaType.MUSIC || this == MediaType.ALBUM || this == MediaType.ARTIST

val MediaType.isMusicTrack: Boolean
    get() = this == MediaType.AUDIO || this == MediaType.MUSIC

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
)

@Immutable
@Serializable
data class NameGuidPair(
    val name: String,
    val id: String,
)
