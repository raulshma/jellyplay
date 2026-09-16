package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.EffectsCommandCore
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
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the uniform "update state → sync engine config → persist pref" shape
 * shared by the engine-effect setters that used to live inline on
 * [VideoPlayerViewModel] (night mode, audio delay, decoder mode, audio
 * passthrough, audio normalization, channel mix, bass boost, virtualizer,
 * reverb). The choreography now rides the shared [EffectsCommandCore] —
 * the same core player-audio's `AudioEffectsController` rides, with THIS
 * adapter's declared STATE_FIRST leg order (the state slice is the
 * `EngineConfigBuilder` input, so it must flip before [syncConfig]
 * rebuilds the config; see the core's KDoc).
 *
 * **State ownership:** the audio-effects slice [AudioEffectsState] is this
 * class's single home, exposed as a read-only [StateFlow] off the core. The
 * ViewModel re-exposes it and keeps thin delegating wrappers so its public
 * API — and the 27 test references to these setters — stay valid.
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
    scope: CoroutineScope,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val playbackStore: PlaybackStore,
    private val syncConfig: () -> Unit,
) {
    private val core = EffectsCommandCore(
        initialState = AudioEffectsState(),
        scope = scope,
        order = EffectsCommandCore.Order.STATE_FIRST,
    )

    val state: StateFlow<AudioEffectsState> get() = core.state

    /**
     * The adapter's command template: every setter's apply leg is
     * [syncConfig] (STATE_FIRST — the state flip feeds the config rebuild).
     */
    private fun command(
        update: (AudioEffectsState) -> AudioEffectsState,
        persist: suspend () -> Unit,
    ) = core.applyAndPersist(apply = syncConfig, update = update, persist = persist)

    fun toggleNightMode() {
        val newVal = !state.value.nightModeEnabled
        command(
            update = { it.copy(nightModeEnabled = newVal) },
            persist = { audioEffectsStore.setNightModeEnabled(newVal) },
        )
    }

    fun setNightModeStrength(strength: EffectStrength) = command(
        update = { it.copy(nightModeStrength = strength) },
        persist = { audioEffectsStore.setNightModeStrength(strength) },
    )

    fun setAudioDelay(ms: Long) = command(
        update = { it.copy(audioDelayMs = ms) },
        persist = { audioStore.setAudioDelay(ms) },
    )

    fun setDecoderMode(mode: DecoderMode) = command(
        update = { it.copy(decoderMode = mode) },
        persist = { playbackStore.setDecoderMode(mode) },
    )

    fun setAudioPassthrough(enabled: Boolean) = command(
        update = { it.copy(audioPassthrough = enabled) },
        persist = { playbackStore.setAudioPassthrough(enabled) },
    )

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        val enabled = mode != AudioNormalizationMode.NONE
        command(
            update = { it.copy(audioNormalizationMode = mode, audioNormalizationEnabled = enabled) },
            persist = {
                audioStore.setAudioNormalizationMode(mode)
                audioStore.setAudioNormalizationEnabled(enabled)
            },
        )
    }

    fun toggleAudioNormalization() {
        val newVal = !state.value.audioNormalizationEnabled
        command(
            update = { it.copy(audioNormalizationEnabled = newVal) },
            persist = { audioStore.setAudioNormalizationEnabled(newVal) },
        )
    }

    fun setChannelMixMode(mode: ChannelMixMode) {
        val enabled = mode != ChannelMixMode.AUTO
        command(
            update = { it.copy(channelMixMode = mode, channelMixEnabled = enabled) },
            persist = {
                audioStore.setChannelMixMode(mode)
                audioStore.setChannelMixEnabled(enabled)
            },
        )
    }

    fun toggleChannelMix() {
        val newVal = !state.value.channelMixEnabled
        command(
            update = { it.copy(channelMixEnabled = newVal) },
            persist = { audioStore.setChannelMixEnabled(newVal) },
        )
    }

    fun toggleBassBoost() {
        val newVal = !state.value.bassBoostEnabled
        command(
            update = { it.copy(bassBoostEnabled = newVal) },
            persist = { audioEffectsStore.setBassBoostEnabled(newVal) },
        )
    }

    fun setBassBoostStrength(strength: EffectStrength) = command(
        update = { it.copy(bassBoostStrength = strength) },
        persist = { audioEffectsStore.setBassBoostStrength(strength) },
    )

    fun toggleVirtualizer() {
        val newVal = !state.value.virtualizerEnabled
        command(
            update = { it.copy(virtualizerEnabled = newVal) },
            persist = { audioEffectsStore.setVirtualizerEnabled(newVal) },
        )
    }

    fun setVirtualizerStrength(strength: Int) = command(
        update = { it.copy(virtualizerStrength = strength) },
        persist = { audioEffectsStore.setVirtualizerStrength(strength) },
    )

    fun setReverbPreset(preset: ReverbPreset) = command(
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
        core.updateState {
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
}
