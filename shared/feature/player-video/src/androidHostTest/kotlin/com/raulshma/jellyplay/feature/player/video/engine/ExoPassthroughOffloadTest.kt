package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the pure passthrough/HDR-posture tables ([ExoPassthroughOffload]) the
 * ExoPlayerEngine track selection and sink split consume — the mode
 * composition (plain offload pref vs the passthrough gate), the per-codec
 * MIME mapping, and the [HdrFormatPreference] MIME ordering.
 */
@RunWith(RobolectricTestRunner::class)
class ExoPassthroughOffloadTest {

    private val disabled = TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
    private val enabled = TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
    private val required = TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_REQUIRED

    // ── offloadMode ─────────────────────────────────────────────────────────

    @Test
    fun noPassthrough_thePlainPrefStands() {
        assertEquals(
            disabled,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = false,
                passthroughCodecs = AudioPassthroughCodec.ALL,
                activeAudioMime = MimeTypes.AUDIO_E_AC3,
                configuredMode = ExoAudioOffloadMode.DISABLED,
            ),
        )
        assertEquals(
            enabled,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = false,
                passthroughCodecs = AudioPassthroughCodec.ALL,
                activeAudioMime = MimeTypes.AUDIO_E_AC3,
                configuredMode = ExoAudioOffloadMode.ENABLED,
            ),
        )
    }

    @Test
    fun passthroughRequested_unknownCodec_requiresOffload() {
        // Load time / the transient between selections: the gate holds the
        // REQUIRED posture until the codec resolves.
        assertEquals(
            required,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = true,
                passthroughCodecs = AudioPassthroughCodec.ALL,
                activeAudioMime = null,
                configuredMode = ExoAudioOffloadMode.DISABLED,
            ),
        )
    }

    @Test
    fun passthroughRequested_allowListedCodec_requiresOffload() {
        assertEquals(
            required,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = true,
                passthroughCodecs = setOf(AudioPassthroughCodec.EAC3),
                activeAudioMime = MimeTypes.AUDIO_E_AC3,
                configuredMode = ExoAudioOffloadMode.DISABLED,
            ),
        )
    }

    @Test
    fun passthroughRequested_excludedCodec_fallsBackToThePlainPref() {
        // E-AC3 not in the allow-list: decode-to-PCM stands (the plain pref,
        // disabled when unset).
        assertEquals(
            disabled,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = true,
                passthroughCodecs = setOf(AudioPassthroughCodec.AC3),
                activeAudioMime = MimeTypes.AUDIO_E_AC3,
                configuredMode = ExoAudioOffloadMode.DISABLED,
            ),
        )
        assertEquals(
            enabled,
            ExoPassthroughOffload.offloadMode(
                passthroughRequested = true,
                passthroughCodecs = setOf(AudioPassthroughCodec.AC3),
                activeAudioMime = MimeTypes.AUDIO_E_AC3,
                configuredMode = ExoAudioOffloadMode.ENABLED,
            ),
        )
    }

    // ── codecForMime ────────────────────────────────────────────────────────

    @Test
    fun codecForMime_mapsTheBitstreamFamilies_andNullsThePcmOnes() {
        assertEquals(AudioPassthroughCodec.AC3, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_AC3))
        assertEquals(AudioPassthroughCodec.EAC3, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_E_AC3))
        assertEquals(AudioPassthroughCodec.DTS, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_DTS))
        assertEquals(AudioPassthroughCodec.DTS_HD, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_DTS_HD))
        assertEquals(AudioPassthroughCodec.TRUEHD, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_TRUEHD))
        assertEquals(null, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_AAC))
        assertEquals(null, ExoPassthroughOffload.codecForMime(MimeTypes.AUDIO_OPUS))
        assertEquals(null, ExoPassthroughOffload.codecForMime(null))
    }

    // ── preferredMimeTypes (HdrFormatPreference fold) ───────────────────────

    @Test
    fun dvOnly_prefersDolbyVisionAlone() {
        assertEquals(
            listOf(MimeTypes.VIDEO_DOLBY_VISION),
            ExoPassthroughOffload.preferredMimeTypes(HdrFormatPreference.DOLBY_VISION_ONLY, listOf(MimeTypes.VIDEO_H264)),
        )
    }

    @Test
    fun hdr10Only_prefersHevc() {
        // HDR10 has no distinct MIME — HEVC is its carrier.
        assertEquals(
            listOf(MimeTypes.VIDEO_H265),
            ExoPassthroughOffload.preferredMimeTypes(HdrFormatPreference.HDR10_ONLY, listOf(MimeTypes.VIDEO_H264)),
        )
    }

    @Test
    fun preferDvOrdersDolbyVisionThenHevc() {
        assertEquals(
            listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265),
            ExoPassthroughOffload.preferredMimeTypes(HdrFormatPreference.PREFER_DV_OVER_HDR10, emptyList()),
        )
    }

    @Test
    fun sdrOnlyAutoAndUnset_leaveTheBaseList() {
        val base = listOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_AV1)
        assertEquals(base, ExoPassthroughOffload.preferredMimeTypes(HdrFormatPreference.SDR_ONLY, base))
        assertEquals(base, ExoPassthroughOffload.preferredMimeTypes(HdrFormatPreference.AUTO, base))
        assertEquals(base, ExoPassthroughOffload.preferredMimeTypes(null, base))
    }
}
