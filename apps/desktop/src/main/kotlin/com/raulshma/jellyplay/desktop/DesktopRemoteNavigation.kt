package com.raulshma.jellyplay.desktop

import androidx.compose.ui.focus.FocusDirection
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.feature.shell.navigation.popPlayerRoutes
import com.raulshma.jellyplay.feature.shell.navigation.routeForNavigationTarget
import kotlinx.coroutines.flow.Flow

/**
 * The desktop bridge collector: consumes `RemoteNavigationBridge.targets`
 * against the scaffold's guarded navigator and focus/key seams. Desktop
 * ignored the bridge entirely before the receiver port — remote Play →
 * desktop, SyncPlay auto-open and ClosePlayer all dead-ended. The pure folds
 * it dispatches through ([routeForNavigationTarget] / [popPlayerRoutes])
 * live in shared/feature/shell (the former desktop hand-mirror is gone);
 * this class is the per-shell LADDER DISPATCH only — focus moves through
 * Compose's FocusManager, select through the synthesized Enter key, and the
 * context menu (which the desktop shell has no affordance for) falls back to
 * a message — so it is JVM-pinnable through fake lambdas (the
 * NavRequestCollector shape).
 */
internal class DesktopRemoteNavCollector(
    private val navigate: (NavKey) -> Unit,
    private val goBack: () -> Unit,
    private val backStacks: () -> Collection<MutableList<NavKey>>,
    private val moveFocus: (RemoteFocusDirection) -> Unit,
    private val invokeSelect: () -> Unit,
    private val presentMessage: suspend (message: String) -> Unit,
) {
    suspend fun collect(targets: Flow<NavigationTarget>) {
        targets.collect { target ->
            when (target) {
                NavigationTarget.ClosePlayer -> popPlayerRoutes(backStacks())
                NavigationTarget.GoBack -> goBack()
                is NavigationTarget.MoveFocus -> moveFocus(target.direction)
                NavigationTarget.InvokeSelect -> invokeSelect()
                NavigationTarget.OpenContextMenu -> presentMessage(CONTEXT_MENU_UNAVAILABLE)
                is NavigationTarget.OpenVideoPlayer,
                is NavigationTarget.OpenAudioPlayer,
                is NavigationTarget.OpenMediaDetail,
                is NavigationTarget.GoToTopLevel -> routeForNavigationTarget(target)?.let(navigate)
            }
        }
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
