package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Android half of the audio-effects manager: media3/audiofx DSP attach +
 * apply + Android lifecycle. The state machine (flows, strengths, night-mode
 * params, per-track ReplayGain computation, setter interplay) lives in the
 * shared [AudioEffectsStateCore] (core:data commonMain) — this class extends
 * it, so every flow, strength accessor and command is inherited unchanged
 * (same instances, same source surface for [AudioEffectsManager] consumers,
 * [AudioPlaybackManager], [AudioCrossfader] and the widget/visualizer taps).
 * Each hook override below performs exactly the DSP write the pre-extraction
 * setter performed after flipping state; the declared state-level
 * divergences (unconditional hook fire, the CUSTOM-preset `levelsRewritten`
 * flag, the null-context ReplayGain shims) are pinned in the core's KDoc.
 */
class AudioEffectsProcessor() : AudioEffectsStateCore() {
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

    /** Session-id PCM taps (overrides the core's permanently-empty defaults). */
    override val fftData: StateFlow<ByteArray> = visualizerHelper.fftData
    override val waveformData: StateFlow<ByteArray> = visualizerHelper.waveformData

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    // ── DSP half of the core's apply hooks ──────────────────────────────────

    override fun onNightModeEnabledChanged() {
        applyNightMode()
    }

    override fun onNightModeStrengthChanged() {
        // Named divergence vs the desktop funnel: Android historically
        // re-applied night mode here ONLY while it is enabled.
        if (_nightModeEnabled.value) applyNightMode()
    }

    override fun onNightModeParamsChanged() {
        if (_nightModeEnabled.value) applyNightMode()
    }

    override fun onDialogueBoostEnabledChanged() {
        applyDialogueBoost()
    }

    override fun onDialogueBoostStrengthChanged() {
        // Historical shape: push the strength unconditionally, re-apply the
        // effect stack only while the boost is on.
        dialogueBoost.setStrength(_dialogueBoostStrength)
        if (_dialogueBoostEnabled.value) applyDialogueBoost()
    }

    override fun onEqualizerEnabledChanged() {
        applyEqualizer()
    }

    override fun onEqualizerSettingsChanged(levelsRewritten: Boolean) {
        // CUSTOM keeps the user's curve and historically pushed NOTHING here.
        if (levelsRewritten) equalizerHelper.setSettings(_equalizerSettings.value)
    }

    override fun onBassBoostEnabledChanged() {
        applyBassBoost()
    }

    override fun onBassBoostStrengthChanged() {
        // Historical shape: strength push only — no attach, no re-enable.
        bassBoostHelper.setStrength(_bassBoostStrength)
    }

    override fun onVirtualizerEnabledChanged() {
        applyVirtualizer()
    }

    override fun onVirtualizerStrengthChanged() {
        // Historical shape: strength push only — no attach, no re-enable.
        virtualizerHelper.setStrength(_virtualizerStrength.value)
    }

    override fun onReverbPresetChanged() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (_reverbPreset.value == ReverbPreset.NONE) {
            reverbHelper.setEnabled(false)
            reverbHelper.detach()
        } else {
            reverbHelper.detach()
            reverbHelper.attach(audioSessionId)
            reverbHelper.setPreset(_reverbPreset.value)
        }
    }

    override fun onLrBalanceChanged() {
        balanceProcessor.setBalance(_lrBalance.value)
    }

    override fun onPitchSemitonesChanged() {
        // Interface-shim semantics: the context-free overload applies at
        // normal speed (AudioPlaybackManager uses the speed-aware overload).
        playerProvider?.invoke()?.playbackParameters = PlaybackParameters(1f, pitchMultiplier)
    }

    override fun onReplayGainModeChanged() {
        // Named divergence vs the desktop funnel: the historical interface
        // shim recomputed with a NULL track context (gain = pre-amp only).
        applyReplayGain(trackGain = null, isShuffled = false)
    }

    override fun onReplayGainPreAmpDbChanged() {
        applyReplayGain(trackGain = null, isShuffled = false)
    }

    override fun onChannelMixChanged() {
        // Primary + crossfade sinks each need their own processor state.
        channelMixProcessor.setMode(_channelMixMode.value)
        channelMixProcessor.setEnabled(_channelMixEnabled.value)
        crossfadeChannelMixProcessor.setMode(_channelMixMode.value)
        crossfadeChannelMixProcessor.setEnabled(_channelMixEnabled.value)
    }

    override fun onVisualizerEnabledChanged(enabled: Boolean) {
        visualizerHelper.setEnabled(enabled)
    }

    // ── DSP apply implementations + Android lifecycle ───────────────────────

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

    /**
     * Push the per-track ReplayGain context and apply it to the in-sink DSP:
     * TRACK/ALBUM set the computed gain on both sinks (ALBUM + shuffled pins
     * it at exactly 0), DYNAMIC zeroes it and enables the compressor (it is
     * mutually exclusive with per-track loudness normalization), NONE zeroes
     * it and disables everything. The computation itself is the core's
     * [AudioEffectsStateCore.effectiveReplayGainDb] over the just-stored
     * context.
     */
    fun applyReplayGain(trackGain: Float?, isShuffled: Boolean = false) {
        setReplayGainContext(trackGain, isShuffled)
        val compressorActive = _replayGainMode.value == AudioNormalizationMode.DYNAMIC
        dynamicsProcessor.setEnabled(compressorActive)
        crossfadeDynamicsProcessor.setEnabled(compressorActive)
        val gain = replayGainContextEffectiveDb()
        replayGainProcessor.setGainDb(gain ?: 0f)
        crossfadeReplayGainProcessor.setGainDb(gain ?: 0f)
    }

    fun setReplayGainMode(mode: AudioNormalizationMode, normalizationGain: Float?, isShuffled: Boolean) {
        _replayGainMode.value = mode
        applyReplayGain(normalizationGain, isShuffled)
    }

    fun setReplayGainPreAmpDb(db: Float, normalizationGain: Float?, isShuffled: Boolean) {
        _replayGainPreAmpDb.value = db
        applyReplayGain(normalizationGain, isShuffled)
    }

    fun setPitchSemitones(semitones: Float, currentSpeed: Float) {
        setPitchSemitonesState(semitones)
        playerProvider?.invoke()?.playbackParameters = PlaybackParameters(currentSpeed, pitchMultiplier)
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

    fun applyBassBoost() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        bassBoostHelper.attach(audioSessionId)
        bassBoostHelper.setStrength(_bassBoostStrength)
        bassBoostHelper.setEnabled(_bassBoostEnabled.value)
    }

    fun applyVirtualizer() {
        val player = playerProvider?.invoke() ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        virtualizerHelper.attach(audioSessionId)
        virtualizerHelper.setStrength(_virtualizerStrength.value)
        virtualizerHelper.setEnabled(_virtualizerEnabled.value)
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
