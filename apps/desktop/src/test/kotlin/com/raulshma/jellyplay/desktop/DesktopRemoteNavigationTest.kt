package com.raulshma.jellyplay.desktop

import androidx.compose.ui.focus.FocusDirection
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the desktop remote-navigation SEAM BUNDLE — the collector's own half
 * after the ladder itself moved to shared/feature/shell's
 * `RemoteNavigationDispatcher` (the full target→sink policy table, including
 * the GoToTopLevel select-not-pop fork, is pinned by its
 * `RemoteNavigationDispatcherTest`): this class only proves the desktop
 * seams are wired to the right arms — back/focus/select through their
 * lambdas, the context-menu fallback message, tab switches through the
 * tab seam (the regression: the hand-copied ladder routed GoToTopLevel
 * through the navigator, popping the already-selected tab to its root), and
 * pushes through the navigate seam. Also pins [composeFocusDirection], the
 * four-branch remote→Compose focus mapping extracted from the scaffold's
 * moveFocus adapter (plain constant objects — no focus tree needed).
 */
class DesktopRemoteNavigationTest {

    // ── DesktopRemoteNavCollector ────────────────────────────────────────

    private class RecordingShell {
        val events = mutableListOf<String>()
        val stacks = mutableListOf<MutableList<NavKey>>()
        val collector = DesktopRemoteNavCollector(
            topLevelKeys = setOf(Route.Home, Route.Search, Route.Settings),
            navigate = { route -> events += "navigate:$route" },
            selectTab = { route -> events += "tab:$route" },
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
                // The intended fix: remote GoHome goes through the TAB seam,
                // not the navigator — no pop-to-root when Home is already
                // the selected tab (the old hand-copied ladder emitted
                // "navigate:Home" here).
                "tab:${Route.Home}",
                "navigate:${Route.MediaDetail("d")}",
            ),
            shell.events,
        )
    }

    @Test
    fun `every remote top-level destination selects its tab, never pushing`() = kotlinx.coroutines.runBlocking {
        val shell = RecordingShell()

        shell.collector.collect(
            kotlinx.coroutines.flow.flowOf(
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME),
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH),
                NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS),
            ),
        )

        assertEquals(
            listOf("tab:${Route.Home}", "tab:${Route.Search}", "tab:${Route.Settings}"),
            shell.events,
        )
    }

    // ── composeFocusDirection ───────────────────────────────────────────

    @Test
    fun `the focus mapping covers all four remote directions`() {
        assertEquals(FocusDirection.Up, composeFocusDirection(RemoteFocusDirection.UP))
        assertEquals(FocusDirection.Down, composeFocusDirection(RemoteFocusDirection.DOWN))
        assertEquals(FocusDirection.Left, composeFocusDirection(RemoteFocusDirection.LEFT))
        assertEquals(FocusDirection.Right, composeFocusDirection(RemoteFocusDirection.RIGHT))
    }
}
