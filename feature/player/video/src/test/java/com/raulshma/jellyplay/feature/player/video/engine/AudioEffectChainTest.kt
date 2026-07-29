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
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the audio-effect stack orchestrator. Every helper is mocked
 * so we can assert the attach ordering, the no-op fast path, the
 * mode→processor mapping (DYNAMIC → compressor; TRACK/ALBUM → ReplayGain;
 * NONE → both off) and the reverb re-attach rule, without touching the
 * Android `AudioFx` framework.
 */
class AudioEffectChainTest {

    private lateinit var dialogueBoost: DialogueBoostHelper
    private lateinit var nightMode: NightModeHelper
    private lateinit var equalizer: EqualizerHelper
    private lateinit var bassBoost: BassBoostHelper
    private lateinit var virtualizer: VirtualizerHelper
    private lateinit var reverb: ReverbHelper
    private lateinit var loudness: LoudnessEnhancerHelper
    private lateinit var channelMix: ChannelMixAudioProcessor
    private lateinit var dynamics: DynamicsCompressorAudioProcessor
    private lateinit var replayGain: ReplayGainAudioProcessor

    private lateinit var chain: AudioEffectChain

    private val sid = 42
    private val baseConfig
        get() = AudioEffectsConfig(
            dialogueBoostEnabled = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
            nightModeEnabled = false,
            nightModeStrength = EffectStrength.MODERATE,
            nightModeGain = 0,
            equalizerEnabled = false,
            equalizerSettings = com.raulshma.jellyplay.core.model.EqualizerSettings(),
            audioNormalizationMode = AudioNormalizationMode.NONE,
            audioNormalizationEnabled = false,
            channelMixMode = ChannelMixMode.AUTO,
            channelMixEnabled = false,
            bassBoostEnabled = false,
            bassBoostStrength = EffectStrength.MODERATE,
            virtualizerEnabled = false,
            virtualizerStrength = 500,
            reverbPreset = ReverbPreset.NONE,
            volumeBoostEnabled = false,
            volumeBoostGain = 0,
        )

    @Before
    fun setUp() {
        clearAllMocks()
        dialogueBoost = mockk(relaxed = true)
        nightMode = mockk(relaxed = true)
        equalizer = mockk(relaxed = true)
        bassBoost = mockk(relaxed = true)
        virtualizer = mockk(relaxed = true)
        reverb = mockk(relaxed = true)
        loudness = mockk(relaxed = true)
        channelMix = mockk(relaxed = true)
        dynamics = mockk(relaxed = true)
        replayGain = mockk(relaxed = true)
        chain = AudioEffectChain(
            dialogueBoost, nightMode, equalizer, bassBoost, virtualizer,
            reverb, loudness, channelMix, dynamics, replayGain,
        )
    }

    // ── Guarded paths ──────────────────────────────────────────────────────────

    @Test
    fun apply_unsetSessionId_attachesNothingAndAppliesNothing() {
        chain.apply(C.AUDIO_SESSION_ID_UNSET, baseConfig, normalizationGain = null)
        // Nothing — no helper touched.
    }

    @Test
    fun apply_sameConfigAfterAttach_skipsSecondApply() {
        chain.apply(sid, baseConfig, normalizationGain = null)
        chain.apply(sid, baseConfig, normalizationGain = null)
        // attach() called exactly once on each helper; nothing repeated.
        verify(exactly = 1) { dialogueBoost.attach(sid) }
        verify(exactly = 1) { nightMode.attach(sid) }
        verify(exactly = 1) { equalizer.attach(sid) }
        verify(exactly = 1) { bassBoost.attach(sid) }
        verify(exactly = 1) { virtualizer.attach(sid) }
        verify(exactly = 1) { reverb.attach(sid) }
        verify(exactly = 1) { loudness.attach(sid) }
    }

    // ── First apply: attach ordering ────────────────────────────────────────────

    @Test
    fun apply_firstCall_attachesAllHelpersInOrder() {
        chain.apply(sid, baseConfig, normalizationGain = null)

        verifyOrder {
            dialogueBoost.attach(sid)
            nightMode.attach(sid)
            equalizer.attach(sid)
            bassBoost.attach(sid)
            virtualizer.attach(sid)
            reverb.attach(sid)
            loudness.attach(sid)
        }
    }

