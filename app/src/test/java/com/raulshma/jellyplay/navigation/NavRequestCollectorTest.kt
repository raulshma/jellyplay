package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
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
 * to live composable-inline — plus the pure policy folds on its companion
 * (the RemoteNavigationRouting precedent) and the message-host adaptation
 * seams ([legacySeverityOf] / the duration maps).
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
        )
    }

    /** UserMessageHostTest's launch idiom — see the class KDoc. */
    private fun TestScope.launchCollector(block: suspend () -> Unit) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block() }

    // ── pendingRouteDispatch: the tab-vs-nested fork ─────────────────────

    @Test
    fun `pendingRouteDispatch folds no pending route to None`() {
        assertEquals(
            NavRequestCollector.PendingRouteDispatch.None,
            NavRequestCollector.pendingRouteDispatch(null, topLevelKeys),
        )
    }

    @Test
    fun `pendingRouteDispatch folds a registered top-level key to SwitchTab`() {
        assertEquals(
            NavRequestCollector.PendingRouteDispatch.SwitchTab(Route.Search),
            NavRequestCollector.pendingRouteDispatch(Route.Search, topLevelKeys),
        )
    }

    @Test
    fun `pendingRouteDispatch folds anything else to Push`() {
        assertEquals(
            NavRequestCollector.PendingRouteDispatch.Push(Route.MediaDetail("item-1")),
            NavRequestCollector.pendingRouteDispatch(Route.MediaDetail("item-1"), topLevelKeys),
        )
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

    // ── syncPlayAutoOpenRoute: the player-open guard ─────────────────────

    @Test
    fun `syncPlayAutoOpenRoute builds the player route when no player is open`() {
        assertEquals(
            Route.VideoPlayer(itemId = "ep-1", startPositionTicks = 42_000L),
            NavRequestCollector.syncPlayAutoOpenRoute(
                SyncPlayOpenRequest(itemId = "ep-1", startPositionTicks = 42_000L),
                backStacks = listOf(
                    mutableListOf(Route.Home, Route.MediaDetail("item-1")),
                ),
            ),
        )
    }

    @Test
    fun `a VideoPlayer on top of any stack vetoes the open`() {
        assertNull(
            NavRequestCollector.syncPlayAutoOpenRoute(
                SyncPlayOpenRequest("ep-1", 0L),
                backStacks = listOf(
                    mutableListOf(Route.Home),
                    mutableListOf(Route.LiveTv, Route.VideoPlayer("already-open")),
                ),
            ),
        )
    }

    @Test
    fun `an AudioPlayer top does not veto — the guard is VideoPlayer-specific`() {
        // The audio player is not the SyncPlay surface; the group's video
        // item still needs a video player pushed.
        assertEquals(
            Route.VideoPlayer("ep-1"),
            NavRequestCollector.syncPlayAutoOpenRoute(
                SyncPlayOpenRequest("ep-1", 0L),
                backStacks = listOf(mutableListOf(Route.Home, Route.AudioPlayer("a-1"))),
            ),
        )
    }

    @Test
    fun `a VideoPlayer buried below a non-player top does not veto`() {
        // Top-only guard: a player the user navigated away from is not
        // visible, so the group playback opens a fresh one.
        assertEquals(
            Route.VideoPlayer("ep-1"),
            NavRequestCollector.syncPlayAutoOpenRoute(
                SyncPlayOpenRequest("ep-1", 0L),
                backStacks = listOf(
                    mutableListOf(Route.Home, Route.VideoPlayer("buried"), Route.MediaDetail("item-1")),
                ),
            ),
        )
    }

    @Test
    fun `empty stacks and an empty stack collection do not veto`() {
        val request = SyncPlayOpenRequest("ep-1", 0L)

        assertEquals(
            Route.VideoPlayer("ep-1"),
            NavRequestCollector.syncPlayAutoOpenRoute(request, backStacks = emptyList()),
        )
        assertEquals(
            Route.VideoPlayer("ep-1"),
            NavRequestCollector.syncPlayAutoOpenRoute(
                request,
                backStacks = listOf(mutableListOf<NavKey>()),
            ),
        )
    }

    // ── nowPlayingSnackbarMessage: the title fallback + template ────────

    @Test
    fun `a titled play event formats into the now-playing template`() {
        assertEquals(
            "Now playing: Episode One",
            NavRequestCollector.nowPlayingSnackbarMessage(
                PlayEventPayload(itemId = "ep-1", title = "Episode One", startPositionTicks = 0L),
                messageTemplate = "Now playing: %s",
            ),
        )
    }

    @Test
    fun `a blank title falls back to the raw item id`() {
        assertEquals(
            "Now playing: ep-1",
            NavRequestCollector.nowPlayingSnackbarMessage(
                PlayEventPayload(itemId = "ep-1", title = " ", startPositionTicks = 0L),
                messageTemplate = "Now playing: %s",
            ),
        )
    }

    // ── collectRemoteNavigation: target → navigate / ClosePlayer → pop ──

    @Test
    fun `a navigation target is mapped and pushed`() = runTest {
        val shell = Shell(topLevelKeys)
        val targets = MutableSharedFlow<NavigationTarget>(extraBufferCapacity = 4)
        launchCollector { shell.collector.collectRemoteNavigation(targets) }

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
        launchCollector { shell.collector.collectRemoteNavigation(targets) }

        targets.tryEmit(NavigationTarget.ClosePlayer)
        advanceUntilIdle()

        // Jellyfin-web "Stop" semantics: player entries popped off the top of
        // every stack, non-player routes untouched, nothing pushed.
        assertEquals(listOf<NavKey>(Route.Home), home)
        assertEquals(listOf<NavKey>(Route.Library, Route.MediaDetail("item-1")), library)
        assertEquals(emptyList<String>(), shell.events)
    }

    // ── collectSyncPlayOpens: the guard drives the push ─────────────────

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
