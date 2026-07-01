package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [EngineConfigBuilder] — the audio-effects / decoder orchestration mapping
 * extracted from `VideoPlayerViewModel` (recommendation #1). Pure function, no
 * Android, so it is exercised directly.
 */
class EngineConfigBuilderTest {

    private fun baselineState() = VideoPlayerUiState()

    @Test
    fun build_defaultsPropagateForNeutralState() {
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = false, prefs = UserPreferences())
        assertEquals(EngineConfig().decoderMode, config.decoderMode)
        assertFalse(config.audioPassthrough)
        assertEquals(0L, config.audioDelayMs)
        assertEquals(0L, config.subtitleDelayMs)
        assertFalse(config.audioEffects.dialogueBoostEnabled)
        assertFalse(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_carriesAudioEffectsFromUiState() {
        val state = baselineState().copy(
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.MODERATE,
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = true,
            channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
            channelMixEnabled = true,
            bassBoostEnabled = true,
            bassBoostStrength = EffectStrength.LOW,
            virtualizerEnabled = true,
            virtualizerStrength = 750,
            reverbPreset = ReverbPreset.LARGE_HALL,
            audioDelayMs = 42L,
        )
        val config = EngineConfigBuilder.build(state, equalizerEnabled = true, prefs = UserPreferences())

        with(config.audioEffects) {
            assertTrue(dialogueBoostEnabled)
            assertEquals(EffectStrength.HIGH, dialogueBoostStrength)
            assertTrue(nightModeEnabled)
            assertEquals(AudioNormalizationMode.DYNAMIC, audioNormalizationMode)
            assertTrue(audioNormalizationEnabled)
            assertEquals(ChannelMixMode.STEREO_DOWNMIX, channelMixMode)
            assertTrue(channelMixEnabled)
            assertTrue(bassBoostEnabled)
            assertTrue(virtualizerEnabled)
            assertEquals(750, virtualizerStrength)
            assertEquals(ReverbPreset.LARGE_HALL, reverbPreset)
            assertTrue(equalizerEnabled)
        }
        assertEquals(42L, config.audioDelayMs)
    }

    @Test
    fun build_equalizerEnabledComesFromParam_notState() {
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = true, prefs = UserPreferences())
        assertTrue(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_equalizerAndVolumeBoostComeFromPrefs() {
        val prefs = UserPreferences(
            equalizerSettings = EqualizerSettings(bandLevels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
            volumeBoostEnabled = true,
            volumeBoostGain = 6,
            pauseOnAudioFocusLoss = false,
        )
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = false, prefs = prefs)

        assertEquals(prefs.equalizerSettings, config.audioEffects.equalizerSettings)
        assertTrue(config.audioEffects.volumeBoostEnabled)
        assertEquals(6, config.audioEffects.volumeBoostGain)
        assertFalse(config.pauseOnAudioFocusLoss)
    }

    @Test
    fun build_subtitleDelayReadsStyleOffset() {
        val state = baselineState().copy(subtitleStyle = SubtitleStyle(offsetMs = 250L))
        val config = EngineConfigBuilder.build(state, equalizerEnabled = false, prefs = UserPreferences())
        assertEquals(250L, config.subtitleDelayMs)
        assertEquals(250L, config.subtitleStyle.offsetMs)
    }

    @Test
    fun build_decoderAndPassthroughReadFromState() {
        val state = baselineState().copy(decoderMode = DecoderMode.SW_ONLY, audioPassthrough = true)
        val config = EngineConfigBuilder.build(state, equalizerEnabled = false, prefs = UserPreferences())
        assertEquals(DecoderMode.SW_ONLY, config.decoderMode)
        assertTrue(config.audioPassthrough)
    }
}
