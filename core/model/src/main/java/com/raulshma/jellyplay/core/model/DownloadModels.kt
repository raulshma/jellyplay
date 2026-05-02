package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadItem(
    val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: MediaType,
    val downloadPath: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val mediaSourceId: String? = null,
)

@Serializable
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class UserPreferences(
    val preferredPlayer: PlayerType = PlayerType.INTERNAL,
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val dynamicTheming: Boolean = true,
    val useBottomNav: Boolean = true,
)

@Serializable
enum class PlayerType {
    INTERNAL,
    EXTERNAL,
}
