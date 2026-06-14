package com.raulshma.jellyplay.core.ui.tv.input

import org.junit.Assert.assertEquals
import org.junit.Test

class DpadRepeatAcceleratorTest {

    private val accelerator = DpadRepeatAccelerator.Default

    @Test
    fun zeroRepeat_returnsBaseMultiplier() {
        assertEquals(1.0f, accelerator.calculateMultiplier(0), 0.001f)
    }

    @Test
    fun oneRepeat_appliesAcceleration() {
        assertEquals(1.1f, accelerator.calculateMultiplier(1), 0.001f)
    }

    @Test
    fun tenRepeats_doublesStep() {
        assertEquals(2.0f, accelerator.calculateMultiplier(10), 0.001f)
    }

    @Test
    fun fifteenRepeats_capsAtMax() {
        assertEquals(2.5f, accelerator.calculateMultiplier(15), 0.001f)
    }

    @Test
    fun hundredRepeats_capsAtMax() {
        assertEquals(2.5f, accelerator.calculateMultiplier(100), 0.001f)
    }

    @Test
    fun calculateStep_baseTenThousand_repeatZero() {
        assertEquals(10_000L, accelerator.calculateStep(10_000L, 0))
    }

    @Test
    fun calculateStep_baseTenThousand_repeatTen() {
        assertEquals(20_000L, accelerator.calculateStep(10_000L, 10))
    }

    @Test
    fun calculateStep_baseTenThousand_repeatHundred_capped() {
        assertEquals(25_000L, accelerator.calculateStep(10_000L, 100))
    }

    @Test
    fun customAccelerator_respectsParameters() {
        val custom = DpadRepeatAccelerator(accelerationFactor = 0.25f, maxMultiplier = 3.0f)
        assertEquals(1.0f, custom.calculateMultiplier(0), 0.001f)
        assertEquals(1.25f, custom.calculateMultiplier(1), 0.001f)
        assertEquals(2.5f, custom.calculateMultiplier(6), 0.001f)
        assertEquals(3.0f, custom.calculateMultiplier(10), 0.001f)
    }
}
