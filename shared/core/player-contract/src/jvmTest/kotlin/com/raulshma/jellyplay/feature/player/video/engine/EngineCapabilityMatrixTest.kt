package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlayerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins [EngineCapabilityMatrix] as the single, complete source of truth for
 * per-engine [EngineCapabilities]. These tests fail loudly if a capability is
 * accidentally flipped or a new [PlayerType] is added without a matrix entry.
 */
class EngineCapabilityMatrixTest {

    @Test
    fun forType_coversEveryPlayerType() {
        PlayerType.entries.forEach { type ->
            assertNotNull(EngineCapabilityMatrix.forType(type), "Missing matrix entry for $type")
        }
    }

    @Test
    fun allByType_keysMatchPlayerTypeEntries() {
        assertEquals(PlayerType.entries.toSet(), EngineCapabilityMatrix.allByType.keys)
    }

    @Test
    fun forType_returnsSameConstantInstancePerType() {
        // Delegation must hand out the canonical immutable constant, not a copy.
        assertSame(EngineCapabilityMatrix.EXO_PLAYER, EngineCapabilityMatrix.forType(PlayerType.EXO_PLAYER))
        assertSame(EngineCapabilityMatrix.MPV, EngineCapabilityMatrix.forType(PlayerType.MPV))
        assertSame(EngineCapabilityMatrix.LIBVLC, EngineCapabilityMatrix.forType(PlayerType.LIBVLC))
        assertSame(EngineCapabilityMatrix.EXTERNAL, EngineCapabilityMatrix.forType(PlayerType.EXTERNAL))
    }

    @Test
    fun external_isAllFalse() {
        val caps = EngineCapabilityMatrix.EXTERNAL
        listOf(
            caps.supportsPip, caps.supportsMiniMode, caps.supportsCues,
            caps.supportsAudioDelay, caps.supportsSubtitleDelay, caps.supportsAudioPassthrough,
            caps.supportsSubtitleStyle, caps.supportsSubtitleVerticalPosition,
            caps.supportsDialogueBoost, caps.supportsNightMode, caps.supportsAudioNormalization,
            caps.supportsChannelMixing, caps.supportsVideoFilters,
            caps.supportsLiveQualitySwitch, caps.supportsBandwidthEstimate,
            caps.supportsAssOverride, caps.supportsAssStyleOverride, caps.supportsFontFamily,
            caps.supportsFreeFormColors, caps.supportsBorderStyles,
            caps.supportsSecondarySubtitles, caps.supportsScreenshot,
            caps.supportsImageSubtitles,
            caps.supportsDeinterlace,
            caps.supportsSpatialAudio,
            caps.supportsHdrPassthrough,
            caps.supportsHdrToneMapping,
        ).forEach { assertFalse(it, "EXTERNAL must advertise no capabilities") }
    }

    // ── Documented per-engine contract (the differentiators) ──
    // These mirror the no-op behaviour contract: a `false` here means the
    // engine silently ignores the related call.

    @Test
    fun exoPlayer_contract() {
        val c = EngineCapabilityMatrix.EXO_PLAYER
        assertTrue(c.supportsPip)
        assertTrue(c.supportsMiniMode)
        assertTrue(c.supportsCues)
        assertFalse(c.supportsAudioDelay)      // ExoPlayer has no audio-delay knob
        assertTrue(c.supportsSubtitleDelay)
        assertFalse(c.supportsAudioPassthrough)
        assertTrue(c.supportsSubtitleStyle)
        assertTrue(c.supportsSubtitleVerticalPosition)
        assertTrue(c.supportsDialogueBoost)
        assertTrue(c.supportsNightMode)
        assertTrue(c.supportsAudioNormalization)
        assertTrue(c.supportsChannelMixing)
        assertFalse(c.supportsVideoFilters)    // ExoPlayer has no video-filter chain
        assertTrue(c.supportsLiveQualitySwitch)
        assertTrue(c.supportsBandwidthEstimate)
        assertTrue(c.supportsScreenshot)
        assertTrue(c.supportsSpatialAudio)
    }

