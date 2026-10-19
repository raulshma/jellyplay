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
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlin.math.roundToInt

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
    private var lastAppliedSpatialActive: Boolean = false

    /**
     * Pushes [config] onto the effect stack for session [sid].
     *
     * No-op when [sid] is unset. Skips the apply when the config is unchanged
     * since the last apply AND the chain is still attached (the per-tick
     * fast path).
     *
     * [normalizationGain] is the per-track ReplayGain dB resolved by the
     * engine at load(); it drives TRACK/ALBUM mode alongside [config].
     *
     * [spatialAudioActive] is the ENGINE's effective spatial-audio gate
     * ([resolveEffectiveSpatialState] == ACTIVE): while true it OVERRIDES the
     * config's channel-mix/speaker-cap/virtualizer/downmix-boost settings —
     * the in-sink downmix and cap are held off so multichannel PCM reaches
     * the AudioTrack the platform spatializer can engage, the Virtualizer is
     * held off (mutually exclusive; spatial wins, supersession recorded by
     * the engine), and the stereo-oriented downmix loudness boost is not
     * applied to the now-multichannel stream. It participates in the skip
     * check so a flip alone re-applies.
     */
    fun apply(sid: Int, config: AudioEffectsConfig, normalizationGain: Float?, spatialAudioActive: Boolean) {
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        if (lastAppliedConfig == config && attached && lastAppliedSpatialActive == spatialAudioActive) return
        lastAppliedSpatialActive = spatialAudioActive

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

        // The channel-mix effect + speaker-layout cap ride the same processor.
        // Spatial-active override: while the platform spatializer is effective
        // the engine must NOT narrow the stream — the mixer needs the source
        // channel layout, so both the downmix effect and the cap are held off
        // regardless of the config (the config values return on the next
        // apply once spatial is no longer effective).
        if (spatialAudioActive) {
            channelMixProcessor.setMode(ChannelMixMode.AUTO)
            channelMixProcessor.setEnabled(false)
            channelMixProcessor.setChannelCap(null)
        } else {
            channelMixProcessor.setMode(config.channelMixMode)
            channelMixProcessor.setEnabled(config.channelMixEnabled)
            // The speaker-layout cap rides the same processor: it clamps the
            // configured output layout independently of the channel-mix effect
            // (null for AUTO). ExoPlayerEngine re-runs the pipeline when either
            // moves (requiresAudioPipelineReconfiguration).
            channelMixProcessor.setChannelCap(config.maxAudioChannels.channelCount)
        }

        bassBoostHelper.setStrength(config.bassBoostStrength)
        bassBoostHelper.setEnabled(config.bassBoostEnabled)

        // Spatial-active override: the Virtualizer session effect is mutually
        // exclusive with platform spatialization, and SPATIAL WINS when the
        // user enabled both — the engine holds the virtualizer off (the
        // supersession is recorded by the engine for the stats surface).
        virtualizerHelper.setStrength(config.virtualizerStrength)
        virtualizerHelper.setEnabled(config.virtualizerEnabled && !spatialAudioActive)

        // The loudness enhancer is ONE session effect shared by the volume
        // boost and the stereo-downmix boost: the stronger of the two gains
        // wins, and it is enabled while either is on (a 0-gain enable is the
        // historical volume-boost-on-with-0-gain shape, preserved).
        // Spatial-active override: the downmix boost is calibrated for the
        // 5.1→2.0 downmix the engine holds OUT of the way while spatial is
        // effective — boosting the now-multichannel stream would loudness-
        // lift channels the user never asked to boost. It is skipped under
        // the same gate as the channel-mix/cap/virtualizer neutralization
        // (the config value returns on the next apply once spatial is no
        // longer effective; the spatial flag participates in the skip check
        // above, so the flip alone re-applies).
        val downmixGainMb = if (spatialAudioActive) 0 else (config.downmixBoostDb * 100).roundToInt()
        loudnessEnhancerHelper.setGain(maxOf(config.volumeBoostGain, downmixGainMb))
        loudnessEnhancerHelper.setEnabled(config.volumeBoostEnabled || downmixGainMb > 0)

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
        lastAppliedSpatialActive = false
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
        old.maxAudioChannels != new.maxAudioChannels ||
        old.audioNormalizationMode != new.audioNormalizationMode ||
        old.audioNormalizationEnabled != new.audioNormalizationEnabled ||
        old.dialogueBoostEnabled != new.dialogueBoostEnabled ||
        // The spatial gate flips what the ChannelMixAudioProcessor is told
        // (downmix neutralized + cap lifted while spatial is effective), so a
        // toggle needs the same pipeline re-run the channel-mix knobs get.
        // onlyHeadphones is deliberately absent: it only narrows WHEN the gate
        // applies (the route evaluation), never the processor state itself.
        old.spatialAudioEnabled != new.spatialAudioEnabled
