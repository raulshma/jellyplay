package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.network.library.MediaSourceInfoWire
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal Jellyfin wire DTOs for the Phase W wasm playback client
 * (docs/kmp-migration-plan.md §Phase W chunk 2), following the chunk-1
 * `auth/AuthWireDto.kt` pattern. Response mapping semantics mirror the
 * jvmShared `PlaybackApiClientImpl` + `JellyfinDtoMappers.toMediaSource` /
 * `toMediaStream` (via `library/` mappers, shared with the detail path).
 */
@Serializable
data class PlaybackInfoRequestDtoWire(
    @SerialName("UserId") val userId: String? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    /**
     * wasm v1 cut: no codec-constraining DeviceProfile is sent (no codec
     * negotiation exists until HtmlVideoEngine lands in a later Phase W
     * chunk and documents the web `<video>` profile) — the flag table in
     * `resolveWasmPlaybackFlags` still honors PlaybackMode/LiveStreamOption.
     */
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean? = null,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean? = null,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean? = null,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean? = null,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean? = null,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean? = null,
)

/** Wire form of the `POST /Items/{id}/PlaybackInfo` response. */
@Serializable
data class PlaybackInfoResponseDtoWire(
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoWire> = emptyList(),
)

/**
 * Wire form of `PlaybackStartInfo` (the fields the JVM impl populates).
 * CanSeek/IsPaused/IsMuted/PlayMethod/RepeatMode/PlaybackOrder carry no
 * Kotlin defaults, exactly like the SDK model's required properties — so
 * they are ALWAYS serialized (kotlinx drops only default-VALUED optional
 * properties), matching the JVM wire where the SDK encodes them even at
 * default values.
 */
@Serializable
data class PlaybackStartInfoDtoWire(
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("IsMuted") val isMuted: Boolean,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("RepeatMode") val repeatMode: String,
    @SerialName("PlaybackOrder") val playbackOrder: String,
)

/**
 * Wire form of `PlaybackProgressInfo` (start fields + PositionTicks); the
 * same no-defaults rule as [PlaybackStartInfoDtoWire] applies — every field
 * below is always serialized, like the SDK's required properties.
 */
@Serializable
data class PlaybackProgressInfoDtoWire(
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("IsMuted") val isMuted: Boolean,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("RepeatMode") val repeatMode: String,
    @SerialName("PlaybackOrder") val playbackOrder: String,
)

/** Wire form of `PlaybackStopInfo`; `failed` is required (always serialized). */
@Serializable
data class PlaybackStopInfoDtoWire(
    @SerialName("ItemId") val itemId: String,
    @SerialName("SessionId") val sessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("Failed") val failed: Boolean,
)

/** Wire `PlayMethod` serial name for the app [com.raulshma.jellyplay.core.model.PlayMethod]. */
fun com.raulshma.jellyplay.core.model.PlayMethod.wireName(): String = when (this) {
    com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY -> "DirectPlay"
    com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_STREAM -> "DirectStream"
    com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE -> "Transcode"
}

/** Wire subset of `SessionInfoDto` read by the active-transcode-reasons scan. */
@Serializable
data class SessionInfoDtoWire(
    @SerialName("DeviceId") val deviceId: String? = null,
    @SerialName("NowPlayingItem") val nowPlayingItem: SessionNowPlayingDtoWire? = null,
    @SerialName("TranscodingInfo") val transcodingInfo: TranscodingInfoDtoWire? = null,
)

@Serializable
data class SessionNowPlayingDtoWire(
    @SerialName("Id") val id: String? = null,
)

@Serializable
data class TranscodingInfoDtoWire(
    @SerialName("TranscodeReasons") val transcodeReasons: List<String> = emptyList(),
)

/**
 * Converts a wire `TranscodeReason` serial name ("ContainerNotSupported") to
 * the Kotlin enum-constant name the JVM impl surfaces
 * (`TranscodeReason.name` → "CONTAINER_NOT_SUPPORTED"), so diagnostics
 * strings match across platforms. Underscore before an uppercase letter
 * preceded by a lowercase one — checked on the ORIGINAL string, since the
 * builder content is already uppercased (the SDK serials carry no acronyms,
 * so this reproduces every enum constant name).
 */
fun transcodeReasonName(serialName: String): String = buildString {
    for ((index, ch) in serialName.withIndex()) {
        if (ch.isUpperCase() && index > 0 && !serialName[index - 1].isUpperCase()) append('_')
        append(ch.uppercase())
    }
}

/** Wire form of `MediaSegmentDto` (`GET /MediaSegments/{itemId}`). */
@Serializable
data class MediaSegmentDtoWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("ItemId") val itemId: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0,
)

/** Wire form of `MediaSegmentDtoQueryResult` (the `/MediaSegments` wrapper). */
@Serializable
data class MediaSegmentQueryResultDtoWire(
    @SerialName("Items") val items: List<MediaSegmentDtoWire> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

/** Wire form of `GET /GetUtcTime`. Dates stay raw ISO strings (wasm delta: no zone shift). */
@Serializable
data class UtcTimeDtoWire(
    @SerialName("RequestReceptionTime") val requestReceptionTime: String? = null,
    @SerialName("ResponseTransmissionTime") val responseTransmissionTime: String? = null,
)
