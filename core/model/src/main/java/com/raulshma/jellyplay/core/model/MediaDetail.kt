package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class MediaDetail(
    val item: MediaItem,
    val backdropImageTag: String? = null,
    val posterImageTag: String? = null,
    val logoImageTag: String? = null,
    val sortName: String? = null,
    val customRating: String? = null,
    val criticRating: Float? = null,
    val taglines: List<String> = emptyList(),
    val productionLocations: List<String> = emptyList(),
    val lockData: Boolean = false,
    val lockedFields: List<String> = emptyList(),
    val status: String? = null,
    val airDays: List<String> = emptyList(),
    val airTime: String? = null,
    val displayOrder: String? = null,
    val preferredMetadataLanguage: String? = null,
    val preferredMetadataCountryCode: String? = null,
    val imageInfos: List<ImageInfo> = emptyList(),
    val dateCreated: String? = null,
    val overviewImageTag: String? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val people: List<PersonInfo> = emptyList(),
    val relatedItems: List<MediaItem> = emptyList(),
    val mediaSources: List<MediaSource> = emptyList(),
    val externalUrls: List<ExternalUrl> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val studios: List<StudioInfo> = emptyList(),
    val tagItems: List<TagInfo> = emptyList(),
)

@Immutable
@Serializable
data class ChapterInfo(
    val name: String,
    val startPositionTicks: Long,
    val imageDateModified: String? = null,
)

@Immutable
@Serializable
data class PersonInfo(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String,
    val primaryImageTag: String? = null,
    val primaryBlurHash: String? = null,
) {
    /**
     * True for person types Jellyfin treats as on-screen/credited cast (Actor,
     * Director) that carry a primary image. Centralizes the predicate duplicated
     * by the offline cast-preload + cast-image-persist paths so the eligible set
     * stays consistent. Guest stars and crew types without a primary image are
     * excluded (no fetchable portrait).
     */
    fun hasCastImage(): Boolean =
        (type == "Actor" || type == "Director") && !primaryImageTag.isNullOrBlank()
}

@Immutable
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
    /** Server-issued live-stream id for Live TV channels; must be appended to
     *  the stream URL as `LiveStreamId` so the tuner session is opened. */
    val liveStreamId: String? = null,
    /** True when the server requires the live stream to be explicitly opened
     *  before playback (Live TV). Drives the `LiveStreamId` query param. */
    val requiresOpening: Boolean = false,
    val path: String? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val trickplayInfo: TrickplayInfo? = null,
)

@Immutable
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
    /** Hearing-impaired / SDH caption flag from the server stream. */
    val isHearingImpaired: Boolean = false,
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

@Immutable
@Serializable
enum class StreamType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    EMBEDDED_IMAGE,
}

@Immutable
@Serializable
data class ExternalUrl(
    val name: String,
    val url: String,
)

@Immutable
@Serializable
data class StudioInfo(
    val name: String,
    val id: String,
)

@Immutable
@Serializable
data class TagInfo(
    val name: String,
    val id: String,
)
