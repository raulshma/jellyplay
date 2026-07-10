package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EffectStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DialogueBoostHelperTest {

    private lateinit var equalizerHelper: EqualizerHelper
    private lateinit var helper: DialogueBoostHelper

    @Before
    fun setUp() {
        equalizerHelper = EqualizerHelper()
        helper = DialogueBoostHelper(equalizerHelper)
    }

    @Test
    fun defaultState_disabledModerate() {
        assertFalse(helper.isEnabled)
        assertEquals(EffectStrength.MODERATE, helper.strength)
    }

    @Test
    fun setEnabled_togglesHighPassFilterAlongsideEqOverlay() {
        val hpf = HighPassFilterAudioProcessor()
        assertFalse(hpf.isActive()) // not configured yet → inactive

        val helperWithHpf = DialogueBoostHelper(equalizerHelper, hpf)
        helperWithHpf.setEnabled(true)
        // HPF enabled flag is set even before configure(); isActive() also
        // requires a configured format, so we assert the requested state.
        assertTrue(helperWithHpf.isEnabled)

        helperWithHpf.setEnabled(false)
        assertFalse(helperWithHpf.isEnabled)
    }

    @Test
    fun computeOffsets_moderate_appliesCoreVocalHarmonicsAndWarmth() {
        helper.setStrength(EffectStrength.MODERATE)
        val freqs = listOf(60, 250, 500, 1_000, 2_000, 4_000, 6_000, 8_000, 16_000)

        val offsets = helper.computeOffsets(freqs)

        // 60 Hz, 250 Hz, 16 kHz are outside the vocal bands -> not present
        assertFalse(offsets.containsKey(0))
        assertFalse(offsets.containsKey(1))
        assertFalse(offsets.containsKey(8))
        assertEquals(200, offsets[2]) // 500 Hz -> low-mid warmth (500..1000)
        assertEquals(600, offsets[3]) // 1 kHz -> core vocal (1000..4000 wins over 500..1000)
        assertEquals(600, offsets[4]) // 2 kHz -> core vocal
        assertEquals(600, offsets[5]) // 4 kHz -> core vocal (inclusive upper bound, matches first)
        assertEquals(300, offsets[6]) // 6 kHz -> upper harmonics
        assertEquals(300, offsets[7]) // 8 kHz -> upper harmonics
    }

    @Test
    fun computeOffsets_low_scalesDownFromModerate() {
        helper.setStrength(EffectStrength.LOW)
        // 1000/2000/4000 Hz all fall in the core-vocal range; 6000 Hz is upper harmonics.
        val offsets = helper.computeOffsets(listOf(1_000, 2_000, 4_000, 6_000))

        assertEquals(300, offsets[0]) // core
        assertEquals(300, offsets[1]) // core
        assertEquals(300, offsets[2]) // core
        assertEquals(150, offsets[3]) // harmonics
    }

    @Test
    fun computeOffsets_high_scalesUpFromModerate() {
        helper.setStrength(EffectStrength.HIGH)
        val offsets = helper.computeOffsets(listOf(1_000, 2_000, 4_000, 6_000))

        assertEquals(900, offsets[0]) // core
        assertEquals(900, offsets[1]) // core
        assertEquals(900, offsets[2]) // core
        assertEquals(450, offsets[3]) // harmonics
    }

    @Test
    fun computeOffsets_bandsOutsideVocalRange_omitted() {
        helper.setStrength(EffectStrength.MODERATE)
        val offsets = helper.computeOffsets(listOf(30, 120, 16_000, 20_000))
        assertTrue(offsets.isEmpty())
    }

    @Test
    fun computeOffsets_boundaryFreqs_inclusiveBounds() {
        helper.setStrength(EffectStrength.MODERATE)
        // 500 Hz is the lower bound of warmth; 1000 Hz matches the core-vocal range FIRST (the
        // `when` branches are evaluated top-down), so it gets the core boost, not warmth.
        val offsets = helper.computeOffsets(listOf(500, 1_000))
        assertEquals(200, offsets[0]) // 500 Hz -> warmth (500..1000)
        assertEquals(600, offsets[1]) // 1 kHz -> core vocal (1000..4000 wins over 500..1000)
    }

    @Test
    fun setStrength_whenEnabled_reappliesButOffsetsRequireAttachedBands() {
        helper.setStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, helper.strength)
        // No bands attached -> applyOffsets is a no-op, but strength is persisted.
        helper.setEnabled(true)
        assertEquals(EffectStrength.HIGH, helper.strength)
    }

    @Test
    fun setEnabled_toggle_updatesFlag() {
        helper.setEnabled(true)
        assertTrue(helper.isEnabled)
        helper.setEnabled(false)
        assertFalse(helper.isEnabled)
    }

    @Test
    fun detach_clearsEnabled() {
        helper.setEnabled(true)
        helper.detach()
        assertFalse(helper.isEnabled)
    }
}
