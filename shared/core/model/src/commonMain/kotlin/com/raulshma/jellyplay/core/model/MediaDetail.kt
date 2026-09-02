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
    /** Jellyfin chapter image tag; pairs with the list index to resolve the
     *  `/Items/{itemId}/Images/Chapter/{index}` thumbnail. */
    val imageTag: String? = null,
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
     * True whenever a primary portrait is fetchable and worth preloading /
     * persisting for offline — i.e. the person carries a `primaryImageTag`,
     * regardless of type. Centralizes the predicate the offline cast-preload +
     * cast-image-persist paths share so the eligible set stays consistent with
     * the dedicated Cast & Crew screen, which renders any tagged portrait.
     *
     * Originally restricted to Actor/Director; broadened to include crew types
     * (Writer/Producer/Composer/GuestStar/…) that carry a `primaryImageTag`, so
     * the offline main detail cast row no longer suppresses crew portraits.
     * Equivalent to [hasPortrait]; both now agree on "a tag means a portrait".
     */
    fun hasCastImage(): Boolean = hasPortrait()

    /**
     * True whenever a primary portrait is fetchable, regardless of person type.
     * Used by the dedicated Cast & Crew screen to render portraits for crew
     * types too, as long as a primary image tag is present.
     */
    fun hasPortrait(): Boolean = !primaryImageTag.isNullOrBlank()
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
) {
    /**
     * True for subtitle streams JellyPlay can bundle offline: external sidecars
     * or embedded subs exposing a server delivery URL. Centralizes the predicate
     * the download writer ([com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter.downloadExternalSubtitles]),
     * the offline sync comparator, and the pre-download picker UI each
     * re-implemented inline — and could silently drift apart when one changed.
     */
    val isBundleableSubtitle: Boolean
        get() = type == StreamType.SUBTITLE && (isExternal || !deliveryUrl.isNullOrBlank())
}

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
