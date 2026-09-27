package com.raulshma.jellyplay.desktop

import androidx.compose.ui.focus.FocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the desktop remote-navigation declarations that remain after the
 * `DesktopRemoteNavCollector` wrapper folded into [DesktopShellServices]:
 * the class was eight forwarded constructor params around shared/feature
 * shell's `RemoteNavigationDispatcher` plus one string constant and a
 * collect() one-liner, so its ladder-coverage rows here duplicated
 * `RemoteNavigationDispatcherTest` (which pins every NavigationTarget
 * case's sink, the GoToTopLevel select-not-pop fork, ClosePlayer's
 * multi-stack pop and the context-menu fallback) — those rows collapsed
 * into references to the shared test. What is genuinely desktop-owned
 * stays pinned HERE:
 *
 *  - [composeFocusDirection], the four-branch remote→Compose focus mapping
 *    extracted from the scaffold's moveFocus adapter (plain constant
 *    objects — no focus tree needed);
 *  - [DESKTOP_CONTEXT_MENU_UNAVAILABLE], the context-menu fallback wording
 *    the desktop seam passes the shared ladder (the desktop shell has no
 *    context-menu affordance, so every OpenContextMenu lands on it).
 */
class DesktopRemoteNavigationTest {

    // ── composeFocusDirection ───────────────────────────────────────────

    @Test
    fun `the focus mapping covers all four remote directions`() {
        assertEquals(FocusDirection.Up, composeFocusDirection(RemoteFocusDirection.UP))
        assertEquals(FocusDirection.Down, composeFocusDirection(RemoteFocusDirection.DOWN))
        assertEquals(FocusDirection.Left, composeFocusDirection(RemoteFocusDirection.LEFT))
        assertEquals(FocusDirection.Right, composeFocusDirection(RemoteFocusDirection.RIGHT))
    }

    // ── DESKTOP_CONTEXT_MENU_UNAVAILABLE ────────────────────────────────

    @Test
    fun `the context menu fallback names its unavailability`() {
        // The exact user-facing string the shared dispatcher's fallback
        // presents for a remote OpenContextMenu on this shell (the ladder's
        // delivery of it is RemoteNavigationDispatcherTest's row).
        assertEquals("Context menu not available here", DESKTOP_CONTEXT_MENU_UNAVAILABLE)
    }
}
