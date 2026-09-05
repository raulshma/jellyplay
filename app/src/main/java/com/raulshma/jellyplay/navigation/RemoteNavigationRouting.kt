package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure remote-navigation routing — the two decisions behind the
 * remoteNavigationBridge collector in [JellyPlayApp], extracted with no
 * compose, no navigator and no back-stack access so both are unit-pinned on
 * the JVM (see `RemoteNavigationRoutingTest`; PlaybackHostRouter precedent).
 *
 * [routeForNavigationTarget] maps a server-emitted target to the Route to
 * push; [popPlayerRoutes] is the Jellyfin-web "Stop" semantics for
 * `ClosePlayer` — every player entry popped off the top of EVERY back stack,
 * so the player UI actually disappears instead of hiding behind a tab switch.
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
    // Not a navigation — the collector branches to [popPlayerRoutes] instead.
    NavigationTarget.ClosePlayer -> null
}

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
