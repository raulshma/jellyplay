package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

class AudioEffectsProcessor() {
    private lateinit var scope: CoroutineScope

    var playerProvider: (() -> ExoPlayer?)? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val equalizerHelper = EqualizerHelper()
    private val highPassFilter = HighPassFilterAudioProcessor()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper, highPassFilter)
    private val bassBoostHelper = BassBoostHelper()
    private val virtualizerHelper = VirtualizerHelper()
    private val reverbHelper = ReverbHelper()
    val balanceProcessor = BalanceAudioProcessor()
    private val visualizerHelper = AudioVisualizerHelper()

    val replayGainProcessor = ReplayGainAudioProcessor()
    val crossfadeReplayGainProcessor = ReplayGainAudioProcessor()
    /** Real PCM matrix channel mixer (downmix/upmix/mono). */
    val channelMixProcessor = ChannelMixAudioProcessor()
    /** Feed-forward dynamics compressor for DYNAMIC normalization mode. */
    val dynamicsProcessor = DynamicsCompressorAudioProcessor()
    /**
     * Sub-bass high-pass for dialogue-boost de-noise; shared with
     * [dialogueBoost]. Also drives the crossfade player's chain.
     */
    val highPassProcessor: HighPassFilterAudioProcessor get() = highPassFilter
    /** Mirror processors for the crossfade player (separate sinks). */
    val crossfadeChannelMixProcessor = ChannelMixAudioProcessor()
    val crossfadeDynamicsProcessor = DynamicsCompressorAudioProcessor()
    val crossfadeHighPassProcessor = HighPassFilterAudioProcessor()

    private var _dialogueBoostStrength = EffectStrength.MODERATE
    private var _nightModeStrength = EffectStrength.MODERATE

    private val _nightModeEnabled = MutableStateFlow(false)
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _dialogueBoostEnabled = MutableStateFlow(false)
    val dialogueBoostEnabled: StateFlow<Boolean> = _dialogueBoostEnabled.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(EqualizerPreset.FLAT)
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private var _bassBoostStrength = EffectStrength.MODERATE
    val bassBoostStrengthState: EffectStrength get() = _bassBoostStrength

    private val _virtualizerEnabled = MutableStateFlow(false)
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(500)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _reverbPreset = MutableStateFlow(ReverbPreset.NONE)
    val reverbPresetState: StateFlow<ReverbPreset> = _reverbPreset.asStateFlow()

    private val _lrBalance = MutableStateFlow(0f)
    val lrBalance: StateFlow<Float> = _lrBalance.asStateFlow()

    private val _pitchSemitones = MutableStateFlow(0f)
    val pitchSemitones: StateFlow<Float> = _pitchSemitones.asStateFlow()

    private val _autoEqByGenre = MutableStateFlow(false)
    val autoEqByGenre: StateFlow<Boolean> = _autoEqByGenre.asStateFlow()

    val fftData: StateFlow<ByteArray> = visualizerHelper.fftData
    val waveformData: StateFlow<ByteArray> = visualizerHelper.waveformData

    private val _replayGainMode = MutableStateFlow(AudioNormalizationMode.NONE)
    val replayGainMode: StateFlow<AudioNormalizationMode> = _replayGainMode.asStateFlow()

    private val _replayGainPreAmpDb = MutableStateFlow(0f)
    val replayGainPreAmpDb: StateFlow<Float> = _replayGainPreAmpDb.asStateFlow()

    private val _channelMixMode = MutableStateFlow(ChannelMixMode.AUTO)
    val channelMixMode: StateFlow<ChannelMixMode> = _channelMixMode.asStateFlow()

    private val _channelMixEnabled = MutableStateFlow(false)
    val channelMixEnabled: StateFlow<Boolean> = _channelMixEnabled.asStateFlow()

    val nightModeVolumeForStrength: Float
        get() = EffectStrengthMapping.nightModeVolumeAttenuation(_nightModeStrength)

    val nightModeGainForStrength: Int
        get() = EffectStrengthMapping.nightModeGainMb(_nightModeStrength)

    var nightModeVolume = 0.4f
    var nightModeGain = 1200

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    fun toggleNightMode() {
        _nightModeEnabled.value = !_nightModeEnabled.value
        applyNightMode()
    }

    fun toggleDialogueBoost() {
        _dialogueBoostEnabled.value = !_dialogueBoostEnabled.value
        applyDialogueBoost()
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        _dialogueBoostStrength = strength
        dialogueBoost.setStrength(strength)
        if (_dialogueBoostEnabled.value) applyDialogueBoost()
    }

    fun setNightModeStrength(strength: EffectStrength) {
        _nightModeStrength = strength
        if (_nightModeEnabled.value) applyNightMode()
    }

    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
        applyEqualizer()
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = _equalizerSettings.value.bandLevels.toMutableList()
        newLevels[bandIndex] = levelDb
        _equalizerSettings.value = EqualizerSettings(newLevels)
        _equalizerPreset.value = EqualizerPreset.CUSTOM
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    fun resetEqualizer() {
        _equalizerSettings.value = EqualizerSettings()
        _equalizerPreset.value = EqualizerPreset.FLAT
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    fun setNightModeParams(volume: Float, gain: Int) {
        nightModeVolume = volume
        nightModeGain = gain
        if (_nightModeEnabled.value) applyNightMode()
    }

    fun applyNightMode() {
        val player = playerProvider?.invoke() ?: return
        if (_nightModeEnabled.value) {
            player.volume = nightModeVolumeForStrength
            attachLoudnessEnhancer(player.audioSessionId, nightModeGainForStrength)
        } else {
            player.volume = 1.0f
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }
    }

    fun applyDialogueBoost() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        // Ensure the shared Equalizer exists; the boost overlay is
        // applied on top of the user's base levels via setBandOffsets.
        // The co-enabling rule (on while EITHER effect is on) lives inside
        // [EqualizerHelper.setEnabled] — callers pass both flags and reuse
        // the resolved result instead of re-deriving the `||`.
        equalizerHelper.attach(audioSessionId)
        val eitherOn = equalizerHelper.setEnabled(
            equalizerEnabled = _equalizerEnabled.value,
            dialogueBoostEnabled = _dialogueBoostEnabled.value,
        )
        if (eitherOn) {
            equalizerHelper.setSettings(_equalizerSettings.value)
        }
        dialogueBoost.attach(audioSessionId)
        dialogueBoost.setStrength(_dialogueBoostStrength)
        dialogueBoost.setEnabled(_dialogueBoostEnabled.value)
        // Mirror the dialogue-boost rumble cut onto the crossfade sink so
        // the incoming track is filtered during the crossfade window too.
        crossfadeHighPassProcessor.setEnabled(_dialogueBoostEnabled.value)
    }

    fun applyEqualizer() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        // The co-enabling rule (on while EITHER effect is on) lives inside
        // [EqualizerHelper.setEnabled] — callers pass both flags.
        equalizerHelper.attach(audioSessionId)
        equalizerHelper.setEnabled(
            equalizerEnabled = _equalizerEnabled.value,
            dialogueBoostEnabled = _dialogueBoostEnabled.value,
        )
        equalizerHelper.setSettings(_equalizerSettings.value)
    }

    fun applyReplayGain(trackGain: Float?, isShuffled: Boolean = false) {
        val mode = _replayGainMode.value
        when (mode) {
            AudioNormalizationMode.TRACK,
            AudioNormalizationMode.ALBUM -> {
                // DYNAMIC compressor is mutually exclusive with per-track
                // loudness normalization; disable it while ReplayGain runs.
                dynamicsProcessor.setEnabled(false)
                crossfadeDynamicsProcessor.setEnabled(false)
                if (mode == AudioNormalizationMode.ALBUM && isShuffled) {
                    replayGainProcessor.setGainDb(0f)
                    crossfadeReplayGainProcessor.setGainDb(0f)
                    return
                }
                val preAmp = _replayGainPreAmpDb.value
                val gain = (trackGain ?: 0f) + preAmp
                replayGainProcessor.setGainDb(gain)
                crossfadeReplayGainProcessor.setGainDb(gain)
            }
            AudioNormalizationMode.DYNAMIC -> {
                // No per-track gain; drive the DSP compressor instead.
                replayGainProcessor.setGainDb(0f)
                crossfadeReplayGainProcessor.setGainDb(0f)
                dynamicsProcessor.setEnabled(true)
                crossfadeDynamicsProcessor.setEnabled(true)
            }
            AudioNormalizationMode.NONE -> {
                replayGainProcessor.setGainDb(0f)
                crossfadeReplayGainProcessor.setGainDb(0f)
                dynamicsProcessor.setEnabled(false)
                crossfadeDynamicsProcessor.setEnabled(false)
            }
        }
    }

    fun setReplayGainMode(mode: AudioNormalizationMode, normalizationGain: Float?, isShuffled: Boolean) {
        _replayGainMode.value = mode
        applyReplayGain(normalizationGain, isShuffled)
    }

    fun setReplayGainPreAmpDb(db: Float, normalizationGain: Float?, isShuffled: Boolean) {
        _replayGainPreAmpDb.value = db
        applyReplayGain(normalizationGain, isShuffled)
    }

    /**
     * Push the channel-mix mode + enabled flag to the DSP
     * [channelMixProcessor]. Drives real downmix/upmix/mono via ITU
     * BS.775 coefficients; on the audio/music path this replaces the
     * previously unwired [ChannelMixMode] preference.
     */
    fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        _channelMixMode.value = mode
        _channelMixEnabled.value = enabled
        // Primary + crossfade sinks each need their own processor state.
        channelMixProcessor.setMode(mode)
        channelMixProcessor.setEnabled(enabled)
        crossfadeChannelMixProcessor.setMode(mode)
        crossfadeChannelMixProcessor.setEnabled(enabled)
    }

    fun attachLoudnessEnhancer(audioSessionId: Int, gain: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        applyEqualizer()
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        _bassBoostEnabled.value = enabled
        applyBassBoost()
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        _virtualizerEnabled.value = enabled
        applyVirtualizer()
    }

    fun setDialogueBoostEnabled(enabled: Boolean) {
        _dialogueBoostEnabled.value = enabled
        applyDialogueBoost()
    }

    fun setNightModeEnabled(enabled: Boolean) {
        _nightModeEnabled.value = enabled
        applyNightMode()
    }

    fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        // The user-facing EQ and DialogueBoost both ride on the single
        // underlying priority-0 `Equalizer` owned by `equalizerHelper`
        // (see EqualizerHelper/DialogueBoostHelper kdoc). The co-enabling
        // rule (on while EITHER is on) lives inside
        // [EqualizerHelper.setEnabled]; DialogueBoostHelper overlays its
        // vocal-band offsets on top of the user's base levels via
        // setBandOffsets.
        if (_equalizerEnabled.value || _dialogueBoostEnabled.value) {
            equalizerHelper.attach(audioSessionId)
            equalizerHelper.setEnabled(
                equalizerEnabled = _equalizerEnabled.value,
                dialogueBoostEnabled = _dialogueBoostEnabled.value,
            )
            equalizerHelper.setSettings(_equalizerSettings.value)
        }
        if (_dialogueBoostEnabled.value) {
            dialogueBoost.attach(audioSessionId)
            dialogueBoost.setEnabled(true)
            crossfadeHighPassProcessor.setEnabled(true)
        }
        if (_bassBoostEnabled.value) {
            bassBoostHelper.attach(audioSessionId)
            bassBoostHelper.setEnabled(true)
        }
        if (_virtualizerEnabled.value) {
            virtualizerHelper.attach(audioSessionId)
            virtualizerHelper.setEnabled(true)
        }
        if (_reverbPreset.value != ReverbPreset.NONE) {
            reverbHelper.attach(audioSessionId)
            reverbHelper.setEnabled(true)
        }
        visualizerHelper.attach(audioSessionId)
        if (visualizerHelper.isEnabled) {
            visualizerHelper.setEnabled(true)
        }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        if (preset != EqualizerPreset.CUSTOM) {
            val settings = EqualizerSettings(preset.bandLevels())
            _equalizerSettings.value = settings
            equalizerHelper.setSettings(settings)
        }
    }

    fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
        applyBassBoost()
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        _bassBoostStrength = strength
        bassBoostHelper.setStrength(strength)
    }

    fun applyBassBoost() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        bassBoostHelper.attach(audioSessionId)
        bassBoostHelper.setStrength(_bassBoostStrength)
        bassBoostHelper.setEnabled(_bassBoostEnabled.value)
    }

    fun toggleVirtualizer() {
        _virtualizerEnabled.value = !_virtualizerEnabled.value
        applyVirtualizer()
    }

    fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        virtualizerHelper.setStrength(strength)
    }

    fun applyVirtualizer() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        virtualizerHelper.attach(audioSessionId)
        virtualizerHelper.setStrength(_virtualizerStrength.value)
        virtualizerHelper.setEnabled(_virtualizerEnabled.value)
    }

    fun setReverbPreset(preset: ReverbPreset) {
        _reverbPreset.value = preset
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (preset == ReverbPreset.NONE) {
            reverbHelper.setEnabled(false)
            reverbHelper.detach()
        } else {
            reverbHelper.detach()
            reverbHelper.attach(audioSessionId)
            reverbHelper.setPreset(preset)
        }
    }

    fun setLrBalance(balance: Float) {
        _lrBalance.value = balance
        balanceProcessor.setBalance(balance)
    }

    fun setPitchSemitones(semitones: Float, currentSpeed: Float) {
        _pitchSemitones.value = semitones
        val multiplier = if (semitones == 0f) 1.0f else {
            2.0f.pow(semitones / 12.0f)
        }
        playerProvider?.invoke()?.playbackParameters = PlaybackParameters(currentSpeed, multiplier)
    }

    fun setAutoEqByGenre(enabled: Boolean) {
        _autoEqByGenre.value = enabled
    }

    fun applyAutoEqForGenre(genres: List<String>?) {
        if (!_autoEqByGenre.value) return
        if (genres.isNullOrEmpty()) return
        val matchedPreset = genres.firstNotNullOfOrNull { genre ->
            EqualizerPreset.fromGenre(genre)
        } ?: return
        if (matchedPreset != _equalizerPreset.value) {
            setEqualizerPreset(matchedPreset)
        }
    }

    fun enableVisualizer(enabled: Boolean) {
        visualizerHelper.setEnabled(enabled)
    }

    fun reattachForCrossfade(audioSessionId: Int) {
        // Re-attach effects that hold their own audiofx session to the new
        // ExoPlayer's session id. LoudnessEnhancer (NightMode) is included
        // here for symmetry with attachAudioEffects + applyNightMode so the
        // crossfade path is self-sufficient even if the player's
        // onAudioSessionIdChanged callback doesn't fire (e.g. when the new
        // session id happens to equal the previous one). No-op when the new
        // session id is still AUDIO_SESSION_ID_UNSET — the listener path
        // (AudioPlaybackManager.playerListener.onAudioSessionIdChanged) takes
        // over once the AudioTrack actually opens.
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (_reverbPreset.value != ReverbPreset.NONE) {
            reverbHelper.detach()
            reverbHelper.attach(audioSessionId)
            reverbHelper.setPreset(_reverbPreset.value)
        }
        visualizerHelper.attach(audioSessionId)
        if (visualizerHelper.isEnabled) {
            visualizerHelper.setEnabled(true)
        }
        if (_nightModeEnabled.value) {
            attachLoudnessEnhancer(audioSessionId, nightModeGainForStrength)
        }
    }

    fun releaseAll() {
        dialogueBoost.detach()
        crossfadeHighPassProcessor.setEnabled(false)
        equalizerHelper.detach()
        bassBoostHelper.detach()
        virtualizerHelper.detach()
        reverbHelper.detach()
        visualizerHelper.detach()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
    }
}
