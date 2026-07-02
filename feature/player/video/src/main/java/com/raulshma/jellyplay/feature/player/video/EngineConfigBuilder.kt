package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig

/**
 * Builds the engine [EngineConfig] from the current UI state plus the cached
 * preferences snapshot.
 *
 * Extracted verbatim from `VideoPlayerViewModel.updateConfigWithUiState()`
 * (recommendation #1 — continue the collaborator-extraction pattern) so the
 * audio-effects / decoder / subtitle-delay → [EngineConfig] mapping is a pure,
 * unit-testable function instead of an inline block inside the ~2 kLOC
 * ViewModel. The ViewModel still owns the live state and applies the result to
 * the active engine; this object performs only the translation.
 *
 * Notes:
 *  - `equalizerEnabled` is passed explicitly because it lives on the ViewModel
 *    (it is not part of [VideoPlayerUiState]).
 *  - `equalizerSettings`, `volumeBoost*` and `pauseOnAudioFocusLoss` are read
 *    from the cached [UserPreferences] snapshot, preserving the prior behaviour.
 *
 * Use [buildFromPreferences] on the *initial-load* / engine-swap paths where the
 * config is being constructed before any UI state exists (e.g. from
 * `PlayerSessionManager`). Routing both paths through this object keeps a single
 * construction site — previously `PlayerSessionManager` built its own inline
 * `EngineConfig` literal that silently dropped bass boost / virtualizer /
 * reverb / volume boost / video effects (which only appear in `build`/`buildFromPreferences`),
 * so those prefs were ignored until the first runtime `updateConfig`.
 */
internal object EngineConfigBuilder {

    fun build(
        state: VideoPlayerUiState,
        equalizerEnabled: Boolean,
        prefs: UserPreferences,
    ): EngineConfig = EngineConfig(
        decoderMode = state.decoderMode,
        audioPassthrough = state.audioPassthrough,
        audioDelayMs = state.audioDelayMs,
        subtitleDelayMs = state.subtitleStyle.offsetMs,
        subtitleStyle = state.subtitleStyle,
        videoEffects = state.videoEffects,
        audioEffects = audioEffects(
            dialogueBoostEnabled = state.dialogueBoostEnabled,
            dialogueBoostStrength = state.dialogueBoostStrength,
            nightModeEnabled = state.nightModeEnabled,
            nightModeStrength = state.nightModeStrength,
            equalizerEnabled = equalizerEnabled,
            audioNormalizationMode = state.audioNormalizationMode,
            audioNormalizationEnabled = state.audioNormalizationEnabled,
            channelMixMode = state.channelMixMode,
            channelMixEnabled = state.channelMixEnabled,
            bassBoostEnabled = state.bassBoostEnabled,
            bassBoostStrength = state.bassBoostStrength,
            virtualizerEnabled = state.virtualizerEnabled,
            virtualizerStrength = state.virtualizerStrength,
            reverbPreset = state.reverbPreset,
            prefs = prefs,
        ),
        pauseOnAudioFocusLoss = prefs.pauseOnAudioFocusLoss,
    )

    /**
     * Initial-load / engine-swap variant. Builds the config directly from a
     * [UserPreferences] snapshot (the state available before the player UI has
     * hydrated). [mediaStreams] drives HDR-aware subtitle styling; [itemId]
     * resolves per-item video effects; [engineSpecific] is the caller-resolved
     * typed per-engine config (or `null`).
     */
    fun buildFromPreferences(
        prefs: UserPreferences,
        mediaStreams: List<MediaStream>,
        itemId: String?,
        engineSpecific: EngineSpecificConfig?,
    ): EngineConfig {
        val isHdr = prefs.isHdrFromStreams(mediaStreams)
        val subtitleStyle = prefs.resolvedSubtitleStyle(isHdr = isHdr)
        return EngineConfig(
            decoderMode = prefs.decoderMode,
            audioPassthrough = prefs.audioPassthrough,
            audioDelayMs = prefs.audioDelayMs,
            subtitleDelayMs = subtitleStyle.offsetMs,
            subtitleStyle = subtitleStyle,
            videoEffects = itemId?.let { prefs.videoEffectsByItem[it] } ?: VideoEffectsConfig(),
            audioEffects = audioEffects(
                dialogueBoostEnabled = prefs.dialogueBoostEnabled,
                dialogueBoostStrength = prefs.dialogueBoostStrength,
                nightModeEnabled = prefs.nightModeEnabled,
                nightModeStrength = prefs.nightModeStrength,
                equalizerEnabled = prefs.equalizerEnabled,
                audioNormalizationMode = prefs.audioNormalizationMode,
                audioNormalizationEnabled = prefs.audioNormalizationEnabled,
                channelMixMode = prefs.channelMixMode,
                channelMixEnabled = prefs.channelMixEnabled,
                bassBoostEnabled = prefs.bassBoostEnabled,
                bassBoostStrength = prefs.bassBoostStrength,
                virtualizerEnabled = prefs.virtualizerEnabled,
                virtualizerStrength = prefs.virtualizerStrength,
                reverbPreset = prefs.reverbPreset,
                prefs = prefs,
            ),
            engineSpecific = engineSpecific,
            pauseOnAudioFocusLoss = prefs.pauseOnAudioFocusLoss,
        )
    }

    /**
     * Shared [AudioEffectsConfig] constructor so the two entry points cannot
     * drift (the original bug was the load path omitting bass/virtualizer/
     * reverb/volume-boost that this centralises).
     */
    private fun audioEffects(
        dialogueBoostEnabled: Boolean,
        dialogueBoostStrength: EffectStrength,
        nightModeEnabled: Boolean,
        nightModeStrength: EffectStrength,
        equalizerEnabled: Boolean,
        audioNormalizationMode: AudioNormalizationMode,
        audioNormalizationEnabled: Boolean,
        channelMixMode: ChannelMixMode,
        channelMixEnabled: Boolean,
        bassBoostEnabled: Boolean,
        bassBoostStrength: EffectStrength,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Int,
        reverbPreset: ReverbPreset,
        prefs: UserPreferences,
    ): AudioEffectsConfig = AudioEffectsConfig(
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        nightModeGain = prefs.audioNightModeGain,
        equalizerEnabled = equalizerEnabled,
        equalizerSettings = prefs.equalizerSettings,
        audioNormalizationMode = audioNormalizationMode,
        audioNormalizationEnabled = audioNormalizationEnabled,
        channelMixMode = channelMixMode,
        channelMixEnabled = channelMixEnabled,
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        reverbPreset = reverbPreset,
        volumeBoostEnabled = prefs.volumeBoostEnabled,
        volumeBoostGain = prefs.volumeBoostGain,
    )
}