    @Test
    fun mpv_contract() {
        val c = EngineCapabilityMatrix.MPV
        assertTrue(c.supportsPip)
        assertFalse(c.supportsMiniMode)
        assertTrue(c.supportsCues)             // MPV exposes sub-text/sub-start for cue accumulation
        assertTrue(c.supportsAudioDelay)
        assertTrue(c.supportsSubtitleDelay)
        assertTrue(c.supportsAudioPassthrough)
        assertTrue(c.supportsSubtitleStyle)
        assertTrue(c.supportsSubtitleVerticalPosition)
        assertTrue(c.supportsDialogueBoost)
        assertTrue(c.supportsNightMode)
        assertTrue(c.supportsAudioNormalization)
        assertTrue(c.supportsChannelMixing)
        assertTrue(c.supportsVideoFilters)
        assertFalse(c.supportsLiveQualitySwitch)
        assertFalse(c.supportsBandwidthEstimate)
        assertTrue(c.supportsScreenshot)
    }

    @Test
    fun libvlc_contract() {
        val c = EngineCapabilityMatrix.LIBVLC
        assertTrue(c.supportsPip)
        assertFalse(c.supportsMiniMode)
        assertFalse(c.supportsCues)            // VLC does not surface cue text
        assertTrue(c.supportsAudioDelay)
        assertTrue(c.supportsSubtitleDelay)
        assertTrue(c.supportsAudioPassthrough)
        assertTrue(c.supportsSubtitleStyle)
        assertTrue(c.supportsSubtitleVerticalPosition)
        // These four are Android-AudioEffect-based but LibVLC exposes no audio
        // session id (returns UNSET), so the effect helpers short-circuit and
        // the effects cannot be toggled at runtime — only as startup filters.
        assertFalse(c.supportsDialogueBoost)
        assertFalse(c.supportsNightMode)
        assertFalse(c.supportsAudioNormalization)
        assertFalse(c.supportsChannelMixing)
        assertTrue(c.supportsVideoFilters)
        assertFalse(c.supportsLiveQualitySwitch)
        assertFalse(c.supportsBandwidthEstimate)
        assertTrue(c.supportsScreenshot)
    }

    @Test
    fun engines_areNotAllIdentical() {
        // Guard against a refactor accidentally collapsing the matrices.
        assertNotEquals(EngineCapabilityMatrix.EXO_PLAYER, EngineCapabilityMatrix.MPV)
        assertNotEquals(EngineCapabilityMatrix.EXO_PLAYER, EngineCapabilityMatrix.LIBVLC)
        assertNotEquals(EngineCapabilityMatrix.MPV, EngineCapabilityMatrix.LIBVLC)
    }

    // ── ASS / SSA subtitle capability flags ──

    @Test
    fun exoPlayer_assCapabilities() {
        val c = EngineCapabilityMatrix.EXO_PLAYER
        assertTrue(c.supportsAssOverride)
        // FORCE override rides the ASS header rewrite at track parse time
        // (AssStyleOverrideParserFactory) — apply-at-load, not live like mpv.
        assertTrue(c.supportsAssStyleOverride)
        assertTrue(c.supportsFontFamily)
        assertTrue(c.supportsFreeFormColors)
        assertTrue(c.supportsBorderStyles)
    }

    @Test
    fun mpv_assCapabilities() {
        val c = EngineCapabilityMatrix.MPV
        assertTrue(c.supportsAssOverride)
        // mpv applies user style overrides (colors/edges/Force) to ASS live.
        assertTrue(c.supportsAssStyleOverride)
        assertTrue(c.supportsFontFamily)
        assertTrue(c.supportsFreeFormColors)
        assertTrue(c.supportsBorderStyles)
    }

    // ── Image (bitmap) subtitle capability flag ──

