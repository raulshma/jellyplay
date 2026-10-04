package com.raulshma.jellyplay.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for [DesktopAccelerators] — the one accelerator table the two former
 * hand-copies (Main.kt's window onPreviewKeyEvent chain, DesktopTitleBar's
 * menu shortcut literals) now both fold through. Rows are matched exactly as
 * the old if/else-if chain matched: Ctrl rows need `isCtrlPressed`, and the
 * F11 row IGNORES modifiers (the original arm never checked Ctrl — Ctrl+F11
 * still toggles fullscreen). The paste row (Ctrl+Shift+V, the
 * clipboard-open accelerator) additionally gates on Shift: the preview
 * handler runs BEFORE the Compose focus chain, so a shift-less Ctrl+V must
 * decline here or it would eat the text fields' paste.
 */
class DesktopAcceleratorTest {

    @Test
    fun `ctrl+r matches refresh and requires ctrl`() {
        assertEquals(
            DesktopAcceleratorAction.Refresh,
            DesktopAccelerators.match(Key.R, isCtrlPressed = true)?.action,
        )
        assertNull(DesktopAccelerators.match(Key.R, isCtrlPressed = false))
    }

    @Test
    fun `ctrl+q matches exit and requires ctrl`() {
        assertEquals(
            DesktopAcceleratorAction.Exit,
            DesktopAccelerators.match(Key.Q, isCtrlPressed = true)?.action,
        )
        assertNull(DesktopAccelerators.match(Key.Q, isCtrlPressed = false))
    }

    @Test
    fun `f11 matches regardless of modifiers — the original arm never checked ctrl`() {
        assertEquals(
            DesktopAcceleratorAction.ToggleFullscreen,
            DesktopAccelerators.match(Key.F11, isCtrlPressed = false)?.action,
        )
        assertEquals(
            DesktopAcceleratorAction.ToggleFullscreen,
            DesktopAccelerators.match(Key.F11, isCtrlPressed = true)?.action,
        )
    }

    @Test
    fun `ctrl+shift+v matches paste-open-link and requires both modifiers`() {
        assertEquals(
            DesktopAcceleratorAction.PasteOpenLink,
            DesktopAccelerators.match(Key.V, isCtrlPressed = true, isShiftPressed = true)?.action,
        )
        assertNull(DesktopAccelerators.match(Key.V, isCtrlPressed = false, isShiftPressed = true))
        assertNull(DesktopAccelerators.match(Key.V, isCtrlPressed = true, isShiftPressed = false))
        assertNull(DesktopAccelerators.match(Key.V, isCtrlPressed = false, isShiftPressed = false))
    }

    @Test
    fun `ctrl+v without shift declines so text fields keep their paste`() {
        // The load-bearing negative: the window-level preview handler sees
        // every key before the Compose focus chain — a match here would
        // starve the sign-in form's paste.
        assertNull(DesktopAccelerators.match(Key.V, isCtrlPressed = true, isShiftPressed = false))
    }

    @Test
    fun `rows render the same display literals the menus always showed`() {
        assertEquals("Ctrl+R", DesktopAccelerators.Refresh.displayLabel)
        assertEquals("Ctrl+Q", DesktopAccelerators.Exit.displayLabel)
        assertEquals("F11", DesktopAccelerators.ToggleFullscreen.displayLabel)
        assertEquals("Ctrl+Shift+V", DesktopAccelerators.PasteOpenLink.displayLabel)
    }

    @Test
    fun `the table is exactly the four rows`() {
        assertEquals(4, DesktopAccelerators.All.size)
        assertEquals(
            DesktopAccelerators.All.map { it.action }.toSet(),
            setOf(
                DesktopAcceleratorAction.Refresh,
                DesktopAcceleratorAction.Exit,
                DesktopAcceleratorAction.ToggleFullscreen,
                DesktopAcceleratorAction.PasteOpenLink,
            ),
        )
    }

    @Test
    fun `other keys never match — with or without ctrl`() {
        assertNull(DesktopAccelerators.match(Key.Spacebar, isCtrlPressed = true))
        assertNull(DesktopAccelerators.match(Key.Escape, isCtrlPressed = true))
        assertNull(DesktopAccelerators.match(Key.Spacebar, isCtrlPressed = false))
        assertFalse(DesktopAccelerators.Refresh.matches(Key.Q, isCtrlPressed = true))
        assertFalse(DesktopAccelerators.ToggleFullscreen.matches(Key.Q, isCtrlPressed = true))
        assertTrue(DesktopAccelerators.Exit.matches(Key.Q, isCtrlPressed = true))
    }
}
