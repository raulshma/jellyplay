package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop [AudioEffectsManager] (wave 9B): honest degradation per the seam
 * contract — every state flow is present with the SAME initial values as the
 * Android `AudioEffectsProcessor`, and every setter mutates that in-memory
 * state exactly like the Android one does, but NO digital signal processing
 * happens: the mpv `af` chain is a declared later item (plan §V2 cuts). The
 * audible difference is that toggles change UI state only.
 *
 * Why flip the flows at all (rather than freezing them): the audio player's
 * ViewModel persists `uiState.effects.<flag>` right after calling a setter —
 * a frozen flow would write the PREVIOUS value back to the store, silently
 * undoing every toggle. Keeping the state machine intact means the desktop
 * UI behaves identically and the preferences round-trip; only the DSP half is
 * missing. `fftData`/`waveformData` stay empty (no visualizer taps without an
 * audio-session id); the ReplayGain/channel-mix state flows track the user's
 * selections but the sink applies nothing.
 */
class DesktopAudioEffectsManager : AudioEffectsManager {

    // Initial values: AudioEffectsProcessor line-by-line.
    private val _nightModeEnabled = MutableStateFlow(false)
    override val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _dialogueBoostEnabled = MutableStateFlow(false)
    override val dialogueBoostEnabled: StateFlow<Boolean> = _dialogueBoostEnabled.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    override val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    override val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(EqualizerPreset.FLAT)
    override val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    override val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private var bassBoostStrengthInternal: EffectStrength = EffectStrength.MODERATE
    override val bassBoostStrengthState: EffectStrength get() = bassBoostStrengthInternal

    private val _virtualizerEnabled = MutableStateFlow(false)
    override val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(500)
    override val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _reverbPreset = MutableStateFlow(ReverbPreset.NONE)
    override val reverbPresetState: StateFlow<ReverbPreset> = _reverbPreset.asStateFlow()

    private val _lrBalance = MutableStateFlow(0f)
    override val lrBalance: StateFlow<Float> = _lrBalance.asStateFlow()

    private val _pitchSemitones = MutableStateFlow(0f)
    override val pitchSemitones: StateFlow<Float> = _pitchSemitones.asStateFlow()

    private val _autoEqByGenre = MutableStateFlow(false)
    override val autoEqByGenre: StateFlow<Boolean> = _autoEqByGenre.asStateFlow()

    private val _fftData = MutableStateFlow(ByteArray(0))
    override val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()

    private val _waveformData = MutableStateFlow(ByteArray(0))
    override val waveformData: StateFlow<ByteArray> = _waveformData.asStateFlow()

    private val _replayGainMode = MutableStateFlow(AudioNormalizationMode.NONE)
    override val replayGainMode: StateFlow<AudioNormalizationMode> = _replayGainMode.asStateFlow()

    private val _replayGainPreAmpDb = MutableStateFlow(0f)
    override val replayGainPreAmpDb: StateFlow<Float> = _replayGainPreAmpDb.asStateFlow()

    private val _channelMixMode = MutableStateFlow(ChannelMixMode.AUTO)
    override val channelMixMode: StateFlow<ChannelMixMode> = _channelMixMode.asStateFlow()

    private val _channelMixEnabled = MutableStateFlow(false)
    override val channelMixEnabled: StateFlow<Boolean> = _channelMixEnabled.asStateFlow()

    // ── Setters: state machine identical to the Android processor, DSP cut ──

    override fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
    }

    override fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
    }

    override fun setDialogueBoostStrength(strength: EffectStrength) {
        // Android keeps a non-flow strength field; the DSP attach is cut.
    }

    override fun setNightModeStrength(strength: EffectStrength) {
        // Same as above — the strength feeds the loudness curve only.
    }

    override fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
    }

    override fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = _equalizerSettings.value.bandLevels.toMutableList()
        if (bandIndex in newLevels.indices) {
            newLevels[bandIndex] = levelDb
            _equalizerSettings.value = EqualizerSettings(newLevels)
            _equalizerPreset.value = EqualizerPreset.CUSTOM
        }
    }

    override fun resetEqualizer() {
        _equalizerSettings.value = EqualizerSettings()
        _equalizerPreset.value = EqualizerPreset.FLAT
    }

    override fun setNightModeParams(volume: Float, gain: Int) {
        // Parameters only feed the DSP path on Android.
    }

    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        _replayGainMode.value = mode
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        _replayGainPreAmpDb.value = db
    }

    override fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        _channelMixMode.value = mode
        _channelMixEnabled.value = enabled
    }

    override fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        if (preset != EqualizerPreset.CUSTOM) {
            _equalizerSettings.value = EqualizerSettings(preset.bandLevels())
        }
    }

    override fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
    }

    override fun setBassBoostStrength(strength: EffectStrength) {
        bassBoostStrengthInternal = strength
    }

    override fun toggleVirtualizer() {
        _virtualizerEnabled.value = !_virtualizerEnabled.value
    }

    override fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
    }

    override fun setReverbPreset(preset: ReverbPreset) {
        _reverbPreset.value = preset
    }

    override fun setLrBalance(balance: Float) {
        _lrBalance.value = balance
    }

    override fun setPitchSemitones(semitones: Float) {
        _pitchSemitones.value = semitones
    }

    override fun setAutoEqByGenre(enabled: Boolean) {
        _autoEqByGenre.value = enabled
    }

    override fun applyAutoEqForGenre(genres: List<String>?) {
        // Android resolves a genre-matched preset onto the equalizer; with no
        // DSP chain there is nothing to apply.
    }

    override fun enableVisualizer(enabled: Boolean) {
        // No audio-session visualizer taps on desktop; fft/waveform stay empty.
    }
}
