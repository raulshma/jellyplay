package com.raulshma.jellyplay.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Programmatic cover for the tray menu actions.
 *
 * Scope honesty: a real ComposeWindow is a java.awt Window subclass and
 * cannot be constructed in a headless test JVM, so the restore/focus half
 * of [DesktopTrayActions.showMainWindow] stays a documented manual check.
 * What IS testable headless is the null-window contract and the quit
 * delegation — exactly what these tests pin.
 */
class DesktopTrayActionsTest {

    @Test
    fun `quit invokes the exit callback exactly once`() {
        var invocations = 0
        DesktopTrayActions.quit { invocations++ }
        assertEquals(1, invocations, "exitApplication must fire exactly once — no double-exit, no drop")
    }

    @Test
    fun `showMainWindow with null window is a no-throw no-op`() {
        // Null = tray item fired while the window ref was empty (icon never
        // composed, or disposed + CAS-cleared): must stay a no-throw no-op.
        DesktopTrayActions.showMainWindow(null)
    }
}
