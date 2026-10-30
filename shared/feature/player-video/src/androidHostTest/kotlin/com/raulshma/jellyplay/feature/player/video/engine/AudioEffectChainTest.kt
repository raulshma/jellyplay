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
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two pin sets over the audio-effect split:
 *
 *  - The spatial-active override over the loudness enhancer (the Exo chain's
 *    in-sink remainder): the stereo-downmix boost is calibrated for the
 *    5.1→2.0 downmix the engine holds OUT of the way while spatial audio is
 *    effective, so applying it to the now-multichannel stream would loudness-
 *    lift channels the user never asked to boost. Pins that
 *    [AudioEffectChain.apply] skips the downmix gain under the same gate as
 *    the channel-mix/cap/virtualizer neutralization while the volume boost
 *    keeps riding the shared effect, and that the configured downmix gain
 *    returns when the gate lifts (the flip alone must re-apply — the spatial
 *    flag participates in the skip check).
 *  - The engine-agnostic session-effect trio ([AudioSessionEffectTrio]): the
 *    attach/apply/release choreography and the equalizer/dialogue-boost
 *    co-enabling rule in their ONE home — the same choreography mpv rides
 *    (its former hand-copy had no test).
 */
@RunWith(AndroidJUnit4::class)
class AudioEffectChainTest {

    /** The enhancer the chain was built with, paired for assertions. */
    private class Bench(
        val chain: AudioEffectChain,
        val enhancer: LoudnessEnhancerHelper,
        val equalizer: EqualizerHelper,
        val dialogueBoost: DialogueBoostHelper,
        val nightMode: NightModeHelper,
    )

    private fun bench(): Bench {
        val enhancer = LoudnessEnhancerHelper()
        val equalizer = EqualizerHelper()
        val dialogueBoost = DialogueBoostHelper(equalizer)
        val nightMode = NightModeHelper()
        val chain = AudioEffectChain(
            dialogueBoost = dialogueBoost,
            nightMode = nightMode,
            equalizerHelper = equalizer,
            bassBoostHelper = BassBoostHelper(),
            virtualizerHelper = VirtualizerHelper(),
            reverbHelper = ReverbHelper(),
            loudnessEnhancerHelper = enhancer,
            channelMixProcessor = ChannelMixAudioProcessor(),
            dynamicsProcessor = DynamicsCompressorAudioProcessor(),
            replayGainProcessor = ReplayGainAudioProcessor(),
        )
        return Bench(chain, enhancer, equalizer, dialogueBoost, nightMode)
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

    // ── AudioSessionEffectTrio ─────────────────────────────────────────

    /** The trio plus its three helpers, paired for assertions. */
    private class TrioBench(
        val trio: AudioSessionEffectTrio,
        val equalizer: EqualizerHelper,
        val dialogueBoost: DialogueBoostHelper,
        val nightMode: NightModeHelper,
    )

    private fun trioBench(): TrioBench {
        val equalizer = EqualizerHelper()
        val dialogueBoost = DialogueBoostHelper(equalizer)
        val nightMode = NightModeHelper()
        return TrioBench(
            AudioSessionEffectTrio(equalizer, dialogueBoost, nightMode),
            equalizer,
            dialogueBoost,
            nightMode,
        )
    }

    private fun sessionEffects(
        equalizerEnabled: Boolean = false,
        dialogueBoostEnabled: Boolean = false,
        dialogueBoostStrength: EffectStrength = EffectStrength.HIGH,
        nightModeEnabled: Boolean = false,
        nightModeStrength: EffectStrength = EffectStrength.LOW,
    ) = AudioEffectsConfig(
        equalizerEnabled = equalizerEnabled,
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        reverbPreset = ReverbPreset.NONE,
    )

    @Test
    fun trio_equalizerAloneOn_enablesTheSharedEffect() {
        val bench = trioBench()

        bench.trio.attach(42)
        bench.trio.apply(sessionEffects(equalizerEnabled = true))

        assertTrue(bench.equalizer.isEnabled)
    }

    @Test
    fun trio_dialogueBoostAloneOn_enablesTheSharedEffect() {
        // The co-enabling rule's load-bearing half: the boost overlays its
        // vocal-band gains on the shared priority-0 Equalizer, so the EQ
        // must be held open while ONLY the boost is on.
        val bench = trioBench()

        bench.trio.attach(42)
        bench.trio.apply(sessionEffects(dialogueBoostEnabled = true))

        assertTrue(bench.equalizer.isEnabled)
        assertTrue(bench.dialogueBoost.isEnabled)
    }

    @Test
    fun trio_bothOff_disablesTheSharedEffect() {
        val bench = trioBench()

        bench.trio.attach(42)
        bench.trio.apply(sessionEffects(equalizerEnabled = true))
        bench.trio.apply(sessionEffects(equalizerEnabled = false, dialogueBoostEnabled = false))

        assertFalse(bench.equalizer.isEnabled)
    }

    @Test
    fun trio_applyMirrorsTheConfigOntoTheThreeHelpers() {
        val bench = trioBench()

        bench.trio.attach(42)
        bench.trio.apply(
            sessionEffects(
                equalizerEnabled = true,
                dialogueBoostEnabled = true,
                dialogueBoostStrength = EffectStrength.LOW,
                nightModeEnabled = true,
                nightModeStrength = EffectStrength.HIGH,
            ),
        )

        assertTrue(bench.equalizer.isEnabled)
        assertTrue(bench.dialogueBoost.isEnabled)
        assertEquals(EffectStrength.LOW, bench.dialogueBoost.strength)
        assertTrue(bench.nightMode.isEnabled)
    }

    @Test
    fun trio_release_detachesAndAFreshSessionRebinds() {
        // mpv's per-load shape: release() drops the old session's handles,
        // the next load allocates a fresh session id and the attach+apply
        // ladder re-drives the helper state on it.
        val bench = trioBench()

        bench.trio.attach(42)
        bench.trio.apply(sessionEffects(dialogueBoostEnabled = true))
        assertTrue(bench.dialogueBoost.isEnabled)

        bench.trio.release()
        assertFalse(bench.dialogueBoost.isEnabled)

        bench.trio.attach(43)
        bench.trio.apply(sessionEffects(dialogueBoostEnabled = true))
        assertTrue(bench.dialogueBoost.isEnabled)
    }

    @Test
    fun chainApply_drivesTheSharedTrioChoreography() {
        // The Exo chain composes the same trio mpv rides: its apply must
        // land the session-effect slices exactly like a direct trio apply.
        val bench = bench()

        bench.chain.apply(
            sid = 42,
            config = effects(downmixBoostDb = 0f)
                .copy(
                    dialogueBoostEnabled = true,
                    dialogueBoostStrength = EffectStrength.MODERATE,
                    nightModeEnabled = true,
                    equalizerEnabled = false,
                ),
            normalizationGain = null,
            spatialAudioActive = false,
        )

        assertTrue(bench.dialogueBoost.isEnabled)
        // Equalizer held open by the boost alone — the co-enabling call the
        // chain no longer spells itself.
        assertTrue(bench.equalizer.isEnabled)
        assertTrue(bench.nightMode.isEnabled)
        assertEquals(EffectStrength.MODERATE, bench.dialogueBoost.strength)
    }
}
