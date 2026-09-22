package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.data.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure remote-navigation routing — the decisions behind the
 * remoteNavigationBridge collector in [JellyPlayApp], extracted with no
 * compose, no navigator and no back-stack access so both are unit-pinned on
 * the JVM (see `RemoteNavigationRoutingTest`; PlaybackHostRouter precedent).
 *
 * [routeForNavigationTarget] maps a server-emitted target to the Route to
 * push; [popPlayerRoutes] is the Jellyfin-web "Stop" semantics for
 * `ClosePlayer` — every player entry popped off the top of EVERY back stack,
 * so the player UI actually disappears instead of hiding behind a tab switch.
 *
 * Adds the navigation-ladder targets: [NavigationTarget.GoToTopLevel]
 * folds onto its Route (the collector then reuses [NavRequestCollector]'s
 * tab-vs-push `pendingRouteDispatch` fork), while [NavigationTarget.GoBack],
 * [NavigationTarget.MoveFocus], [NavigationTarget.InvokeSelect] and
 * [NavigationTarget.OpenContextMenu] are NOT routes — the collector branches
 * on them directly (back stack pop / synthesized key events) and this fold
 * returns null.
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
    // Not a navigation — the collector branches to [popPlayerRoutes] instead.
    NavigationTarget.ClosePlayer -> null
    // Not a navigation — the collector branches to the back-stack pop /
    // key-synthesis seams (see NavRequestCollector.collectRemoteNavigation).
    NavigationTarget.GoBack,
    is NavigationTarget.MoveFocus,
    NavigationTarget.InvokeSelect,
    NavigationTarget.OpenContextMenu -> null
}

/** The Android keycodes the remote d-pad/select/context-menu targets synthesize. */
internal fun keyCodeForFocusDirection(direction: RemoteFocusDirection): Int = when (direction) {
    RemoteFocusDirection.UP -> android.view.KeyEvent.KEYCODE_DPAD_UP
    RemoteFocusDirection.DOWN -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
    RemoteFocusDirection.LEFT -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
    RemoteFocusDirection.RIGHT -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
}

/** The select activation keycode (D-pad center; ENTER on keyboard remotes). */
internal val REMOTE_SELECT_KEYCODE: Int = android.view.KeyEvent.KEYCODE_DPAD_CENTER

/** The context-menu keycode (the remote hamburger/"menu" key). */
internal val REMOTE_CONTEXT_MENU_KEYCODE: Int = android.view.KeyEvent.KEYCODE_MENU

/**
 * Pops contiguous player entries ([Route.VideoPlayer], [Route.AudioPlayer],
 * [Route.LiveTvChannelPlayer]) off the top of every supplied back stack,
 * stopping each stack at its first non-player entry (routes buried below a
 * non-player top are untouched). Empty stacks and an empty collection are
 * no-ops. Mutates the stacks in place — they are the live
 * [com.raulshma.jellyplay.core.ui.navigation.NavigationState] back stacks.
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
