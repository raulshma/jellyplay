package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

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
    val mediaSourceId: String? = null,
    val imageUrl: String? = null,
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
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val maxCacheSizeMb: Int = 500,
    val autoDeleteCache: Boolean = true,
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val kidsModeEnabled: Boolean = false,
    val kidsModeMaxRating: String = "PG",
)

@Serializable
enum class PlayerType {
    INTERNAL,
    EXTERNAL,
}

@Serializable
data class SubtitleStyle(
    val fontSize: Int = 24,
    val fontColor: SubtitleColor = SubtitleColor.WHITE,
    val backgroundColor: SubtitleColor = SubtitleColor.BLACK,
    val backgroundOpacity: Float = 0.6f,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.NONE,
    val edgeColor: SubtitleColor = SubtitleColor.BLACK,
    val offsetMs: Long = 0L,
)

@Serializable
enum class SubtitleColor(val value: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFFF00.toInt()),
    GREEN(0xFF00FF00.toInt()),
    CYAN(0xFF00FFFF.toInt()),
    RED(0xFFFF0000.toInt()),
    BLACK(0xFF000000.toInt()),
    BLUE(0xFF0000FF.toInt()),
}

@Serializable
enum class SubtitleEdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
}

@Serializable
enum class StreamingQuality {
    AUTO,
    LOW_360P,
    SD_480P,
    HD_720P,
    FHD_1080P,
    UHD_4K,
}
