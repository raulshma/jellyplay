package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class AudioSettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val audioCacheClearer: AudioCacheClearer,
) : JellyPlayViewModel() {

    /** Audio-screen slice — recomposes this screen only on audio-field writes. */
    val preferences: StateFlow<AudioPreferences> = projections.audioPreferences

    private val advancedSettings = AdvancedSettingsGate(appearanceStore, editor)

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    fun setAudioAutoplayNext(enabled: Boolean) = editor.setAudioAutoplayNext(enabled)
    fun setAudioVisualizerEnabled(enabled: Boolean) =
        editor.edit { audio.setAudioVisualizerEnabled(enabled) }
    fun setPreferAudioDescription(enabled: Boolean) =
        editor.edit { subtitle.setPreferAudioDescription(enabled) }
    fun setAudioNightModeVolume(volume: Float) =
        editor.edit { audio.setAudioNightModeVolume(volume) }
    fun setAudioNightModeGain(gain: Int) =
        editor.edit { audio.setAudioNightModeGain(gain) }
    fun setAudioSkipPreviousThresholdMs(ms: Long) =
        editor.edit { audio.setAudioSkipPreviousThresholdMs(ms) }
    fun setGaplessEnabled(enabled: Boolean) = editor.setGaplessEnabled(enabled)
    fun setCrossfadeDurationMs(ms: Long) = editor.setCrossfadeDurationMs(ms)
    fun setAudioPreloadBufferSize(size: PreloadBufferSize) =
        editor.edit { audio.setAudioPreloadBufferSize(size) }
    fun setAudioNormalizationMode(mode: AudioNormalizationMode) =
        editor.edit { audio.setAudioNormalizationMode(mode) }
    fun setReplayGainPreAmpDb(db: Float) =
        editor.edit { audio.setReplayGainPreAmpDb(db) }
    fun setEqualizerEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setEqualizerEnabled(enabled) }
    fun setEqualizerPreset(preset: EqualizerPreset) =
        editor.edit { audioEffects.setEqualizerPreset(preset) }
    fun setDialogueBoostEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setDialogueBoostEnabled(enabled) }
    fun setDialogueBoostStrength(strength: EffectStrength) =
        editor.edit { audioEffects.setDialogueBoostStrength(strength) }
    fun setNightModeEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setNightModeEnabled(enabled) }
    fun setNightModeStrength(strength: EffectStrength) =
        editor.edit { audioEffects.setNightModeStrength(strength) }
    fun setBassBoostEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setBassBoostEnabled(enabled) }
    fun setBassBoostStrength(strength: EffectStrength) =
        editor.edit { audioEffects.setBassBoostStrength(strength) }
    fun setVirtualizerEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setVirtualizerEnabled(enabled) }
    fun setVirtualizerStrength(strength: Int) =
        editor.edit { audioEffects.setVirtualizerStrength(strength) }
    fun setReverbPreset(preset: ReverbPreset) =
        editor.edit { audioEffects.setReverbPreset(preset) }
    fun setAutoEqByGenre(enabled: Boolean) =
        editor.edit { audioEffects.setAutoEqByGenre(enabled) }
    fun setChannelMixEnabled(enabled: Boolean) =
        editor.edit { audio.setChannelMixEnabled(enabled) }
    fun setChannelMixMode(mode: ChannelMixMode) =
        editor.edit { audio.setChannelMixMode(mode) }
    fun setLrBalance(balance: Float) =
        editor.edit { audioEffects.setLrBalance(balance) }
    fun setPitchSemitones(semitones: Float) =
        editor.edit { audioEffects.setPitchSemitones(semitones) }
    fun setAudioDefaultSpeed(speed: Float) = editor.setAudioDefaultSpeed(speed)
    fun setSleepTimerDurationMs(ms: Long) =
        editor.edit { audio.setSleepTimerDurationMs(ms) }
    fun setAudioCachingEnabled(enabled: Boolean) =
        editor.edit { audioCache.setAudioCachingEnabled(enabled) }
    fun setAudioCacheSizeMb(sizeMb: Int) =
        editor.edit { audioCache.setAudioCacheSizeMb(sizeMb) }
    fun setAudioPrefetchLookahead(lookahead: Int) =
        editor.edit { audioCache.setAudioPrefetchLookahead(lookahead) }
    fun setAudioPrefetchBackfill(backfill: Int) =
        editor.edit { audioCache.setAudioPrefetchBackfill(backfill) }
    fun setEqualizerSettings(settings: EqualizerSettings) =
        editor.edit { audioEffects.setEqualizerSettings(settings) }

    fun setVolumeBoostEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setVolumeBoostEnabled(enabled) }

    fun setVolumeBoostGain(gain: Int) =
        editor.edit { audioEffects.setVolumeBoostGain(gain) }

    fun setAudioCacheNetworkPolicy(policy: com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy) =
        editor.edit { audioCache.setAudioCacheNetworkPolicy(policy) }

    fun clearAudioCache() {
        launch { audioCacheClearer.clear() }
    }
}
