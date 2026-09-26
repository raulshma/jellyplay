package com.raulshma.jellyplay.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins for [desktopBackKeyDecision] — the exact truth table the two former
 * hand-copies (DesktopAppRoot's scaffold Row, the signed-out shell's shared
 * SignedOutAuthHost frame) implemented: Esc / Alt+Left pop at depth > 1,
 * plain Left is never back, and ANY key at the root (depth <= 1) is refused.
 */
class DesktopBackKeyDecisionTest {

    @Test
    fun `escape pops above the root`() {
        assertTrue(desktopBackKeyDecision(Key.Escape, isAltPressed = false, stackDepth = 2))
        // Alt changes nothing for Esc — both arms of the or-arm are independent.
        assertTrue(desktopBackKeyDecision(Key.Escape, isAltPressed = true, stackDepth = 3))
    }

    @Test
    fun `alt-left pops above the root`() {
        assertTrue(desktopBackKeyDecision(Key.DirectionLeft, isAltPressed = true, stackDepth = 2))
        assertTrue(desktopBackKeyDecision(Key.DirectionLeft, isAltPressed = true, stackDepth = 9))
    }

    @Test
    fun `plain left is never back`() {
        assertFalse(desktopBackKeyDecision(Key.DirectionLeft, isAltPressed = false, stackDepth = 2))
        assertFalse(desktopBackKeyDecision(Key.DirectionLeft, isAltPressed = false, stackDepth = 9))
    }

    @Test
    fun `any key at the root is refused`() {
        // Depth 1 == the seeded tab/seed root: even back keys fall through.
        assertFalse(desktopBackKeyDecision(Key.Escape, isAltPressed = false, stackDepth = 1))
        assertFalse(desktopBackKeyDecision(Key.DirectionLeft, isAltPressed = true, stackDepth = 1))
        // Degenerate-depth guard: an empty stack refuses too.
        assertFalse(desktopBackKeyDecision(Key.Escape, isAltPressed = false, stackDepth = 0))
        // Non-back keys at the root refuse as well (they never decide back).
        assertFalse(desktopBackKeyDecision(Key.Spacebar, isAltPressed = false, stackDepth = 1))
    }

    @Test
    fun `other keys never decide back`() {
        assertFalse(desktopBackKeyDecision(Key.DirectionRight, isAltPressed = true, stackDepth = 3))
        assertFalse(desktopBackKeyDecision(Key.Spacebar, isAltPressed = false, stackDepth = 3))
        assertFalse(desktopBackKeyDecision(Key.DirectionUp, isAltPressed = true, stackDepth = 3))
    }
}
