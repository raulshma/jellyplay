package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertTrue

/**
 * Tests for [EpisodeContinuationController] — the episode-continuation
 * cluster extracted from the ViewModel (the navigator stack, the
 * mark-watched/unwatched overflow orchestration, the autoplay-cancel wiring,
 * the Up Next loading flag and the smart-download cleanup). Drives it through
 * its constructor lambdas faked here (the [SubtitleStyleControllerTest]
 * shape); the navigator inside is exercised through the controller's own
 * verbs. No ViewModel, no uiState.
 *
 * Pins the load-bearing invariants: the mark arms mirror the watched-threshold
 * callback's two paths (server vs offline-local, incognito-gated); the
 * watched-and-skip advance reuses the navigator (single-flight latch +
 * SyncPlay routing intact); cancel flips the autoplay clock and the overlay
 * mirror; the smart-download cleanup is gated by the pref and the duration
 * floor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeContinuationControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val catalogue: EpisodeCatalogue = mockk(relaxed = true)

    private lateinit var sessionState: MutableStateFlow<PlayerSessionState>
    private val sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private lateinit var episodes: EpisodeBrowserState

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

    private lateinit var controller: EpisodeContinuationController

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

    private fun controller(scope: CoroutineScope): EpisodeContinuationController =
        EpisodeContinuationController(
            scope = scope,
            sessionState = sessionState,
            sessionEvents = sessionEvents,
            episodeCatalogue = catalogue,
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
        Dispatchers.resetMain()
    }

    // ── next-episode advance + overlay flag ────────────────────────────────

    @Test
    fun `playNextEpisode advances to the sibling and settles the overlay flag`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val c = controller(this)

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
        val c = controller(this)

        c.playNextEpisode()
        // runCurrent (not advanceUntilIdle): the settle timeout is under test.
        runCurrent()
        assertTrue(c.isNextEpisodeLoading.value, "the latch holds while the load is in flight")
        // Single-flight (#146): a re-tap inside the window starts nothing.
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
            val c = controller(this)

            c.playNextEpisode()
            advanceUntilIdle()

            assertEquals(listOf("pl-3"), sentNext, "the advance goes through the group command")
            assertTrue(initialized.isEmpty(), "no local reload when the group holds the sibling")
            assertFalse(c.isNextEpisodeLoading.value)
        }

    // ── mark watched & skip ────────────────────────────────────────────────

    @Test
    fun `markWatchedAndSkip marks played server-side and advances to the next episode`() =
        runTest(dispatcher) {
            stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
            hasNext = true
            val c = controller(this)

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
            val c = controller(this)

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
            val c = controller(this)

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
        val c = controller(this)

        c.markUnwatchedAndQuit()
        runCurrent()

        assertEquals(listOf("ep2"), markedUnwatched)
        assertEquals(1, closeRequests)
        assertTrue(markedPlayed.isEmpty())
    }

    @Test
    fun `markUnwatchedAndQuit in incognito is a no-op mark that still closes`() = runTest(dispatcher) {
        incognito = true
        val c = controller(this)

        c.markUnwatchedAndQuit()
        runCurrent()

        assertTrue(markedUnwatched.isEmpty(), "incognito leaves no watch state behind")
        assertEquals(1, closeRequests, "the exit happens on both mark paths")
    }

    // ── autoplay-cancel wiring ─────────────────────────────────────────────

    @Test
    fun `cancelAutoplay flips the decision clock and the overlay mirror`() = runTest(dispatcher) {
        val c = controller(this)

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
        val c = controller(this)

        c.handleSmartDownloadCleanup("ep2")
        advanceUntilIdle()

        assertEquals(1, deleteAttempts)
        assertEquals(1, deletedNotices, "the destructive action is surfaced, not silent")
    }

    @Test
    fun `smartDownloadCleanup is gated by the preference and the duration floor`() = runTest(dispatcher) {
        val c = controller(this)

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
