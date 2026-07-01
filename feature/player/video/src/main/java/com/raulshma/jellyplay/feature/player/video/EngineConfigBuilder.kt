package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.UserPreferences
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
        audioEffects = AudioEffectsConfig(
            dialogueBoostEnabled = state.dialogueBoostEnabled,
            dialogueBoostStrength = state.dialogueBoostStrength,
            nightModeEnabled = state.nightModeEnabled,
            nightModeStrength = state.nightModeStrength,
            equalizerEnabled = equalizerEnabled,
            equalizerSettings = prefs.equalizerSettings,
            audioNormalizationMode = state.audioNormalizationMode,
            audioNormalizationEnabled = state.audioNormalizationEnabled,
            channelMixMode = state.channelMixMode,
            channelMixEnabled = state.channelMixEnabled,
            bassBoostEnabled = state.bassBoostEnabled,
            bassBoostStrength = state.bassBoostStrength,
            virtualizerEnabled = state.virtualizerEnabled,
            virtualizerStrength = state.virtualizerStrength,
            reverbPreset = state.reverbPreset,
            volumeBoostEnabled = prefs.volumeBoostEnabled,
            volumeBoostGain = prefs.volumeBoostGain,
        ),
        pauseOnAudioFocusLoss = prefs.pauseOnAudioFocusLoss,
    )
}
