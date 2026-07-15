package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
    private val audioStreamCache: AudioStreamCache,
) : JellyPlayViewModel() {

    /** Audio-screen slice — recomposes this screen only on audio-field writes. */
    val preferences: StateFlow<AudioPreferences> = store.audioPreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    fun setAudioAutoplayNext(enabled: Boolean) = editor.setAudioAutoplayNext(enabled)
    fun setAudioVisualizerEnabled(enabled: Boolean) =
        editor.edit { setAudioVisualizerEnabled(enabled) }
    fun setPreferAudioDescription(enabled: Boolean) =
        editor.edit { setPreferAudioDescription(enabled) }
    fun setAudioNightModeVolume(volume: Float) =
        editor.edit { setAudioNightModeVolume(volume) }
    fun setAudioNightModeGain(gain: Int) =
        editor.edit { setAudioNightModeGain(gain) }
    fun setAudioSkipPreviousThresholdMs(ms: Long) =
        editor.edit { setAudioSkipPreviousThresholdMs(ms) }
    fun setGaplessEnabled(enabled: Boolean) = editor.setGaplessEnabled(enabled)
    fun setCrossfadeDurationMs(ms: Long) = editor.setCrossfadeDurationMs(ms)
    fun setAudioPreloadBufferSize(size: PreloadBufferSize) =
        editor.edit { setAudioPreloadBufferSize(size) }
    fun setAudioNormalizationMode(mode: AudioNormalizationMode) =
        editor.edit { setAudioNormalizationMode(mode) }
    fun setReplayGainPreAmpDb(db: Float) =
        editor.edit { setReplayGainPreAmpDb(db) }
    fun setEqualizerEnabled(enabled: Boolean) =
        editor.edit { setEqualizerEnabled(enabled) }
    fun setEqualizerPreset(preset: EqualizerPreset) =
        editor.edit { setEqualizerPreset(preset) }
    fun setDialogueBoostEnabled(enabled: Boolean) =
        editor.edit { setDialogueBoostEnabled(enabled) }
    fun setDialogueBoostStrength(strength: EffectStrength) =
        editor.edit { setDialogueBoostStrength(strength) }
    fun setNightModeEnabled(enabled: Boolean) =
        editor.edit { setNightModeEnabled(enabled) }
    fun setNightModeStrength(strength: EffectStrength) =
        editor.edit { setNightModeStrength(strength) }
    fun setBassBoostEnabled(enabled: Boolean) =
        editor.edit { setBassBoostEnabled(enabled) }
    fun setBassBoostStrength(strength: EffectStrength) =
        editor.edit { setBassBoostStrength(strength) }
    fun setVirtualizerEnabled(enabled: Boolean) =
        editor.edit { setVirtualizerEnabled(enabled) }
    fun setVirtualizerStrength(strength: Int) =
        editor.edit { setVirtualizerStrength(strength) }
    fun setReverbPreset(preset: ReverbPreset) =
        editor.edit { setReverbPreset(preset) }
    fun setAutoEqByGenre(enabled: Boolean) =
        editor.edit { setAutoEqByGenre(enabled) }
    fun setChannelMixEnabled(enabled: Boolean) =
        editor.edit { setChannelMixEnabled(enabled) }
    fun setChannelMixMode(mode: ChannelMixMode) =
        editor.edit { setChannelMixMode(mode) }
    fun setLrBalance(balance: Float) =
        editor.edit { setLrBalance(balance) }
    fun setPitchSemitones(semitones: Float) =
        editor.edit { setPitchSemitones(semitones) }
    fun setAudioDefaultSpeed(speed: Float) = editor.setAudioDefaultSpeed(speed)
    fun setSleepTimerDurationMs(ms: Long) =
        editor.edit { setSleepTimerDurationMs(ms) }
    fun setAudioCachingEnabled(enabled: Boolean) =
        editor.edit { setAudioCachingEnabled(enabled) }
    fun setAudioCacheSizeMb(sizeMb: Int) =
        editor.edit { setAudioCacheSizeMb(sizeMb) }
    fun setAudioPrefetchLookahead(lookahead: Int) =
        editor.edit { setAudioPrefetchLookahead(lookahead) }
    fun setAudioPrefetchBackfill(backfill: Int) =
        editor.edit { setAudioPrefetchBackfill(backfill) }
    fun setEqualizerSettings(settings: EqualizerSettings) =
        editor.edit { setEqualizerSettings(settings) }

    fun setVolumeBoostEnabled(enabled: Boolean) =
        editor.edit { setVolumeBoostEnabled(enabled) }

    fun setVolumeBoostGain(gain: Int) =
        editor.edit { setVolumeBoostGain(gain) }

    fun setAudioCacheNetworkPolicy(policy: com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy) =
        editor.edit { setAudioCacheNetworkPolicy(policy) }

    fun clearAudioCache() {
        launch { audioStreamCache.clear() }
    }
}
