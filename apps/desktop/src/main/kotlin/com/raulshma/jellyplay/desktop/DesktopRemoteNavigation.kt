package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.data.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.flow.Flow

/**
 * Desktop remote-navigation routing — the desktop twin of the Android app's
 * `RemoteNavigationRouting` (same package shape, same pure-fold style; the
 * Android original lives in the app module this shell cannot depend on, so
 * the ~30-line fold is mirrored here with its own pinning test).
 *
 * [routeForNavigationTarget] maps a server-emitted target onto the Route to
 * push through the guarded navigator (top-level destinations switch tabs —
 * Navigator.navigate's own top-level branch); [popPlayerRoutes] is the
 * Jellyfin-web "Stop" semantics for `ClosePlayer`. The ladder targets
 * (GoBack / MoveFocus / InvokeSelect / OpenContextMenu) are executed by the
 * collector's injected seams, not mapped here.
 */
internal fun routeForNavigationTarget(target: NavigationTarget): Route? = when (target) {
    is NavigationTarget.OpenVideoPlayer -> Route.VideoPlayer(
        itemId = target.itemId,
        mediaSourceId = target.mediaSourceId,
        startPositionTicks = target.startPositionTicks,
        audioStreamIndex = target.audioStreamIndex,
        subtitleStreamIndex = target.subtitleStreamIndex,
    )
    is NavigationTarget.OpenAudioPlayer -> Route.AudioPlayer(target.itemId)
    is NavigationTarget.OpenMediaDetail -> Route.MediaDetail(target.itemId)
    is NavigationTarget.GoToTopLevel -> when (target.destination) {
        RemoteTopLevelDestination.HOME -> Route.Home
        RemoteTopLevelDestination.SEARCH -> Route.Search
        RemoteTopLevelDestination.SETTINGS -> Route.Settings
    }
    NavigationTarget.ClosePlayer -> null
    NavigationTarget.GoBack,
    is NavigationTarget.MoveFocus,
    NavigationTarget.InvokeSelect,
    NavigationTarget.OpenContextMenu -> null
}

/**
 * Pops contiguous player entries off the top of every supplied back stack,
 * stopping each stack at its first non-player entry — the desktop twin of
 * the Android fold (`ClosePlayer` must dismiss a mounted player, and the
 * desktop scaffold keeps one back stack per top-level tab exactly like the
 * phone shell).
 */
internal fun popPlayerRoutes(backStacks: Collection<MutableList<NavKey>>) {
    backStacks.forEach { stack ->
        while (stack.isNotEmpty()) {
            val last = stack.last()
            if (last is Route.VideoPlayer ||
                last is Route.AudioPlayer ||
                last is Route.LiveTvChannelPlayer
            ) {
                stack.removeLastOrNull()
            } else {
                break
            }
        }
    }
}

/**
 * The desktop bridge collector: consumes `RemoteNavigationBridge.targets`
 * against the scaffold's guarded navigator and focus/key seams. Desktop
 * ignored the bridge entirely before the receiver port — remote Play →
 * desktop, SyncPlay auto-open and ClosePlayer all dead-ended. The folds
 * above are pure; this class only dispatches, so it is JVM-pinnable through
 * fake lambdas (the NavRequestCollector shape).
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
