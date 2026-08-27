package com.raulshma.jellyplay.desktop

import androidx.compose.ui.awt.ComposeWindow

/**
 * Wave 13A testability extraction: the tray menu's "Show JellyPlay" window
 * restore and "Quit" handlers previously lived as inline lambdas inside
 * Main.kt's tray block, which put them outside any programmatic test reach
 * (docs/perf/desktop-skia-baseline.md, limits §6). Behavior here is
 * IDENTICAL to the former inline code — Main.kt delegates to these
 * functions; only the location changed.
 *
 * Testing split is deliberate: the null-window path and the quit delegation
 * are unit-covered (DesktopTrayActionsTest); the visual restore of a live
 * ComposeWindow cannot be constructed headless, so that half stays a
 * documented one-time manual check.
 */
internal object DesktopTrayActions {

    /**
     * Tray "Show JellyPlay": restore the main window from minimized/maximized
     * state and focus it. Caller owns the AWT event-queue hop (Main.kt wraps
     * this in `EventQueue.invokeLater`, unchanged from the inline original —
     * tray clicks arrive through AWT menu machinery, and the one-loop-turn
     * hop guarantees every listener variant stays on-thread).
     *
     * A null window (icon never composed, or the window already disposed and
     * the ref CAS-cleared) is a silent no-op — identical to the former
     * `if (w != null)` guard.
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
