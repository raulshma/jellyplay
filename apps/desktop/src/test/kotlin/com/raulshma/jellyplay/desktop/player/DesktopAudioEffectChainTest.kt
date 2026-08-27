package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 14C: the mpv `af`-chain builder pin for the desktop audio effects —
 * the Android→mpv filter parity table in [DesktopAudioEffectChain] is only
 * as good as these strings. Every case cites the Android application path
 * (`AudioEffectsProcessor` / `AudioEffectChain` / the Android MPV engine's
 * own af branches) it mirrors. Pure functions: no mpv handle needed.
 */
class DesktopAudioEffectChainTest {

    private val allOff = AudioEffectsConfig()

    // ── empty chain ───────────────────────────────────────────────────────

    @Test
    fun allEffectsOffBuildsNoChain() {
        assertNull(DesktopAudioEffectChain.buildAfChain(allOff, outputChannelCount = 2))
    }

    // ── equalizer + dialogue boost (shared-stage parity) ──────────────────

    @Test
    fun equalizerPresetEmitsPeakingStagesPerNonZeroBandWithSharedFrequencies() {
        val bass = AudioEffectsConfig(
            equalizerEnabled = true,
            equalizerSettings = EqualizerSettings(EqualizerPreset.BASS_BOOST.bandLevels()),
        )
        val chain = DesktopAudioEffectChain.buildAfChain(bass, outputChannelCount = 2)!!
        // BASS_BOOST levels: 600,500,400,200 then zeros — four stages, exact
        // EqualizerHelper math (dB * 100 mB, ±1500 mB clamp → g in dB).
        assertEquals(
            listOf(
                "lavfi=[equalizer=f=60:t=q:w=1:g=6.00]",
                "lavfi=[equalizer=f=170:t=q:w=1:g=5.00]",
                "lavfi=[equalizer=f=310:t=q:w=1:g=4.00]",
                "lavfi=[equalizer=f=600:t=q:w=1:g=2.00]",
            ),
            chain.split(","),
        )
    }

    @Test
    fun dialogueBoostAloneKeepsEqStagesLiveWithVocalOffsetsAndHighPass() {
        // Co-enable rule: the EQ stages exist while EITHER the user EQ or
        // the boost is on (EqualizerHelper.setEnabled parity).
        val chain = DesktopAudioEffectChain.buildAfChain(
            AudioEffectsConfig(dialogueBoostEnabled = true),
            outputChannelCount = 2,
        )!!
        // MODERATE offsets over the shared band list: 600 Hz → warmth +2 dB,
        // 1k/3k → core +6 dB, 6k → harmonics +3 dB (DialogueBoostHelper table).
        assertEquals(
            listOf(
                "lavfi=[equalizer=f=600:t=q:w=1:g=2.00]",
                "lavfi=[equalizer=f=1000:t=q:w=1:g=6.00]",
                "lavfi=[equalizer=f=3000:t=q:w=1:g=6.00]",
                "lavfi=[equalizer=f=6000:t=q:w=1:g=3.00]",
                "lavfi=[highpass=f=80]",
            ),
            chain.split(","),
        )
    }

    @Test
    fun dialogueBoostOffsetsFoldOntoUserLevels() {
        // User raises 1 kHz to +9 dB (900 mB — band levels are millibels,
        // exactly what the ±1500 slider emits) while boosting (MODERATE
        // +600 mB): 900 + 600 = 1500 → +15 dB (the EqualizerHelper clamp).
        val levels = List(10) { 0 }.toMutableList().also { it[4] = 900 }
        val chain = DesktopAudioEffectChain.buildAfChain(
            AudioEffectsConfig(
                dialogueBoostEnabled = true,
                equalizerSettings = EqualizerSettings(levels),
            ),
            outputChannelCount = 2,
        )!!
        assertEquals(
            listOf(
                "lavfi=[equalizer=f=600:t=q:w=1:g=2.00]",
                "lavfi=[equalizer=f=1000:t=q:w=1:g=15.00]",
                "lavfi=[equalizer=f=3000:t=q:w=1:g=6.00]",
                "lavfi=[equalizer=f=6000:t=q:w=1:g=3.00]",
                "lavfi=[highpass=f=80]",
            ),
            chain.split(","),
        )
    }

