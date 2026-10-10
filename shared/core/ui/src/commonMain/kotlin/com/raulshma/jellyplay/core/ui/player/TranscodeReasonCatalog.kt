package com.raulshma.jellyplay.core.ui.player

import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_anamorphic
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_bit_depth
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_bitrate
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_channels
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_codec_not_supported
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_is_external
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_profile
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_audio_sample_rate
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_container_bitrate
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_container_not_supported
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_direct_play_error
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_decoder
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_engine
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_quality
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_interlaced
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_ref_frames
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_secondary_audio
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_stream_count
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_subtitle_codec
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_unknown
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_unknown_audio_info
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_unknown_video_info
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_bit_depth
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_bitrate
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_codec_not_supported
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_codec_tag
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_framerate
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_level
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_profile
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_range
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_video_resolution
import org.jetbrains.compose.resources.StringResource

/**
 * The server-reported reasons a source is being transcoded, read from the
 * live session's `TranscodingInfo.TranscodeReasons` (the SDK's PlaybackInfo
 * response does not expose reason tokens). One app enum entry per SDK
 * `TranscodeReason` token, plus a catch-all for anything the server adds
 * faster than this map.
 *
 * Raw tokens arrive in either the SDK's SCREAMING_SNAKE enum spelling
 * (`VIDEO_CODEC_NOT_SUPPORTED` — what the session fetch stores) or the wire
 * PascalCase spelling (`VideoCodecNotSupported`);
 * [normalizeReasonToken] normalizes both.
 *
 * Lives in commonMain (moved out of the androidMain formatter) so BOTH
 * platforms share the one token vocabulary — see [TranscodeReasonCatalog].
 */
enum class TranscodeReasonKind {
    CONTAINER_NOT_SUPPORTED,
    VIDEO_CODEC_NOT_SUPPORTED,
    AUDIO_CODEC_NOT_SUPPORTED,
    VIDEO_BITRATE_NOT_SUPPORTED,
    AUDIO_BITRATE_NOT_SUPPORTED,
    CONTAINER_BITRATE_EXCEEDS_LIMIT,
    VIDEO_BIT_DEPTH_NOT_SUPPORTED,
    AUDIO_BIT_DEPTH_NOT_SUPPORTED,
    AUDIO_CHANNELS_NOT_SUPPORTED,
    AUDIO_PROFILE_NOT_SUPPORTED,
    AUDIO_SAMPLE_RATE_NOT_SUPPORTED,
    AUDIO_IS_EXTERNAL,
    SECONDARY_AUDIO_NOT_SUPPORTED,
    INTERLACED_VIDEO_NOT_SUPPORTED,
    ANAMORPHIC_VIDEO_NOT_SUPPORTED,
    REF_FRAMES_NOT_SUPPORTED,
    VIDEO_CODEC_TAG_NOT_SUPPORTED,
    VIDEO_FRAMERATE_NOT_SUPPORTED,
    VIDEO_LEVEL_NOT_SUPPORTED,
    VIDEO_PROFILE_NOT_SUPPORTED,
    VIDEO_RANGE_TYPE_NOT_SUPPORTED,
    VIDEO_RESOLUTION_NOT_SUPPORTED,
    SUBTITLE_CODEC_NOT_SUPPORTED,
    STREAM_COUNT_EXCEEDS_LIMIT,
    DIRECT_PLAY_ERROR,
    UNKNOWN_VIDEO_STREAM_INFO,
    UNKNOWN_AUDIO_STREAM_INFO,
}

/** Spelling-agnostic token normalization: strip non-alphanumerics, lowercase. */
fun String.normalizeReasonToken(): String =
    filter { it.isLetterOrDigit() }.lowercase()

/**
 * The blank-filter + spelling-dedupe prelude every transcode-reason consumer
 * runs before [TranscodeReasonCatalog.lookup] — one copy so all surfaces
 * merge casing duplicates identically.
 */
fun List<String>.distinctTranscodeReasons(): List<String> =
    filter { it.isNotBlank() }.distinctBy { it.normalizeReasonToken() }

