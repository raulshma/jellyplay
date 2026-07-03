package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class OfflineMediaItem(
    val id: String,
    val name: String,
    val mediaType: MediaType,
    val overview: String? = null,
    val year: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val blurHashPrimary: String? = null,
    val blurHashBackdrop: String? = null,
    val genres: List<String> = emptyList(),
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus? = null,
    val downloadedBytes: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val childCount: Int = 0,
    // Playback progress. Seeded from server UserData at download
    // time and updated locally as the user watches offline. `playedPercentage` is
    // derived (0–100) and stored so it can be rendered without recomputing.
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double = 0.0,
    val isPlayed: Boolean = false,
    val lastPlayedDate: String? = null,
    // Rich metadata persisted at download time so the offline detail screens
    // mirror the online detail screen. Populated from MediaDetail; null for
    // items downloaded before these fields existed.
    val originalTitle: String? = null,
    val criticRating: Float? = null,
    val studios: List<String> = emptyList(),
    val tagline: String? = null,
    val cast: List<OfflinePersonInfo> = emptyList(),
    // Epoch millis the offline_media row was created (download date).
    val createdAt: Long = 0L,
)

/**
 * A person (actor/crew) persisted for offline detail screens. Stored as a
 * JSON column on [OfflineMediaItem]; decoded at read time.
 */
@Immutable
@Serializable
data class OfflinePersonInfo(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String = "Actor",
    val imageTag: String? = null,
    val blurHash: String? = null,
)

/**
 * Adapts an [OfflineMediaItem] into a [MediaItem] so the shared online card
 * components ([PosterCard], [WideMediaCard], [PersonItem], …) can render
 * offline content unchanged. Only fields present on the offline model are
 * populated; the rest use the [MediaItem] defaults.
 */
fun OfflineMediaItem.toMediaItem(): MediaItem = MediaItem(
    id = id,
    name = name,
    originalTitle = originalTitle,
    overview = overview,
    mediaType = mediaType,
    year = year,
    communityRating = communityRating,
    officialRating = officialRating,
    runTimeTicks = runTimeTicks,
    playbackPositionTicks = playbackPositionTicks,
    isPlayed = isPlayed,
    premiereDate = null,
    genres = genres,
    studios = studios,
    parentId = null,
    seriesId = seriesId,
    seasonId = seasonId,
    seriesName = seriesName,
    seasonName = seasonName,
    episodeNumber = episodeNumber,
    seasonNumber = seasonNumber,
    childCount = if (childCount > 0) childCount else null,
    lastPlayedDate = lastPlayedDate,
    blurHashes = ImageBlurHashes(primary = blurHashPrimary, backdrop = blurHashBackdrop),
)
