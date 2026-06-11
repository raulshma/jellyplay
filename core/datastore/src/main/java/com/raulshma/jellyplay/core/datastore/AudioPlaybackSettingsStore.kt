package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class AudioPlaybackSettings(
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val audioDelayMs: Long = 0L,
    val audioPassthrough: Boolean = false,
)

/**
 * Focused facade over [UserPreferencesStore] that exposes only audio-related
 * settings. Backed by the same DataStore (no data migration) but provides a
 * cleaner, narrower API to consumers and a dedicated [StateFlow] of audio
 * settings so subscribers don't re-render on unrelated preference changes.
 */
@Singleton
class AudioPlaybackSettingsStore @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    val settings: StateFlow<AudioPlaybackSettings> = userPreferencesStore.preferences
        .map { it.toAudioSettings() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AudioPlaybackSettings())

    private fun com.raulshma.jellyplay.core.model.UserPreferences.toAudioSettings() =
        AudioPlaybackSettings(
            streamingQuality = streamingQuality,
            audioDefaultSpeed = audioDefaultSpeed,
            audioNightModeVolume = audioNightModeVolume,
            audioNightModeGain = audioNightModeGain,
            audioSkipPreviousThresholdMs = audioSkipPreviousThresholdMs,
            audioAutoplayNext = audioAutoplayNext,
            audioPreloadBufferSize = audioPreloadBufferSize,
            audioNormalizationMode = audioNormalizationMode,
            audioNormalizationEnabled = audioNormalizationEnabled,
            replayGainPreAmpDb = replayGainPreAmpDb,
            channelMixMode = channelMixMode,
            channelMixEnabled = channelMixEnabled,
            audioGaplessEnabled = audioGaplessEnabled,
            audioCrossfadeDurationMs = audioCrossfadeDurationMs,
            nightModeEnabled = nightModeEnabled,
            nightModeStrength = nightModeStrength,
            dialogueBoostEnabled = dialogueBoostEnabled,
            dialogueBoostStrength = dialogueBoostStrength,
            equalizerEnabled = equalizerEnabled,
            equalizerSettings = equalizerSettings,
            equalizerPreset = equalizerPreset,
            bassBoostEnabled = bassBoostEnabled,
            bassBoostStrength = bassBoostStrength,
            virtualizerEnabled = virtualizerEnabled,
            virtualizerStrength = virtualizerStrength,
            reverbPreset = reverbPreset,
            lrBalance = lrBalance,
            autoEqByGenre = autoEqByGenre,
            pitchSemitones = pitchSemitones,
            audioDelayMs = audioDelayMs,
            audioPassthrough = audioPassthrough,
        )

    suspend fun setNightModeEnabled(enabled: Boolean) =
        userPreferencesStore.setNightModeEnabled(enabled)

    suspend fun setNightModeStrength(strength: EffectStrength) =
        userPreferencesStore.setNightModeStrength(strength)

    suspend fun setDialogueBoostEnabled(enabled: Boolean) =
        userPreferencesStore.setDialogueBoostEnabled(enabled)

    suspend fun setDialogueBoostStrength(strength: EffectStrength) =
        userPreferencesStore.setDialogueBoostStrength(strength)

    suspend fun setEqualizerEnabled(enabled: Boolean) =
        userPreferencesStore.setEqualizerEnabled(enabled)

    suspend fun setEqualizerSettings(settings: EqualizerSettings) =
        userPreferencesStore.setEqualizerSettings(settings)

    suspend fun setEqualizerPreset(preset: EqualizerPreset) =
        userPreferencesStore.setEqualizerPreset(preset)

    suspend fun setBassBoostEnabled(enabled: Boolean) =
        userPreferencesStore.setBassBoostEnabled(enabled)

    suspend fun setBassBoostStrength(strength: EffectStrength) =
        userPreferencesStore.setBassBoostStrength(strength)

    suspend fun setVirtualizerEnabled(enabled: Boolean) =
        userPreferencesStore.setVirtualizerEnabled(enabled)

    suspend fun setVirtualizerStrength(strength: Int) =
        userPreferencesStore.setVirtualizerStrength(strength)

    suspend fun setReverbPreset(preset: ReverbPreset) =
        userPreferencesStore.setReverbPreset(preset)

    suspend fun setLrBalance(balance: Float) =
        userPreferencesStore.setLrBalance(balance)

    suspend fun setAutoEqByGenre(enabled: Boolean) =
        userPreferencesStore.setAutoEqByGenre(enabled)

    suspend fun setPitchSemitones(semitones: Float) =
        userPreferencesStore.setPitchSemitones(semitones)

    suspend fun setAudioPassthrough(enabled: Boolean) =
        userPreferencesStore.setAudioPassthrough(enabled)

    suspend fun setReplayGainPreAmpDb(db: Float) =
        userPreferencesStore.setReplayGainPreAmpDb(db)

    suspend fun setAudioGaplessEnabled(enabled: Boolean) =
        userPreferencesStore.setGaplessEnabled(enabled)

    suspend fun setAudioCrossfadeDurationMs(durationMs: Long) =
        userPreferencesStore.setCrossfadeDurationMs(durationMs)

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) =
        userPreferencesStore.setAudioNormalizationMode(mode)

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) =
        userPreferencesStore.setAudioNormalizationEnabled(enabled)

    suspend fun setChannelMixMode(mode: ChannelMixMode) =
        userPreferencesStore.setChannelMixMode(mode)

    suspend fun setChannelMixEnabled(enabled: Boolean) =
        userPreferencesStore.setChannelMixEnabled(enabled)

    suspend fun setAudioDefaultSpeed(speed: Float) =
        userPreferencesStore.setAudioDefaultSpeed(speed)

    suspend fun setAudioAutoplayNext(enabled: Boolean) =
        userPreferencesStore.setAudioAutoplayNext(enabled)

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) =
        userPreferencesStore.setAudioPreloadBufferSize(size)

    suspend fun setAudioNightModeVolume(volume: Float) =
        userPreferencesStore.setAudioNightModeVolume(volume)

    suspend fun setAudioNightModeGain(gain: Int) =
        userPreferencesStore.setAudioNightModeGain(gain)

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) =
        userPreferencesStore.setAudioSkipPreviousThresholdMs(ms)

    suspend fun setAudioDelayMs(ms: Long) =
        userPreferencesStore.setAudioDelay(ms)

    suspend fun setStreamingQuality(quality: StreamingQuality) =
        userPreferencesStore.setStreamingQuality(quality)
}