/**
 * The ONE token → compose-resource lookup table for transcode reasons,
 * commonMain so both platforms localize. Every consumer resolves through it —
 * player-video's `rememberFormattedTranscodeReasons` (composable
 * `stringResource`), player-live's `TranscodeReasonsRenderer` and the admin
 * transcodes monitor (suspend `getString` / `stringResource` at their own
 * edges) — so adding a reason is one enum entry plus its string resources,
 * and every surface renders identical text.
 */
object TranscodeReasonCatalog {

    /** Localized strings per kind — one table instead of parallel switches. */
    data class ReasonStrings(
        val explanation: StringResource,
        val hint: StringResource? = null,
    )

    val unknownTemplate: StringResource = Res.string.transcode_reason_unknown

    private val stringsByKind: Map<TranscodeReasonKind, ReasonStrings> = mapOf(
        TranscodeReasonKind.CONTAINER_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_container_not_supported,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_CODEC_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_codec_not_supported,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.AUDIO_CODEC_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_codec_not_supported,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_BITRATE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_bitrate,
            Res.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.AUDIO_BITRATE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_bitrate,
            Res.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.CONTAINER_BITRATE_EXCEEDS_LIMIT to ReasonStrings(
            Res.string.transcode_reason_container_bitrate,
            Res.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.VIDEO_BIT_DEPTH_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_bit_depth,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_BIT_DEPTH_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_bit_depth,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_CHANNELS_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_channels,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_PROFILE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_profile,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_SAMPLE_RATE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_audio_sample_rate,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_IS_EXTERNAL to ReasonStrings(
            Res.string.transcode_reason_audio_is_external,
        ),
        TranscodeReasonKind.SECONDARY_AUDIO_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_secondary_audio,
        ),
        TranscodeReasonKind.INTERLACED_VIDEO_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_interlaced,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.ANAMORPHIC_VIDEO_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_anamorphic,
        ),
        TranscodeReasonKind.REF_FRAMES_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_ref_frames,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_CODEC_TAG_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_codec_tag,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_FRAMERATE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_framerate,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_LEVEL_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_level,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_PROFILE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_profile,
            Res.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_RANGE_TYPE_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_range,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_RESOLUTION_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_video_resolution,
            Res.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.SUBTITLE_CODEC_NOT_SUPPORTED to ReasonStrings(
            Res.string.transcode_reason_subtitle_codec,
            Res.string.transcode_reason_hint_subtitle,
        ),
        TranscodeReasonKind.STREAM_COUNT_EXCEEDS_LIMIT to ReasonStrings(
            Res.string.transcode_reason_stream_count,
        ),
        TranscodeReasonKind.DIRECT_PLAY_ERROR to ReasonStrings(
            Res.string.transcode_reason_direct_play_error,
        ),
        TranscodeReasonKind.UNKNOWN_VIDEO_STREAM_INFO to ReasonStrings(
            Res.string.transcode_reason_unknown_video_info,
        ),
        TranscodeReasonKind.UNKNOWN_AUDIO_STREAM_INFO to ReasonStrings(
            Res.string.transcode_reason_unknown_audio_info,
        ),
    )

    private val byNormalizedKey: Map<String, ReasonStrings> =
        TranscodeReasonKind.entries.associateBy { it.name.normalizeReasonToken() }
            .mapValues { (_, kind) -> stringsByKind.getValue(kind) }

    /**
     * Look up the localized strings for a raw server token, or null when the
     * token is unknown (callers keep the raw text visible in that case).
     */
    fun lookup(rawReason: String): ReasonStrings? =
        byNormalizedKey[rawReason.normalizeReasonToken()]

    /**
     * The canonical one-reason render: explanation, then the optional hint
     * on a second line. Every consumer surface (stats overlay, error
     * dialogs, the live error conveyor, admin monitor) renders identical
     * text through this one shape.
     */
    fun renderedLine(explanation: String, hint: String?): String =
        if (hint != null) "$explanation\n$hint" else explanation
}
