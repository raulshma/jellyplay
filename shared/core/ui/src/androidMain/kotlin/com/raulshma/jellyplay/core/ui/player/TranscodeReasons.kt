package com.raulshma.jellyplay.core.ui.player

import android.content.Context
import androidx.annotation.StringRes
import com.raulshma.jellyplay.shared.core.ui.R

/** One reason resolved to localized text: the plain-language explanation of
 *  why the server transcoded, plus an optional actionable remedy hint. */
data class FormattedTranscodeReason(
    val raw: String,
    /** Pre-localized explanation; unknown tokens embed their raw server text. */
    val explanation: String,
    /** Remedy pointing at an in-app control (decoder, quality, engine…);
     *  `null` when no user action can avoid this transcode. */
    val hint: String?,
) {
    /** The canonical multi-line render: explanation, then the hint on a
     *  second line when present (error dialog, Live TV error detail). */
    val renderedText: String
        get() = if (hint != null) "$explanation\n$hint" else explanation
}

/**
 * Android resolution for the shared transcode-reason table:
 * [TranscodeReasonKind] and token normalization live in commonMain's
 * `TranscodeReasonCatalog.kt` (the ONE token→key table, which the desktop
 * seam reads via compose-resources so both platforms localize). This
 * androidMain formatter mirrors that table with `R.string` ids because its
 * callers resolve synchronously through a `Context`, which compose-resources
 * cannot serve off the main thread.
 *
 * Maps raw server transcode-reason tokens to localized explanations and
 * remedy hints, shared by the video stats overlay, the playback error
 * dialog, and the Live TV fallback banner (hence core/ui — the only player
 * surface both player modules share). Strings are resolved here, once, so
 * every surface renders identical text (including the unknown-token
 * fallback, which interpolates the raw server token).
 */
object TranscodeReasonsFormatter {

    private val byNormalizedKey: Map<String, TranscodeReasonKind> =
        TranscodeReasonKind.entries.associateBy { it.name.normalizeReasonToken() }

    /** Localized strings per kind — one table instead of parallel switches. */
    private data class ReasonStrings(
        @StringRes val explanationRes: Int,
        @StringRes val hintRes: Int? = null,
    )

    private val stringsByKind: Map<TranscodeReasonKind, ReasonStrings> = mapOf(
        TranscodeReasonKind.CONTAINER_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_container_not_supported,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_CODEC_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_codec_not_supported,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.AUDIO_CODEC_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_codec_not_supported,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_BITRATE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_bitrate,
            R.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.AUDIO_BITRATE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_bitrate,
            R.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.CONTAINER_BITRATE_EXCEEDS_LIMIT to ReasonStrings(
            R.string.transcode_reason_container_bitrate,
            R.string.transcode_reason_hint_quality,
        ),
        TranscodeReasonKind.VIDEO_BIT_DEPTH_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_bit_depth,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_BIT_DEPTH_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_bit_depth,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_CHANNELS_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_channels,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_PROFILE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_profile,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_SAMPLE_RATE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_audio_sample_rate,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.AUDIO_IS_EXTERNAL to ReasonStrings(
            R.string.transcode_reason_audio_is_external,
        ),
        TranscodeReasonKind.SECONDARY_AUDIO_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_secondary_audio,
        ),
        TranscodeReasonKind.INTERLACED_VIDEO_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_interlaced,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.ANAMORPHIC_VIDEO_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_anamorphic,
        ),
        TranscodeReasonKind.REF_FRAMES_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_ref_frames,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_CODEC_TAG_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_codec_tag,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_FRAMERATE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_framerate,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_LEVEL_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_level,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_PROFILE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_profile,
            R.string.transcode_reason_hint_decoder,
        ),
        TranscodeReasonKind.VIDEO_RANGE_TYPE_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_range,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.VIDEO_RESOLUTION_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_video_resolution,
            R.string.transcode_reason_hint_engine,
        ),
        TranscodeReasonKind.SUBTITLE_CODEC_NOT_SUPPORTED to ReasonStrings(
            R.string.transcode_reason_subtitle_codec,
            R.string.transcode_reason_hint_subtitle,
        ),
        TranscodeReasonKind.STREAM_COUNT_EXCEEDS_LIMIT to ReasonStrings(
            R.string.transcode_reason_stream_count,
        ),
        TranscodeReasonKind.DIRECT_PLAY_ERROR to ReasonStrings(
            R.string.transcode_reason_direct_play_error,
        ),
        TranscodeReasonKind.UNKNOWN_VIDEO_STREAM_INFO to ReasonStrings(
            R.string.transcode_reason_unknown_video_info,
        ),
        TranscodeReasonKind.UNKNOWN_AUDIO_STREAM_INFO to ReasonStrings(
            R.string.transcode_reason_unknown_audio_info,
        ),
    )

    fun format(context: Context, rawReasons: List<String>): List<FormattedTranscodeReason> =
        rawReasons
            .filter { it.isNotBlank() }
            .distinctBy { it.normalizeReasonToken() }
            .map { raw ->
                val strings = byNormalizedKey[raw.normalizeReasonToken()]
                    ?.let { stringsByKind[it] }
                // Unknown token: keep it visible rather than hide a reason
                // the server considered important.
                FormattedTranscodeReason(
                    raw = raw,
                    explanation = strings?.let { context.getString(it.explanationRes) }
                        ?: context.getString(R.string.transcode_reason_unknown, raw),
                    hint = strings?.hintRes?.let { context.getString(it) },
                )
            }
}
