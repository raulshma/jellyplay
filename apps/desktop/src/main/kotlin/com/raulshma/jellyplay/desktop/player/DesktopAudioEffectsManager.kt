package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop [AudioEffectsManager] (wave 9B state machine, wave 14C DSP half).
 *
 * Every state flow is present with the SAME initial values as the Android
 * `AudioEffectsProcessor`, and every setter mutates that in-memory state
 * exactly like the Android one does. Since wave 14C the state is ALSO
 * applied as real DSP: [snapshotConfig] folds the full state into the shared
 * [AudioEffectsConfig] and the desktop audio core ([DesktopAudioQueueManager])
 * pushes it onto the audio engine's mpv `af` chain — on load and live on
 * every change (the [onEffectsChanged] hook). The filter mapping lives in
 * [DesktopAudioEffectChain] (Android effect → mpv filter parity table there);
 * this class owns only the state machine + the final per-track ReplayGain
 * computation, which mirrors `AudioEffectsProcessor.applyReplayGain` exactly.
 *
 * Why flip the flows at all (rather than freezing them): the audio player's
 * ViewModel persists `uiState.effects.<flag>` right after calling a setter —
 * a frozen flow would write the PREVIOUS value back to the store, silently
 * undoing every toggle.
 *
 * Still absent (declared): the visualizer taps — `fftData`/`waveformData`
 * stay empty because mpv offers no in-sink PCM tap without a full render-API
 * audio pull (Android taps the audio session id). `enableVisualizer` is a
 * state-only no-op like wave 9B.
 */
class DesktopAudioEffectsManager : AudioEffectsManager {

    internal var onEffectsChanged: (() -> Unit)? = null

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

    // ── Non-flow DSP inputs (Android keeps the same private fields) ────────

    private var dialogueBoostStrengthInternal: EffectStrength = EffectStrength.MODERATE
    private var nightModeStrengthInternal: EffectStrength = EffectStrength.MODERATE

    /** `setNightModeParams` mirrors — stored like Android's public fields. */
    internal var nightModeVolumeInternal: Float = 0.4f
        private set
    internal var nightModeGainInternal: Int = 1200
        private set

    /** Last per-track ReplayGain context seen ([applyReplayGainForTrack]). */
    private var lastTrackGainDb: Float? = null
    private var lastShuffled: Boolean = false

    private fun notifyChanged() {
        onEffectsChanged?.invoke()
    }

    /**
     * Fold the whole state machine into the shared engine config. The queue
     * manager pushes this onto the engine via `updateConfig` — on engine
     * creation and after every mutation below.
     */
    internal fun snapshotConfig(): AudioEffectsConfig = AudioEffectsConfig(
        dialogueBoostEnabled = _dialogueBoostEnabled.value,
        dialogueBoostStrength = dialogueBoostStrengthInternal,
        nightModeEnabled = _nightModeEnabled.value,
        nightModeStrength = nightModeStrengthInternal,
        nightModeGain = nightModeGainInternal,
        equalizerEnabled = _equalizerEnabled.value,
        equalizerSettings = _equalizerSettings.value,
        audioNormalizationMode = _replayGainMode.value,
        audioNormalizationEnabled = _replayGainMode.value != AudioNormalizationMode.NONE,
        channelMixMode = _channelMixMode.value,
        channelMixEnabled = _channelMixEnabled.value,
        bassBoostEnabled = _bassBoostEnabled.value,
        bassBoostStrength = bassBoostStrengthInternal,
        virtualizerEnabled = _virtualizerEnabled.value,
        virtualizerStrength = _virtualizerStrength.value,
        reverbPreset = _reverbPreset.value,
        lrBalance = _lrBalance.value,
        pitchSemitones = _pitchSemitones.value,
        replayGainEffectiveDb = computeEffectiveReplayGainDb(),
    )

