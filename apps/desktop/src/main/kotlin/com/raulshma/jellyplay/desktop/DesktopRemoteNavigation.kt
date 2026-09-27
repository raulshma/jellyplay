package com.raulshma.jellyplay.desktop

import androidx.compose.ui.focus.FocusDirection
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * The desktop bridge collector: consumes `RemoteNavigationBridge.targets`
 * against the scaffold's guarded navigator and focus/key seams. Desktop
 * ignored the bridge entirely before the receiver port — remote Play →
 * desktop, SyncPlay auto-open and ClosePlayer all dead-ended. The LADDER
 * itself is shared/feature/shell's [RemoteNavigationDispatcher] (beside the
 * `RemoteNavigationRouting` folds; the former desktop hand-copy is gone) —
 * this class is the desktop SEAM BUNDLE only: pushes through the guarded
 * navigator (dead-end routes surface the guard's snackbar), tab switches
 * write `topLevelRoute` directly, focus moves through Compose's
 * FocusManager, select through the synthesized Enter key, and the context
 * menu (which the desktop shell has no affordance for) keeps the default
 * never-consumed key arm and falls back to a message — so it is
 * JVM-pinnable through fake lambdas (the NavRequestCollector shape).
 *
 * DELTA vs the hand-copied ladder this replaced (the intended fix): a
 * remote `GoToTopLevel` naming the already-selected tab now goes through the
 * tab-switch seam — a pure switch, like the Android shell always did — where
 * the old `guardedNavigator.navigate` path hit `Navigator.navigate`'s
 * already-on-tab behavior and POPPED that tab's stack to its root. Only the
 * already-selected case is observable: for a non-current tab both seams
 * switch identically.
 */
internal class DesktopRemoteNavCollector(
    topLevelKeys: Set<Route>,
    navigate: (NavKey) -> Unit,
    selectTab: (Route) -> Unit,
    goBack: () -> Unit,
    backStacks: () -> Collection<MutableList<NavKey>>,
    moveFocus: (RemoteFocusDirection) -> Unit,
    invokeSelect: () -> Unit,
    presentMessage: suspend (message: String) -> Unit,
) {
    private val dispatcher = RemoteNavigationDispatcher(
        topLevelKeys = topLevelKeys,
        navigate = navigate,
        selectTab = selectTab,
        goBack = goBack,
        backStacks = backStacks,
        presentMessage = presentMessage,
        moveFocus = moveFocus,
        invokeSelect = invokeSelect,
        // No context-menu affordance: the default never-consumed arm sends
        // every OpenContextMenu to the fallback message.
    )

    suspend fun collect(targets: Flow<NavigationTarget>) {
        dispatcher.collect(targets, CONTEXT_MENU_UNAVAILABLE)
    }

    private companion object {
        const val CONTEXT_MENU_UNAVAILABLE = "Context menu not available here"
    }
}

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
