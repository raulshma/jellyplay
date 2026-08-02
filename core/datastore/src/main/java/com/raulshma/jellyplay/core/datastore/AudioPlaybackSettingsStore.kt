package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Focused facade that exposes only audio-related settings, rebuilt by combining
 * the [AudioStore], [AudioEffectsStore], and [PlaybackStore] domain-store slice
 * flows. Subscribers don't re-render on unrelated preference changes.
 */
@Singleton
class AudioPlaybackSettingsStore @Inject constructor(
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val playbackStore: PlaybackStore,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    val settings: StateFlow<AudioPlaybackSettings> = combine(
        audioStore.audio,
        audioEffectsStore.audioEffects,
        playbackStore.playback,
    ) { audio, effects, playback ->
        toAudioSettings(audio, effects, playback)
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AudioPlaybackSettings())

    private fun toAudioSettings(
        audio: AudioSlice,
        effects: AudioEffectsSlice,
        playback: PlaybackSlice,
    ) = AudioPlaybackSettings(
        streamingQuality = playback.streamingQuality,
        audioDefaultSpeed = audio.audioDefaultSpeed,
        audioNightModeVolume = audio.audioNightModeVolume,
        audioNightModeGain = audio.audioNightModeGain,
        audioSkipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs,
        audioAutoplayNext = audio.audioAutoplayNext,
        audioPreloadBufferSize = audio.audioPreloadBufferSize,
        audioNormalizationMode = audio.audioNormalizationMode,
        audioNormalizationEnabled = audio.audioNormalizationEnabled,
        replayGainPreAmpDb = audio.replayGainPreAmpDb,
        channelMixMode = audio.channelMixMode,
        channelMixEnabled = audio.channelMixEnabled,
        audioGaplessEnabled = audio.audioGaplessEnabled,
        audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
        nightModeEnabled = effects.nightModeEnabled,
        nightModeStrength = effects.nightModeStrength,
        dialogueBoostEnabled = effects.dialogueBoostEnabled,
        dialogueBoostStrength = effects.dialogueBoostStrength,
        equalizerEnabled = effects.equalizerEnabled,
        equalizerSettings = effects.equalizerSettings,
        equalizerPreset = effects.equalizerPreset,
        bassBoostEnabled = effects.bassBoostEnabled,
        bassBoostStrength = effects.bassBoostStrength,
        virtualizerEnabled = effects.virtualizerEnabled,
        virtualizerStrength = effects.virtualizerStrength,
        reverbPreset = effects.reverbPreset,
        lrBalance = effects.lrBalance,
        autoEqByGenre = effects.autoEqByGenre,
        pitchSemitones = effects.pitchSemitones,
        audioDelayMs = audio.audioDelayMs,
        audioPassthrough = playback.audioPassthrough,
    )

    suspend fun setNightModeEnabled(enabled: Boolean) =
        audioEffectsStore.setNightModeEnabled(enabled)

    suspend fun setNightModeStrength(strength: EffectStrength) =
        audioEffectsStore.setNightModeStrength(strength)

    suspend fun setDialogueBoostEnabled(enabled: Boolean) =
        audioEffectsStore.setDialogueBoostEnabled(enabled)

    suspend fun setDialogueBoostStrength(strength: EffectStrength) =
        audioEffectsStore.setDialogueBoostStrength(strength)

    suspend fun setEqualizerEnabled(enabled: Boolean) =
        audioEffectsStore.setEqualizerEnabled(enabled)

    suspend fun setEqualizerSettings(settings: EqualizerSettings) =
        audioEffectsStore.setEqualizerSettings(settings)

    suspend fun setEqualizerPreset(preset: EqualizerPreset) =
        audioEffectsStore.setEqualizerPreset(preset)

    suspend fun setBassBoostEnabled(enabled: Boolean) =
        audioEffectsStore.setBassBoostEnabled(enabled)

    suspend fun setBassBoostStrength(strength: EffectStrength) =
        audioEffectsStore.setBassBoostStrength(strength)

    suspend fun setVirtualizerEnabled(enabled: Boolean) =
        audioEffectsStore.setVirtualizerEnabled(enabled)

    suspend fun setVirtualizerStrength(strength: Int) =
        audioEffectsStore.setVirtualizerStrength(strength)

    suspend fun setReverbPreset(preset: ReverbPreset) =
        audioEffectsStore.setReverbPreset(preset)

    suspend fun setLrBalance(balance: Float) =
        audioEffectsStore.setLrBalance(balance)

    suspend fun setAutoEqByGenre(enabled: Boolean) =
        audioEffectsStore.setAutoEqByGenre(enabled)

    suspend fun setPitchSemitones(semitones: Float) =
        audioEffectsStore.setPitchSemitones(semitones)

    suspend fun setAudioPassthrough(enabled: Boolean) =
        playbackStore.setAudioPassthrough(enabled)

    suspend fun setReplayGainPreAmpDb(db: Float) =
        audioStore.setReplayGainPreAmpDb(db)

    suspend fun setAudioGaplessEnabled(enabled: Boolean) =
        audioStore.setAudioGaplessEnabled(enabled)

    suspend fun setAudioCrossfadeDurationMs(durationMs: Long) =
        audioStore.setAudioCrossfadeDurationMs(durationMs)

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) =
        audioStore.setAudioNormalizationMode(mode)

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) =
        audioStore.setAudioNormalizationEnabled(enabled)

    suspend fun setChannelMixMode(mode: ChannelMixMode) =
        audioStore.setChannelMixMode(mode)

    suspend fun setChannelMixEnabled(enabled: Boolean) =
        audioStore.setChannelMixEnabled(enabled)

    suspend fun setAudioDefaultSpeed(speed: Float) =
        audioStore.setAudioDefaultSpeed(speed)

    suspend fun setAudioAutoplayNext(enabled: Boolean) =
        audioStore.setAudioAutoplayNext(enabled)

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) =
        audioStore.setAudioPreloadBufferSize(size)

    suspend fun setAudioNightModeVolume(volume: Float) =
        audioStore.setAudioNightModeVolume(volume)

    suspend fun setAudioNightModeGain(gain: Int) =
        audioStore.setAudioNightModeGain(gain)

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) =
        audioStore.setAudioSkipPreviousThresholdMs(ms)

    suspend fun setAudioDelayMs(ms: Long) =
        audioStore.setAudioDelay(ms)

    suspend fun setStreamingQuality(quality: StreamingQuality) =
        playbackStore.setStreamingQuality(quality)
}
