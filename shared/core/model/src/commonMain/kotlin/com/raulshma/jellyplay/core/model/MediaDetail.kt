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
    /** Server filesystem path (wire `Path`). Books have no MediaSources —
     *  this is the only place their file format is knowable. */
    val path: String? = null,
    /** Reading/playback progress from UserData, denested so surfaces holding
     *  only the detail don't reach through [item]. 0 = no position. Books
     *  store page-based progress as `pageIndex × 10,000` ticks. */
    val playbackPositionTicks: Long = 0L,
    val isPlayed: Boolean = false,
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

/**
 * The server's media-source classification (wire `MediaSourceType`).
 * [DEFAULT] is a playable file; [GROUPING] is the synthetic wrapper Jellyfin
 * puts on version-merged items; [PLACEHOLDER] is a not-yet-probed stub. Used
 * by the version pickers so a grouping entry can be labeled instead of
 * silently blending in with real files.
 */
@Immutable
@Serializable
enum class MediaSourceType {
    DEFAULT,
    GROUPING,
    PLACEHOLDER,
}

@Immutable
@Serializable
data class MediaSource(
    val id: String,
    val name: String,
    /** Server classification of this source (Default/Grouping/Placeholder). */
    val type: MediaSourceType = MediaSourceType.DEFAULT,
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
) {
    /** First video stream of this source, or null when it has none. */
    val videoStream: MediaStream?
        get() = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }

    /**
     * Compact quality label ("4K HDR10" / "HD SDR" / "SDR"-style) for this
     * source's video stream, or null when the source has no video stream.
     * Single owner of the 4K/HD/SD bucket + range formatting shared by the
     * detail-screen badges (`mediaQualityLabel` in feature/details delegates
     * here) and the version pickers.
     */
    fun qualityLabel(): String? = videoStream?.let { mediaQualityLabel(it) }

    /**
     * Display label for a version picker row: the server-assigned version
     * name when present, else the derived quality label, else the container,
     * else the source id (stable last resort). Pure → directly unit-testable.
     */
    fun versionLabel(): String =
        name.takeIf { it.isNotBlank() }
            ?: qualityLabel()
            ?: container?.takeIf { it.isNotBlank() }
            ?: id
}

/**
 * The preferred version's source when [preferredId] still exists among the
 * detail's sources, else the server's default (first) source, else null —
 * the single owner of the "preferred id validated, else first" fold the
 * detail screen's play button and the player's preferred-version memory
 * each inlined.
 */
fun MediaDetail.preferredMediaSource(preferredId: String?): MediaSource? =
    preferredId?.let { id -> mediaSources.firstOrNull { it.id == id } }
        ?: mediaSources.firstOrNull()

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

    /**
     * Fallback display name for handing a stream to a player: server display
     * title, else stream title, else language, else "Unknown". The single
     * owner of the fold the external-player hand-off (app MainViewModel) and
     * the in-app side-load builder (PlayerSessionManager) each inlined.
     */
    val displayName: String
        get() = displayTitle ?: title ?: language ?: "Unknown"
}

@Immutable
@Serializable
enum class StreamType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    EMBEDDED_IMAGE,
}

/**
 * Compact quality label ("<bucket> <RANGE>") for a video stream — the
 * 4K/HD/SD bucket from height plus the HDR/SDR/Dolby Vision suffix
 * ("4K HDR10", "HD SDR", "Auto SDR" when the stream carries no height).
 * Pure → directly unit-testable. The single owner of this formatting:
 * [MediaSource.qualityLabel] derives from it and the detail-screen badge
 * helper (feature/details `mediaQualityLabel`) delegates to it, so the
 * badges and the version pickers can never drift apart.
 */
fun mediaQualityLabel(video: MediaStream?): String = buildString {
    val bucket = video?.height?.let { h ->
        when {
            h >= 2160 -> "4K"
            h >= 720 -> "HD"
            else -> "SD"
        }
    } ?: "Auto"
    append(bucket)
    append(" ")
    val range = video?.videoDoViTitle
        ?: video?.videoRangeType
        ?: video?.videoRange
        ?: "SDR"
    append(range.uppercase())
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
