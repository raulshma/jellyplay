package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.UserPreferences
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
        val prefs = UserPreferences()
        assertTrue(AudioPreferencesReducer.diff(prefs, prefs).isEmpty())
    }

    @Test
    fun `default-then-default produces no commands`() {
        // Two distinct instances with identical fields → no commands.
        assertTrue(AudioPreferencesReducer.diff(UserPreferences(), UserPreferences()).isEmpty())
    }

    @Test
    fun `single field change emits exactly one command`() {
        val next = UserPreferences().copy(audioVisualizerEnabled = true)
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
        assertEquals(1, commands.size)
        assertEquals(EffectCommand.SetVisualizerEnabled(true), commands.single())
    }

    @Test
    fun `equalizer preset change emits preset command`() {
        val next = UserPreferences().copy(equalizerPreset = EqualizerPreset.ROCK)
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
        assertEquals(listOf(EffectCommand.SetEqualizerPreset(EqualizerPreset.ROCK)), commands)
    }

    @Test
    fun `lr balance change emits balance command`() {
        val next = UserPreferences().copy(lrBalance = 0.5f)
        assertEquals(
            listOf(EffectCommand.SetLrBalance(0.5f)),
            AudioPreferencesReducer.diff(UserPreferences(), next),
        )
    }

    @Test
    fun `strength change emits strength command before enabled flag`() {
        // The load-bearing ordering invariant: strengths must precede their
        // enabled flags so the effect attaches with the right value.
        val next = UserPreferences().copy(
            bassBoostStrength = EffectStrength.HIGH,
            bassBoostEnabled = true,
        )
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
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
        val next = UserPreferences().copy(
            bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = true,
            virtualizerStrength = 800, virtualizerEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH, dialogueBoostEnabled = true,
            nightModeStrength = EffectStrength.HIGH, nightModeEnabled = true,
        )
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
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
        val next = UserPreferences().copy(
            bassBoostEnabled = true,
            reverbPreset = ReverbPreset.LARGE_HALL,
        )
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
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
        // Regression guard: a future field added to UserPreferences without a
        // matching reducer branch would silently break effects. This test
        // flips every known effect field; if the reducer forgets one, the
        // count assertion fails.
        val next = UserPreferences().copy(
            audioVisualizerEnabled = true,
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
        val commands = AudioPreferencesReducer.diff(UserPreferences(), next)
        // 4 standalone + 4 strengths + 5 enabled + 1 reverb = 14.
        assertEquals(14, commands.size)
    }

    @Test
    fun `command carries the new value not the old`() {
        val prev = UserPreferences(bassBoostStrength = EffectStrength.LOW)
        val next = UserPreferences(bassBoostStrength = EffectStrength.HIGH)
        val commands = AudioPreferencesReducer.diff(prev, next)
        assertEquals(
            EffectCommand.SetBassBoostStrength(EffectStrength.HIGH),
            commands.single(),
        )
    }

    @Test
    fun `unchanged field does not emit a command`() {
        // Bass strength identical but bass enabled flips — only the enabled
        // command fires, not the strength command.
        val prev = UserPreferences(bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = false)
        val next = UserPreferences(bassBoostStrength = EffectStrength.HIGH, bassBoostEnabled = true)
        val commands = AudioPreferencesReducer.diff(prev, next)
        assertEquals(listOf(EffectCommand.SetBassBoostEnabled(true)), commands)
    }
}
