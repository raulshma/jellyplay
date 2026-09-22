package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.data.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the desktop remote-navigation folds + collector (the desktop
 * consumption half — the twin of the Android app's
 * RemoteNavigationRoutingTest): the target→route mapping, the top-level
 * destination folds, the Jellyfin-web "Stop" pop semantics, and the
 * collector's ladder dispatch (back / focus / select / context-menu) through
 * fake lambdas — the NavRequestCollector shape.
 */
class DesktopRemoteNavigationTest {

    // ── routeForNavigationTarget ─────────────────────────────────────────

    @Test
    fun `player and detail targets map onto their routes`() {
        assertEquals(
            Route.VideoPlayer(itemId = "v", startPositionTicks = 7L, subtitleStreamIndex = 2),
            routeForNavigationTarget(
                NavigationTarget.OpenVideoPlayer(
                    itemId = "v",
                    startPositionTicks = 7L,
                    subtitleStreamIndex = 2,
                ),
            ),
        )
        assertEquals(
            Route.AudioPlayer("a"),
            routeForNavigationTarget(NavigationTarget.OpenAudioPlayer("a")),
        )
        assertEquals(
            Route.MediaDetail("d"),
            routeForNavigationTarget(NavigationTarget.OpenMediaDetail("d")),
        )
    }

    @Test
    fun `top-level destinations fold onto their tab routes`() {
        assertEquals(Route.Home, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME)))
        assertEquals(Route.Search, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH)))
        assertEquals(Route.Settings, routeForNavigationTarget(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS)))
    }

    @Test
    fun `ladder and close targets are not routes`() {
        assertEquals(null, routeForNavigationTarget(NavigationTarget.ClosePlayer))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.GoBack))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.MoveFocus(RemoteFocusDirection.UP)))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.InvokeSelect))
        assertEquals(null, routeForNavigationTarget(NavigationTarget.OpenContextMenu))
    }

    // ── popPlayerRoutes ──────────────────────────────────────────────────

    @Test
    fun `close player pops contiguous player entries off every stack`() {
        val home = mutableListOf<NavKey>(Route.Home, Route.VideoPlayer("v1"), Route.AudioPlayer("a1"))
        val search = mutableListOf<NavKey>(Route.Search, Route.MediaDetail("m"), Route.VideoPlayer("v2"))
        val settings = mutableListOf<NavKey>(Route.Settings)

        popPlayerRoutes(listOf(home, search, settings))

        assertEquals(listOf<NavKey>(Route.Home), home)
        // A player buried below a non-player top is untouched.
        assertEquals(listOf<NavKey>(Route.Search, Route.MediaDetail("m")), search)
        assertEquals(listOf<NavKey>(Route.Settings), settings)
    }

    @Test
    fun `pop player on empty stacks is a no-op`() {
        val stack = mutableListOf<NavKey>()
        popPlayerRoutes(listOf(stack))
        assertTrue(stack.isEmpty())
    }

    // ── DesktopRemoteNavCollector ────────────────────────────────────────

    private class RecordingShell {
        val events = mutableListOf<String>()
        val stacks = mutableListOf<MutableList<NavKey>>()
        val collector = DesktopRemoteNavCollector(
            navigate = { route -> events += "navigate:$route" },
            goBack = { events += "goBack" },
            backStacks = { stacks },
            moveFocus = { direction -> events += "focus:$direction" },
            invokeSelect = { events += "select" },
            presentMessage = { message -> events += "message:$message" },
        )
    }

    @Test
    fun `the collector dispatches every ladder target to its seam`() = kotlinx.coroutines.runBlocking {
        val shell = RecordingShell()
        shell.collector.collect(
            kotlinx.coroutines.flow.flowOf(
                NavigationTarget.GoBack,
                NavigationTarget.MoveFocus(RemoteFocusDirection.LEFT),
                NavigationTarget.InvokeSelect,
                NavigationTarget.OpenContextMenu,
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME),
                NavigationTarget.OpenMediaDetail("d"),
                NavigationTarget.ClosePlayer,
            ),
        )

        assertEquals(
            listOf(
                "goBack",
                "focus:LEFT",
                "select",
                "message:Context menu not available here",
                "navigate:${Route.Home}",
                "navigate:${Route.MediaDetail("d")}",
            ),
            shell.events,
        )
    }
}
