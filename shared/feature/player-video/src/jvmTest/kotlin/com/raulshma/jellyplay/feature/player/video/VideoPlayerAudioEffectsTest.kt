package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests for audio effect state management. After state ownership moved (plan
 * 01), the user effects live in [AudioEffectsState] (owned by
 * [VideoEffectsController]); the per-item resolver-driven dialogue boost stays
 * on [VideoPlayerUiState]. These replicate the toggle/update logic as pure
 * state-machine tests.
 */
class VideoPlayerAudioEffectsTest {

    // ─── Dialogue boost (stays on VideoPlayerUiState) ──────────────────────────

    @Test
    fun defaults_dialogueBoostDisabled() {
        val state = VideoPlayerUiState()
        assertFalse(state.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.dialogueBoostStrength)
    }

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

    // ─── Defaults (AudioEffectsState) ──────────────────────────────────────────

    @Test
    fun defaults_nightModeDisabled() {
        val state = AudioEffectsState()
        assertFalse(state.nightModeEnabled)
        assertEquals(EffectStrength.MODERATE, state.nightModeStrength)
    }

    @Test
    fun defaults_audioNormalizationDisabled() {
        val state = AudioEffectsState()
        assertFalse(state.audioNormalizationEnabled)
        assertEquals(AudioNormalizationMode.NONE, state.audioNormalizationMode)
    }

    @Test
    fun defaults_channelMixDisabled() {
        val state = AudioEffectsState()
        assertFalse(state.channelMixEnabled)
        assertEquals(ChannelMixMode.AUTO, state.channelMixMode)
    }

    @Test
    fun defaults_bassBoostDisabled() {
        val state = AudioEffectsState()
        assertFalse(state.bassBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.bassBoostStrength)
    }

    @Test
    fun defaults_virtualizerDisabled() {
        val state = AudioEffectsState()
        assertFalse(state.virtualizerEnabled)
        assertEquals(500, state.virtualizerStrength)
    }

    @Test
    fun defaults_reverbNone() {
        val state = AudioEffectsState()
        assertEquals(ReverbPreset.NONE, state.reverbPreset)
    }

    // ─── Toggle night mode ─────────────────────────────────────────────────────

    @Test
    fun toggleNightMode_enablesWhenDisabled() {
        val state = AudioEffectsState(nightModeEnabled = false)
        val updated = state.copy(nightModeEnabled = !state.nightModeEnabled)
        assertTrue(updated.nightModeEnabled)
    }

    @Test
    fun toggleNightMode_disablesWhenEnabled() {
        val state = AudioEffectsState(nightModeEnabled = true)
        val updated = state.copy(nightModeEnabled = !state.nightModeEnabled)
        assertFalse(updated.nightModeEnabled)
    }

    @Test
    fun setNightModeStrength_low() {
        val state = AudioEffectsState().copy(nightModeStrength = EffectStrength.LOW)
        assertEquals(EffectStrength.LOW, state.nightModeStrength)
    }

    // ─── Audio normalization ───────────────────────────────────────────────────

    @Test
    fun setAudioNormalizationMode_NONE_disablesNormalization() {
        val state = AudioEffectsState(
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
        val state = AudioEffectsState().copy(
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = AudioNormalizationMode.DYNAMIC != AudioNormalizationMode.NONE,
        )
        assertTrue(state.audioNormalizationEnabled)
        assertEquals(AudioNormalizationMode.DYNAMIC, state.audioNormalizationMode)
    }

    @Test
    fun toggleAudioNormalization_flipsEnabled() {
        var state = AudioEffectsState(audioNormalizationEnabled = false)
        state = state.copy(audioNormalizationEnabled = !state.audioNormalizationEnabled)
        assertTrue(state.audioNormalizationEnabled)
    }

    // ─── Channel mix ───────────────────────────────────────────────────────────

    @Test
    fun toggleChannelMix_enablesWhenDisabled() {
        val state = AudioEffectsState(channelMixEnabled = false)
        val updated = state.copy(channelMixEnabled = !state.channelMixEnabled)
        assertTrue(updated.channelMixEnabled)
    }

    @Test
    fun setChannelMixMode_AUTO_disablesChannelMix() {
        val state = AudioEffectsState(
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
        val state = AudioEffectsState(bassBoostEnabled = false)
        val updated = state.copy(bassBoostEnabled = !state.bassBoostEnabled)
        assertTrue(updated.bassBoostEnabled)
    }

    @Test
    fun setBassBoostStrength_high() {
        val state = AudioEffectsState().copy(bassBoostStrength = EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, state.bassBoostStrength)
    }

    // ─── Virtualizer ───────────────────────────────────────────────────────────

    @Test
    fun toggleVirtualizer_enablesWhenDisabled() {
        val state = AudioEffectsState(virtualizerEnabled = false)
        val updated = state.copy(virtualizerEnabled = !state.virtualizerEnabled)
        assertTrue(updated.virtualizerEnabled)
    }

    @Test
    fun setVirtualizerStrength_updatesStrength() {
        val state = AudioEffectsState().copy(virtualizerStrength = 750)
        assertEquals(750, state.virtualizerStrength)
    }

    @Test
    fun setVirtualizerStrength_minValue_zero() {
        val state = AudioEffectsState().copy(virtualizerStrength = 0)
        assertEquals(0, state.virtualizerStrength)
    }

    @Test
    fun setVirtualizerStrength_maxValue_1000() {
        val state = AudioEffectsState().copy(virtualizerStrength = 1000)
        assertEquals(1000, state.virtualizerStrength)
    }

    // ─── Reverb ────────────────────────────────────────────────────────────────

    @Test
    fun setReverbPreset_largeRoom() {
        val state = AudioEffectsState().copy(reverbPreset = ReverbPreset.LARGE_ROOM)
        assertEquals(ReverbPreset.LARGE_ROOM, state.reverbPreset)
    }

    @Test
    fun setReverbPreset_none_clearsReverb() {
        val state = AudioEffectsState(reverbPreset = ReverbPreset.LARGE_ROOM)
            .copy(reverbPreset = ReverbPreset.NONE)
        assertEquals(ReverbPreset.NONE, state.reverbPreset)
    }

    // ─── Audio passthrough / delay ─────────────────────────────────────────────

    @Test
    fun audioPassthrough_defaultFalse() {
        val state = AudioEffectsState()
        assertFalse(state.audioPassthrough)
    }

    @Test
    fun audioPassthrough_canBeEnabled() {
        val state = AudioEffectsState().copy(audioPassthrough = true)
        assertTrue(state.audioPassthrough)
    }

    @Test
    fun audioDelay_defaultsToZero() {
        val state = AudioEffectsState()
        assertEquals(0L, state.audioDelayMs)
        assertEquals(DecoderMode.HW_PREFERRED, state.decoderMode)
    }
}
