package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.PlayEventPayload
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Pins the ONE remote-navigation ladder both shells dispatch through
 * ([RemoteNavigationDispatcher]) — the merge of the two hand-copied
 * collector ladders, including the measured behavioral fork this class
 * closed: a routed top-level destination goes through the TAB-SWITCH seam,
 * never the push seam, so a remote GoHome/GoToSettings/GoToSearch for the
 * already-selected tab cannot pop that tab's stack to its root (desktop's
 * old `Navigator.navigate` path did exactly that; Android's shell always
 * selected). Every [NavigationTarget] case's sink is pinned through fake
 * lambdas (the NavRequestCollectorTest idiom — one shared event log), plus
 * the [RemoteNavigationDispatcher.routeDispatch] fold and the two companion
 * folds the Android collector's delegating companions share (their full
 * tables also stay pinned there through the delegation).
 *
 * Pure JVM: no compose, no navigator, no focus tree.
 */
class RemoteNavigationDispatcherTest {

    /**
     * Fake shell: recording sinks + the mutable stacks the backStacks
     * provider serves. The single [events] log keeps every seam's dispatch
     * observable and ordered.
     */
    private class Shell(
        topLevelKeys: Set<Route> = ALL_TABS,
        contextMenuHandled: Boolean = false,
    ) {
        val events = mutableListOf<String>()
        val stacks = mutableListOf<MutableList<NavKey>>()
        val dispatcher = RemoteNavigationDispatcher(
            topLevelKeys = topLevelKeys,
            navigate = { route -> events += "navigate:$route" },
            selectTab = { route -> events += "tab:$route" },
            goBack = { events += "goBack" },
            backStacks = { stacks },
            presentMessage = { message -> events += "message:$message" },
            moveFocus = { direction -> events += "focus:$direction" },
            invokeSelect = { events += "select" },
            contextMenuKey = { contextMenuHandled },
        )
    }

    private fun Shell.collect(vararg targets: NavigationTarget) = runTest {
        dispatcher.collect(flowOf(*targets), contextMenuUnavailableMessage = "no context menu here")
    }

    private companion object {
        /** The full tab vocabulary — every GoToTopLevel destination folds to a tab. */
        val ALL_TABS = setOf(Route.Home, Route.Search, Route.Settings)
    }

    // ── the push arm: open targets ride the push seam ───────────────────

    @Test
    fun `open targets push their mapped routes`() {
        val shell = Shell()

        shell.collect(
            NavigationTarget.OpenVideoPlayer(itemId = "item-1", startPositionTicks = 10_000L),
            NavigationTarget.OpenAudioPlayer("track-1"),
            NavigationTarget.OpenMediaDetail("item-2"),
        )

        assertEquals(
            listOf(
                "navigate:VideoPlayer(itemId=item-1, mediaSourceId=null, startPositionTicks=10000, subtitleStreamIndex=null, audioStreamIndex=null)",
                "navigate:AudioPlayer(itemId=track-1)",
                "navigate:MediaDetail(itemId=item-2, openDownloadSheet=false)",
            ),
            shell.events,
        )
    }

    // ── the tab-vs-push fork: the select-not-pop policy ──────────────────

