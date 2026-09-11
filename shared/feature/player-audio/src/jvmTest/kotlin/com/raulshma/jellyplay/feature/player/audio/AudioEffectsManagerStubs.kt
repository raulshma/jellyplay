package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The effects read-surface stub shared by the ViewModel suites (the
 * [AudioEffectsController] persist legs read these synchronously — "compute
 * from the manager's `.value`, not the mirror" — so real flows, not relaxed
 * mocks, and the flow-less strength accessors stay explicit instead of
 * relying on relaxed-mock defaults). Per-suite extras (replay-gain flows,
 * toggle `answers`) stay local to the suite that needs them.
 */
internal fun stubAudioEffectsReadSurface(effectsManager: AudioEffectsManager) {
    every { effectsManager.nightModeEnabled } returns MutableStateFlow(false)
    every { effectsManager.dialogueBoostEnabled } returns MutableStateFlow(false)
    every { effectsManager.equalizerEnabled } returns MutableStateFlow(false)
    every { effectsManager.equalizerSettings } returns MutableStateFlow(EqualizerSettings())
    every { effectsManager.equalizerPreset } returns MutableStateFlow(EqualizerPreset.FLAT)
    every { effectsManager.bassBoostEnabled } returns MutableStateFlow(false)
    every { effectsManager.virtualizerEnabled } returns MutableStateFlow(false)
    every { effectsManager.bassBoostStrengthState } returns EffectStrength.MODERATE
    every { effectsManager.dialogueBoostStrengthState } returns EffectStrength.MODERATE
    every { effectsManager.nightModeStrengthState } returns EffectStrength.MODERATE
}
