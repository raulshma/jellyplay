package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.model.ColorBlindMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the no-op / no-graphics-layer contracts of the two color-filter
 * modifier factories (the matrices themselves are built inside private
 * helpers and verified visually — what is testable headlessly is WHICH modes
 * alter the modifier chain and which must not):
 *
 *  - [Modifier.blueLightFilter] returns the SAME instance when disabled or
 *    when the strength is zero-or-below — disabled filters must not add a
 *    graphics layer to every card in a grid;
 *  - a positive strength attaches a graphics layer (different instance),
 *    including for out-of-range strengths (clamped inside);
 *  - [Modifier.colorBlindFilter] returns the same instance only for
 *    [ColorBlindMode.NONE]; every other mode wraps the chain.
 */
class ColorFilterModifiersTest {

    @Test
    fun blueLightFilter_disabled_returnsSameModifier() {
        val base = Modifier

        assertSameInstance(base, base.blueLightFilter(enabled = false, strength = 0.8f))
    }

    @Test
    fun blueLightFilter_zeroStrength_returnsSameModifier() {
        val base = Modifier

        assertSameInstance(base, base.blueLightFilter(enabled = true, strength = 0f))
    }

    @Test
    fun blueLightFilter_negativeStrength_returnsSameModifier() {
        // strength <= 0 is checked BEFORE clamping — a stray negative must not
        // wrap the chain.
        val base = Modifier

        assertSameInstance(base, base.blueLightFilter(enabled = true, strength = -1f))
    }

    @Test
    fun blueLightFilter_positiveStrength_attachesGraphicsLayer() {
        val base = Modifier

        val filtered = base.blueLightFilter(enabled = true, strength = 0.5f)

        assertFalse(base === filtered, "an active filter must wrap the chain")
    }

    @Test
    fun blueLightFilter_outOfRangeStrength_doesNotCrash() {
        // Clamping happens inside the factory (matrix blend), so both extremes
        // must build a modifier without throwing.
        Modifier.blueLightFilter(enabled = true, strength = 5f)
        Modifier.blueLightFilter(enabled = true, strength = -5f).let { /* strength<=0: no-op */ }
    }

    @Test
    fun colorBlindFilter_none_returnsSameModifier() {
        val base = Modifier

        assertSameInstance(base, base.colorBlindFilter(ColorBlindMode.NONE))
    }

    @Test
    fun colorBlindFilter_eachDeficiencyMode_wrapsTheChain() {
        val base = Modifier

        assertFalse(base === base.colorBlindFilter(ColorBlindMode.PROTANOPIA))
        assertFalse(base === base.colorBlindFilter(ColorBlindMode.DEUTERANOPIA))
        assertFalse(base === base.colorBlindFilter(ColorBlindMode.TRITANOPIA))
    }

    private fun assertSameInstance(expected: Modifier, actual: Modifier) {
        assertTrue(expected === actual, "expected the same, unmodified instance")
    }
}