    @Test
    fun `every top-level destination switches the tab and never pushes`() {
        val shell = Shell()

        shell.collect(
            NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME),
            NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH),
            NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS),
        )

        // The fork's whole point: the TAB seam (no pop-to-root when the tab
        // is already selected, no push filter), never navigate — even though
        // navigate would look equivalent for a non-current tab.
        assertEquals(
            listOf("tab:Home", "tab:Search", "tab:Settings"),
            shell.events,
        )
    }

    @Test
    fun `a destination outside this shell's tab vocabulary pushes`() {
        val shell = Shell(topLevelKeys = setOf(Route.Home, Route.Search))

        shell.collect(NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS))

        assertEquals(listOf("navigate:Settings"), shell.events)
    }

    @Test
    fun `routeDispatch folds a registered tab to SwitchTab and anything else to Push`() {
        assertEquals(
            RemoteRouteDispatch.SwitchTab(Route.Search),
            RemoteNavigationDispatcher.routeDispatch(Route.Search, ALL_TABS),
        )
        assertEquals(
            RemoteRouteDispatch.Push(Route.MediaDetail("item-1")),
            RemoteNavigationDispatcher.routeDispatch(Route.MediaDetail("item-1"), ALL_TABS),
        )
        // A non-tab route that happens to collide with no registered key —
        // the player routes push too, tab membership is the only fork.
        assertEquals(
            RemoteRouteDispatch.Push(Route.VideoPlayer("v-1")),
            RemoteNavigationDispatcher.routeDispatch(Route.VideoPlayer("v-1"), ALL_TABS),
        )
    }

    // ── the navigation-ladder arms ───────────────────────────────────────

    @Test
    fun `goBack drives the back seam`() {
        val shell = Shell()

        shell.collect(NavigationTarget.GoBack)

        assertEquals(listOf("goBack"), shell.events)
    }

    @Test
    fun `moveFocus and invokeSelect drive their per-shell seams`() {
        val shell = Shell()

        shell.collect(
            NavigationTarget.MoveFocus(RemoteFocusDirection.DOWN),
            NavigationTarget.InvokeSelect,
        )

        assertEquals(listOf("focus:DOWN", "select"), shell.events)
    }

    @Test
    fun `an unhandled context menu surfaces the fallback message`() {
        val shell = Shell(contextMenuHandled = false)

        shell.collect(NavigationTarget.OpenContextMenu)

        assertEquals(listOf("message:no context menu here"), shell.events)
    }

    @Test
    fun `a consumed context menu key stays silent`() {
        val shell = Shell(contextMenuHandled = true)

        shell.collect(NavigationTarget.OpenContextMenu)

        assertEquals(emptyList<String>(), shell.events)
    }

    // ── ClosePlayer: the Jellyfin-web "Stop" pop ─────────────────────────

    @Test
    fun `closePlayer pops player routes off every stack and emits nothing`() {
        val shell = Shell()
        val home = mutableListOf<NavKey>(Route.Home)
        val library = mutableListOf<NavKey>(
            Route.Library,
            Route.MediaDetail("item-1"),
            Route.VideoPlayer("v-1"),
            Route.AudioPlayer("a-1"),
        )
        shell.stacks += home
        shell.stacks += library

        shell.collect(NavigationTarget.ClosePlayer)

        // Player entries popped off the top of every stack, non-player
        // routes untouched, no seam event fired.
        assertEquals(listOf<NavKey>(Route.Home), home)
        assertEquals(listOf<NavKey>(Route.Library, Route.MediaDetail("item-1")), library)
        assertEquals(emptyList<String>(), shell.events)
    }

    // ── the companion folds the Android collector's companions share ────

    @Test
    fun `syncPlayAutoOpenRoute guards on a VideoPlayer topping any stack`() {
        val stacks = listOf(
            mutableListOf<NavKey>(Route.Home),
            mutableListOf<NavKey>(Route.LiveTv, Route.VideoPlayer("already-open")),
        )

        assertNull(
            RemoteNavigationDispatcher.syncPlayAutoOpenRoute(
                itemId = "ep-1",
                startPositionTicks = 0L,
                backStacks = stacks,
            ),
        )
        assertEquals(
            Route.VideoPlayer(itemId = "ep-1", startPositionTicks = 7_000L),
            RemoteNavigationDispatcher.syncPlayAutoOpenRoute(
                itemId = "ep-1",
                startPositionTicks = 7_000L,
                backStacks = listOf(mutableListOf<NavKey>(Route.Home, Route.AudioPlayer("a-1"))),
            ),
        )
    }

    @Test
    fun `nowPlayingMessage falls back to the item id on a blank title`() {
        assertEquals(
            "Now playing: Episode One",
            RemoteNavigationDispatcher.nowPlayingMessage(
                PlayEventPayload(itemId = "ep-1", title = "Episode One", startPositionTicks = 0L),
                messageTemplate = "Now playing: %s",
            ),
        )
        assertEquals(
            "Now playing: ep-2",
            RemoteNavigationDispatcher.nowPlayingMessage(
                PlayEventPayload(itemId = "ep-2", title = " ", startPositionTicks = 0L),
                messageTemplate = "Now playing: %s",
            ),
        )
    }
}
