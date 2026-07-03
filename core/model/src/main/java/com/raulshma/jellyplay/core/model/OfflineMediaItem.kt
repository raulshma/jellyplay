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
    // Playback progress (issue #65-A/B). Seeded from server UserData at download
    // time and updated locally as the user watches offline. `playedPercentage` is
    // derived (0–100) and stored so it can be rendered without recomputing.
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double = 0.0,
    val isPlayed: Boolean = false,
    val lastPlayedDate: String? = null,
)
