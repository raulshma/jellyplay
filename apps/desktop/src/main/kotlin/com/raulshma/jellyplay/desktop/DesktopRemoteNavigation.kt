package com.raulshma.jellyplay.desktop

import androidx.compose.ui.focus.FocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection

/**
 * The desktop remote-navigation seams — everything this shell adds around
 * shared/feature/shell's [com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationDispatcher]
 * ladder. The former `DesktopRemoteNavCollector` wrapper (eight forwarded
 * constructor params around the dispatcher plus this constant and a
 * collect() one-liner) is gone: the dispatcher is now constructed directly
 * by [DesktopShellServices], which owns the shell-service wiring; only the
 * two genuinely desktop-owned declarations below remain here.
 *
 * Desktop ignored the bridge entirely before the receiver port — remote
 * Play → desktop, SyncPlay auto-open and ClosePlayer all dead-ended. The
 * ladder's target→sink table (including the GoToTopLevel select-not-pop
 * fork) is shared and pinned by `RemoteNavigationDispatcherTest`; the
 * desktop seams it is constructed over: pushes through the guarded
 * navigator (dead-end routes surface the guard's snackbar), tab switches
 * write `topLevelRoute` directly, focus moves through Compose's
 * FocusManager ([composeFocusDirection]), select through the synthesized
 * AWT Enter key, and the context menu (which the desktop shell has no
 * affordance for) keeps the default never-consumed key arm and falls back
 * to [DESKTOP_CONTEXT_MENU_UNAVAILABLE].
 */

/**
 * The context-menu fallback message: the desktop shell has no context-menu
 * affordance, so the dispatcher's default never-consumed context-menu key
 * arm sends every `OpenContextMenu` here. Top-level internal so the
 * collection seam ([DesktopShellServices]) and
 * DesktopRemoteNavigationTest pin the exact user-facing string.
 */
internal const val DESKTOP_CONTEXT_MENU_UNAVAILABLE = "Context menu not available here"

/**
 * The four-branch remote→Compose focus mapping the scaffold's `moveFocus`
 * adapter used to inline: the receiver's [RemoteFocusDirection] onto
 * Compose's [FocusDirection] for `FocusManager.moveFocus`. Total (the wire
 * enum has exactly these four members) and pure — the objects are plain
 * constants, so DesktopRemoteNavigationTest pins all four rows on the JVM
 * without a focus tree.
 */
internal fun composeFocusDirection(direction: RemoteFocusDirection): FocusDirection =
    when (direction) {
        RemoteFocusDirection.UP -> FocusDirection.Up
        RemoteFocusDirection.DOWN -> FocusDirection.Down
        RemoteFocusDirection.LEFT -> FocusDirection.Left
        RemoteFocusDirection.RIGHT -> FocusDirection.Right
    }
