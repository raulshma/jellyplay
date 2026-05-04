package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaDetail(
    val item: MediaItem,
    val backdropImageTag: String? = null,
    val posterImageTag: String? = null,
    val logoImageTag: String? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val people: List<PersonInfo> = emptyList(),
    val relatedItems: List<MediaItem> = emptyList(),
    val mediaSources: List<MediaSource> = emptyList(),
    val externalUrls: List<ExternalUrl> = emptyList(),
    val studios: List<StudioInfo> = emptyList(),
    val tagItems: List<TagInfo> = emptyList(),
)

@Serializable
data class ChapterInfo(
    val name: String,
    val startPositionTicks: Long,
    val imageDateModified: String? = null,
)

@Serializable
data class PersonInfo(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String,
    val primaryImageTag: String? = null,
    val primaryBlurHash: String? = null,
)

@Serializable
data class MediaSource(
    val id: String,
    val name: String,
    val container: String? = null,
    val size: Long? = null,
    val bitrate: Long? = null,
    val runTimeTicks: Long? = null,
    val supportsTranscoding: Boolean = false,
    val supportsDirectStream: Boolean = false,
    val supportsDirectPlay: Boolean = false,
    val transcodeUrl: String? = null,
    val directStreamUrl: String? = null,
    val path: String? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
)

@Serializable
data class MediaStream(
    val index: Int,
    val type: StreamType,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val bitRate: Long? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val deliveryUrl: String? = null,
    val videoRange: String? = null,
    val videoRangeType: String? = null,
    val realFrameRate: Float? = null,
    val videoDoViTitle: String? = null,
    val audioBitRate: Long? = null,
    val audioSampleRate: Int? = null,
    val keyFrames: List<Long>? = null,
)

@Serializable
enum class StreamType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    EMBEDDED_IMAGE,
}

@Serializable
data class ExternalUrl(
    val name: String,
    val url: String,
)

@Serializable
data class StudioInfo(
    val name: String,
    val id: String,
)

@Serializable
data class TagInfo(
    val name: String,
    val id: String,
)