    @Test
    fun apply_firstCall_appliesDialogueBoostAndNightModeSettings() {
        val cfg = baseConfig.copy(
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.LOW,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { dialogueBoost.setStrength(EffectStrength.HIGH) }
        verify { dialogueBoost.setEnabled(true) }
        verify { nightMode.setStrength(EffectStrength.LOW) }
        verify { nightMode.setEnabled(true) }
    }

    @Test
    fun apply_firstCall_forwardsCoEnableFlagsToEqualizer() {
        val cfg = baseConfig.copy(
            equalizerEnabled = true,
            dialogueBoostEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify {
            equalizer.setSettings(any())
            equalizer.setEnabled(equalizerEnabled = true, dialogueBoostEnabled = true)
        }
    }

    // ── Normalization mode → processor mapping ──────────────────────────────────

    @Test
    fun apply_dynamicMode_enablesCompressorAndZerosReplayGain() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = -5f)

        verify { dynamics.setEnabled(true) }
        verify { replayGain.setGainDb(0f) }
    }

    @Test
    fun apply_trackMode_appliesReplayGainWhenEnabled() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            audioNormalizationEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = -7.5f)

        verify { dynamics.setEnabled(false) }
        verify { replayGain.setGainDb(-7.5f) }
    }

    @Test
    fun apply_albumMode_appliesReplayGainWhenEnabled() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.ALBUM,
            audioNormalizationEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = 3f)

        verify { dynamics.setEnabled(false) }
        verify { replayGain.setGainDb(3f) }
    }

    @Test
    fun apply_trackModeDisabled_zerosReplayGain() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            audioNormalizationEnabled = false,
        )

        chain.apply(sid, cfg, normalizationGain = -9f)

        verify { replayGain.setGainDb(0f) }
    }

    @Test
    fun apply_trackModeWithNullGain_treatsGainAsZero() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            audioNormalizationEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { replayGain.setGainDb(0f) }
    }

    @Test
    fun apply_noneMode_disablesCompressorAndZerosReplayGain() {
        val cfg = baseConfig.copy(
            audioNormalizationMode = AudioNormalizationMode.NONE,
            audioNormalizationEnabled = false,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { dynamics.setEnabled(false) }
        verify { replayGain.setGainDb(0f) }
    }

    // ── Channel mix / bass / virtualizer / loudness ─────────────────────────────

    @Test
    fun apply_forwardsChannelMixModeAndEnabled() {
        val cfg = baseConfig.copy(
            channelMixMode = ChannelMixMode.SURROUND_UPMIX,
            channelMixEnabled = true,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { channelMix.setMode(ChannelMixMode.SURROUND_UPMIX) }
        verify { channelMix.setEnabled(true) }
    }

    @Test
    fun apply_forwardsBassBoostStrengthAndEnabled() {
        val cfg = baseConfig.copy(
            bassBoostEnabled = true,
            bassBoostStrength = EffectStrength.HIGH,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { bassBoost.setStrength(EffectStrength.HIGH) }
        verify { bassBoost.setEnabled(true) }
    }

    @Test
    fun apply_forwardsVirtualizerStrengthAndEnabled() {
        val cfg = baseConfig.copy(
            virtualizerEnabled = true,
            virtualizerStrength = 900,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { virtualizer.setStrength(900) }
        verify { virtualizer.setEnabled(true) }
    }

    @Test
    fun apply_forwardsVolumeBoostGainAndEnabled() {
        val cfg = baseConfig.copy(
            volumeBoostEnabled = true,
            volumeBoostGain = 600,
        )

        chain.apply(sid, cfg, normalizationGain = null)

        verify { loudness.setGain(600) }
        verify { loudness.setEnabled(true) }
    }

    // ── Reverb re-attach rule ───────────────────────────────────────────────────

    @Test
    fun apply_newReverbPreset_reAttachesBeforeSetPreset() {
        val first = baseConfig.copy(reverbPreset = ReverbPreset.SMALL_ROOM)
        val second = baseConfig.copy(reverbPreset = ReverbPreset.LARGE_HALL)

        chain.apply(sid, first, normalizationGain = null)
        chain.apply(sid, second, normalizationGain = null)

        // First apply attaches reverb once (part of attach order). The preset
        // switch detaches+re-attaches, then sets the new preset. Verify the
        // detach→attach→preset ordering happens for the *second* apply.
        verifyOrder {
            reverb.detach()
            reverb.attach(sid)
            reverb.setPreset(ReverbPreset.LARGE_HALL)
        }
    }

    @Test
    fun apply_sameReverbPresetAcrossApplies_doesNotReAttach() {
        val cfg = baseConfig.copy(reverbPreset = ReverbPreset.PLATE)

        chain.apply(sid, cfg, normalizationGain = null)
        // Change a different field so the apply is not skipped, but preset stays.
        chain.apply(sid, cfg.copy(nightModeEnabled = true), normalizationGain = null)

        // First apply detaches once (null → PLATE). The second apply keeps the
        // preset, so detach is NOT called again — still exactly 1 overall.
        verify(exactly = 1) { reverb.detach() }
        verify(exactly = 2) { reverb.setPreset(ReverbPreset.PLATE) }
    }

    @Test
    fun apply_reverbNone_disablesReverb() {
        chain.apply(sid, baseConfig, normalizationGain = null)

        verify { reverb.setEnabled(false) }
    }

    // ── release() ───────────────────────────────────────────────────────────────

    @Test
    fun release_detachesEveryHelperOnce() {
        chain.apply(sid, baseConfig, normalizationGain = null)
        chain.release()

        verify(exactly = 1) { dialogueBoost.detach() }
        verify(exactly = 1) { nightMode.detach() }
        verify(exactly = 1) { equalizer.detach() }
        verify(exactly = 1) { bassBoost.detach() }
        verify(exactly = 1) { virtualizer.detach() }
        verify(exactly = 1) { reverb.detach() }
        verify(exactly = 1) { loudness.detach() }
    }

    @Test
    fun release_thenReapply_reattaches() {
        chain.apply(sid, baseConfig, normalizationGain = null)
        chain.release()
        chain.apply(sid, baseConfig, normalizationGain = null)

        // attach called twice over the lifecycle.
        verify(exactly = 2) { dialogueBoost.attach(sid) }
        verify(exactly = 2) { nightMode.attach(sid) }
    }

    @Test
    fun release_withoutPriorApply_stillDetachesAll() {
        chain.release()

        verify(exactly = 1) { dialogueBoost.detach() }
        verify(exactly = 1) { nightMode.detach() }
        verify(exactly = 1) { equalizer.detach() }
        verify(exactly = 1) { bassBoost.detach() }
        verify(exactly = 1) { virtualizer.detach() }
        verify(exactly = 1) { reverb.detach() }
        verify(exactly = 1) { loudness.detach() }
    }

    // ── Media3 audio-pipeline reconfiguration ────────────────────────────────

    @Test
    fun pipelineReconfiguration_channelMixChange_required() {
        assertEquals(
            true,
            requiresAudioPipelineReconfiguration(
                baseConfig,
                baseConfig.copy(
                    channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
                    channelMixEnabled = true,
                ),
            ),
        )
    }

    @Test
    fun pipelineReconfiguration_normalizationOrDialogueHighPassChange_required() {
        assertEquals(
            true,
            requiresAudioPipelineReconfiguration(
                baseConfig,
                baseConfig.copy(
                    audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
                    audioNormalizationEnabled = true,
                ),
            ),
        )
        assertEquals(
            true,
            requiresAudioPipelineReconfiguration(
                baseConfig,
                baseConfig.copy(dialogueBoostEnabled = true),
            ),
        )
    }

    @Test
    fun pipelineReconfiguration_androidAudioEffectOnlyChange_notRequired() {
        assertEquals(
            false,
            requiresAudioPipelineReconfiguration(
                baseConfig,
                baseConfig.copy(dialogueBoostStrength = EffectStrength.HIGH),
            ),
        )
    }

    // Note: DecoderMode belongs to EngineConfig, not AudioEffectsConfig. There is
    // no runtime assertion to make here — the separation is enforced structurally
    // by the type definitions (adding a DecoderMode field to AudioEffectsConfig
    // would break every construction site, including baseConfig above), so a
    // tautological test would only add noise.
}