    @Test
    fun imageSubtitles_mpvOnly() {
        // Only mpv's libav decoders render external bitmap sidecars
        // (PGS .sup / VobSub). Gates offline manifest side-loading.
        assertTrue(EngineCapabilityMatrix.MPV.supportsImageSubtitles)
        assertFalse(EngineCapabilityMatrix.EXO_PLAYER.supportsImageSubtitles)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsImageSubtitles)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsImageSubtitles)
    }

    // ── Deinterlace capability flag ──

    @Test
    fun deinterlace_mpvOnly() {
        // mpv's `deinterlace` property is runtime-settable on both mpv
        // adapters; the other engines have no surface. Gates the gear-menu
        // "Deinterlace" cycle item.
        assertTrue(EngineCapabilityMatrix.MPV.supportsDeinterlace)
        assertFalse(EngineCapabilityMatrix.EXO_PLAYER.supportsDeinterlace)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsDeinterlace)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsDeinterlace)
    }

    // ── Spatial audio capability flag ──

    @Test
    fun spatialAudio_exoPlayerOnly() {
        // Only ExoPlayer's sink hands the platform spatializer (API 32+) an
        // AudioTrack-qualifying multichannel stream without defeating it.
        // Runtime-gated (device support / system setting / route) — this
        // matrix flag is the declared admission, per
        // EngineCapabilities.supportsSpatialAudio's KDoc. Gates the in-player
        // spatial quick toggle.
        assertTrue(EngineCapabilityMatrix.EXO_PLAYER.supportsSpatialAudio)
        assertFalse(EngineCapabilityMatrix.MPV.supportsSpatialAudio)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsSpatialAudio)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsSpatialAudio)
    }

    // ── HDR output capability flags ──

    @Test
    fun hdrPassthrough_mpvOnly() {
        // mpv's `target-colorspace-hint` works on BOTH hosts now (desktop
        // HWND-embed, Android SurfaceView vo=gpu-next); ExoPlayer has no
        // passthrough switch and LibVLC no HDR surface at all.
        assertTrue(EngineCapabilityMatrix.MPV.supportsHdrPassthrough)
        assertFalse(EngineCapabilityMatrix.EXO_PLAYER.supportsHdrPassthrough)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsHdrPassthrough)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsHdrPassthrough)
    }

    @Test
    fun hdrToneMapping_exoPlayerOnly() {
        // The Media3 GL video-graph path (resolveExoHdrOutput) is the only
        // in-engine HDR→SDR tone-map ENGAGEMENT flag; mpv tone maps by default
        // (its dial lives in MpvEngineConfig.toneMapping, not a capability),
        // LibVLC has no tone mapping surface.
        assertTrue(EngineCapabilityMatrix.EXO_PLAYER.supportsHdrToneMapping)
        assertFalse(EngineCapabilityMatrix.MPV.supportsHdrToneMapping)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsHdrToneMapping)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsHdrToneMapping)
    }

    @Test
    fun libvlc_noAssCapabilities() {
        val c = EngineCapabilityMatrix.LIBVLC
        assertFalse(c.supportsAssOverride)
        assertFalse(c.supportsAssStyleOverride)
        assertTrue(c.supportsFontFamily)          // freetype font path still works
        // LibVLC now routes color resolution through SubtitleColorResolver (via
        // LibVlcSubtitleStyleMapping.colorOptions) and honors borderStyle via the
        // background-opacity mapping — so free-form colors and border styles are
        // applied consistently with ExoPlayer/MPV.
        assertFalse(c.supportsFreeFormColors)
        assertFalse(c.supportsBorderStyles)
    }

    // ── Custom subtitle color picker gate ──

    @Test
    fun customSubtitleColors_exoAndMpv() {
        // The free-form picker gates on supportsCustomSubtitleColors =
        // supportsFreeFormColors && supportsAssStyleOverride: both ExoPlayer
        // (ASS header rewrite, apply-at-load) and mpv (live) can hold hex
        // colors on ASS tracks now; LibVLC renders ASS as-authored — see the
        // "Subtitle nuance" paragraph in docs/player-engines.md.
        assertTrue(EngineCapabilityMatrix.MPV.supportsCustomSubtitleColors)
        assertTrue(EngineCapabilityMatrix.EXO_PLAYER.supportsCustomSubtitleColors)
        assertFalse(EngineCapabilityMatrix.LIBVLC.supportsCustomSubtitleColors)
        assertFalse(EngineCapabilityMatrix.EXTERNAL.supportsCustomSubtitleColors)
    }
}
