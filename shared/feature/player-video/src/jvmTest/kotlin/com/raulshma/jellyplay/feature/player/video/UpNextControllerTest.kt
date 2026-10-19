package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [UpNextController] — the Up Next module extracted from the
 * ViewModel (the navigator stack, the QUEUE-FIRST advance + manual queue-row
 * activation, the card + has-queued-next projections, the mark-watched/
 * unwatched overflow orchestration, the autoplay-cancel wiring, the Up Next
 * loading flag and the smart-download cleanup). Drives it through its
 * constructor lambdas faked here (the [SubtitleStyleControllerTest] shape);
 * the navigator inside is exercised through the controller's own verbs. No
 * ViewModel, no uiState.
 *
 * Pins the load-bearing invariants: the mark arms mirror the watched-threshold
 * callback's two paths (server vs offline-local, incognito-gated); the
 * watched-and-skip advance reuses the navigator (single-flight latch +
 * SyncPlay routing intact); cancel flips the autoplay clock and the overlay
 * mirror; the smart-download cleanup is gated by the pref and the duration
 * floor; the card's precedence matches the advance's (queue head over episode
 * sibling, null under SyncPlay); and BOTH queue consumption arms run
 * settle-then-consume, so a failed load never deletes a row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpNextControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val catalogue: EpisodeCatalogue = mockk(relaxed = true)

    private lateinit var sessionState: MutableStateFlow<PlayerSessionState>
    private val sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private lateinit var episodes: EpisodeBrowserState
    private var videoQueue: VideoQueueController? = null
    private val syncPlayMirrorFlow = MutableStateFlow(false)

    // Mirror fakes (the VM-side state the lambdas read/write).
    private var detail: MediaDetail? = seriesDetail()
    private var incognito = false
    private var hasNext = false
    private var syncPlayMirror = false
    private var currentItemId: String? = "ep2"
    private var syncGroupLive = false
    private var group: SyncPlayGroup? = null
    private var smartDownloadsEnabled = false
    private var durationMs = 0L
    private var deleteResult = false

    // Recording sinks.
    private val markedPlayed = mutableListOf<String>()
    private val recordedOffline = mutableListOf<String>()
    private val markedUnwatched = mutableListOf<String>()
    private val initialized = mutableListOf<Pair<String, Long>>()
    private val sentNext = mutableListOf<String>()
    private val sentPrevious = mutableListOf<String>()
    private var closeRequests = 0
    private var cancelDecisionCalls = 0
    private var autoplayCancelledMirror = false
    private var deleteAttempts = 0
    private var deletedNotices = 0

    private fun seriesDetail(): MediaDetail = MediaDetail(
        item = MediaItem(
            id = "ep2",
            name = "S1E2",
            mediaType = MediaType.EPISODE,
            seriesId = "series-1",
            seasonId = "season-1",
        ),
    )

    private fun episode(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
    )

    private fun stubSeason(vararg items: MediaItem) {
        coEvery { catalogue.loadSeasonEpisodes("series-1", "season-1", any()) } returns Result.success(items.toList())
    }

    /**
     * The Up Next module's reactive projections ([UpNextController.upNextCard]
     * / [UpNextController.hasQueuedNext]) are Eagerly-shared collectors that
     * park on never-completing StateFlows BY DESIGN (the queue, the
     * adjacent-episode mirror, the SyncPlay mirror). Launched as children of
     * the runTest coroutine they trip UncompletedCoroutinesError in every
     * test; a dedicated scope cancelled in teardown is the
     * EngineAttachControllerTest pattern (SyncPlayBridgeTest before it). The
     * VERBS (advance, queue consumption, marks) keep their normal launch
     * timing — the shared [dispatcher] drives this scope and runTest's alike.
     */
    private val controllerScope = CoroutineScope(SupervisorJob() + dispatcher)

    private fun controller(): UpNextController =
        UpNextController(
            scope = controllerScope,
            sessionState = sessionState,
            sessionEvents = sessionEvents,
            episodeCatalogue = catalogue,
            videoQueue = videoQueue,
            syncPlaySessionMirror = syncPlayMirrorFlow,
            getDetail = { detail },
            getSeriesId = { detail?.item?.seriesId },
            updateEpisodes = { update -> episodes = update(episodes) },
            initializeItem = { itemId, ticks -> initialized.add(itemId to ticks) },
            reportLoadError = { },
            isInSyncPlayGroup = { syncGroupLive },
            getCurrentGroup = { group },
            sendNextItem = { playlistItemId -> sentNext.add(playlistItemId) },
            sendPreviousItem = { playlistItemId -> sentPrevious.add(playlistItemId) },
            isIncognito = { incognito },
            markPlayed = { itemId -> markedPlayed.add(itemId) },
            recordPlayedOffline = { itemId -> recordedOffline.add(itemId) },
            markUnwatched = { itemId -> markedUnwatched.add(itemId) },
            getCurrentItemId = { currentItemId },
            hasNextEpisode = { hasNext },
            isInSyncPlaySession = { syncPlayMirror },
            closePlayer = { closeRequests++ },
            cancelAutoplayDecision = { cancelDecisionCalls++ },
            setAutoplayCancelledMirror = { autoplayCancelledMirror = it },
            isSmartDownloadsEnabled = { smartDownloadsEnabled },
            getDurationMs = { durationMs },
            deleteDownload = { _ ->
                deleteAttempts++
                deleteResult
            },
            notifySmartDownloadDeleted = { deletedNotices++ },
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionState = MutableStateFlow(PlayerSessionState(currentItemId = "ep2"))
        episodes = EpisodeBrowserState()
    }

    @AfterTest
    fun tearDown() {
        // Kill the never-completing projection collectors the controller
        // launched — the structural counterpart of the dedicated scope above.
        controllerScope.cancel()
        Dispatchers.resetMain()
    }

    // ── next-episode advance + overlay flag ────────────────────────────────

    @Test
    fun `playNextEpisode advances to the sibling and settles the overlay flag`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val c = controller()

        c.playNextEpisode()
        // Complete the settle wait by binding the new item (the latch holds
        // until the session settles on a DIFFERENT non-null item).
        sessionState.value = sessionState.value.copy(currentItemId = "ep3")
        advanceUntilIdle()

        assertEquals(listOf("ep3" to 0L), initialized)
        assertFalse(c.isNextEpisodeLoading.value)
    }

    @Test
    fun `playNextEpisode flips the overlay flag on and holds it until settle`() = runTest(dispatcher) {
        stubSeason(episode("ep2"), episode("ep3"))
        val c = controller()

        c.playNextEpisode()
        // runCurrent (not advanceUntilIdle): the settle timeout is under test.
        runCurrent()
        assertTrue(c.isNextEpisodeLoading.value, "the latch holds while the load is in flight")
        // Single-flight: a re-tap inside the window starts nothing.
        c.playNextEpisode()
        runCurrent()
        assertEquals(1, initialized.size)

        sessionState.value = sessionState.value.copy(currentItemId = "ep3")
        runCurrent()
        assertFalse(c.isNextEpisodeLoading.value, "settling on the new item releases the latch")
    }

    @Test
    fun `playNextEpisode routes through the SyncPlay queue when the group holds the sibling`() =
        runTest(dispatcher) {
            stubSeason(episode("ep2"), episode("ep3"))
            syncGroupLive = true
            group = SyncPlayGroup(
                groupId = "g1",
                groupName = "g1",
                participantCount = 2,
                playingPlaylistItemId = "pl-3",
                playlistItemMap = mapOf("pl-2" to "ep2", "pl-3" to "ep3"),
            )
            val c = controller()

            c.playNextEpisode()
            advanceUntilIdle()

            assertEquals(listOf("pl-3"), sentNext, "the advance goes through the group command")
            assertTrue(initialized.isEmpty(), "no local reload when the group holds the sibling")
            assertFalse(c.isNextEpisodeLoading.value)
        }

    // ── queue-first advance (the manual up-next queue arm) ──────────────────

    private fun queuedMovie(id: String) {
        videoQueue = VideoQueueController().apply { enqueueLast(VideoQueueItem(itemId = id, title = id)) }
    }

    @Test
    fun `playNextEpisode with a queued head plays the head without episode resolution`() =
        runTest(dispatcher) {
            queuedMovie("movie-1")
            val c = controller()

            c.playNextEpisode()
            runCurrent()

            assertEquals(listOf("movie-1" to 0L), initialized, "the queue arm never resolves seasons")
            assertTrue(queued("movie-1"), "the head is NOT consumed before the session settles")
        }

    @Test
    fun `queue advance settles on the head and consumes it`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        val c = controller()

        c.playNextEpisode()
        sessionState.value = sessionState.value.copy(currentItemId = "movie-1")
        advanceUntilIdle()

        assertFalse(queued("movie-1"), "settling on the head consumes it")
    }

    @Test
    fun `queue advance that errors keeps the head queued`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        val c = controller()

        c.playNextEpisode()
        runCurrent()
        sessionEvents.tryEmit(SessionEvent.ShowError(error = "boom", retryable = true))
        advanceUntilIdle()

        assertTrue(queued("movie-1"), "a failed advance leaves the queue intact")
        assertTrue(c.hasQueuedNext.value, "the reactive gate still counts the kept head")
    }

    @Test
    fun `queue advance superseded by another item keeps the head`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        stubSeason(episode("ep2"), episode("ep3"))
        val c = controller()

        c.playNextEpisode()
        runCurrent()
        // The session binds a DIFFERENT item (user navigated elsewhere while
        // the head was loading) — never settles on the head.
        sessionState.value = sessionState.value.copy(currentItemId = "other")
        advanceTimeBy(NEXT_EPISODE_SETTLE_TIMEOUT_MS + 1)
        advanceUntilIdle()

        assertTrue(queued("movie-1"), "a superseded advance keeps the head")
        // And the latch is released: a fresh advance goes through (queue is
        // untouched, so it re-initializes the head).
        c.playNextEpisode()
        runCurrent()
        assertEquals(listOf("movie-1", "movie-1"), initialized.map { it.first })
    }

    @Test
    fun `queue advance re-tap while settling does not double-initialize`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        val c = controller()

        c.playNextEpisode()
        runCurrent()
        c.playNextEpisode()
        runCurrent()
        assertEquals(1, initialized.size, "the queue arm is single-flight")

        sessionState.value = sessionState.value.copy(currentItemId = "movie-1")
        advanceUntilIdle()
        assertFalse(queued("movie-1"), "settling consumed the head")

        // Latch released: a fresh head advances again (the previous item is a
        // movie — the episode navigator has no sibling for it, so the queue
        // arm is the only advance path here).
        videoQueue?.enqueueLast(VideoQueueItem(itemId = "movie-2", title = "movie-2"))
        c.playNextEpisode()
        runCurrent()
        assertEquals(listOf("movie-1", "movie-2"), initialized.map { it.first })
    }

    @Test
    fun `queue arm is suppressed during SyncPlay even with a head`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        stubSeason(episode("ep2"), episode("ep3"))
        syncGroupLive = true
        group = null // group with no map: the SyncPlay route falls through to the sibling
        // A live group session also flips the uiState SyncPlay mirror in
        // production (PlaybackSession's arm collector) — the reactive
        // projections gate on THAT mirror, the advance on the manager flag.
        syncPlayMirrorFlow.value = true
        val c = controller()

        c.playNextEpisode()
        advanceUntilIdle()

        assertTrue(queued("movie-1"), "SyncPlay owns the order — the queue is untouched")
        assertEquals(listOf("ep3" to 0L), initialized, "the episode sibling plays instead")
        assertFalse(c.hasQueuedNext.value, "the reactive gate mirrors the SyncPlay suppression")
        assertNull(c.upNextCard.value, "the card is suppressed with the gate")
    }

    private fun queued(id: String): Boolean =
        videoQueue?.queue?.value?.any { it.itemId == id } == true

    // ── the Up Next card projection (display precedence == advance) ─────────

    @Test
    fun `upNextCard queue head beats the episode sibling`() = runTest(dispatcher) {
        queuedMovie("movie-1")
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val c = controller()

        c.refreshAdjacent(seriesDetail())
        advanceUntilIdle()

        val card = c.upNextCard.value
        assertTrue(card != null, "a card is projected")
        assertEquals(UpNextCard.Source.QUEUE, card!!.source, "the queue head WINS over the sibling")
        assertEquals("movie-1", card.itemId)
        assertEquals("movie-1", card.title)
    }

    @Test
    fun `upNextCard shows the queued movie when there is no episode sibling`() = runTest(dispatcher) {
        // The headline case the old `episode?.name ?: queued?.title` display
        // read failed: a movie has no sibling, yet the card must show it.
        queuedMovie("movie-1")
        val c = controller()
        advanceUntilIdle()

        val card = c.upNextCard.value
        assertTrue(card != null, "the queued movie alone projects a card")
        assertEquals(UpNextCard.Source.QUEUE, card!!.source)
        assertEquals("movie-1", card.itemId)
        assertEquals("movie-1", card.title)
    }

    @Test
    fun `upNextCard falls back to the episode sibling when the queue is empty`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val c = controller()

        c.refreshAdjacent(seriesDetail())
        advanceUntilIdle()

        val card = c.upNextCard.value
        assertTrue(card != null, "the sibling alone projects a card")
        assertEquals(UpNextCard.Source.EPISODE, card!!.source)
        assertEquals("ep3", card.itemId, "the navigator's adjacent snapshot is the card source")
    }

    @Test
    fun `upNextCard is null with no queue and no sibling`() = runTest(dispatcher) {
        val c = controller()
        advanceUntilIdle()

        assertNull(c.upNextCard.value, "nothing to play next — no card")
    }

    @Test
    fun `upNextCard is null while a SyncPlay session owns the order and returns on leave`() =
        runTest(dispatcher) {
            queuedMovie("movie-1")
            val c = controller()
            advanceUntilIdle()
            assertTrue(c.upNextCard.value != null, "the card is up before the group joins")

            syncPlayMirrorFlow.value = true
            advanceUntilIdle()
            assertNull(c.upNextCard.value, "the group's server-side queue owns the order — no card")
            assertFalse(c.hasQueuedNext.value, "the has-queued gate suppresses with the card")

            syncPlayMirrorFlow.value = false
            advanceUntilIdle()
            assertTrue(c.upNextCard.value != null, "leaving the group restores the card")
        }

    // ── manual queue-row activation (the settle-then-consume protocol) ─────

    @Test
    fun `playQueueItem settles on the row and consumes it`() = runTest(dispatcher) {
        videoQueue = VideoQueueController().apply {
            enqueueLast(VideoQueueItem(itemId = "a", title = "a"))
            enqueueLast(VideoQueueItem(itemId = "b", title = "b"))
        }
        val c = controller()

        c.playQueueItem(1)
        runCurrent()
        assertEquals(listOf("b" to 0L), initialized, "the row is STARTED, not yet consumed")
        assertTrue(queued("b"), "a mid-list row is not consumed before the session settles")

        sessionState.value = sessionState.value.copy(currentItemId = "b")
        advanceUntilIdle()

        assertFalse(queued("b"), "settling on the row consumes it")
        assertTrue(queued("a"), "the other rows stay untouched")
    }

    @Test
    fun `playQueueItem that errors keeps the row`() = runTest(dispatcher) {
        videoQueue = VideoQueueController().apply {
            enqueueLast(VideoQueueItem(itemId = "a", title = "a"))
            enqueueLast(VideoQueueItem(itemId = "b", title = "b"))
        }
        val c = controller()

        c.playQueueItem(1)
        runCurrent()
        sessionEvents.tryEmit(SessionEvent.ShowError(error = "boom", retryable = true))
        advanceUntilIdle()

        assertTrue(queued("a") && queued("b"), "a failed play-now leaves the queue intact")
        assertTrue(c.hasQueuedNext.value, "the reactive gate keeps counting the queue")
    }

    @Test
    fun `playQueueItem with a moved row leaves the queue intact`() = runTest(dispatcher) {
        videoQueue = VideoQueueController().apply {
            enqueueLast(VideoQueueItem(itemId = "a", title = "a"))
            enqueueLast(VideoQueueItem(itemId = "b", title = "b"))
            enqueueLast(VideoQueueItem(itemId = "c", title = "c"))
        }
        val c = controller()

        c.playQueueItem(1) // starts "b"
        runCurrent()
        // The row moves before the load settles: index 1 now holds "c".
        videoQueue?.removeAt(0)
        sessionState.value = sessionState.value.copy(currentItemId = "b")
        advanceUntilIdle()

        assertTrue(queued("b"), "the started item has moved off index 1 — nothing of it left there")
        assertTrue(queued("c"), "the UNRELATED row that moved into index 1 must NOT be consumed for b")
        assertEquals(listOf("b", "c"), videoQueue?.queue?.value?.map { it.itemId })
    }

    @Test
    fun `playQueueItem re-tap while settling does not double-initialize`() = runTest(dispatcher) {
        videoQueue = VideoQueueController().apply {
            enqueueLast(VideoQueueItem(itemId = "a", title = "a"))
            enqueueLast(VideoQueueItem(itemId = "b", title = "b"))
        }
        val c = controller()

        c.playQueueItem(1)
        runCurrent()
        c.playQueueItem(1)
        runCurrent()
        assertEquals(1, initialized.size, "the manual activation is single-flight")

        sessionState.value = sessionState.value.copy(currentItemId = "b")
        advanceUntilIdle()
        assertFalse(queued("b"), "settling consumed the row")
    }

    @Test
    fun `playQueueItem out of bounds starts nothing`() = runTest(dispatcher) {
        videoQueue = VideoQueueController().apply {
            enqueueLast(VideoQueueItem(itemId = "a", title = "a"))
        }
        val c = controller()

        c.playQueueItem(5)
        runCurrent()
        advanceUntilIdle()

        assertTrue(initialized.isEmpty(), "no row, no load")
        assertTrue(queued("a"), "the queue is untouched")
    }

    @Test
    fun `playQueueItem during a settling advance preempts it`() = runTest(dispatcher) {
        // A manual "play now" is a deliberate pick: it must NOT be dropped
        // because the queue-head ADVANCE happens to be settling (separate
        // latches — the advance's settle guard then keeps its row).
        queuedMovie("movie-1")
        val c = controller()

        c.playNextEpisode()
        runCurrent()
        videoQueue?.enqueueLast(VideoQueueItem(itemId = "movie-2", title = "movie-2"))
        c.playQueueItem(1)
        runCurrent()
        assertEquals(listOf("movie-1", "movie-2"), initialized.map { it.first }, "the manual pick is not dropped")

        // Settling on the manual pick consumes only that row.
        sessionState.value = sessionState.value.copy(currentItemId = "movie-2")
        advanceUntilIdle()
        assertTrue(queued("movie-1"), "the superseded advance keeps its head")
        assertFalse(queued("movie-2"), "the manual pick is consumed on settle")
    }

    // ── mark watched & skip ────────────────────────────────────────────────

    @Test
    fun `markWatchedAndSkip marks played server-side and advances to the next episode`() =
        runTest(dispatcher) {
            stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
            hasNext = true
            val c = controller()

            c.markWatchedAndSkip()
            runCurrent()
            sessionState.value = sessionState.value.copy(currentItemId = "ep3")
            advanceUntilIdle()

            assertTrue(markedPlayed.contains("ep2"), "the SERVER mark arm fired")
            assertTrue(recordedOffline.isEmpty(), "online never uses the offline-local arm")
            assertEquals(listOf("ep3" to 0L), initialized, "the advance reuses the navigator verb")
            assertEquals(0, closeRequests, "a known next episode advances instead of closing")
        }

    @Test
    fun `markWatchedAndSkip without a next episode marks played and closes the player`() =
        runTest(dispatcher) {
            hasNext = false
            syncPlayMirror = false
            val c = controller()

            c.markWatchedAndSkip()
            runCurrent()

            assertTrue(markedPlayed.contains("ep2"))
            assertEquals(1, closeRequests)
            assertTrue(initialized.isEmpty(), "no sibling to advance to")
        }

    @Test
    fun `markWatchedAndSkip in incognito records the offline-local mark instead`() =
        runTest(dispatcher) {
            stubSeason(episode("ep2"), episode("ep3"))
            hasNext = true
            incognito = true
            val c = controller()

            c.markWatchedAndSkip()
            runCurrent()
            sessionState.value = sessionState.value.copy(currentItemId = "ep3")
            advanceUntilIdle()

            assertTrue(recordedOffline.contains("ep2"), "the OFFLINE_LOCAL mark arm fired")
            assertTrue(markedPlayed.isEmpty(), "incognito never reaches the server or an outbox row")
            assertEquals(listOf("ep3" to 0L), initialized, "incognito does not block the advance")
        }

    // ── mark unwatched & quit ──────────────────────────────────────────────

    @Test
    fun `markUnwatchedAndQuit clears the played flag and closes the player`() = runTest(dispatcher) {
        val c = controller()

        c.markUnwatchedAndQuit()
        runCurrent()

        assertEquals(listOf("ep2"), markedUnwatched)
        assertEquals(1, closeRequests)
        assertTrue(markedPlayed.isEmpty())
    }

    @Test
    fun `markUnwatchedAndQuit in incognito is a no-op mark that still closes`() = runTest(dispatcher) {
        incognito = true
        val c = controller()

        c.markUnwatchedAndQuit()
        runCurrent()

        assertTrue(markedUnwatched.isEmpty(), "incognito leaves no watch state behind")
        assertEquals(1, closeRequests, "the exit happens on both mark paths")
    }

    // ── autoplay-cancel wiring ─────────────────────────────────────────────

    @Test
    fun `cancelAutoplay flips the decision clock and the overlay mirror`() = runTest(dispatcher) {
        val c = controller()

        c.cancelAutoplay()

        assertEquals(1, cancelDecisionCalls, "the autoplay decision clock is cancelled first")
        assertTrue(autoplayCancelledMirror, "the Up Next overlay's cancelled mirror follows")
    }

    // ── smart-download cleanup ─────────────────────────────────────────────

    @Test
    fun `smartDownloadCleanup deletes the download and surfaces the deletion`() = runTest(dispatcher) {
        smartDownloadsEnabled = true
        durationMs = 6 * 60 * 1000L
        deleteResult = true
        val c = controller()

        c.handleSmartDownloadCleanup("ep2")
        advanceUntilIdle()

        assertEquals(1, deleteAttempts)
        assertEquals(1, deletedNotices, "the destructive action is surfaced, not silent")
    }

    @Test
    fun `smartDownloadCleanup is gated by the preference and the duration floor`() = runTest(dispatcher) {
        val c = controller()

        // Gate 1: the pref is off.
        smartDownloadsEnabled = false
        durationMs = 60 * 60 * 1000L
        deleteResult = true
        c.handleSmartDownloadCleanup("ep2")
        advanceUntilIdle()
        assertEquals(0, deleteAttempts, "the pref gates the cleanup entirely")

        // Gate 2: a too-short resolved duration (live stream / buggy container).
        smartDownloadsEnabled = true
        durationMs = 4 * 60 * 1000L
        c.handleSmartDownloadCleanup("ep2")
        advanceUntilIdle()
        assertEquals(0, deleteAttempts, "durations under the floor never delete")

        // The delete itself failing never notifies.
        durationMs = 60 * 60 * 1000L
        deleteResult = false
        c.handleSmartDownloadCleanup("ep2")
        advanceUntilIdle()
        assertEquals(1, deleteAttempts)
        assertEquals(0, deletedNotices, "a failed delete is not announced as deleted")
    }
}
