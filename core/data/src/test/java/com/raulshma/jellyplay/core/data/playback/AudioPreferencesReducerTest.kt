package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [AudioPreferencesReducer.diff] — the pure preference→effect diff
 * previously inlined as a ~77-line hand-rolled reducer with 14 stale `prev*`
 * locals in [AudioPlaybackManager.init].
 *
 * Each branch of the diff (one command per changed field, the strengths-before-
 * enabled ordering invariant, empty output on no-change) gets a direct test.
 * The reducer is pure; no MockK, no Dispatchers.Main, no ExoPlayer.
 */
class AudioPreferencesReducerTest {

    @Test
    fun `identical preferences produce no commands`() {
        val effects = AudioEffectsSlice()
        val audio = AudioSlice()
        assertTrue(AudioPreferencesReducer.diff(effects, audio, effects, audio).isEmpty())
    }

    @Test
    fun `default-then-default produces no commands`() {
        // Two distinct instances with identical fields → no commands.
        assertTrue(
            AudioPreferencesReducer.diff(
                AudioEffectsSlice(), AudioSlice(),
                AudioEffectsSlice(), AudioSlice(),
            ).isEmpty(),
        )
    }

    @Test
    fun `single field change emits exactly one command`() {
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice()
        val nextAudio = AudioSlice(audioVisualizerEnabled = true)
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        assertEquals(1, commands.size)
        assertEquals(EffectCommand.SetVisualizerEnabled(true), commands.single())
    }

    @Test
    fun `equalizer preset change emits preset command`() {
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(equalizerPreset = EqualizerPreset.ROCK)
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        assertEquals(listOf(EffectCommand.SetEqualizerPreset(EqualizerPreset.ROCK)), commands)
    }

    @Test
    fun `lr balance change emits balance command`() {
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(lrBalance = 0.5f)
        val nextAudio = AudioSlice()
        assertEquals(
            listOf(EffectCommand.SetLrBalance(0.5f)),
            AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio),
        )
    }

    @Test
    fun `strength change emits strength command before enabled flag`() {
        // The load-bearing ordering invariant: strengths must precede their
        // enabled flags so the effect attaches with the right value.
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(
            bassBoostStrength = EffectStrength.HIGH,
            bassBoostEnabled = true,
        )
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        val strengthIdx = commands.indexOfFirst { it is EffectCommand.SetBassBoostStrength }
        val enabledIdx = commands.indexOfFirst { it is EffectCommand.SetBassBoostEnabled }
        assertTrue("strength must be emitted", strengthIdx >= 0)
        assertTrue("enabled must be emitted", enabledIdx >= 0)
        assertTrue(
            "strength ($strengthIdx) must precede enabled ($enabledIdx)",
            strengthIdx < enabledIdx,
        )
    }

    @Test
    fun `all four strength commands precede their enabled flags`() {
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(
            bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = true,
            virtualizerStrength = 800, virtualizerEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH, dialogueBoostEnabled = true,
            nightModeStrength = EffectStrength.HIGH, nightModeEnabled = true,
        )
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        val lastStrengthIdx = commands.indexOfLast {
            it is EffectCommand.SetBassBoostStrength ||
                it is EffectCommand.SetVirtualizerStrength ||
                it is EffectCommand.SetDialogueBoostStrength ||
                it is EffectCommand.SetNightModeStrength
        }
        val firstEnabledIdx = commands.indexOfFirst {
            it is EffectCommand.SetEqualizerEnabled ||
                it is EffectCommand.SetBassBoostEnabled ||
                it is EffectCommand.SetVirtualizerEnabled ||
                it is EffectCommand.SetDialogueBoostEnabled ||
                it is EffectCommand.SetNightModeEnabled
        }
        assertTrue(
            "all strengths ($lastStrengthIdx) must precede all enabled ($firstEnabledIdx)",
            lastStrengthIdx < firstEnabledIdx,
        )
    }

    @Test
    fun `reverb preset comes after enabled flags`() {
        // Reverb re-attaches the effect when changed, so it must run after the
        // enabled flags settle.
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(
            bassBoostEnabled = true,
            reverbPreset = ReverbPreset.LARGE_HALL,
        )
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        val reverbIdx = commands.indexOfFirst { it is EffectCommand.SetReverbPreset }
        val lastEnabledIdx = commands.indexOfLast {
            it is EffectCommand.SetEqualizerEnabled ||
                it is EffectCommand.SetBassBoostEnabled ||
                it is EffectCommand.SetVirtualizerEnabled ||
                it is EffectCommand.SetDialogueBoostEnabled ||
                it is EffectCommand.SetNightModeEnabled
        }
        assertTrue(reverbIdx > lastEnabledIdx)
    }

    @Test
    fun `all fields changed emits all commands`() {
        // Regression guard: a future field added to the slices without a
        // matching reducer branch would silently break effects. This test
        // flips every known effect field; if the reducer forgets one, the
        // count assertion fails.
        val prevEffects = AudioEffectsSlice()
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(
            equalizerPreset = EqualizerPreset.POP,
            lrBalance = -0.3f,
            pitchSemitones = 2f,
            bassBoostStrength = EffectStrength.HIGH,
            virtualizerStrength = 900,
            dialogueBoostStrength = EffectStrength.LOW,
            nightModeStrength = EffectStrength.HIGH,
            equalizerEnabled = true,
            bassBoostEnabled = true,
            virtualizerEnabled = true,
            dialogueBoostEnabled = true,
            nightModeEnabled = true,
            reverbPreset = ReverbPreset.MEDIUM_ROOM,
        )
        val nextAudio = AudioSlice(audioVisualizerEnabled = true)
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        // 4 standalone + 4 strengths + 5 enabled + 1 reverb = 14.
        assertEquals(14, commands.size)
    }

    @Test
    fun `command carries the new value not the old`() {
        val prevEffects = AudioEffectsSlice(bassBoostStrength = EffectStrength.LOW)
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(bassBoostStrength = EffectStrength.HIGH)
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        assertEquals(
            EffectCommand.SetBassBoostStrength(EffectStrength.HIGH),
            commands.single(),
        )
    }

    @Test
    fun `unchanged field does not emit a command`() {
        // Bass strength identical but bass enabled flips — only the enabled
        // command fires, not the strength command.
        val prevEffects = AudioEffectsSlice(bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = false)
        val prevAudio = AudioSlice()
        val nextEffects = AudioEffectsSlice(bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = true)
        val nextAudio = AudioSlice()
        val commands = AudioPreferencesReducer.diff(prevEffects, prevAudio, nextEffects, nextAudio)
        assertEquals(listOf(EffectCommand.SetBassBoostEnabled(true)), commands)
    }
}
