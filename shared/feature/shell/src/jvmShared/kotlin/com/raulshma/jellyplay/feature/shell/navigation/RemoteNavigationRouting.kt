package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure remote-navigation routing shared by both shells — the decisions behind
 * the `RemoteNavigationBridge` collectors, extracted with no compose, no
 * navigator and no back-stack access so both are unit-pinned on the JVM (see
 * `RemoteNavigationRoutingTest`; the PlaybackHostRouter precedent). The
 * former hand-mirrored twins (the Android app's `RemoteNavigationRouting`
 * folds and the desktop mirror in `DesktopRemoteNavigation`) both folded onto
 * this one home; the per-shell LADDER DISPATCH stays per-shell (Android
 * synthesizes D-pad/center/menu keycodes, desktop moves focus through
 * AWT/Compose seams).
 *
 * [routeForNavigationTarget] maps a server-emitted target to the Route to
 * push; [popPlayerRoutes] is the Jellyfin-web "Stop" semantics for
 * [NavigationTarget.ClosePlayer] — every player entry popped off the top of
 * EVERY back stack, so the player UI actually disappears instead of hiding
 * behind a tab switch.
 *
 * The navigation-ladder targets: [NavigationTarget.GoToTopLevel] folds onto
 * its Route (the collector then reuses its tab-vs-push dispatch fork), while
 * [NavigationTarget.GoBack], [NavigationTarget.MoveFocus],
 * [NavigationTarget.InvokeSelect] and [NavigationTarget.OpenContextMenu] are
 * NOT routes — the collectors branch on them directly (back stack pop /
 * focus move / synthesized key events) and this fold returns null.
 */
fun routeForNavigationTarget(target: NavigationTarget): Route? = when (target) {
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
    // focus-or-key-synthesis seams (NavRequestCollector / DesktopRemoteNavCollector).
    NavigationTarget.GoBack,
    is NavigationTarget.MoveFocus,
    NavigationTarget.InvokeSelect,
    NavigationTarget.OpenContextMenu -> null
}

/**
 * Pops contiguous player entries ([Route.VideoPlayer], [Route.AudioPlayer],
 * [Route.LiveTvChannelPlayer]) off the top of every supplied back stack,
 * stopping each stack at its first non-player entry (routes buried below a
 * non-player top are untouched). Empty stacks and an empty collection are
 * no-ops. Mutates the stacks in place — they are the live
 * [com.raulshma.jellyplay.core.ui.navigation.NavigationState] back stacks.
 */
fun popPlayerRoutes(backStacks: Collection<MutableList<NavKey>>) {
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
