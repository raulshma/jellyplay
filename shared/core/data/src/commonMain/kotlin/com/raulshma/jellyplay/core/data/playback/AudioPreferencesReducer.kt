package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset

/**
 * Deep module: the pure preference→effect diff that drives audio-effects
 * application.
 *
 * **Why this lives here.** Pre-extraction [AudioPlaybackManager.init] carried
 * a ~77-line `preferences.collect { ... }` block that hand-tracked 14 stale
 * `prev*` locals and diffed each preference field against its predecessor to
 * decide which effect setter to call. Adding a new effect field meant
 * remembering to add a new `prev*` local + a new `if (changed)` branch — easy
 * to forget, and the resulting "effect silently stops responding to its
 * preference" bug was untestable (the block ran in an `init {}` on a class
 * with 18 mocked collaborators on `Dispatchers.Main`).
 *
 * Lifting the diff into a pure function means:
 *   - the rule has a direct JVM test (inputs in, command list out)
 *   - a forgotten field becomes a failing test, not a silent runtime bug
 *   - the manager shrinks to a thin command-dispatcher
 *
 * **Depth.** Interface: one function, two model values in, ordered list out.
 * Implementation: the field-by-field comparison that was previously inlined.
 * The ordering is load-bearing — strengths must be applied before their
 * enabled flag so an effect uses the correct value when it attaches — and the
 * reducer's output preserves it; see [diff].
 */
object AudioPreferencesReducer {

    /**
     * Computes the ordered list of [EffectCommand]s that transform the
     * `prev` effect state into the `next` effect state.
     *
     * Ordering contract (matches the pre-extraction init-block comment
     * "Strengths must be applied before their enabled flag so the effect uses
     * the correct value when it attaches"):
     *
     *   1. Standalone settings (visualizer, EQ preset, L/R balance, pitch) —
     *      order among these is immaterial.
     *   2. Strengths (bass, virtualizer, dialogue, night-mode) — before their
     *      corresponding enabled flags.
     *   3. Enabled flags (EQ, bass, virtualizer, dialogue, night-mode).
     *   4. Reverb preset (last — it re-attaches the effect when changed).
     *
     * Returns an empty list when nothing changed.
     */
    fun diff(
        prevEffects: AudioEffectsSlice,
        prevAudio: AudioSlice,
        nextEffects: AudioEffectsSlice,
        nextAudio: AudioSlice,
    ): List<EffectCommand> {
        if (prevEffects === nextEffects && prevAudio === nextAudio) return emptyList()
        val commands = mutableListOf<EffectCommand>()

        // 1. Standalone settings.
        if (nextAudio.audioVisualizerEnabled != prevAudio.audioVisualizerEnabled) {
            commands += EffectCommand.SetVisualizerEnabled(nextAudio.audioVisualizerEnabled)
        }
        if (nextEffects.equalizerPreset != prevEffects.equalizerPreset) {
            commands += EffectCommand.SetEqualizerPreset(nextEffects.equalizerPreset)
        }
        if (nextEffects.lrBalance != prevEffects.lrBalance) {
            commands += EffectCommand.SetLrBalance(nextEffects.lrBalance)
        }
        if (nextEffects.pitchSemitones != prevEffects.pitchSemitones) {
            commands += EffectCommand.SetPitchSemitones(nextEffects.pitchSemitones)
        }

        // 2. Strengths BEFORE their enabled flags so the effect picks up the
        //    new value when (re-)attaching.
        if (nextEffects.bassBoostStrength != prevEffects.bassBoostStrength) {
            commands += EffectCommand.SetBassBoostStrength(nextEffects.bassBoostStrength)
        }
        if (nextEffects.virtualizerStrength != prevEffects.virtualizerStrength) {
            commands += EffectCommand.SetVirtualizerStrength(nextEffects.virtualizerStrength)
        }
        if (nextEffects.dialogueBoostStrength != prevEffects.dialogueBoostStrength) {
            commands += EffectCommand.SetDialogueBoostStrength(nextEffects.dialogueBoostStrength)
        }
        if (nextEffects.nightModeStrength != prevEffects.nightModeStrength) {
            commands += EffectCommand.SetNightModeStrength(nextEffects.nightModeStrength)
        }

        // 3. Enabled flags.
        if (nextEffects.equalizerEnabled != prevEffects.equalizerEnabled) {
            commands += EffectCommand.SetEqualizerEnabled(nextEffects.equalizerEnabled)
        }
        if (nextEffects.bassBoostEnabled != prevEffects.bassBoostEnabled) {
            commands += EffectCommand.SetBassBoostEnabled(nextEffects.bassBoostEnabled)
        }
        if (nextEffects.virtualizerEnabled != prevEffects.virtualizerEnabled) {
            commands += EffectCommand.SetVirtualizerEnabled(nextEffects.virtualizerEnabled)
        }
        if (nextEffects.dialogueBoostEnabled != prevEffects.dialogueBoostEnabled) {
            commands += EffectCommand.SetDialogueBoostEnabled(nextEffects.dialogueBoostEnabled)
        }
        if (nextEffects.nightModeEnabled != prevEffects.nightModeEnabled) {
            commands += EffectCommand.SetNightModeEnabled(nextEffects.nightModeEnabled)
        }

        // 4. Reverb preset — changing it re-attaches the effect, so it runs
        //    after the enabled flags settle.
        if (nextEffects.reverbPreset != prevEffects.reverbPreset) {
            commands += EffectCommand.SetReverbPreset(nextEffects.reverbPreset)
        }

        return commands
    }
}

/**
 * One mutation to apply to the audio-effects processor. Sealed so the
 * dispatcher's `when` is exhaustive — adding a new effect means adding a new
 * variant and the compiler flags every dispatcher that forgets it.
 *
 * The dispatcher ([AudioPlaybackManager] in production) maps each variant to
 * the corresponding `effectsProcessor.setX(...)` call.
 */
sealed interface EffectCommand {
    data class SetVisualizerEnabled(val enabled: Boolean) : EffectCommand
    data class SetEqualizerPreset(val preset: EqualizerPreset) : EffectCommand
    data class SetLrBalance(val balance: Float) : EffectCommand
    data class SetPitchSemitones(val semitones: Float) : EffectCommand
    data class SetBassBoostStrength(val strength: EffectStrength) : EffectCommand
    data class SetVirtualizerStrength(val strength: Int) : EffectCommand
    data class SetDialogueBoostStrength(val strength: EffectStrength) : EffectCommand
    data class SetNightModeStrength(val strength: EffectStrength) : EffectCommand
    data class SetEqualizerEnabled(val enabled: Boolean) : EffectCommand
    data class SetBassBoostEnabled(val enabled: Boolean) : EffectCommand
    data class SetVirtualizerEnabled(val enabled: Boolean) : EffectCommand
    data class SetDialogueBoostEnabled(val enabled: Boolean) : EffectCommand
    data class SetNightModeEnabled(val enabled: Boolean) : EffectCommand
    data class SetReverbPreset(val preset: ReverbPreset) : EffectCommand
}
