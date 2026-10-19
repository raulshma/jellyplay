package com.raulshma.jellyplay.desktop

import androidx.compose.ui.awt.ComposeWindow

/**
 * The tray menu's "Show JellyPlay" window restore and "Quit" handlers,
 * held here as plain functions so programmatic tests can reach them;
 * Main.kt delegates.
 *
 * Testing split is deliberate: the null-window path and the quit delegation
 * are unit-covered (DesktopTrayActionsTest); the visual restore of a live
 * ComposeWindow cannot be constructed headless, so that half stays a
 * one-time manual check.
 */
internal object DesktopTrayActions {

    /**
     * Tray "Show JellyPlay": restore the main window from minimized/maximized
     * state and focus it. Caller owns the AWT event-queue hop (Main.kt wraps
     * this in `EventQueue.invokeLater` — tray clicks arrive through AWT menu
     * machinery, and the one-loop-turn hop guarantees every listener variant
     * stays on-thread).
     *
     * A null window (icon never composed, or the window already disposed and
     * the ref CAS-cleared) is a silent no-op.
     */
    fun showMainWindow(window: ComposeWindow?) {
        val w = window ?: return
        w.extendedState = java.awt.Frame.NORMAL
        w.requestFocus()
    }

    /**
     * Tray "Quit": delegates to compose's `exitApplication` exactly once.
     * The callback indirection keeps this testable without a live
     * application scope.
     */
    fun quit(exitApplication: () -> Unit) {
        exitApplication()
    }
}
