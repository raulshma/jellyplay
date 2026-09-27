package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.PlayEventPayload
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessage as SharedUserMessage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.UserMessageDuration
import com.raulshma.jellyplay.shell.SyncPlayOpenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the nav-request collector choreography [NavRequestCollector] owns for
 * JellyPlayApp's MainContent — the five collect-then-dispatch loops that used
 * to live composable-inline — plus the one pure fold left on its companion
 * (the nullable wrapper [NavRequestCollector.pendingRouteDispatch] adds over
 * the shared route-dispatch table) and the message-host adaptation seams
 * ([legacySeverityOf] / the duration maps). The fold tables themselves — the
 * tab-vs-push decision, the SyncPlay auto-open guard, the now-playing message
 * format — are the shared RemoteNavigationDispatcher's and are pinned ONCE in
 * RemoteNavigationDispatcherTest; the collector drives them here only through
 * its loops (the RemoteNavigationRouting precedent).
 *
 * Every test drives the controller through fake constructor lambdas (one
 * shared event log so dispatch→consume ORDER is asserted, not just counts)
 * and MutableSharedFlow emissions under runTest's virtual scheduler — no
 * compose, no Navigator, no SnackbarHostState. The collector launch idiom is
 * UserMessageHostTest's: an eager UnconfinedTestDispatcher on the test
 * scheduler, [advanceUntilIdle] drives the rest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavRequestCollectorTest {

    /** Minimal route vocabulary — the fork only needs a top-level set with two members. */
    private val topLevelKeys = setOf<Route>(Route.Home, Route.Search)

    /**
     * Fake shell: recording sinks + the mutable stacks the backStacks
     * provider serves. The single [events] log keeps ordering observable.
     */
    private class Shell(topLevelKeys: Set<Route>) {
        val events = mutableListOf<String>()
        val stacks = mutableListOf<MutableList<NavKey>>()
        val collector = NavRequestCollector(
            topLevelKeys = topLevelKeys,
            navigate = { route -> events += "navigate:$route" },
            selectTopLevelTab = { route -> events += "tab:$route" },
            backStacks = { stacks },
            consumePendingRoute = { events += "consume" },
            presentSnackbar = { message -> events += "snackbar:$message" },
            goBack = { events += "goBack" },
            dispatchKey = { keyCode -> events += "key:$keyCode"; true },
        )
    }

    /** UserMessageHostTest's launch idiom — see the class KDoc. */
    private fun TestScope.launchCollector(block: suspend () -> Unit) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }

    // ── pendingRouteDispatch: the null wrapper over the shared table ────
    // (the tab-vs-push rows themselves are RemoteNavigationDispatcherTest's)

    @Test
    fun `pendingRouteDispatch folds no pending route to null`() {
        assertNull(NavRequestCollector.pendingRouteDispatch(null, topLevelKeys))
    }

    // ── dispatchPendingRoute: dispatch, then consume-once ────────────────

    @Test
    fun `a top-level pending route switches the tab directly and consumes`() {
        val shell = Shell(topLevelKeys)

        shell.collector.dispatchPendingRoute(Route.Search)

        // SwitchTab deliberately bypasses the Navigator (no pop-to-root when
        // the tab is already selected, no navigate filter) and the consume
        // ack lands after the dispatch.
        assertEquals(listOf("tab:Search", "consume"), shell.events)
    }

    @Test
    fun `a nested pending route pushes through the navigate seam and consumes`() {
        val shell = Shell(topLevelKeys)

        shell.collector.dispatchPendingRoute(Route.MediaDetail("item-1"))

        assertEquals(
            listOf("navigate:MediaDetail(itemId=item-1, openDownloadSheet=false)", "consume"),
            shell.events,
        )
    }

    @Test
    fun `a null pending route dispatches nothing and does not consume`() {
        val shell = Shell(topLevelKeys)

        shell.collector.dispatchPendingRoute(null)

        assertEquals(emptyList<String>(), shell.events)
    }

    // ── collectRemoteNavigation: target → navigate / ClosePlayer → pop ──

    @Test
    fun `a navigation target is mapped and pushed`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(
            NavigationTarget.OpenVideoPlayer(
                itemId = "item-1",
                startPositionTicks = 10_000L,
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("navigate:VideoPlayer(itemId=item-1, mediaSourceId=null, startPositionTicks=10000, subtitleStreamIndex=null, audioStreamIndex=null)"),
            shell.events,
        )
    }

    @Test
    fun `closePlayer pops player routes off every stack instead of navigating`() = runTest {
        val shell = Shell(topLevelKeys)
        val home = mutableListOf<NavKey>(Route.Home)
        val library = mutableListOf<NavKey>(
            Route.Library,
            Route.MediaDetail("item-1"),
            Route.VideoPlayer("v-1"),
        )
        shell.stacks += home
        shell.stacks += library
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(NavigationTarget.ClosePlayer)
        advanceUntilIdle()

        // Jellyfin-web "Stop" semantics: player entries popped off the top of
        // every stack, non-player routes untouched, nothing pushed.
        assertEquals(listOf<NavKey>(Route.Home), home)
        assertEquals(listOf<NavKey>(Route.Library, Route.MediaDetail("item-1")), library)
        assertEquals(emptyList<String>(), shell.events)
    }

    // ── collectRemoteNavigation: the navigation ladder ─────────────

    @Test
    fun `goBack drives the back seam`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(NavigationTarget.GoBack)
        advanceUntilIdle()

        assertEquals(listOf("goBack"), shell.events)
    }

    @Test
    fun `moveFocus and invokeSelect synthesize their key events`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(NavigationTarget.MoveFocus(com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection.DOWN))
        targets.tryEmit(NavigationTarget.InvokeSelect)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "key:${android.view.KeyEvent.KEYCODE_DPAD_DOWN}",
                "key:${android.view.KeyEvent.KEYCODE_DPAD_CENTER}",
            ),
            shell.events,
        )
    }

    @Test
    fun `an unhandled context menu surfaces the fallback message`() = runTest {
        val shell = Shell(topLevelKeys)
        val collector = NavRequestCollector(
            topLevelKeys = topLevelKeys,
            navigate = { route -> shell.events += "navigate:$route" },
            selectTopLevelTab = { route -> shell.events += "tab:$route" },
            backStacks = { shell.stacks },
            consumePendingRoute = { shell.events += "consume" },
            presentSnackbar = { message -> shell.events += "snackbar:$message" },
            goBack = { shell.events += "goBack" },
            dispatchKey = { _ -> false },
        )
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "Context menu not available here") }

        targets.tryEmit(NavigationTarget.OpenContextMenu)
        advanceUntilIdle()

        assertEquals(listOf("snackbar:Context menu not available here"), shell.events)
    }

    @Test
    fun `a handled context menu key stays silent`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "Context menu not available here") }

        targets.tryEmit(NavigationTarget.OpenContextMenu)
        advanceUntilIdle()

        // The Shell fake's dispatcher always reports handled → message only.
        assertEquals(listOf("key:${android.view.KeyEvent.KEYCODE_MENU}"), shell.events)
    }

    @Test
    fun `goHome switches the tab through the shared route-dispatch fork`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(
            NavigationTarget.GoToTopLevel(com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination.HOME),
        )
        advanceUntilIdle()

        assertEquals(listOf("tab:Home"), shell.events)
    }

    @Test
    fun `goToSettings pushes when the destination is not a top-level key of this shell`() = runTest {
        val shell = Shell(topLevelKeys) // Home + Search only — Settings pushes.
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets, contextMenuUnavailableMessage = "no context menu here") }

        targets.tryEmit(
            NavigationTarget.GoToTopLevel(com.raulshma.jellyplay.core.model.remote.RemoteTopLevelDestination.SETTINGS),
        )
        advanceUntilIdle()

        assertEquals(listOf("navigate:Settings"), shell.events)
    }

    // ── collectSyncPlayOpens: the guard drives the push ─────────────────
    // (the guard's own fold table — veto / build / top-only — is
    // RemoteNavigationDispatcherTest's; these rows pin the loop wiring)

    @Test
    fun `a group open request pushes the video player when none is open`() = runTest {
        val shell = Shell(topLevelKeys)
        shell.stacks += mutableListOf<NavKey>(Route.Home)
        val requests = MutableSharedFlow<SyncPlayOpenRequest>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectSyncPlayOpens(requests) }

        requests.tryEmit(SyncPlayOpenRequest(itemId = "ep-1", startPositionTicks = 7_000L))
        advanceUntilIdle()

        assertEquals(
            listOf("navigate:VideoPlayer(itemId=ep-1, mediaSourceId=null, startPositionTicks=7000, subtitleStreamIndex=null, audioStreamIndex=null)"),
            shell.events,
        )
    }

    @Test
    fun `a group open request stays silent while a player tops any stack`() = runTest {
        val shell = Shell(topLevelKeys)
        shell.stacks += mutableListOf(Route.Home, Route.VideoPlayer("already-open"))
        val requests = MutableSharedFlow<SyncPlayOpenRequest>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectSyncPlayOpens(requests) }

        requests.tryEmit(SyncPlayOpenRequest(itemId = "ep-2", startPositionTicks = 0L))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), shell.events)
    }

    // ── collectNowPlayingSnackbars: present through the seam ────────────

    @Test
    fun `a play event presents the formatted now-playing message`() = runTest {
        val shell = Shell(topLevelKeys)
        val events = MutableSharedFlow<PlayEventPayload>(extraBufferCapacity = 4)
        launchCollector {
            shell.collector.collectNowPlayingSnackbars(events, messageTemplate = "Now playing: %s")
        }

        events.tryEmit(PlayEventPayload(itemId = "ep-1", title = "", startPositionTicks = 0L))
        advanceUntilIdle()

        assertEquals(listOf("snackbar:Now playing: ep-1"), shell.events)
    }

    // ── message-host adaptation seams ────────────────────────────────────

    @Test
    fun `legacySeverityOf projects both legacy arms onto the shared severity`() {
        assertEquals(
            SharedUserMessage.Severity.Error,
            legacySeverityOf(UserMessage.Error(UiText.Raw("boom"))),
        )
        assertEquals(
            SharedUserMessage.Severity.Info,
            legacySeverityOf(UserMessage.Info(UiText.Raw("done"))),
        )
    }

    @Test
    fun `duration maps keep the shared severity policy on both surfaces`() {
        assertEquals(android.widget.Toast.LENGTH_LONG, toastDurationFor(UserMessageDuration.Long))
        assertEquals(android.widget.Toast.LENGTH_SHORT, toastDurationFor(UserMessageDuration.Short))
        assertEquals(
            androidx.compose.material3.SnackbarDuration.Long,
            snackbarDurationFor(UserMessageDuration.Long),
        )
        assertEquals(
            androidx.compose.material3.SnackbarDuration.Short,
            snackbarDurationFor(UserMessageDuration.Short),
        )
    }
}
