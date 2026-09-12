package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EffectStrength
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the [NightModeHelper] delegation contract: the helper owns no gain
 * table of its own — every strength change, attach, enable and detach is
 * forwarded to its [LoudnessEnhancerHelper] with the value derived from the
 * shared [EffectStrengthMapping.nightModeGainMb] table
 * (NONE=0, LOW=1500, MODERATE=3000, HIGH=4500 mB). Any drift between the
 * helper's internal mapping and the audio path's would reintroduce the exact
 * duplication the mapping object was extracted to kill.
 *
 * The internal enhancer is intercepted via `mockkConstructor` so no
 * `android.media.audiofx` construction is required — the delegation and the
 * pushed millibel values are asserted, not the native effect.
 */
class NightModeHelperTest {

    @Before
    fun setUp() {
        mockkConstructor(LoudnessEnhancerHelper::class)
        every { anyConstructed<LoudnessEnhancerHelper>().setGain(any()) } just runs
        every { anyConstructed<LoudnessEnhancerHelper>().attach(any()) } just runs
        every { anyConstructed<LoudnessEnhancerHelper>().setEnabled(any()) } just runs
        every { anyConstructed<LoudnessEnhancerHelper>().detach() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `default state is disabled with MODERATE strength`() {
        val helper = NightModeHelper()

        assertFalse(helper.isEnabled)
        assertEquals(EffectStrength.MODERATE, helper.strength)
    }

    @Test
    fun `setStrength pushes the pinned night-mode gain for every EffectStrength`() {
        val helper = NightModeHelper()

        helper.setStrength(EffectStrength.NONE)
        helper.setStrength(EffectStrength.LOW)
        helper.setStrength(EffectStrength.MODERATE)
        helper.setStrength(EffectStrength.HIGH)

        verifyOrder {
            anyConstructed<LoudnessEnhancerHelper>().setGain(0)
            anyConstructed<LoudnessEnhancerHelper>().setGain(1500)
            anyConstructed<LoudnessEnhancerHelper>().setGain(3000)
            anyConstructed<LoudnessEnhancerHelper>().setGain(4500)
        }
        assertEquals(EffectStrength.HIGH, helper.strength)
    }

    @Test
    fun `attach re-pushes the current strength gain before attaching the session`() {
        val helper = NightModeHelper()
        helper.setStrength(EffectStrength.HIGH)

        helper.attach(audioSessionId = 42)

        verifyOrder {
            anyConstructed<LoudnessEnhancerHelper>().setGain(4500)
            anyConstructed<LoudnessEnhancerHelper>().attach(42)
        }
    }

    @Test
    fun `setEnabled delegates the enable flag to the enhancer`() {
        val helper = NightModeHelper()

        helper.setEnabled(true)
        assertTrue(helper.isEnabled)
        helper.setEnabled(false)
        assertFalse(helper.isEnabled)

        verifyOrder {
            anyConstructed<LoudnessEnhancerHelper>().setEnabled(true)
            anyConstructed<LoudnessEnhancerHelper>().setEnabled(false)
        }
    }

    @Test
    fun `strength change while enabled retargets the enhancer gain`() {
        val helper = NightModeHelper()
        helper.setEnabled(true)

        helper.setStrength(EffectStrength.LOW)

        verify {
            anyConstructed<LoudnessEnhancerHelper>().setGain(1500)
        }
        assertEquals(EffectStrength.LOW, helper.strength)
    }

    @Test
    fun `detach delegates to the enhancer`() {
        val helper = NightModeHelper()

        helper.detach()

        verify(exactly = 1) { anyConstructed<LoudnessEnhancerHelper>().detach() }
    }
}
