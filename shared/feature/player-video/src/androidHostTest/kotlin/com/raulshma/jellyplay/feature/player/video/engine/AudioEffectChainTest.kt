package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.raulshma.jellyplay.core.model.ReverbPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The spatial-active override over the loudness enhancer: the stereo-downmix
 * boost is calibrated for the 5.1→2.0 downmix the engine holds OUT of the
 * way while spatial audio is effective, so applying it to the now-
 * multichannel stream would loudness-lift channels the user never asked to
 * boost. Pins that [AudioEffectChain.apply] skips the downmix gain under the
 * same gate as the channel-mix/cap/virtualizer neutralization while the
 * volume boost keeps riding the shared effect, and that the configured
 * downmix gain returns when the gate lifts (the flip alone must re-apply —
 * the spatial flag participates in the skip check).
 */
@RunWith(AndroidJUnit4::class)
class AudioEffectChainTest {

    /** The enhancer the chain was built with, paired for assertions. */
    private class Bench(val chain: AudioEffectChain, val enhancer: LoudnessEnhancerHelper)

    private fun bench(): Bench {
        val enhancer = LoudnessEnhancerHelper()
        val equalizer = EqualizerHelper()
        val chain = AudioEffectChain(
            dialogueBoost = DialogueBoostHelper(equalizer),
            nightMode = NightModeHelper(),
            equalizerHelper = equalizer,
            bassBoostHelper = BassBoostHelper(),
            virtualizerHelper = VirtualizerHelper(),
            reverbHelper = ReverbHelper(),
            loudnessEnhancerHelper = enhancer,
            channelMixProcessor = ChannelMixAudioProcessor(),
            dynamicsProcessor = DynamicsCompressorAudioProcessor(),
            replayGainProcessor = ReplayGainAudioProcessor(),
        )
        return Bench(chain, enhancer)
    }

    private fun effects(
        downmixBoostDb: Float,
        volumeBoostEnabled: Boolean = false,
        volumeBoostGain: Int = 0,
    ) = AudioEffectsConfig(
        downmixBoostDb = downmixBoostDb,
        volumeBoostEnabled = volumeBoostEnabled,
        volumeBoostGain = volumeBoostGain,
        reverbPreset = ReverbPreset.NONE,
    )

    @Test
    fun spatialActive_skipsDownmixBoost_butKeepsVolumeBoost() {
        val bench = bench()

        bench.chain.apply(
            sid = 42,
            config = effects(downmixBoostDb = 6f, volumeBoostEnabled = true, volumeBoostGain = 150),
            normalizationGain = null,
            spatialAudioActive = true,
        )

        // Downmix gain neutralized; the volume boost still rides the shared
        // effect at its own gain.
        assertEquals(150, bench.enhancer.gainmB)
        assertTrue(bench.enhancer.isEnabled)
    }

    @Test
    fun spatialActive_withVolumeBoostOff_leavesTheSharedEffectOff() {
        val bench = bench()

        bench.chain.apply(
            sid = 42,
            config = effects(downmixBoostDb = 6f),
            normalizationGain = null,
            spatialAudioActive = true,
        )

        // No downmix gain, no volume boost: nothing drives the enhancer on.
        assertEquals(0, bench.enhancer.gainmB)
        assertFalse(bench.enhancer.isEnabled)
    }

    @Test
    fun spatialInactive_appliesTheConfiguredDownmixGain() {
        val bench = bench()

        bench.chain.apply(
            sid = 42,
            config = effects(downmixBoostDb = 6f),
            normalizationGain = null,
            spatialAudioActive = false,
        )

        // The historical behavior the override must restore: 6 dB → 600 mB.
        assertEquals(600, bench.enhancer.gainmB)
        assertTrue(bench.enhancer.isEnabled)
    }

    @Test
    fun gateFlip_reappliesAndRestoresTheConfiguredGain() {
        val bench = bench()
        val config = effects(downmixBoostDb = 6f)

        bench.chain.apply(sid = 42, config = config, normalizationGain = null, spatialAudioActive = true)
        assertEquals(0, bench.enhancer.gainmB)
        assertFalse(bench.enhancer.isEnabled)

        // Same config, gate lifted: the skip check must NOT swallow the
        // re-apply (the spatial flag participates in it).
        bench.chain.apply(sid = 42, config = config, normalizationGain = null, spatialAudioActive = false)
        assertEquals(600, bench.enhancer.gainmB)
        assertTrue(bench.enhancer.isEnabled)
    }
}
