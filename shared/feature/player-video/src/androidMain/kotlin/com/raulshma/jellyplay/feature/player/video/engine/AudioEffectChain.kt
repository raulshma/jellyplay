package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.C
import com.raulshma.jellyplay.core.data.playback.BassBoostHelper
import com.raulshma.jellyplay.core.data.playback.ChannelMixAudioProcessor
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.DynamicsCompressorAudioProcessor
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.LoudnessEnhancerHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.ReplayGainAudioProcessor
import com.raulshma.jellyplay.core.data.playback.ReverbHelper
import com.raulshma.jellyplay.core.data.playback.VirtualizerHelper
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ReverbPreset

/**
 * Orchestrates the audio-effect stack for [ExoPlayerEngine].
 *
 * Previously the engine owned 7 audiofx helpers + 3 in-sink AudioProcessors
 * as flat fields and applied them via a ~100-line `applyAudioEffects()`
 * block plus a mirror `releaseAudioEffects()`. This class:
 *
 *  - Owns the attach/apply/release lifecycle + the `attached`,
 *    `lastAppliedConfig`, `lastAppliedReverbPreset` bookkeeping so the
 *    engine stops knowing those details.
 *  - Preserves the exact behaviour of the prior inline block — same
 *    ordering, same mode→processor mapping, same equalizer/boost
 *    co-enabling rule.
 *
 * Not a Hilt type: the engine constructs it directly because the helpers
 * and in-sink processors are engine-private instances (the processors are
 * also installed into the renderers factory, so they must be the same
 * instances the chain applies state to).
 */
internal class AudioEffectChain(
    private val dialogueBoost: DialogueBoostHelper,
    private val nightMode: NightModeHelper,
    private val equalizerHelper: EqualizerHelper,
    private val bassBoostHelper: BassBoostHelper,
    private val virtualizerHelper: VirtualizerHelper,
    private val reverbHelper: ReverbHelper,
    private val loudnessEnhancerHelper: LoudnessEnhancerHelper,
    private val channelMixProcessor: ChannelMixAudioProcessor,
    private val dynamicsProcessor: DynamicsCompressorAudioProcessor,
    private val replayGainProcessor: ReplayGainAudioProcessor,
) {
    private var attached: Boolean = false
    private var lastAppliedConfig: AudioEffectsConfig? = null
    private var lastAppliedReverbPreset: ReverbPreset? = null

    /**
     * Pushes [config] onto the effect stack for session [sid].
     *
     * No-op when [sid] is unset. Skips the apply when the config is unchanged
     * since the last apply AND the chain is still attached (the per-tick
     * fast path).
     *
     * [normalizationGain] is the per-track ReplayGain dB resolved by the
     * engine at load(); it drives TRACK/ALBUM mode alongside [config].
     */
    fun apply(sid: Int, config: AudioEffectsConfig, normalizationGain: Float?) {
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        if (lastAppliedConfig == config && attached) return

        if (!attached) {
            dialogueBoost.attach(sid)
            nightMode.attach(sid)
            equalizerHelper.attach(sid)
            bassBoostHelper.attach(sid)
            virtualizerHelper.attach(sid)
            reverbHelper.attach(sid)
            loudnessEnhancerHelper.attach(sid)
            attached = true
        }

        dialogueBoost.setStrength(config.dialogueBoostStrength)
        dialogueBoost.setEnabled(config.dialogueBoostEnabled)
        // dialogueBoost also toggles its rumble-cut high-pass internally.

        nightMode.setStrength(config.nightModeStrength)
        nightMode.setEnabled(config.nightModeEnabled)

        // equalizerHelper owns the single priority-0 Equalizer for this
        // session and DialogueBoostHelper overlays its vocal-band gains on
        // top. The co-enabling rule (on while EITHER is on) lives inside
        // [EqualizerHelper.setEnabled] — callers pass both flags.
        equalizerHelper.setSettings(config.equalizerSettings)
        equalizerHelper.setEnabled(
            equalizerEnabled = config.equalizerEnabled,
            dialogueBoostEnabled = config.dialogueBoostEnabled,
        )

        // Normalization modes — handled by the in-sink AudioProcessor chain
        // (consistent with the audio/music + MPV paths):
        //  - DYNAMIC    → DSP compressor
        //  - TRACK/ALBUM → per-track ReplayGain when the item has a gain,
        //                  else no-op
        //  - NONE       → both off
        when (config.audioNormalizationMode) {
            AudioNormalizationMode.DYNAMIC -> {
                dynamicsProcessor.setEnabled(config.audioNormalizationEnabled)
                replayGainProcessor.setGainDb(0f)
            }
            AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> {
                dynamicsProcessor.setEnabled(false)
                val gain = normalizationGain ?: 0f
                replayGainProcessor.setGainDb(if (config.audioNormalizationEnabled) gain else 0f)
            }
            AudioNormalizationMode.NONE -> {
                dynamicsProcessor.setEnabled(false)
                replayGainProcessor.setGainDb(0f)
            }
        }

        channelMixProcessor.setMode(config.channelMixMode)
        channelMixProcessor.setEnabled(config.channelMixEnabled)

        bassBoostHelper.setStrength(config.bassBoostStrength)
        bassBoostHelper.setEnabled(config.bassBoostEnabled)

        virtualizerHelper.setStrength(config.virtualizerStrength)
        virtualizerHelper.setEnabled(config.virtualizerEnabled)

        loudnessEnhancerHelper.setGain(config.volumeBoostGain)
        loudnessEnhancerHelper.setEnabled(config.volumeBoostEnabled)

        if (config.reverbPreset != ReverbPreset.NONE) {
            if (lastAppliedReverbPreset != config.reverbPreset) {
                reverbHelper.detach()
                reverbHelper.attach(sid)
            }
            reverbHelper.setPreset(config.reverbPreset)
        } else {
            reverbHelper.setEnabled(false)
        }
        lastAppliedReverbPreset = config.reverbPreset

        lastAppliedConfig = config
    }

    fun release() {
        dialogueBoost.detach()
        nightMode.detach()
        equalizerHelper.detach()
        bassBoostHelper.detach()
        virtualizerHelper.detach()
        reverbHelper.detach()
        loudnessEnhancerHelper.detach()
        attached = false
        lastAppliedConfig = null
        lastAppliedReverbPreset = null
    }
}

/**
 * Returns whether Media3 must rebuild its audio-processing pipeline for this
 * effect change. [DefaultAudioSink] decides which processors are active, and
 * the channel count they produce, when it configures the pipeline. Updating a
 * processor's fields alone therefore cannot activate an initially-inactive
 * processor or change an already-configured output layout.
 */
internal fun requiresAudioPipelineReconfiguration(
    old: AudioEffectsConfig,
    new: AudioEffectsConfig,
): Boolean =
    old.channelMixMode != new.channelMixMode ||
        old.channelMixEnabled != new.channelMixEnabled ||
        old.audioNormalizationMode != new.audioNormalizationMode ||
        old.audioNormalizationEnabled != new.audioNormalizationEnabled ||
        old.dialogueBoostEnabled != new.dialogueBoostEnabled
