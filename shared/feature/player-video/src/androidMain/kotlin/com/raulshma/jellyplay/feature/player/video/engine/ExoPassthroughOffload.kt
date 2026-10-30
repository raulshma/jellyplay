package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.HdrFormatPreference

/**
 * The pure decision half of ExoPlayerEngine's bitstream passthrough (audio
 * offload) posture and the [HdrFormatPreference] MIME ordering — extracted so
 * both tables stay androidHostTest-pinnable without an engine instance (the
 * `selectedTextTrackIsAss` precedent: top-level, no android types beyond
 * media3 constants).
 *
 * ## Bitstream passthrough via offload
 *
 * Media3 has no ExoPlayer-level "passthrough" switch: bitstream output rides
 * the audio OFFLOAD path — the sink opens an offload (or direct)
 * `AudioTrack` for the encoded stream and the device's audio HAL bitstreams
 * it to the receiver. Two levers compose:
 *
 *  - the SINK must be offload-capable: in media3 1.11 the per-format offload
 *    support is resolved intrinsically (AudioOutputProvider → platform
 *    AudioTrack checks, API 29+), so capability means the sink is built
 *    WITHOUT the PCM in-sink AudioProcessor chain (audio processors force
 *    the decode-to-PCM path and are incompatible with offload; the engine's
 *    `buildAudioSink` override owns that split), and
 *  - the TRACK SELECTION must ask for it ([TrackSelectionParameters.AudioOffloadPreferences]):
 *    REQUIRED when the passthrough gate is active, else the user's plain
 *    offload pref (or disabled) — media3's renderer sets the sink's offload
 *    mode from the selection.
 *
 * Platform reality this table encodes honestly: offload (and direct
 * bitstream playback) exists on API 29+, and even there the endpoint's
 * offload/direct support is per-device and per-codec — media3's track
 * selector consults the platform at selection time and the renderer falls
 * back to the decode path when the sink reports the format unsupported with
 * ENABLED (REQUIRED surfaces a track-selection failure instead — the honest
 * "this device cannot honor passthrough for this stream" signal). The
 * capability matrix keeps `supportsAudioPassthrough = false` for the Exo
 * engine for exactly this reason: the availability is runtime-gated by the
 * platform, not a universally-honored engine feature (mpv/VLC own the
 * always-capable rows), so the UI's passthrough toggle stays on the engines
 * that always deliver it.
 */
internal object ExoPassthroughOffload {

    /** The media3 audio MIME → passthrough-codec vocabulary mapping. */
    fun codecForMime(mime: String?): AudioPassthroughCodec? = when (mime) {
        MimeTypes.AUDIO_AC3 -> AudioPassthroughCodec.AC3
        MimeTypes.AUDIO_E_AC3 -> AudioPassthroughCodec.EAC3
        MimeTypes.AUDIO_DTS -> AudioPassthroughCodec.DTS
        MimeTypes.AUDIO_DTS_HD -> AudioPassthroughCodec.DTS_HD
        MimeTypes.AUDIO_TRUEHD -> AudioPassthroughCodec.TRUEHD
        else -> null
    }

    /**
     * The track-selector offload mode for a session:
     *
     *  - passthrough NOT requested → the user's plain offload pref (disabled
     *    when unset);
     *  - passthrough requested → REQUIRED while the ACTIVE audio codec is
     *    unknown (`mime == null` — load time and the transient between
     *    selections; the tracks-changed reconciliation re-pushes) or in the
     *    per-codec allow-list, else the plain pref again (the codec is
     *    explicitly excluded — decode-to-PCM stands).
     *
     * The PCM in-sink effects chain is incompatible with offload: when the
     * REQUIRED posture is active the engine builds its sink without it (see
     * the class KDoc) — the passthrough session forgoes the in-sink DSP,
     * which is the point of bitstreaming.
     */
    fun offloadMode(
        passthroughRequested: Boolean,
        passthroughCodecs: Set<AudioPassthroughCodec>,
        activeAudioMime: String?,
        configuredMode: ExoAudioOffloadMode,
    ): Int {
        val prefMode = when (configuredMode) {
            ExoAudioOffloadMode.DISABLED -> null
            ExoAudioOffloadMode.ENABLED ->
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            ExoAudioOffloadMode.REQUIRED ->
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_REQUIRED
        }
        if (!passthroughRequested) {
            return prefMode ?: TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        val codec = codecForMime(activeAudioMime)
        val allowed = codec == null || codec in passthroughCodecs
        return if (allowed) {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_REQUIRED
        } else {
            prefMode ?: TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
    }

    /**
     * The [HdrFormatPreference] MIME ordering for the track selector — the
     * delivery-posture fold over `setPreferredVideoMimeTypes`. MIME-level
     * preference is an ORDERING (the selector prefers the listed families in
     * order; nothing is hard-restricted — a DV-less device still plays the
     * HEVC/HDR10 rendition):
     *
     *  - [HdrFormatPreference.DOLBY_VISION_ONLY] → DV only;
     *  - [HdrFormatPreference.HDR10_ONLY] → HEVC (HDR10 has no distinct
     *    MIME — HEVC is its carrier, the transfer rides the color metadata);
     *  - [HdrFormatPreference.PREFER_DV_OVER_HDR10] → DV then HEVC;
     *  - [HdrFormatPreference.SDR_ONLY] → the base list unchanged: SDR is a
     *    transfer function, not a MIME, so no ordering can express it here —
     *    that posture is the transcode side's (the device profile's codec
     *    restrictions), with the GL tone-mapping path as the player-side
     *    companion;
     *  - `null` / [HdrFormatPreference.AUTO] → the base list unchanged (the
     *    engine's default negotiation stands).
     */
    fun preferredMimeTypes(pref: HdrFormatPreference?, base: List<String>): List<String> = when (pref) {
        HdrFormatPreference.DOLBY_VISION_ONLY -> listOf(MimeTypes.VIDEO_DOLBY_VISION)
        HdrFormatPreference.HDR10_ONLY -> listOf(MimeTypes.VIDEO_H265)
        HdrFormatPreference.PREFER_DV_OVER_HDR10 ->
            listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265)
        HdrFormatPreference.SDR_ONLY, HdrFormatPreference.AUTO, null -> base
    }
}
