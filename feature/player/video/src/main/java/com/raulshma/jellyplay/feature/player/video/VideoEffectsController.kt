package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the uniform "update state → sync engine config → persist pref" shape
 * shared by the engine-effect setters that used to live inline on
 * [VideoPlayerViewModel] (night mode, audio delay, decoder mode, audio
 * passthrough, audio normalization, channel mix, bass boost, virtualizer,
 * reverb).
 *
 * **State ownership:** the audio-effects slice [AudioEffectsState] is this
 * class's single home, exposed as a read-only [StateFlow]. The ViewModel
 * re-exposes it and keeps thin delegating wrappers so its public API — and the
 * 27 test references to these setters — stay valid.
 *
 * **Item-switch semantics: user effects PERSIST across episodes** (they were
 * whitelisted in the ViewModel's former reset ritual — persistence is now
 * simply "not reset"). There is deliberately no `resetForItem()` for these
 * fields. The one per-item exception in the former whitelist — zeroing the
 * dialogue boost so a stored per-item/series rule can re-apply — concerns
 * `dialogueBoost*`, which is resolver-driven and stays on
 * [VideoPlayerUiState]; the ViewModel keeps that reset next to its preference
 * resolution.
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
 * Not a Hilt type: the VM constructs it directly with its own scope so the
 * controller shares the VM's lifecycle.
 */
internal class VideoEffectsController(
    private val scope: CoroutineScope,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val playbackStore: PlaybackStore,
    private val syncConfig: () -> Unit,
) {
    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    fun toggleNightMode() {
        val newVal = !_state.value.nightModeEnabled
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
        val newVal = !_state.value.audioNormalizationEnabled
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
        val newVal = !_state.value.channelMixEnabled
        applyAndPersist(
            update = { it.copy(channelMixEnabled = newVal) },
            persist = { audioStore.setChannelMixEnabled(newVal) },
        )
    }

    fun toggleBassBoost() {
        val newVal = !_state.value.bassBoostEnabled
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
        val newVal = !_state.value.virtualizerEnabled
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
     * Seeds the preference-backed fields from the cached aggregate when a new
     * engine binds (the engineFlow collector's former UiState writes). Exactly
     * the fields that collector seeded — bass boost / virtualizer / reverb were
     * never seeded there and keep their live values. The engine config is NOT
     * re-synced here; the caller pushes the built config to the new engine.
     */
    fun seedFromPreferences(
        audioDelayMs: Long,
        decoderMode: DecoderMode,
        audioPassthrough: Boolean,
        nightModeEnabled: Boolean,
        nightModeStrength: EffectStrength,
        audioNormalizationMode: AudioNormalizationMode,
        audioNormalizationEnabled: Boolean,
        channelMixMode: ChannelMixMode,
        channelMixEnabled: Boolean,
    ) {
        _state.update {
            it.copy(
                audioDelayMs = audioDelayMs,
                decoderMode = decoderMode,
                audioPassthrough = audioPassthrough,
                nightModeEnabled = nightModeEnabled,
                nightModeStrength = nightModeStrength,
                audioNormalizationMode = audioNormalizationMode,
                audioNormalizationEnabled = audioNormalizationEnabled,
                channelMixMode = channelMixMode,
                channelMixEnabled = channelMixEnabled,
            )
        }
    }

    /**
     * Shared "update state → sync engine config → persist pref" shape every
     * setter above reduces to. `persist` is a suspend block capturing one or
     * more DataStore setters; it runs inside the launched coroutine so writes
     * stay off the main thread. `update` and [syncConfig] are synchronous so
     * the engine sees the new value in the same frame the UI does. Modifiers:
     * `noinline` because `update` is stored in a lambda, `crossinline` because
     * `persist` is inlined into a nested lambda (the launched coroutine body).
     */
    private inline fun applyAndPersist(
        noinline update: (AudioEffectsState) -> AudioEffectsState,
        crossinline persist: suspend () -> Unit,
    ) {
        _state.update(update)
        syncConfig()
        scope.launch { persist() }
    }
}