    // ── bass boost / virtualizer / reverb ─────────────────────────────────

    @Test
    fun bassBoostStrengthsMapOntoLowShelfGains() {
        val high = DesktopAudioEffectChain.buildAfChain(
            AudioEffectsConfig(bassBoostEnabled = true, bassBoostStrength = EffectStrength.HIGH),
            outputChannelCount = 2,
        )
        assertEquals("lavfi=[bass=g=9.0:f=100]", high)
        assertEquals(
            "lavfi=[bass=g=3.6:f=100]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(bassBoostEnabled = true, bassBoostStrength = EffectStrength.LOW),
                outputChannelCount = 2,
            ),
        )
    }

    @Test
    fun virtualizerStrengthMapsOntoStereoWidth() {
        assertEquals(
            "lavfi=[extrastereo=m=1.50]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(virtualizerEnabled = true, virtualizerStrength = 500),
                outputChannelCount = 2,
            ),
        )
        assertEquals(
            "lavfi=[extrastereo=m=2.00]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(virtualizerEnabled = true, virtualizerStrength = 5000), // clamped
                outputChannelCount = 2,
            ),
        )
    }

    @Test
    fun reverbPresetsMapOntoAechoTablesAndNoneIsAbsent() {
        assertEquals(
            "lavfi=[aecho=0.85:0.88:40:0.30]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(reverbPreset = ReverbPreset.SMALL_ROOM),
                outputChannelCount = 2,
            ),
        )
        assertEquals(
            "lavfi=[aecho=0.65:0.85:180|260|340:0.40|0.30|0.20]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(reverbPreset = ReverbPreset.LARGE_HALL),
                outputChannelCount = 2,
            ),
        )
        assertNull(DesktopAudioEffectChain.buildAfChain(allOff.copy(reverbPreset = ReverbPreset.NONE), 2))
    }

    // ── night mode / replaygain gain staging ──────────────────────────────

    @Test
    fun nightModeCollapsesVolumeCutAndLoudnessCompensationIntoOneVolumeStage() {
        // EffectStrengthMapping: MODERATE = 0.4 cut (−7.96 dB) + 3000 mB (+3 dB).
        assertEquals(
            "lavfi=[volume=-4.96dB]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(nightModeEnabled = true, nightModeStrength = EffectStrength.MODERATE),
                outputChannelCount = 2,
            ),
        )
        // LOW: 0.7 cut (−3.10 dB) + 1500 mB (+1.5 dB).
        assertEquals(
            "lavfi=[volume=-1.60dB]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(nightModeEnabled = true, nightModeStrength = EffectStrength.LOW),
                outputChannelCount = 2,
            ),
        )
    }

    @Test
    fun dynamicNormalizationEmitsTheSharedCompressorString() {
        // Exact string the Android MPV path emits (and the parameter set the
        // Android DynamicsCompressorAudioProcessor documents as its defaults).
        assertEquals(
            "lavfi=[${DesktopAudioEffectChain.DYNAMIC_COMPRESSOR_FILTER}]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(
                    audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
                    audioNormalizationEnabled = true,
                ),
                outputChannelCount = 2,
            ),
        )
    }

    @Test
    fun trackReplayGainEmitsVolumeAtTheManagerComputedEffectiveDb() {
        assertEquals(
            "lavfi=[volume=3.50dB]",
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(
                    audioNormalizationMode = AudioNormalizationMode.TRACK,
                    audioNormalizationEnabled = true,
                    replayGainEffectiveDb = 3.5f,
                ),
                outputChannelCount = 2,
            ),
        )
        // ALBUM+shuffled zeroing lands as 0 dB — a no-op stage is skipped.
        assertNull(
            DesktopAudioEffectChain.buildAfChain(
                AudioEffectsConfig(
                    audioNormalizationMode = AudioNormalizationMode.ALBUM,
                    audioNormalizationEnabled = true,
                    replayGainEffectiveDb = 0f,
                ),
                outputChannelCount = 2,
            ),
        )
    }

    @Test
    fun normalizationModesAreMutuallyExclusive() {
        val chain = DesktopAudioEffectChain.buildAfChain(
            AudioEffectsConfig(
                audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
                audioNormalizationEnabled = true,
                replayGainEffectiveDb = 6f,
            ),
            outputChannelCount = 2,
        )!!
        assertTrue(chain.contains("acompressor"), chain)
        assertFalse(chain.contains("volume"), "per-track gain must not ride alongside the compressor")
    }

    // ── balance (layout-gated) ────────────────────────────────────────────

    @Test
    fun balanceEmitsPanOnlyForStereoOutputWithProcessorGains() {
        val config = allOff.copy(lrBalance = 0.5f)
        assertEquals(
            "lavfi=[pan=stereo|c0=c0*0.500|c1=c1*1.000]",
            DesktopAudioEffectChain.buildAfChain(config, outputChannelCount = 2),
        )
        // Negative balance attenuates the RIGHT side (BalanceAudioProcessor).
        assertEquals(
            "lavfi=[pan=stereo|c0=c0*1.000|c1=c1*0.500]",
            DesktopAudioEffectChain.buildAfChain(config.copy(lrBalance = -0.5f), outputChannelCount = 2),
        )
        // Layout gate: pan pins the output layout, so unknown/mono/surround skip.
        assertNull(DesktopAudioEffectChain.buildAfChain(config, outputChannelCount = null))
        assertNull(DesktopAudioEffectChain.buildAfChain(config, outputChannelCount = 1))
        assertNull(DesktopAudioEffectChain.buildAfChain(config, outputChannelCount = 6))
        // Centered balance never emits.
        assertNull(DesktopAudioEffectChain.buildAfChain(allOff.copy(lrBalance = 0f), outputChannelCount = 2))
    }

    // ── channel mix + pitch (property mappings) ───────────────────────────

    @Test
    fun channelMixMapsToAudioChannelsLikeTheAndroidMpvPath() {
        assertEquals("stereo", DesktopAudioEffectChain.channelMixToAudioChannels(ChannelMixMode.STEREO_DOWNMIX, true))
        assertEquals("mono", DesktopAudioEffectChain.channelMixToAudioChannels(ChannelMixMode.MONO, true))
        assertEquals("5.1", DesktopAudioEffectChain.channelMixToAudioChannels(ChannelMixMode.SURROUND_UPMIX, true))
        assertEquals("auto", DesktopAudioEffectChain.channelMixToAudioChannels(ChannelMixMode.AUTO, true))
        assertEquals("auto", DesktopAudioEffectChain.channelMixToAudioChannels(ChannelMixMode.MONO, false))
    }

    @Test
    fun pitchSemitonesMapOntoTwelfthRootRatios() {
        assertEquals(1.0, DesktopAudioEffectChain.pitchRatio(0f))
        assertEquals(2.0, DesktopAudioEffectChain.pitchRatio(12f))
        assertEquals(0.5, DesktopAudioEffectChain.pitchRatio(-12f))
        assertEquals(1.0594630943592953, DesktopAudioEffectChain.pitchRatio(1f))
    }

    // ── ordering (gain → tone → spatial → balance) ────────────────────────

    @Test
    fun fullStackOrdersGainStagesFirstAndBalanceLast() {
        val chain = DesktopAudioEffectChain.buildAfChain(
            AudioEffectsConfig(
                nightModeEnabled = true,
                audioNormalizationMode = AudioNormalizationMode.TRACK,
                audioNormalizationEnabled = true,
                replayGainEffectiveDb = 2f,
                equalizerEnabled = true,
                equalizerSettings = EqualizerSettings(EqualizerPreset.VOCAL.bandLevels()),
                dialogueBoostEnabled = true,
                bassBoostEnabled = true,
                virtualizerEnabled = true,
                reverbPreset = ReverbPreset.MEDIUM_ROOM,
                lrBalance = 0.25f,
            ),
            outputChannelCount = 2,
        )!!
        val stages = chain.split(",")
        assertTrue(stages.first().startsWith("lavfi=[volume="), stages.first())
        assertTrue(stages.last().startsWith("lavfi=[pan="), stages.last())
        val highPassIdx = stages.indexOfFirst { it.contains("highpass") }
        val bassIdx = stages.indexOfFirst { it.contains("bass") }
        val aechoIdx = stages.indexOfFirst { it.contains("aecho") }
        assertTrue(highPassIdx < bassIdx && bassIdx < aechoIdx, stages.toString())
    }
}
