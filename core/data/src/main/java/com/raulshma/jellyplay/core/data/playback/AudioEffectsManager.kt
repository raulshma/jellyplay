package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.flow.StateFlow

interface AudioEffectsManager {
    val nightModeEnabled: StateFlow<Boolean>
    val dialogueBoostEnabled: StateFlow<Boolean>
    val equalizerEnabled: StateFlow<Boolean>
    val equalizerSettings: StateFlow<EqualizerSettings>
    val equalizerPreset: StateFlow<EqualizerPreset>
    val bassBoostEnabled: StateFlow<Boolean>
    val bassBoostStrengthState: EffectStrength
    val virtualizerEnabled: StateFlow<Boolean>
    val virtualizerStrength: StateFlow<Int>
    val reverbPresetState: StateFlow<ReverbPreset>
    val lrBalance: StateFlow<Float>
    val pitchSemitones: StateFlow<Float>
    val autoEqByGenre: StateFlow<Boolean>
    val fftData: StateFlow<ByteArray>
    val waveformData: StateFlow<ByteArray>
    val replayGainMode: StateFlow<AudioNormalizationMode>
    val replayGainPreAmpDb: StateFlow<Float>
    val channelMixMode: StateFlow<ChannelMixMode>
    val channelMixEnabled: StateFlow<Boolean>

    fun toggleNightMode()
    fun toggleDialogueBoost()
    fun setDialogueBoostStrength(strength: EffectStrength)
    fun setNightModeStrength(strength: EffectStrength)
    fun toggleEqualizer()
    fun setEqualizerBand(bandIndex: Int, levelDb: Int)
    fun resetEqualizer()
    fun setNightModeParams(volume: Float, gain: Int)
    fun setReplayGainMode(mode: AudioNormalizationMode)
    fun setReplayGainPreAmpDb(db: Float)
    fun setChannelMix(mode: ChannelMixMode, enabled: Boolean)
    fun setEqualizerPreset(preset: EqualizerPreset)
    fun toggleBassBoost()
    fun setBassBoostStrength(strength: EffectStrength)
    fun toggleVirtualizer()
    fun setVirtualizerStrength(strength: Int)
    fun setReverbPreset(preset: ReverbPreset)
    fun setLrBalance(balance: Float)
    fun setPitchSemitones(semitones: Float)
    fun setAutoEqByGenre(enabled: Boolean)
    fun applyAutoEqForGenre(genres: List<String>?)
    fun enableVisualizer(enabled: Boolean)
}
