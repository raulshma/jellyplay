package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the shared remote-navigation routing folds both shell collectors
 * dispatch through (the former Android `RemoteNavigationRoutingTest` and
 * desktop `DesktopRemoteNavigationTest` fold halves merged into the one
 * shared home): every [NavigationTarget] case's route mapping, and the
 * `ClosePlayer` pop semantics — player routes removed from the top of EVERY
 * back stack, non-player routes untouched (including players buried below a
 * non-player top), empty stacks tolerated.
 *
 * Pure JVM: both functions are free of compose/navigator types beyond the
 * NavKey lists they mutate. The per-shell ladder vocabulary (Android's
 * synthesized keycodes, desktop's focus seams) stays per-shell and keeps its
 * own pins.
 */
class RemoteNavigationRoutingTest {

    // ── routeForNavigationTarget: every NavigationTarget case ───────────

    @Test
    fun `openVideoPlayer maps onto Route_VideoPlayer carrying every stream override`() {
        val target = NavigationTarget.OpenVideoPlayer(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startPositionTicks = 10_000_000L,
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
        )

        assertEquals(
            Route.VideoPlayer(
                itemId = "item-1",
                mediaSourceId = "source-1",
                startPositionTicks = 10_000_000L,
                audioStreamIndex = 2,
                subtitleStreamIndex = 5,
            ),
            routeForNavigationTarget(target),
        )
    }

    @Test
    fun `openVideoPlayer defaults survive the mapping`() {
        assertEquals(
            Route.VideoPlayer(itemId = "item-min"),
            routeForNavigationTarget(NavigationTarget.OpenVideoPlayer(itemId = "item-min")),
        )
    }

    @Test
    fun `openAudioPlayer maps onto Route_AudioPlayer`() {
        assertEquals(
            Route.AudioPlayer("track-1"),
            routeForNavigationTarget(NavigationTarget.OpenAudioPlayer("track-1")),
        )
    }

    @Test
    fun `openMediaDetail maps onto Route_MediaDetail`() {
        assertEquals(
            Route.MediaDetail("item-1"),
            routeForNavigationTarget(NavigationTarget.OpenMediaDetail("item-1")),
        )
    }

    @Test
    fun `closePlayer maps to no route`() {
        // The collector branches to popPlayerRoutes for this target instead.
        assertEquals(null, routeForNavigationTarget(NavigationTarget.ClosePlayer))
    }

    // ── The navigation-ladder targets ─────────────────────────────

    @Test
    fun `top-level destinations fold onto their tab routes`() {
        assertEquals(Route.Home, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME)))
        assertEquals(Route.Search, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH)))
        assertEquals(Route.Settings, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS)))
    }

    @Test
    fun `ladder targets are not routes`() {
        // The collectors branch on these directly (back pop / focus move /
        // synthesized key events / context-menu fallback message).
        assertEquals(null, routeForNavigationTarget(NavigationTarget.GoBack))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.MoveFocus(RemoteFocusDirection.UP)))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.InvokeSelect))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.OpenContextMenu))
    }

    // ── popPlayerRoutes: Jellyfin-web "Stop" semantics ──────────────────

    @Test
    fun `player on top of every stack is popped`() {
        val home = mutableListOf<NavKey>(Route.Home)
        val library = mutableListOf<NavKey>(Route.Library, Route.MediaDetail("item-1"), Route.VideoPlayer("v-1"))
        val audio = mutableListOf<NavKey>(Route.Search, Route.AudioPlayer("a-1"))

        popPlayerRoutes(listOf(home, library, audio))

        assertEquals(listOf<NavKey>(Route.Home), home)
        assertEquals(listOf<NavKey>(Route.Library, Route.MediaDetail("item-1")), library)
        assertEquals(listOf<NavKey>(Route.Search), audio)
    }

    @Test
    fun `contiguous player entries pop down to the first non-player`() {
        val stack = mutableListOf<NavKey>(
            Route.Home,
            Route.MediaDetail("item-1"),
            Route.VideoPlayer("v-1"),
            Route.AudioPlayer("a-1"),
        )

        popPlayerRoutes(listOf(stack))

        assertEquals(
            listOf<NavKey>(Route.Home, Route.MediaDetail("item-1")),
            stack,
        )
    }

    @Test
    fun `player buried below a non-player top is untouched`() {
        // The pop walks from the top and STOPS at the first non-player entry —
        // it must never dig deeper to evict a player the user navigated away
        // from deliberately.
        val stack = mutableListOf<NavKey>(
            Route.Home,
            Route.VideoPlayer("v-1"),
            Route.MediaDetail("item-1"),
        )

        popPlayerRoutes(listOf(stack))

        assertEquals(
            listOf<NavKey>(Route.Home, Route.VideoPlayer("v-1"), Route.MediaDetail("item-1")),
            stack,
        )
    }

    @Test
    fun `stacks with no player routes are left untouched`() {
        val stack = mutableListOf<NavKey>(Route.Home, Route.Settings)

        popPlayerRoutes(listOf(stack))

        assertEquals(listOf<NavKey>(Route.Home, Route.Settings), stack)
    }

    @Test
    fun `empty stacks and an empty stack collection are tolerated`() {
        val emptyStack = mutableListOf<NavKey>()

        popPlayerRoutes(listOf(emptyStack))
        assertTrue(emptyStack.isEmpty())

        popPlayerRoutes(emptyList())
    }

    @Test
    fun `the live-tv channel player counts as a player route`() {
        val stack = mutableListOf<NavKey>(
            Route.LiveTv,
            Route.LiveTvChannelPlayer(channelId = "chan-1", channelName = "Channel One"),
        )

        popPlayerRoutes(listOf(stack))

        assertEquals(listOf<NavKey>(Route.LiveTv), stack)
    }
}
