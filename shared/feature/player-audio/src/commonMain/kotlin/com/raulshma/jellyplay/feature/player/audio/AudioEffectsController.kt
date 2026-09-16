package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.EffectsCommandCore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the ONE "apply to manager → mirror → persist" choreography shared by
 * the audio player's effects setters (extracted from [AudioPlayerViewModel],
 * the same inline pattern player-video's `VideoEffectsController` was itself
 * extracted from — the choreography now rides the shared
 * [EffectsCommandCore]). Every public fun is one ordered entry: the apply
 * leg is synchronous so the manager (and the screen) see the new value in
 * the same frame; the persist leg is a suspend block launched on [scope] so
 * DataStore writes stay off the main thread.
 *
 * **State ownership (the controller's own [state], not a uiState mirror).**
 * The `AudioEffectsState` slice is this controller's single home via its
 * [EffectsCommandCore]: the former `updateEffects` seam into the
 * ViewModel's uiState and the VM's manager-flow mirror collectors both died
 * — the collectors moved HERE (init below, same combines and field lists)
 * and the screen reads [AudioPlayerViewModel.effectsState] (a re-exposure
 * of [state], the `SubtitlePreviewController` precedent). The flow-backed
 * fields therefore mirror straight off the manager; the flow-less strength
 * fields mirror via the command `update` leg and [seedForPlayback]'s
 * read-back.
 *
 * **Authoritative source: the manager, not the state mirror.** Toggles and
 * the equalizer folds persist the value read from the manager's synchronous
 * `StateFlow.value` immediately AFTER the apply leg — the
 * [AudioPlaybackManager] re-exposes the processor flows by reference, and both
 * the Android processor and the desktop state machine flip them inside the
 * setter, so `.value` is correct in the same frame. The state mirror
 * collectors lag a dispatch, and persisting their read-back was only correct
 * by dispatch-order luck. The persisted VALUE is unchanged; only its
 * source is the manager, and the AudioPreferencesReducer timing is
 * untouched.
 *
 * **Strength fields** (`dialogueBoost`/`nightMode`/`bassBoost`): the manager is
 * their single home via the plain read accessors
 * [AudioEffectsManager.dialogueBoostStrengthState] /
 * [AudioEffectsManager.nightModeStrengthState] /
 * [AudioEffectsManager.bassBoostStrengthState] (they carry no flows). The
 * setters mirror their argument (the manager setters are unconditional, so
 * argument == manager state) and [seedForPlayback] feeds prefs through the
 * manager and mirrors back through the accessors — the prefs slice never
 * touches the state slice directly.
 *
 * Deliberately NOT migrated: the [SleepTimerManager] workflow (audio has its
 * own [AudioSleepTimerController]) and the queue/engine transport setters.
 *
 * Not a Koin type: the ViewModel constructs it directly over its own [scope]
 * so the controller shares the VM's lifecycle. The slice type stays
 * module-local (this is player-audio's own `AudioPlayerUiState.AudioEffectsState`).
 */
internal class AudioEffectsController(
    private val scope: CoroutineScope,
    private val effectsManager: AudioEffectsManager,
    private val engine: AudioPlayerEngine,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
) {
    /**
     * The shared choreography core (APPLY_FIRST declared divergence: the DSP
     * manager flip is the source of truth and runs before the optional
     * strength-mirror write; see [EffectsCommandCore]'s KDoc for why this
     * adapter must not adopt STATE_FIRST).
     */
    private val core = EffectsCommandCore(
        initialState = AudioEffectsState(),
        scope = scope,
        order = EffectsCommandCore.Order.APPLY_FIRST,
    )

    /** The audio-effects slice's single home, exposed read-only. */
    val state: StateFlow<AudioEffectsState> get() = core.state

    init {
        // Manager-flow mirror collectors — the former AudioPlayerViewModel
        // init collectors, re-targeted at the owned slice (same combines,
        // same field lists, same scope). The flow-less strength cells have
        // no collector; their mirrors are the command `update` legs and
        // seedForPlayback's read-back.
        scope.launch {
            combine(
                effectsManager.nightModeEnabled,
                effectsManager.dialogueBoostEnabled,
                effectsManager.equalizerEnabled,
                effectsManager.equalizerSettings,
                effectsManager.equalizerPreset,
            ) { night, dialogue, eqEn, eqSet, eqPre ->
                core.updateState {
                    it.copy(
                        nightModeEnabled = night,
                        dialogueBoostEnabled = dialogue,
                        equalizerEnabled = eqEn,
                        equalizerSettings = eqSet,
                        equalizerPreset = eqPre,
                    )
                }
            }.collect {}
        }
        scope.launch {
            combine(
                effectsManager.bassBoostEnabled,
                effectsManager.virtualizerEnabled,
                effectsManager.virtualizerStrength,
                effectsManager.reverbPresetState,
            ) { bass, virtEn, virtStr, rev ->
                core.updateState {
                    it.copy(
                        bassBoostEnabled = bass,
                        virtualizerEnabled = virtEn,
                        virtualizerStrength = virtStr,
                        reverbPreset = rev,
                    )
                }
            }.collect {}
        }
        scope.launch {
            combine(
                effectsManager.lrBalance,
                effectsManager.pitchSemitones,
                effectsManager.autoEqByGenre,
            ) { lr, pitch, autoEq ->
                core.updateState { it.copy(lrBalance = lr, pitchSemitones = pitch, autoEqByGenre = autoEq) }
            }.collect {}
        }
        // The replay-gain pair the VM used to fold alongside
        // `crossfadeDurationMs` — that uiState field keeps its own collector
        // on the VM; the effects half mirrors here.
        scope.launch {
            combine(
                effectsManager.replayGainMode,
                effectsManager.replayGainPreAmpDb,
            ) { rg, pre ->
                core.updateState { it.copy(normalizationMode = rg, preAmpDb = pre) }
            }.collect {}
        }
    }

    fun toggleDialogueBoost() = core.applyAndPersist(
        apply = effectsManager::toggleDialogueBoost,
        persist = { audioEffectsStore.setDialogueBoostEnabled(effectsManager.dialogueBoostEnabled.value) },
    )

    fun setDialogueBoostStrength(strength: EffectStrength) = core.applyAndPersist(
        apply = { effectsManager.setDialogueBoostStrength(strength) },
        update = { it.copy(dialogueBoostStrength = strength) },
        persist = { audioEffectsStore.setDialogueBoostStrength(strength) },
    )

    fun toggleNightMode() = core.applyAndPersist(
        apply = effectsManager::toggleNightMode,
        persist = { audioEffectsStore.setNightModeEnabled(effectsManager.nightModeEnabled.value) },
    )

    fun setNightModeStrength(strength: EffectStrength) = core.applyAndPersist(
        apply = { effectsManager.setNightModeStrength(strength) },
        update = { it.copy(nightModeStrength = strength) },
        persist = { audioEffectsStore.setNightModeStrength(strength) },
    )

    fun setReplayGainMode(mode: AudioNormalizationMode) = core.applyAndPersist(
        apply = { effectsManager.setReplayGainMode(mode) },
        persist = { audioStore.setAudioNormalizationMode(mode) },
    )

    fun setReplayGainPreAmpDb(db: Float) = core.applyAndPersist(
        apply = { effectsManager.setReplayGainPreAmpDb(db) },
        persist = { audioStore.setReplayGainPreAmpDb(db) },
    )

    fun toggleEqualizer() = core.applyAndPersist(
        apply = effectsManager::toggleEqualizer,
        persist = { audioEffectsStore.setEqualizerEnabled(effectsManager.equalizerEnabled.value) },
    )

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) = core.applyAndPersist(
        apply = { effectsManager.setEqualizerBand(bandIndex, levelDb) },
        persist = { audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value) },
    )

    fun resetEqualizer() = core.applyAndPersist(
        apply = effectsManager::resetEqualizer,
        persist = {
            audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value)
            audioEffectsStore.setEqualizerPreset(effectsManager.equalizerPreset.value)
        },
    )

    fun setEqualizerPreset(preset: EqualizerPreset) = core.applyAndPersist(
        apply = { effectsManager.setEqualizerPreset(preset) },
        persist = {
            audioEffectsStore.setEqualizerPreset(preset)
            audioEffectsStore.setEqualizerSettings(effectsManager.equalizerSettings.value)
        },
    )

    fun toggleBassBoost() = core.applyAndPersist(
        apply = effectsManager::toggleBassBoost,
        persist = { audioEffectsStore.setBassBoostEnabled(effectsManager.bassBoostEnabled.value) },
    )

    fun setBassBoostStrength(strength: EffectStrength) = core.applyAndPersist(
        apply = { effectsManager.setBassBoostStrength(strength) },
        update = { it.copy(bassBoostStrength = strength) },
        persist = { audioEffectsStore.setBassBoostStrength(strength) },
    )

    fun toggleVirtualizer() = core.applyAndPersist(
        apply = effectsManager::toggleVirtualizer,
        persist = { audioEffectsStore.setVirtualizerEnabled(effectsManager.virtualizerEnabled.value) },
    )

    fun setVirtualizerStrength(strength: Int) = core.applyAndPersist(
        apply = { effectsManager.setVirtualizerStrength(strength) },
        persist = { audioEffectsStore.setVirtualizerStrength(strength) },
    )

    fun setReverbPreset(preset: ReverbPreset) = core.applyAndPersist(
        apply = { effectsManager.setReverbPreset(preset) },
        persist = { audioEffectsStore.setReverbPreset(preset) },
    )

    fun setLrBalance(balance: Float) = core.applyAndPersist(
        apply = { effectsManager.setLrBalance(balance) },
        persist = { audioEffectsStore.setLrBalance(balance) },
    )

    fun setPitchSemitones(semitones: Float) = core.applyAndPersist(
        apply = { effectsManager.setPitchSemitones(semitones) },
        persist = { audioEffectsStore.setPitchSemitones(semitones) },
    )

    fun setAutoEqByGenre(enabled: Boolean) = core.applyAndPersist(
        apply = { effectsManager.setAutoEqByGenre(enabled) },
        persist = { audioEffectsStore.setAutoEqByGenre(enabled) },
    )

    fun updateCrossfadeDuration(ms: Long) = core.applyAndPersist(
        apply = { engine.setCrossfadeDurationMs(ms) },
        persist = { audioStore.setAudioCrossfadeDurationMs(ms) },
    )

    fun updateGaplessPlayback(enabled: Boolean) = core.applyAndPersist(
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
        // home) instead of copying the prefs slice into the state slice.
        core.updateState {
            it.copy(
                dialogueBoostStrength = effectsManager.dialogueBoostStrengthState,
                nightModeStrength = effectsManager.nightModeStrengthState,
                bassBoostStrength = effectsManager.bassBoostStrengthState,
            )
        }
    }
}
