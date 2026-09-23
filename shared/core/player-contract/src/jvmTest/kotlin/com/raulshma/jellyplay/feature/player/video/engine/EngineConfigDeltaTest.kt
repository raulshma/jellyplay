package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Table-tests the total [EngineConfigDelta.of] diff — no change, each slice
 * alone, and the shared-pairs group — so both mpv hosts switching on the
 * delta cannot silently re-shape their `onConfigChanged` reconfiguration
 * gates.
 */
class EngineConfigDeltaTest {

    private val base = EngineConfig()

    private fun changed(transform: EngineConfig.() -> EngineConfig): EngineConfigDelta =
        EngineConfigDelta.of(base, base.transform())

    private fun assertFlags(
        actual: EngineConfigDelta,
        audioDelay: Boolean = false,
        subtitleDelay: Boolean = false,
        decoderMode: Boolean = false,
        subtitleStyle: Boolean = false,
        engineSpecific: Boolean = false,
        sharedPairs: Boolean = false,
        audioEffects: Boolean = false,
        channelMix: Boolean = false,
        audioAfChain: Boolean = false,
        audioSessionEffects: Boolean = false,
        videoEffects: Boolean = false,
    ) {
        assertEquals(audioDelay, actual.audioDelayChanged, "audioDelayChanged")
        assertEquals(subtitleDelay, actual.subtitleDelayChanged, "subtitleDelayChanged")
        assertEquals(decoderMode, actual.decoderModeChanged, "decoderModeChanged")
        assertEquals(subtitleStyle, actual.subtitleStyleChanged, "subtitleStyleChanged")
        assertEquals(engineSpecific, actual.engineSpecificChanged, "engineSpecificChanged")
        assertEquals(sharedPairs, actual.sharedPairsChanged, "sharedPairsChanged")
        assertEquals(audioEffects, actual.audioEffectsChanged, "audioEffectsChanged")
        assertEquals(channelMix, actual.channelMixChanged, "channelMixChanged")
        assertEquals(audioAfChain, actual.audioAfChainChanged, "audioAfChainChanged")
        assertEquals(audioSessionEffects, actual.audioSessionEffectsChanged, "audioSessionEffectsChanged")
        assertEquals(videoEffects, actual.videoEffectsChanged, "videoEffectsChanged")
    }

    // ── no change ──

    @Test
    fun of_identicalConfigs_isNone() {
        assertEquals(EngineConfigDelta.NONE, EngineConfigDelta.of(base, base))
    }

    // ── each scalar slice alone ──

    @Test
    fun of_audioDelayAlone() {
        assertFlags(changed { copy(audioDelayMs = 500) }, audioDelay = true)
    }

    @Test
    fun of_subtitleDelayAlone() {
        assertFlags(changed { copy(subtitleDelayMs = 250) }, subtitleDelay = true)
    }

    @Test
    fun of_decoderModeAlone() {
        assertFlags(changed { copy(decoderMode = DecoderMode.SW_ONLY) }, decoderMode = true)
    }

    @Test
    fun of_subtitleStyleAlone() {
        assertFlags(
            changed { copy(subtitleStyle = SubtitleStyle(applyCustomStyle = true)) },
            subtitleStyle = true,
        )
    }

    // ── the shared-pairs re-diff group ──

    @Test
    fun of_audioPassthroughAlone_flipsOnlyTheGroup() {
        assertFlags(changed { copy(audioPassthrough = true) }, sharedPairs = true)
    }

    @Test
    fun of_deinterlaceAlone_flipsOnlyTheGroup() {
        assertFlags(changed { copy(deinterlace = DeinterlaceMode.ON) }, sharedPairs = true)
    }

    @Test
    fun of_hdrSourceAlone_flipsOnlyTheGroup() {
        assertFlags(changed { copy(hdrSource = true) }, sharedPairs = true)
    }

    @Test
    fun of_engineSpecificAlone_flipsItselfAndTheGroup() {
        // Convergence pin: a change TO an engine-specific slice (including to
        // null — see EngineConfigDelta's KDoc) must re-diff the shared pairs.
        assertFlags(
            changed { copy(engineSpecific = MpvEngineConfig()) },
            engineSpecific = true,
            sharedPairs = true,
        )
    }

    @Test
    fun of_engineSpecificToNull_stillFlipsTheGroup() {
        // The desktop's former `newConfig.engineSpecific != null` guard skipped
        // the re-diff on exactly this edge.
        val withEngine = base.copy(engineSpecific = MpvEngineConfig())
        assertFlags(
            EngineConfigDelta.of(withEngine, base),
            engineSpecific = true,
            sharedPairs = true,
        )
    }

    // ── the audio-effects slice and its narrower groups ──

    @Test
    fun of_channelMixAlone_flipsTheGroupAndTheSlice() {
        assertFlags(
            changed { copy(audioEffects = audioEffects.copy(channelMixEnabled = true)) },
            audioEffects = true,
            channelMix = true,
        )
    }

    @Test
    fun of_afChainInputsAlone_flipTheChainGroupAndTheSlice() {
        assertFlags(
            changed { copy(audioEffects = audioEffects.copy(audioNormalizationEnabled = true)) },
            audioEffects = true,
            audioAfChain = true,
        )
    }

    @Test
    fun of_sessionInputsAlone_flipTheSessionGroupAndTheSlice() {
        assertFlags(
            changed { copy(audioEffects = audioEffects.copy(nightModeEnabled = true)) },
            audioEffects = true,
            audioSessionEffects = true,
        )
    }

    @Test
    fun of_audioEffectOutsideEveryGroup_flipsOnlyTheSlice() {
        // pitch is a real audio-effects change (desktop re-derives its `pitch`
        // write from the slice) but belongs to no Android sub-group.
        assertFlags(
            changed { copy(audioEffects = audioEffects.copy(pitchSemitones = 2f)) },
            audioEffects = true,
        )
    }

    // ── video slice ──

    @Test
    fun of_videoEffectsAlone() {
        assertFlags(changed { copy(videoEffects = videoEffects.copy(brightness = 0.2f)) }, videoEffects = true)
    }

    // ── everything at once ──

    @Test
    fun of_everySliceChanged_flipsEveryFlag() {
        val loud = base.copy(
            audioDelayMs = 100,
            subtitleDelayMs = 100,
            decoderMode = DecoderMode.SW_ONLY,
            subtitleStyle = SubtitleStyle(applyCustomStyle = true),
            engineSpecific = MpvEngineConfig(),
            audioPassthrough = true,
            deinterlace = DeinterlaceMode.ON,
            hdrSource = true,
            audioEffects = base.audioEffects.copy(channelMixEnabled = true),
            videoEffects = base.videoEffects.copy(brightness = 0.2f),
        )
        assertFlags(
            EngineConfigDelta.of(base, loud),
            audioDelay = true,
            subtitleDelay = true,
            decoderMode = true,
            subtitleStyle = true,
            engineSpecific = true,
            sharedPairs = true,
            audioEffects = true,
            channelMix = true,
            audioAfChain = false,
            audioSessionEffects = false,
            videoEffects = true,
        )
    }
}
