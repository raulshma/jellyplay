package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the ONE "apply to manager → mirror → persist" choreography shared by
 * the audio player's effects setters (extracted from [AudioPlayerViewModel],
 * the same inline pattern player-video's `VideoEffectsController.applyAndPersist`
 * KDoc says that controller was itself extracted from). Every public fun is one
 * ordered entry: the apply leg is synchronous so the manager (and the screen)
 * see the new value in the same frame; the persist leg is a suspend block
 * launched on [scope] so DataStore writes stay off the main thread.
 *
 * **Authoritative source: the manager, not the uiState mirror.** Toggles and
 * the equalizer folds persist the value read from the manager's synchronous
 * `StateFlow.value` immediately AFTER the apply leg — the
 * [AudioPlaybackManager] re-exposes the processor flows by reference, and both
 * the Android processor and the desktop state machine flip them inside the
 * setter, so `.value` is correct in the same frame. The ViewModel's uiState
 * mirror collectors remain (screens read them) but are no longer a persistence
 * input — they lag a dispatch, and persisting their read-back was only correct
 * by dispatch-order luck. The persisted VALUE is unchanged; only its
 * source is now the manager, and the AudioPreferencesReducer timing is
 * untouched.
 *
 * **Strength fields** (`dialogueBoost`/`nightMode`/`bassBoost`): the manager is
 * their single home via the plain read accessors
 * [AudioEffectsManager.dialogueBoostStrengthState] /
 * [AudioEffectsManager.nightModeStrengthState] /
 * [AudioEffectsManager.bassBoostStrengthState] (they carry no flows). The
 * former VM shadow copies are gone: setters mirror their argument (the manager
 * setters are unconditional, so argument == manager state) and
 * [seedForPlayback] feeds prefs through the manager and mirrors back through
 * the accessors — the prefs slice never touches uiState directly.
 *
 * Deliberately NOT migrated: the [SleepTimerManager] workflow (audio has its
 * own [AudioSleepTimerController]) and the queue/engine transport setters.
 *
 * Not a Koin type: the ViewModel constructs it directly over its own [scope]
 * so the controller shares the VM's lifecycle. The [updateEffects] lambda is
 * the SettingsProjector-style seam over the VM's uiState effects slice; the
 * slice type stays module-local (this is player-audio's own
 * `AudioPlayerUiState.AudioEffectsState`).
 */
