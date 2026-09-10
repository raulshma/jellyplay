package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [EpisodeNavigator] through its interface — the choreography formerly
 * inline in VideoPlayerViewModel (unreachable by any test): the #146
 * single-flight latch, mark-played-on-advance, SyncPlay queue routing, the
 * failed-resolution message, and the browsing/adjacency slice writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeNavigatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val catalogue: EpisodeCatalogue = mockk(relaxed = true)

    private lateinit var sessionState: MutableStateFlow<PlayerSessionState>
    private val sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    private lateinit var episodes: EpisodeBrowserState

    private var detail: MediaDetail? = seriesDetail()
    private var reportedErrors = 0
    private var advanceCalls = mutableListOf<String>()
    private var syncPlayNextRouted: MutableList<String> = mutableListOf()
    private var syncPlayPreviousRouted: MutableList<String> = mutableListOf()
    private var initialized = mutableListOf<Pair<String, Long>>()

    private fun seriesDetail(): MediaDetail = MediaDetail(
        item = MediaItem(
            id = "ep2",
            name = "S1E2",
            mediaType = MediaType.EPISODE,
            seriesId = "series-1",
            seasonId = "season-1",
        ),
    )

    private fun episode(id: String, positionTicks: Long? = null) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        playbackPositionTicks = positionTicks,
    )

    private fun navigator(scope: CoroutineScope): EpisodeNavigator = EpisodeNavigator(
        scope = scope,
        sessionState = sessionState,
        sessionEvents = sessionEvents,
        getDetail = { detail },
        getSeriesId = { detail?.item?.seriesId },
        episodeCatalogue = catalogue,
        trySyncPlayNext = { id -> syncPlayNextRouted.add(id); false },
        trySyncPlayPrevious = { id -> syncPlayPreviousRouted.add(id); false },
        onAdvanceFrom = { advanceCalls.add(it) },
        reportLoadError = { reportedErrors++ },
        initializeItem = { id, ticks -> initialized.add(id to ticks) },
        updateEpisodes = { update -> episodes = update(episodes) },
    )

    private fun stubSeason(vararg items: MediaItem) {
        coEvery { catalogue.loadSeasonEpisodes("series-1", "season-1", any()) } returns Result.success(items.toList())
    }

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

    @Test
    fun `next resolves the sibling, marks the current item played and initializes from zero`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val nav = navigator(this)

        nav.next()
        // Complete the settle wait by binding the new item.
        sessionState.value = sessionState.value.copy(currentItemId = "ep3")
        advanceUntilIdle()

        assertEquals(listOf("ep2"), advanceCalls)
        assertEquals(listOf("ep3" to 0L), initialized)
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next at season end is a no-op that never marks played`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"))
        val nav = navigator(this)

        nav.next()
        advanceUntilIdle()

        assertTrue(advanceCalls.isEmpty())
        assertTrue(initialized.isEmpty())
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next holds the latch until the session settles on a non-null sibling`() = runTest(dispatcher) {
        stubSeason(episode("ep2"), episode("ep3"))
        val nav = navigator(this)

        nav.next()
        // runCurrent (not advanceUntilIdle): advanceUntilIdle would fast-forward
        // virtual time past the settle timeout, which is itself under test.
        runCurrent()
        // initialize() returned but the session has not rebound: the transient
        // null-item reset must NOT release the latch (#146).
        sessionState.value = sessionState.value.copy(currentItemId = null)
        runCurrent()
        assertTrue(nav.isNextEpisodeLoading.value)
        // A second tap inside the window is ignored — single-flight.
        initialized.clear()
        nav.next()
        runCurrent()
        assertTrue(initialized.isEmpty())

        sessionState.value = sessionState.value.copy(currentItemId = "ep3")
        runCurrent()
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next latch releases on session error`() = runTest(dispatcher) {
        stubSeason(episode("ep2"), episode("ep3"))
        val nav = navigator(this)

        nav.next()
        runCurrent()
        assertTrue(nav.isNextEpisodeLoading.value)
        sessionEvents.tryEmit(SessionEvent.ShowError("load failed", retryable = false))
        runCurrent()
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next releases the latch on settle timeout`() = runTest(dispatcher) {
        stubSeason(episode("ep2"), episode("ep3"))
        val nav = navigator(this)

        nav.next()
        runCurrent()
        assertTrue(nav.isNextEpisodeLoading.value)
        advanceTimeBy(NEXT_EPISODE_SETTLE_TIMEOUT_MS + 1_000)
        runCurrent()
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next on a failed season resolution reports the error and stays idle`() = runTest(dispatcher) {
        coEvery { catalogue.loadSeasonEpisodes(any(), any(), any()) } returns Result.failure(RuntimeException("offline timeout"))
        val nav = navigator(this)

        nav.next()
        advanceUntilIdle()

        assertEquals(1, reportedErrors)
        assertTrue(initialized.isEmpty())
        assertTrue(advanceCalls.isEmpty())
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `next routes through the SyncPlay queue when the sibling is queued`() = runTest(dispatcher) {
        stubSeason(episode("ep2"), episode("ep3"))
        val nav = EpisodeNavigator(
            scope = this,
            sessionState = sessionState,
            sessionEvents = sessionEvents,
            getDetail = { detail },
            getSeriesId = { detail?.item?.seriesId },
            episodeCatalogue = catalogue,
            trySyncPlayNext = { id ->
                syncPlayNextRouted.add(id)
                id == "ep3"
            },
            trySyncPlayPrevious = { false },
            onAdvanceFrom = { advanceCalls.add(it) },
            reportLoadError = { reportedErrors++ },
            initializeItem = { id, ticks -> initialized.add(id to ticks) },
            updateEpisodes = { update -> episodes = update(episodes) },
        )

        nav.next()
        advanceUntilIdle()

        // Routed through the group; no local initialize; still marks played;
        // latch released (group advance settles on its own).
        assertEquals(listOf("ep3"), syncPlayNextRouted)
        assertTrue(initialized.isEmpty())
        assertEquals(listOf("ep2"), advanceCalls)
        assertFalse(nav.isNextEpisodeLoading.value)
    }

    @Test
    fun `previous resumes from the sibling's saved position`() = runTest(dispatcher) {
        stubSeason(episode("ep1", positionTicks = 250L), episode("ep2"))
        val nav = navigator(this)

        nav.previous()
        advanceUntilIdle()

        assertEquals(listOf("ep1" to 250L), initialized)
    }

    @Test
    fun `previous at season start is a no-op`() = runTest(dispatcher) {
        detail = MediaDetail(
            item = MediaItem(
                id = "ep1",
                name = "S1E1",
                mediaType = MediaType.EPISODE,
                seriesId = "series-1",
                seasonId = "season-1",
            ),
        )
        sessionState.value = PlayerSessionState(currentItemId = "ep1")
        stubSeason(episode("ep1"), episode("ep2"))
        val nav = navigator(this)

        nav.previous()
        advanceUntilIdle()

        assertTrue(initialized.isEmpty())
    }

    @Test
    fun `previous routes through the SyncPlay queue when the sibling is queued`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"))
        val nav = EpisodeNavigator(
            scope = this,
            sessionState = sessionState,
            sessionEvents = sessionEvents,
            getDetail = { detail },
            getSeriesId = { detail?.item?.seriesId },
            episodeCatalogue = catalogue,
            trySyncPlayNext = { false },
            trySyncPlayPrevious = { id ->
                syncPlayPreviousRouted.add(id)
                id == "ep1"
            },
            onAdvanceFrom = { },
            reportLoadError = { },
            initializeItem = { id, ticks -> initialized.add(id to ticks) },
            updateEpisodes = { update -> episodes = update(episodes) },
        )

        nav.previous()
        advanceUntilIdle()

        assertEquals(listOf("ep1"), syncPlayPreviousRouted)
        assertTrue(initialized.isEmpty())
    }

    @Test
    fun `refreshAdjacent writes the next and previous siblings`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"), episode("ep3"))
        val nav = navigator(this)

        nav.refreshAdjacent(detail!!)
        advanceUntilIdle()

        assertEquals("ep3", episodes.nextEpisode?.id)
        assertEquals("ep1", episodes.previousEpisode?.id)
    }

    @Test
    fun `loadSeason publishes the resolved list and clears the loading flag`() = runTest(dispatcher) {
        stubSeason(episode("ep1"), episode("ep2"))
        val nav = navigator(this)

        nav.loadSeason("season-1")
        advanceUntilIdle()

        assertEquals(listOf("ep1", "ep2"), episodes.seasonEpisodes.map { it.id })
        assertEquals("season-1", episodes.currentSeasonId)
        assertFalse(episodes.isLoadingEpisodes)
    }

    @Test
    fun `loadSeries publishes seasons then the current season's episodes`() = runTest(dispatcher) {
        coEvery { catalogue.loadSeriesEpisodes("series-1", any()) } returns Result.success(
            EpisodeCatalogueSnapshot.empty("series-1").copy(
                seasons = listOf(episode("season-1"), episode("season-2")),
            ),
        )
        stubSeason(episode("ep1"), episode("ep2"))
        val nav = navigator(this)

        nav.loadSeries(detail!!)
        advanceUntilIdle()

        assertEquals(listOf("season-1", "season-2"), episodes.seriesSeasons.map { it.id })
        assertEquals("season-1", episodes.currentSeasonId)
        assertEquals(listOf("ep1", "ep2"), episodes.seasonEpisodes.map { it.id })
    }

    @Test
    fun `non-series details are ignored`() = runTest(dispatcher) {
        detail = MediaDetail(item = MediaItem(id = "m1", name = "A Movie", mediaType = MediaType.MOVIE))
        val nav = navigator(this)

        nav.next()
        nav.previous()
        nav.refreshAdjacent(detail!!)
        nav.loadSeries(detail!!)
        advanceUntilIdle()

        assertTrue(initialized.isEmpty())
        assertNull(episodes.nextEpisode)
    }
}
