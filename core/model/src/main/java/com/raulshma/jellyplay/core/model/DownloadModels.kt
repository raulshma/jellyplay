package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DownloadItem(
    val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: MediaType,
    val downloadPath: String,
    val downloadUrl: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val speedBytesPerSec: Long = 0L,
    val mediaSourceId: String? = null,
    val imageUrl: String? = null,
    val imageBlurHash: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val errorMessage: String? = null,
    val priority: Int = 0,
    /**
     * Original container format as reported by the Jellyfin MediaSource
     * (e.g. "mkv", "mp4", "ts"). Used to attach the correct MIME type to the
     * player engine at playback time so the right extractor is selected for
     * files whose on-disk extension does not match their actual container.
     */
    val container: String? = null,
)

@Immutable
@Serializable
enum class DownloadStatus {
    PENDING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Immutable
@Serializable
enum class DownloadQuality(val displayName: String) {
    ORIGINAL("Original"),
    HIGH_1080P("High (1080p transcoded)"),
    MEDIUM_720P("Medium (720p)"),
    LOW_480P("Low (480p)"),
}