internal class AudioEffectsController(
    private val scope: CoroutineScope,
    private val effectsManager: AudioEffectsManager,
    private val engine: AudioPlayerEngine,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val updateEffects: ((AudioEffectsState) -> AudioEffectsState) -> Unit,
) {

    fun toggleDialogueBoost() = applyAndPersist(
        apply = effectsManager::toggleDialogueBoost,
        persist = { audioEffectsStore.setDialogueBoostEnabled(effectsManager.dialogueBoostEnabled.value) },
    )

    fun setDialogueBoostStrength(strength: EffectStrength) = applyAndPersist(
        apply = { effectsManager.setDialogueBoostStrength(strength) },
        update = { it.copy(dialogueBoostStrength = strength) },
        persist = { audioEffectsStore.setDialogueBoostStrength(strength) },
    )

    fun toggleNightMode() = applyAndPersist(
        apply = effectsManager::toggleNightMode,
        persist = { audioEffectsStore.setNightModeEnabled(effectsManager.nightModeEnabled.value) },
    )

    fun setNightModeStrength(strength: EffectStrength) = applyAndPersist(
        apply = { effectsManager.setNightModeStrength(strength) },
        update = { it.copy(nightModeStrength = strength) },
        persist = { audioEffectsStore.setNightModeStrength(strength) },
    )

    fun setReplayGainMode(mode: AudioNormalizationMode) = applyAndPersist(
        apply = { effectsManager.setReplayGainMode(mode) },
        persist = { audioStore.setAudioNormalizationMode(mode) },
    )

    fun setReplayGainPreAmpDb(db: Float) = applyAndPersist(
        apply = { effectsManager.setReplayGainPreAmpDb(db) },
        persist = { audioStore.setReplayGainPreAmpDb(db) },
    )

    fun toggleEqualizer() = applyAndPersist(
        apply = effectsManager::toggleEqualizer,
        persist = { audioEffectsStore.setEqualizerEnabled(effectsManager.equalizerEnabled.value) },
    )

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) = applyAndPersist(
        apply = { effectsManager.setEqualizerBand(bandIndex, levelDb) },
        persist = { audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value) },
    )

    fun resetEqualizer() = applyAndPersist(
        apply = effectsManager::resetEqualizer,
        persist = {
            audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value)
            audioEffectsStore.setEqualizerPreset(effectsManager.equalizerPreset.value)
        },
    )

    fun setEqualizerPreset(preset: EqualizerPreset) = applyAndPersist(
        apply = { effectsManager.setEqualizerPreset(preset) },
        persist = {
            audioEffectsStore.setEqualizerPreset(preset)
            audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value)
        },
    )

    fun toggleBassBoost() = applyAndPersist(
        apply = effectsManager::toggleBassBoost,
        persist = { audioEffectsStore.setBassBoostEnabled(effectsManager.bassBoostEnabled.value) },
    )

    fun setBassBoostStrength(strength: EffectStrength) = applyAndPersist(
        apply = { effectsManager.setBassBoostStrength(strength) },
        update = { it.copy(bassBoostStrength = strength) },
        persist = { audioEffectsStore.setBassBoostStrength(strength) },
    )

    fun toggleVirtualizer() = applyAndPersist(
        apply = effectsManager::toggleVirtualizer,
        persist = { audioEffectsStore.setVirtualizerEnabled(effectsManager.virtualizerEnabled.value) },
    )

    fun setVirtualizerStrength(strength: Int) = applyAndPersist(
        apply = { effectsManager.setVirtualizerStrength(strength) },
        persist = { audioEffectsStore.setVirtualizerStrength(strength) },
    )

    fun setReverbPreset(preset: ReverbPreset) = applyAndPersist(
        apply = { effectsManager.setReverbPreset(preset) },
        persist = { audioEffectsStore.setReverbPreset(preset) },
    )

    fun setLrBalance(balance: Float) = applyAndPersist(
        apply = { effectsManager.setLrBalance(balance) },
        persist = { audioEffectsStore.setLrBalance(balance) },
    )

    fun setPitchSemitones(semitones: Float) = applyAndPersist(
        apply = { effectsManager.setPitchSemitones(semitones) },
        persist = { audioEffectsStore.setPitchSemitones(semitones) },
    )

    fun setAutoEqByGenre(enabled: Boolean) = applyAndPersist(
        apply = { effectsManager.setAutoEqByGenre(enabled) },
        persist = { audioEffectsStore.setAutoEqByGenre(enabled) },
    )

    fun updateCrossfadeDuration(ms: Long) = applyAndPersist(
        apply = { engine.setCrossfadeDurationMs(ms) },
        persist = { audioStore.setAudioCrossfadeDurationMs(ms) },
    )

    fun updateGaplessPlayback(enabled: Boolean) = applyAndPersist(
        apply = { engine.setGaplessEnabled(enabled) },
        persist = { audioStore.setAudioGaplessEnabled(enabled) },
    )

    /**
     * The ONE ordered seeding entry for [AudioPlayerViewModel.play] — the same
     * field list the setters above own, fed from the persisted slices at track
     * start. Deliberately apply-ONLY: the values came FROM the stores, so the
     * persist leg is skipped (writing them back would round-trip every play and
     * fight in-flight preference edits — this matches the former inline block).
     * The strength fields mirror back through the manager's read accessors, so
     * the prefs slice only feeds the manager. Field coverage is pinned by
     * `AudioEffectsControllerTest`'s seeding-contract guard.
     */
    fun seedForPlayback(audio: AudioSlice, effects: AudioEffectsSlice) {
        effectsManager.setNightModeParams(audio.audioNightModeVolume, audio.audioNightModeGain)
        effectsManager.setDialogueBoostStrength(effects.dialogueBoostStrength)
        effectsManager.setNightModeStrength(effects.nightModeStrength)
        engine.setCrossfadeDurationMs(audio.audioCrossfadeDurationMs)
        engine.setGaplessEnabled(audio.audioGaplessEnabled)
        effectsManager.setReplayGainMode(audio.audioNormalizationMode)
        effectsManager.setReplayGainPreAmpDb(audio.replayGainPreAmpDb)
        effectsManager.setBassBoostStrength(effects.bassBoostStrength)
        effectsManager.setVirtualizerStrength(effects.virtualizerStrength)
        effectsManager.setLrBalance(effects.lrBalance)
        effectsManager.setPitchSemitones(effects.pitchSemitones)
        effectsManager.setAutoEqByGenre(effects.autoEqByGenre)
        // Strengths are flow-less; read them back from the manager (their single
        // home) instead of copying the prefs slice into uiState.
        updateEffects {
            it.copy(
                dialogueBoostStrength = effectsManager.dialogueBoostStrengthState,
                nightModeStrength = effectsManager.nightModeStrengthState,
                bassBoostStrength = effectsManager.bassBoostStrengthState,
            )
        }
    }

    /**
     * Shared choreography every setter above reduces to: apply (synchronous,
     * the manager/engine mutation) → optional uiState slice mirror (only for
     * the flow-less strength fields — everything else reaches the mirror via
     * the VM's flow collectors) → persist (launched). Mirrors
     * `VideoEffectsController.applyAndPersist`; `update` defaults to null so
     * the common no-mirror case stays a two-leg call.
     */
    private inline fun applyAndPersist(
        apply: () -> Unit,
        noinline update: ((AudioEffectsState) -> AudioEffectsState)? = null,
        crossinline persist: suspend () -> Unit,
    ) {
        apply()
        update?.let(updateEffects)
        scope.launch { persist() }
    }
}