    /**
     * `AudioEffectsProcessor.applyReplayGain` verbatim: TRACK →
     * `trackGain ?: 0 + preAmp`; ALBUM → the same, EXCEPT shuffled queues
     * pin the gain at 0; DYNAMIC → null (the compressor stage runs instead);
     * NONE → null (both off).
     */
    private fun computeEffectiveReplayGainDb(): Float? = when (_replayGainMode.value) {
        AudioNormalizationMode.TRACK -> (lastTrackGainDb ?: 0f) + _replayGainPreAmpDb.value
        AudioNormalizationMode.ALBUM ->
            if (lastShuffled) 0f else (lastTrackGainDb ?: 0f) + _replayGainPreAmpDb.value
        AudioNormalizationMode.DYNAMIC, AudioNormalizationMode.NONE -> null
    }

    /**
     * Per-track ReplayGain context from the audio core — the Android manager
     * calls `effectsProcessor.applyReplayGain(item.normalizationGain,
     * isShuffled)` at the same two sites (explicit play + advance).
     */
    internal fun applyReplayGainForTrack(trackGainDb: Float?, isShuffled: Boolean) {
        lastTrackGainDb = trackGainDb
        lastShuffled = isShuffled
        notifyChanged()
    }

    // ── Setters: state machine identical to the Android processor ──────────

    override fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
        notifyChanged()
    }

    override fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
        notifyChanged()
    }

    override fun setDialogueBoostStrength(strength: EffectStrength) {
        dialogueBoostStrengthInternal = strength
        notifyChanged()
    }

    override fun setNightModeStrength(strength: EffectStrength) {
        nightModeStrengthInternal = strength
        notifyChanged()
    }

    override fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
        notifyChanged()
    }

    override fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = _equalizerSettings.value.bandLevels.toMutableList()
        if (bandIndex in newLevels.indices) {
            newLevels[bandIndex] = levelDb
            _equalizerSettings.value = EqualizerSettings(newLevels)
            _equalizerPreset.value = EqualizerPreset.CUSTOM
            notifyChanged()
        }
    }

    override fun resetEqualizer() {
        _equalizerSettings.value = EqualizerSettings()
        _equalizerPreset.value = EqualizerPreset.FLAT
        notifyChanged()
    }

    override fun setNightModeParams(volume: Float, gain: Int) {
        nightModeVolumeInternal = volume
        nightModeGainInternal = gain
        notifyChanged()
    }

    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        _replayGainMode.value = mode
        notifyChanged()
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        _replayGainPreAmpDb.value = db
        notifyChanged()
    }

    override fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        _channelMixMode.value = mode
        _channelMixEnabled.value = enabled
        notifyChanged()
    }

    override fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        if (preset != EqualizerPreset.CUSTOM) {
            _equalizerSettings.value = EqualizerSettings(preset.bandLevels())
        }
        notifyChanged()
    }

    override fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
        notifyChanged()
    }

    override fun setBassBoostStrength(strength: EffectStrength) {
        bassBoostStrengthInternal = strength
        notifyChanged()
    }

    override fun toggleVirtualizer() {
        _virtualizerEnabled.value = !_virtualizerEnabled.value
        notifyChanged()
    }

    override fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        notifyChanged()
    }

    override fun setReverbPreset(preset: ReverbPreset) {
        _reverbPreset.value = preset
        notifyChanged()
    }

    override fun setLrBalance(balance: Float) {
        _lrBalance.value = balance
        notifyChanged()
    }

    override fun setPitchSemitones(semitones: Float) {
        _pitchSemitones.value = semitones
        notifyChanged()
    }

    override fun setAutoEqByGenre(enabled: Boolean) {
        _autoEqByGenre.value = enabled
        notifyChanged()
    }

    override fun applyAutoEqForGenre(genres: List<String>?) {
        // Android verbatim: resolve the first genre-matched preset onto the
        // equalizer (no-op unless the auto flag is on). setEqualizerPreset
        // notifies, so the resolved levels reach the af chain too.
        if (!_autoEqByGenre.value) return
        if (genres.isNullOrEmpty()) return
        val matchedPreset = genres.firstNotNullOfOrNull { genre ->
            EqualizerPreset.fromGenre(genre)
        } ?: return
        if (matchedPreset != _equalizerPreset.value) {
            setEqualizerPreset(matchedPreset)
        }
    }

    override fun enableVisualizer(enabled: Boolean) {
        // No audio-session visualizer taps on desktop; fft/waveform stay empty
        // (declared divergence — see class KDoc).
    }
}
