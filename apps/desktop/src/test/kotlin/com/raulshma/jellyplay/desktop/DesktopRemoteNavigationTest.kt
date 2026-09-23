package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the desktop remote-navigation collector — the per-shell LADDER
 * DISPATCH half (the pure folds it routes through — the target→route mapping
 * and the Jellyfin-web "Stop" pop — live in shared/feature/shell and are
 * pinned by its `RemoteNavigationRoutingTest`): the ladder's back / focus /
 * select / context-menu dispatch through fake lambdas — the
 * NavRequestCollector shape.
 */
class DesktopRemoteNavigationTest {

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
