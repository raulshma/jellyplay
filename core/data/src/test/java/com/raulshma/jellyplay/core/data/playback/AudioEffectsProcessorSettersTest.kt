package com.raulshma.jellyplay.core.data.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioEffectsProcessorSettersTest {

    private lateinit var processor: AudioEffectsProcessor

    @Before
    fun setup() {
        // Construction is JVM-safe: helpers defer android.media.audiofx object
        // creation until attach(). With no playerProvider set, applyXxx() is
        // a no-op, so the setters only update the StateFlow flags — exactly the
        // path used by AudioPlaybackManager.init to restore persisted state.
        processor = AudioEffectsProcessor()
    }

    @Test
    fun `setEqualizerEnabled toggles the equalizer flag`() {
        assertFalse(processor.equalizerEnabled.value)
        processor.setEqualizerEnabled(true)
        assertTrue(processor.equalizerEnabled.value)
        processor.setEqualizerEnabled(false)
        assertFalse(processor.equalizerEnabled.value)
    }

    @Test
    fun `setBassBoostEnabled toggles the bass boost flag`() {
        assertFalse(processor.bassBoostEnabled.value)
        processor.setBassBoostEnabled(true)
        assertTrue(processor.bassBoostEnabled.value)
        processor.setBassBoostEnabled(false)
        assertFalse(processor.bassBoostEnabled.value)
    }

    @Test
    fun `setVirtualizerEnabled toggles the virtualizer flag`() {
        assertFalse(processor.virtualizerEnabled.value)
        processor.setVirtualizerEnabled(true)
        assertTrue(processor.virtualizerEnabled.value)
        processor.setVirtualizerEnabled(false)
        assertFalse(processor.virtualizerEnabled.value)
    }

    @Test
    fun `setDialogueBoostEnabled toggles the dialogue boost flag`() {
        assertFalse(processor.dialogueBoostEnabled.value)
        processor.setDialogueBoostEnabled(true)
        assertTrue(processor.dialogueBoostEnabled.value)
        processor.setDialogueBoostEnabled(false)
        assertFalse(processor.dialogueBoostEnabled.value)
    }

    @Test
    fun `setNightModeEnabled toggles the night mode flag`() {
        assertFalse(processor.nightModeEnabled.value)
        processor.setNightModeEnabled(true)
        assertTrue(processor.nightModeEnabled.value)
        processor.setNightModeEnabled(false)
        assertFalse(processor.nightModeEnabled.value)
    }

    @Test
    fun `setters are idempotent when no player is attached`() {
        // Repeated calls must not crash even though applyXxx() cannot reach a
        // player (playerProvider is null). This mirrors startup restoration,
        // where prefs are applied before the player exists.
        processor.setEqualizerEnabled(true)
        processor.setEqualizerEnabled(true)
        processor.setBassBoostEnabled(true)
        processor.setNightModeEnabled(true)
        assertTrue(processor.equalizerEnabled.value)
        assertTrue(processor.bassBoostEnabled.value)
        assertTrue(processor.nightModeEnabled.value)
    }
}
