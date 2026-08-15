package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The single home for the search choreography tests previously split
 * (informally) across HomeViewModelTest and the SearchViewModel paths:
 * debounce timing under virtual time, cancel-and-replace on rapid queries,
 * the Seerr gate matrix, the blank-query empty state, and the history policy.
 * All engine deps are mocks; `preview` runs on the runTest scheduler — the
 * engine hard-codes no dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaSearchEngineTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)
    private val searchHistoryRepository: SearchHistoryRepository = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true)
    private val experimentalStore: ExperimentalStore = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)

    private val activeUserId = MutableStateFlow<String?>("user-1")
    private val experimental = MutableStateFlow(ExperimentalSlice())
    private val networkStatus = MutableStateFlow(NetworkStatus.Online)

    private lateinit var engine: MediaSearchEngine

    @Before
    fun setUp() {
        every { serverIdentityStore.activeUserId } returns activeUserId
        every { experimentalStore.experimental } returns experimental
        every { offlineModeManager.networkStatus } returns networkStatus
        every { seerrRepository.isConnected() } returns flowOf(true)
        every { seerrRepository.isSearchEnabled() } returns flowOf(true)
        every { searchHistoryRepository.getRecent(any(), any()) } returns flowOf(emptyList())
        stubJellyfinSearch(emptyList())
        engine = MediaSearchEngineImpl(
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            searchHistoryRepository = searchHistoryRepository,
            serverIdentityStore = serverIdentityStore,
            experimentalStore = experimentalStore,
            offlineModeManager = offlineModeManager,
        )
    }

    private fun stubJellyfinSearch(items: List<MediaItem>) {
        coEvery { mediaRepository.search(any(), limit = any()) } returns
            Result.success(SearchResult(items, items.size, 0))
    }

    private fun stubSeerrSearch(items: List<SeerrSearchItem>) {
        coEvery { seerrRepository.search(any(), any()) } returns
            Result.success(SeerrSearchResponse(results = items, totalResults = items.size))
    }

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    // ── Debounce timing ─────────────────────────────────────────────────

    @Test
    fun `preview debounces rapid queries and only searches the final one`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }

        queries.value = "bat"
        queries.value = "batm"
        queries.value = "batman"
        advanceTimeBy(301)
        runCurrent()

        coVerify(exactly = 1) { mediaRepository.search("batman", limit = any()) }
        coVerify(exactly = 0) { mediaRepository.search("bat", limit = any()) }
        coVerify(exactly = 0) { mediaRepository.search("batm", limit = any()) }
        assertEquals("batman", states.last().query)
        job.cancel()
    }

    @Test
    fun `preview waits the full debounce window before searching`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }

        queries.value = "matrix"
        advanceTimeBy(299)
        runCurrent()
        coVerify(exactly = 0) { mediaRepository.search(any(), limit = any()) }

        advanceTimeBy(2)
        runCurrent()
        coVerify(exactly = 1) { mediaRepository.search("matrix", limit = any()) }
        job.cancel()
    }

    // ── Cancel-and-replace ──────────────────────────────────────────────

    @Test
    fun `preview cancels a superseded in-flight search`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }
        // The first query's search never completes on its own; only the
        // cancel-and-replace semantics (flatMapLatest) let the pipeline move on.
        // (Registered last so mockk gives it priority over setUp's catch-all.)
        coEvery { mediaRepository.search("slow", limit = any()) } coAnswers {
            CompletableDeferred<Result<SearchResult>>().await()
        }

        queries.value = "slow"
        advanceTimeBy(301)
        runCurrent()
        queries.value = "fast"
        advanceTimeBy(301)
        runCurrent()

        // The superseded round produced only its searching state — no final
        // state for "slow" ever lands; "fast" resolves normally.
        assertTrue(states.none { it.query == "slow" && !it.isSearching })
        assertEquals("fast", states.last().query)
        assertFalse(states.last().isSearching)
        job.cancel()
    }

    // ── Blank query & emission shape ────────────────────────────────────

    @Test
    fun `blank query emits the empty state without touching the network`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }

        queries.value = "   "
        advanceTimeBy(301)
        runCurrent()

        val last = states.last()
        assertTrue(last.query.isBlank())
        assertTrue(last.jellyfin.isEmpty())
        assertTrue(last.seerr.isEmpty())
        assertFalse(last.isSearching)
        coVerify(exactly = 0) { mediaRepository.search(any(), limit = any()) }
        coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
        job.cancel()
    }

    @Test
    fun `non-blank query emits searching then both result branches`() = runTest {
        stubJellyfinSearch(listOf(item("j1")))
        stubSeerrSearch(listOf(SeerrSearchItem(id = 1, title = "S1")))
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }

        queries.value = "matrix"
        advanceTimeBy(301)
        runCurrent()

        assertEquals(
            listOf(true, false),
            states.filter { it.query == "matrix" }.map { it.isSearching },
        )
        assertEquals(listOf("j1"), states.last().jellyfin.map { it.id })
        assertEquals(listOf(1), states.last().seerr.map { it.id })
        job.cancel()
    }

    @Test
    fun `repository failures degrade to empty results instead of throwing`() = runTest {
        coEvery { mediaRepository.search(any(), limit = any()) } returns Result.failure(RuntimeException("net"))
        coEvery { seerrRepository.search(any(), any()) } returns Result.failure(RuntimeException("seerr"))
        val queries = MutableStateFlow("")
        val states = mutableListOf<MediaSearchPreviewState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(states)
        }

        queries.value = "matrix"
        advanceTimeBy(301)
        runCurrent()

        val last = states.last()
        assertTrue(last.jellyfin.isEmpty())
        assertTrue(last.seerr.isEmpty())
        assertFalse(last.isSearching)
        job.cancel()
    }

    // ── Seerr gate matrix ───────────────────────────────────────────────

    @Test
    fun `seerr branch is skipped when disconnected`() = runTest {
        every { seerrRepository.isConnected() } returns flowOf(false)
        collectOneRound("matrix")

        coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
    }

    @Test
    fun `seerr branch is skipped when search is disabled`() = runTest {
        every { seerrRepository.isSearchEnabled() } returns flowOf(false)
        collectOneRound("matrix")

        coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
    }

    @Test
    fun `seerr branch is skipped on Local network status`() = runTest {
        networkStatus.value = NetworkStatus.Local
        collectOneRound("matrix")

        // Gate closes before any connectivity round-trip: Home's conservative
        // reachability assumption, now shared by the search screen too.
        coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
    }

    @Test
    fun `isSeerrSearchAvailable is false on Local network status`() = runTest {
        networkStatus.value = NetworkStatus.Local
        assertFalse(engine.isSeerrSearchAvailable())
    }

    @Test
    fun `isSeerrSearchAvailable is true when connected and enabled`() = runTest {
        assertTrue(engine.isSeerrSearchAvailable())
    }

    private fun collectOneRound(query: String) = runTest {
        val queries = MutableStateFlow("")
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.preview(queries).toList(mutableListOf())
        }
        queries.value = query
        advanceTimeBy(301)
        runCurrent()
        job.cancel()
    }

    // ── History policy (recordHistory) ──────────────────────────────────

    @Test
    fun `recordHistory saves when results were found`() = runTest {
        engine.recordHistory("matrix", jellyfinHadResults = true)
        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("matrix", "user-1") }
    }

    @Test
    fun `recordHistory skips when jellyfin had no results`() = runTest {
        engine.recordHistory("matrix", jellyfinHadResults = false)
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `recordHistory skips queries shorter than 2 chars`() = runTest {
        // Guards against typo'd single-char queries polluting history.
        engine.recordHistory("a", jellyfinHadResults = true)
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `recordHistory honors the hide-history preference`() = runTest {
        experimental.value = ExperimentalSlice(hideSearchHistory = true)
        engine.recordHistory("matrix", jellyfinHadResults = true)
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `recordHistory skips when there is no active user`() = runTest {
        activeUserId.value = null
        engine.recordHistory("matrix", jellyfinHadResults = true)
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `preview records history when jellyfin matched`() = runTest {
        stubJellyfinSearch(listOf(item("j1")))
        collectOneRound("matrix")

        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("matrix", "user-1") }
    }

    @Test
    fun `preview does not record history when jellyfin found nothing`() = runTest {
        collectOneRound("matrix")

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    // ── History mutations ───────────────────────────────────────────────

    @Test
    fun `deleteHistoryItem delegates to the repository`() = runTest {
        engine.deleteHistoryItem(42L)
        coVerify(exactly = 1) { searchHistoryRepository.deleteById(42L) }
    }

    @Test
    fun `clearHistory clears the active user's history`() = runTest {
        engine.clearHistory()
        coVerify(exactly = 1) { searchHistoryRepository.clearAll("user-1") }
    }

    @Test
    fun `clearHistory no-ops when there is no active user`() = runTest {
        activeUserId.value = null
        engine.clearHistory()
        coVerify(exactly = 0) { searchHistoryRepository.clearAll(any()) }
    }

    // ── recentHistory ───────────────────────────────────────────────────

    @Test
    fun `recentHistory surfaces the active user's history`() = runTest {
        val history = listOf(SearchHistoryItem(id = 1L, query = "matrix", searchedAt = 0L))
        every { searchHistoryRepository.getRecent("user-1", any()) } returns flowOf(history)

        val collected = mutableListOf<List<SearchHistoryItem>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.recentHistory().toList(collected)
        }

        assertEquals(history, collected.last())
        job.cancel()
    }

    @Test
    fun `recentHistory is empty when signed out`() = runTest {
        activeUserId.value = null

        val collected = mutableListOf<List<SearchHistoryItem>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.recentHistory().toList(collected)
        }

        assertTrue(collected.last().isEmpty())
        job.cancel()
    }

    @Test
    fun `recentHistory is empty while the hide-history preference is on`() = runTest {
        val history = listOf(SearchHistoryItem(id = 1L, query = "matrix", searchedAt = 0L))
        every { searchHistoryRepository.getRecent(any(), any()) } returns flowOf(history)
        experimental.value = ExperimentalSlice(hideSearchHistory = true)

        val collected = mutableListOf<List<SearchHistoryItem>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.recentHistory().toList(collected)
        }

        // Hidden preference exposes an empty list while keeping the underlying
        // history intact (no repository mutation).
        assertTrue(collected.last().isEmpty())
        job.cancel()
    }
}
