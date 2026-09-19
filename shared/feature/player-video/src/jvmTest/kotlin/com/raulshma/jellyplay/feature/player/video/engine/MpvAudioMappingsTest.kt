package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins for the pure mpv option/property mapping tables ([MpvAudioMappings.kt])
 * the Android `MpvPlayerEngine` applies at both its load-time `setOptionString`
 * and live `setPropertyString` sites — the arms are pinned verbatim (including
 * the zero-copy hwdec ORDER, which is behavior, not formatting) so the two
 * apply sites can never drift from the tested table.
 */
class MpvAudioMappingsTest {

    // ── decoderModeToHwdec ─────────────────────────────────────────────────

    @Test
    fun hwdec_hwPreferred_listsZeroCopyFirst() {
        // Order is load-bearing: mpv picks the first entry that inits, so a
        // copy-first string silently forces the GPU→CPU→GPU slow path.
        assertEquals("mediacodec,mediacodec-copy,no", decoderModeToHwdec(DecoderMode.HW_PREFERRED))
    }

    @Test
    fun hwdec_hwOnly_keepsCopyFallbackWithoutSoftware() {
        assertEquals("mediacodec,mediacodec-copy", decoderModeToHwdec(DecoderMode.HW_ONLY))
    }

    @Test
    fun hwdec_swOnly_disablesHardwareDecoding() {
        assertEquals("no", decoderModeToHwdec(DecoderMode.SW_ONLY))
    }

    // ── channelMixModeToAudioChannels ──────────────────────────────────────

    @Test
    fun channelMix_disabled_fallsBackToAutoForEveryMode() {
        ChannelMixMode.entries.forEach { mode ->
            assertEquals("auto", channelMixModeToAudioChannels(mode, enabled = false), "mode $mode")
        }
    }

    @Test
    fun channelMix_stereoDownmix_mapsToStereo() {
        assertEquals("stereo", channelMixModeToAudioChannels(ChannelMixMode.STEREO_DOWNMIX))
    }

    @Test
    fun channelMix_mono_mapsToMono() {
        assertEquals("mono", channelMixModeToAudioChannels(ChannelMixMode.MONO))
    }

    @Test
    fun channelMix_surroundUpmix_mapsTo51() {
        assertEquals("5.1", channelMixModeToAudioChannels(ChannelMixMode.SURROUND_UPMIX))
    }

    @Test
    fun channelMix_auto_mapsToAuto() {
        assertEquals("auto", channelMixModeToAudioChannels(ChannelMixMode.AUTO))
    }

    // ── audioNormalizationModeToAfFilter ───────────────────────────────────

    @Test
    fun afFilter_dynamic_isCompressor() {
        assertEquals(
            "acompressor=ratio=3:threshold=0.05:attack=10:release=200",
            audioNormalizationModeToAfFilter(AudioNormalizationMode.DYNAMIC),
        )
    }

    @Test
    fun afFilter_trackAndAlbum_shareLoudnorm() {
        assertEquals(
            "loudnorm=I=-23:LRA=7:tp=-1",
            audioNormalizationModeToAfFilter(AudioNormalizationMode.TRACK),
        )
        assertEquals(
            "loudnorm=I=-23:LRA=7:tp=-1",
            audioNormalizationModeToAfFilter(AudioNormalizationMode.ALBUM),
        )
    }

    @Test
    fun afFilter_none_isOmittedFromTheChain() {
        assertNull(audioNormalizationModeToAfFilter(AudioNormalizationMode.NONE))
    }
}
