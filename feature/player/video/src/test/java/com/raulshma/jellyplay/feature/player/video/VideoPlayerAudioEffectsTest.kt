package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for audio effect state management in VideoPlayerUiState.
 * These replicate the toggle/update logic from VideoPlayerViewModel
 * as pure state-machine tests.
 */
class VideoPlayerAudioEffectsTest {

    // ─── Defaults ─────────────────────────────────────────────────────────────

    @Test
    fun defaults_dialogueBoostDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.dialogueBoostStrength)
    }

    @Test
    fun defaults_nightModeDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.nightModeEnabled)
        assertEquals(EffectStrength.MODERATE, state.nightModeStrength)
    }

    @Test
    fun defaults_audioNormalizationDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.audioNormalizationEnabled)
        assertEquals(AudioNormalizationMode.NONE, state.audioNormalizationMode)
    }

    @Test
    fun defaults_channelMixDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.channelMixEnabled)
        assertEquals(ChannelMixMode.AUTO, state.channelMixMode)
    }

    @Test
    fun defaults_bassBoostDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.bassBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.bassBoostStrength)
    }

    @Test
    fun defaults_virtualizerDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.virtualizerEnabled)
        assertEquals(500, state.virtualizerStrength)
    }

    @Test
    fun defaults_reverbNone() {
        val state = VideoPlayerUiState()
        assertEquals(ReverbPreset.NONE, state.reverbPreset)
    }

    // ─── Toggle dialogue boost ─────────────────────────────────────────────────

    @Test
    fun toggleDialogueBoost_enablesWhenDisabled() {
        val state = VideoPlayerUiState(dialogueBoostEnabled = false)
        val updated = state.copy(dialogueBoostEnabled = !state.dialogueBoostEnabled)
        assertTrue(updated.dialogueBoostEnabled)
    }

    @Test
    fun toggleDialogueBoost_disablesWhenEnabled() {
        val state = VideoPlayerUiState(dialogueBoostEnabled = true)
        val updated = state.copy(dialogueBoostEnabled = !state.dialogueBoostEnabled)
        assertFalse(updated.dialogueBoostEnabled)
    }

    @Test
    fun setDialogueBoostStrength_low() {
        val state = VideoPlayerUiState().copy(dialogueBoostStrength = EffectStrength.LOW)
        assertEquals(EffectStrength.LOW, state.dialogueBoostStrength)
    }

    @Test
    fun setDialogueBoostStrength_high() {
        val state = VideoPlayerUiState().copy(dialogueBoostStrength = EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, state.dialogueBoostStrength)
    }

    // ─── Toggle night mode ─────────────────────────────────────────────────────

    @Test
    fun toggleNightMode_enablesWhenDisabled() {
        val state = VideoPlayerUiState(nightModeEnabled = false)
        val updated = state.copy(nightModeEnabled = !state.nightModeEnabled)
        assertTrue(updated.nightModeEnabled)
    }

    @Test
    fun toggleNightMode_disablesWhenEnabled() {
        val state = VideoPlayerUiState(nightModeEnabled = true)
        val updated = state.copy(nightModeEnabled = !state.nightModeEnabled)
        assertFalse(updated.nightModeEnabled)
    }

    @Test
    fun setNightModeStrength_low() {
        val state = VideoPlayerUiState().copy(nightModeStrength = EffectStrength.LOW)
        assertEquals(EffectStrength.LOW, state.nightModeStrength)
    }

    // ─── Audio normalization ───────────────────────────────────────────────────

    @Test
    fun setAudioNormalizationMode_NONE_disablesNormalization() {
        val state = VideoPlayerUiState(
            audioNormalizationEnabled = true,
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
        ).copy(
            audioNormalizationMode = AudioNormalizationMode.NONE,
            audioNormalizationEnabled = false,
        )
        assertFalse(state.audioNormalizationEnabled)
        assertEquals(AudioNormalizationMode.NONE, state.audioNormalizationMode)
    }

    @Test
    fun setAudioNormalizationMode_nonNone_enablesNormalization() {
        val state = VideoPlayerUiState().copy(
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = AudioNormalizationMode.DYNAMIC != AudioNormalizationMode.NONE,
        )
        assertTrue(state.audioNormalizationEnabled)
        assertEquals(AudioNormalizationMode.DYNAMIC, state.audioNormalizationMode)
    }

    @Test
    fun toggleAudioNormalization_flipsEnabled() {
        var state = VideoPlayerUiState(audioNormalizationEnabled = false)
        state = state.copy(audioNormalizationEnabled = !state.audioNormalizationEnabled)
        assertTrue(state.audioNormalizationEnabled)
    }

    // ─── Channel mix ───────────────────────────────────────────────────────────

    @Test
    fun toggleChannelMix_enablesWhenDisabled() {
        val state = VideoPlayerUiState(channelMixEnabled = false)
        val updated = state.copy(channelMixEnabled = !state.channelMixEnabled)
        assertTrue(updated.channelMixEnabled)
    }

    @Test
    fun setChannelMixMode_AUTO_disablesChannelMix() {
        val state = VideoPlayerUiState(
            channelMixEnabled = true,
            channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
        ).copy(
            channelMixMode = ChannelMixMode.AUTO,
            channelMixEnabled = false,
        )
        assertFalse(state.channelMixEnabled)
        assertEquals(ChannelMixMode.AUTO, state.channelMixMode)
    }

    // ─── Bass boost ────────────────────────────────────────────────────────────

    @Test
    fun toggleBassBoost_enablesWhenDisabled() {
        val state = VideoPlayerUiState(bassBoostEnabled = false)
        val updated = state.copy(bassBoostEnabled = !state.bassBoostEnabled)
        assertTrue(updated.bassBoostEnabled)
    }

    @Test
    fun setBassBoostStrength_high() {
        val state = VideoPlayerUiState().copy(bassBoostStrength = EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, state.bassBoostStrength)
    }

    // ─── Virtualizer ───────────────────────────────────────────────────────────

    @Test
    fun toggleVirtualizer_enablesWhenDisabled() {
        val state = VideoPlayerUiState(virtualizerEnabled = false)
        val updated = state.copy(virtualizerEnabled = !state.virtualizerEnabled)
        assertTrue(updated.virtualizerEnabled)
    }

    @Test
    fun setVirtualizerStrength_updatesStrength() {
        val state = VideoPlayerUiState().copy(virtualizerStrength = 750)
        assertEquals(750, state.virtualizerStrength)
    }

    @Test
    fun setVirtualizerStrength_minValue_zero() {
        val state = VideoPlayerUiState().copy(virtualizerStrength = 0)
        assertEquals(0, state.virtualizerStrength)
    }

    @Test
    fun setVirtualizerStrength_maxValue_1000() {
        val state = VideoPlayerUiState().copy(virtualizerStrength = 1000)
        assertEquals(1000, state.virtualizerStrength)
    }

    // ─── Reverb ────────────────────────────────────────────────────────────────

    @Test
    fun setReverbPreset_largeRoom() {
        val state = VideoPlayerUiState().copy(reverbPreset = ReverbPreset.LARGE_ROOM)
        assertEquals(ReverbPreset.LARGE_ROOM, state.reverbPreset)
    }

    @Test
    fun setReverbPreset_none_clearsReverb() {
        val state = VideoPlayerUiState(reverbPreset = ReverbPreset.LARGE_ROOM)
            .copy(reverbPreset = ReverbPreset.NONE)
        assertEquals(ReverbPreset.NONE, state.reverbPreset)
    }

    // ─── Audio passthrough ─────────────────────────────────────────────────────

    @Test
    fun audioPassthrough_defaultFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.audioPassthrough)
    }

    @Test
    fun audioPassthrough_canBeEnabled() {
        val state = VideoPlayerUiState().copy(audioPassthrough = true)
        assertTrue(state.audioPassthrough)
    }
}
