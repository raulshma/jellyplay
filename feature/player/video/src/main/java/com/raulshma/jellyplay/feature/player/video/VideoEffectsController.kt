package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the uniform "update uiState → sync engine config → persist pref" shape
 * shared by the engine-effect setters that used to live inline on
 * [VideoPlayerViewModel] (night mode, audio delay, decoder mode, audio
 * passthrough, audio normalization, channel mix, bass boost, virtualizer,
 * reverb).
 *
 * Why these and not the rest:
 *  - Dialogue Boost stays on the VM — it persists per-item/series via
 *    [com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository],
 *    not via [PreferencesEditor].
 *  - Video Effects stays on the VM — it persists per-item and is gated by
 *    Cinema Mode state.
 *  - Equalizer toggle stays on the VM — its `equalizerEnabled` flag lives as
 *    a VM field consumed by [EngineConfigBuilder.build] alongside the prefs.
 *
 * Extracted to drop ~150 lines of identical-shape setters from the 2.7k-LOC
 * ViewModel (Divergent Change). The VM exposes thin delegating wrappers so
 * its public API — and the 27 test references to these setters — stay valid.
 *
 * Not a Hilt type: the VM constructs it directly with its own scope/uiState
 * handle so the controller shares the VM's lifecycle and state container.
 */
internal class VideoEffectsController(
    private val scope: CoroutineScope,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val playbackStore: PlaybackStore,
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val syncConfig: () -> Unit,
) {
    fun toggleNightMode() {
        val newVal = !getUiState().nightModeEnabled
        applyAndPersist(
            update = { it.copy(nightModeEnabled = newVal) },
            persist = { audioEffectsStore.setNightModeEnabled(newVal) },
        )
    }

    fun setNightModeStrength(strength: EffectStrength) = applyAndPersist(
        update = { it.copy(nightModeStrength = strength) },
        persist = { audioEffectsStore.setNightModeStrength(strength) },
    )

    fun setAudioDelay(ms: Long) = applyAndPersist(
        update = { it.copy(audioDelayMs = ms) },
        persist = { audioStore.setAudioDelay(ms) },
    )

    fun setDecoderMode(mode: DecoderMode) = applyAndPersist(
        update = { it.copy(decoderMode = mode) },
        persist = { playbackStore.setDecoderMode(mode) },
    )

    fun setAudioPassthrough(enabled: Boolean) = applyAndPersist(
        update = { it.copy(audioPassthrough = enabled) },
        persist = { playbackStore.setAudioPassthrough(enabled) },
    )

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        val enabled = mode != AudioNormalizationMode.NONE
        applyAndPersist(
            update = { it.copy(audioNormalizationMode = mode, audioNormalizationEnabled = enabled) },
            persist = {
                audioStore.setAudioNormalizationMode(mode)
                audioStore.setAudioNormalizationEnabled(enabled)
            },
        )
    }

    fun toggleAudioNormalization() {
        val newVal = !getUiState().audioNormalizationEnabled
        applyAndPersist(
            update = { it.copy(audioNormalizationEnabled = newVal) },
            persist = { audioStore.setAudioNormalizationEnabled(newVal) },
        )
    }

    fun setChannelMixMode(mode: ChannelMixMode) {
        val enabled = mode != ChannelMixMode.AUTO
        applyAndPersist(
            update = { it.copy(channelMixMode = mode, channelMixEnabled = enabled) },
            persist = {
                audioStore.setChannelMixMode(mode)
                audioStore.setChannelMixEnabled(enabled)
            },
        )
    }

    fun toggleChannelMix() {
        val newVal = !getUiState().channelMixEnabled
        applyAndPersist(
            update = { it.copy(channelMixEnabled = newVal) },
            persist = { audioStore.setChannelMixEnabled(newVal) },
        )
    }

    fun toggleBassBoost() {
        val newVal = !getUiState().bassBoostEnabled
        applyAndPersist(
            update = { it.copy(bassBoostEnabled = newVal) },
            persist = { audioEffectsStore.setBassBoostEnabled(newVal) },
        )
    }

    fun setBassBoostStrength(strength: EffectStrength) = applyAndPersist(
        update = { it.copy(bassBoostStrength = strength) },
        persist = { audioEffectsStore.setBassBoostStrength(strength) },
    )

    fun toggleVirtualizer() {
        val newVal = !getUiState().virtualizerEnabled
        applyAndPersist(
            update = { it.copy(virtualizerEnabled = newVal) },
            persist = { audioEffectsStore.setVirtualizerEnabled(newVal) },
        )
    }

    fun setVirtualizerStrength(strength: Int) = applyAndPersist(
        update = { it.copy(virtualizerStrength = strength) },
        persist = { audioEffectsStore.setVirtualizerStrength(strength) },
    )

    fun setReverbPreset(preset: ReverbPreset) = applyAndPersist(
        update = { it.copy(reverbPreset = preset) },
        persist = { audioEffectsStore.setReverbPreset(preset) },
    )

    /**
     * Shared "update uiState → sync engine config → persist pref" shape every
     * setter above reduces to. `persist` is a suspend block capturing one or
     * more DataStore setters; it runs inside the launched coroutine so writes
     * stay off the main thread. `update` and `syncConfig` are synchronous so
     * the engine sees the new value in the same frame the UI does. Modifiers:
     * `noinline` because `update` is stored in a lambda, `crossinline` because
     * `persist` is inlined into a nested lambda (the launched coroutine body).
     */
    private inline fun applyAndPersist(
        noinline update: (VideoPlayerUiState) -> VideoPlayerUiState,
        crossinline persist: suspend () -> Unit,
    ) {
        updateUiState(update)
        syncConfig()
        scope.launch { persist() }
    }
}
