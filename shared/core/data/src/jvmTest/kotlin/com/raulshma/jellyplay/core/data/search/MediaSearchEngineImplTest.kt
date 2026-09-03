package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.toMediaItem
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [MediaSearchEngineImpl] over mocked seams. Covers the engine
 * contract, not the transports:
 *  - blank queries short-circuit without touching any source;
 *  - an online round fans out to Jellyfin + Seerr and lands both slots;
 *  - failure tolerance: one failing source must not kill the round, and the
 *    engine itself must never throw;
 *  - the Seerr gate (connected + search-enabled + not on Local);
 *  - the history policy (result-gated, ≥2 chars, hidden-history and
 *    signed-out guards) and the offline round.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MediaSearchEngineImplTest {

    private val mediaRepository: MediaRepository = mockk()
    private val seerrRepository: SeerrRepository = mockk()
    private val searchHistoryRepository: SearchHistoryRepository = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk()
    private val experimentalStore: ExperimentalStore = mockk()
    private val offlineModeManager: OfflineModeManager = mockk()
    private val offlineRepository: OfflineRepository = mockk()

    private lateinit var engine: MediaSearchEngineImpl

    private val experimentalPrefs = MutableStateFlow(ExperimentalSlice())
    private val networkStatus = MutableStateFlow(NetworkStatus.Online)

    @BeforeTest
    fun setup() {
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        every { experimentalStore.experimental } returns experimentalPrefs
        every { offlineModeManager.isOffline } returns false
        every { offlineModeManager.networkStatus } returns networkStatus
        every { seerrRepository.isConnected() } returns flowOf(true)
        every { seerrRepository.isSearchEnabled() } returns flowOf(true)
        engine = MediaSearchEngineImpl(
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            searchHistoryRepository = searchHistoryRepository,
            serverIdentityStore = serverIdentityStore,
            experimentalStore = experimentalStore,
            offlineModeManager = offlineModeManager,
            offlineRepository = offlineRepository,
        )
    }

    private fun jellyItem(id: String) = MediaItem(id = id, name = "Jelly $id", mediaType = MediaType.MOVIE)

    private fun seerrItem(id: Int) = SeerrSearchItem(id = id, mediaType = "movie", title = "Seerr $id")

    private fun offlineItem(id: String) = OfflineMediaItem(id = id, name = "Offline $id", mediaType = MediaType.MOVIE)

    // ── Blank-query short-circuit ───────────────────────────────────────

    @Test
    fun `blank query short-circuits without touching any source`() = runTest {
        val state = engine.preview(flowOf("   ")).first()

        assertEquals("   ", state.query)
        assertFalse(state.isSearching)
        assertTrue(state.jellyfin.isEmpty())
        assertTrue(state.seerr.isEmpty())
        verify { mediaRepository wasNot Called }
        verify { seerrRepository wasNot Called }
        verify { offlineRepository wasNot Called }
        verify { searchHistoryRepository wasNot Called }
    }

    // ── Online fan-out ──────────────────────────────────────────────────

    @Test
    fun `preview fans out to jellyfin and seerr and lands both slots`() = runTest {
        coEvery { mediaRepository.search("batman", limit = 8) } returns Result.success(
            SearchResult(items = listOf(jellyItem("j1"), jellyItem("j2")), totalRecordCount = 2, startIndex = 0)
        )
        coEvery { seerrRepository.search("batman") } returns Result.success(
            SeerrSearchResponse(results = listOf(seerrItem(1), seerrItem(2), seerrItem(3)))
        )

        val state = engine.preview(flowOf("batman"), seerrLimit = 2).first { !it.isSearching }

        assertEquals("batman", state.query)
        assertEquals(listOf("j1", "j2"), state.jellyfin.map { it.id })
        // seerrLimit caps the seerr slot at 2 of the 3 results.
        assertEquals(listOf(1, 2), state.seerr.map { it.id })
        // A round with jellyfin matches records the query for the active user.
        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("batman", "user-1") }
    }

    @Test
    fun `preview emits a searching state before the completed round`() = runTest {
        coEvery { mediaRepository.search("batman", limit = 8) } returns Result.success(
            SearchResult(items = listOf(jellyItem("j1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { seerrRepository.search("batman") } returns Result.success(SeerrSearchResponse(results = emptyList()))

        val states = engine.preview(flowOf("batman")).toList()

        // The round emits the in-flight hop first, then the resolved state.
        assertEquals(2, states.size)
        val searching = states[0]
        assertTrue(searching.isSearching)
        assertTrue(searching.jellyfin.isEmpty())
        val completed = states[1]
        assertFalse(completed.isSearching)
        assertEquals(listOf("j1"), completed.jellyfin.map { it.id })
    }

    @Test
    fun `a round without jellyfin matches does not record history`() = runTest {
        coEvery { mediaRepository.search("void", limit = 8) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
        )
        coEvery { seerrRepository.search("void") } returns Result.success(SeerrSearchResponse(results = listOf(seerrItem(1))))

        val state = engine.preview(flowOf("void")).first { !it.isSearching }

        assertTrue(state.jellyfin.isEmpty())
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    // ── Error tolerance ─────────────────────────────────────────────────

    @Test
    fun `a throwing jellyfin source degrades to an empty jellyfin slot`() = runTest {
        coEvery { mediaRepository.search(any(), any(), any(), any()) } throws RuntimeException("server down")
        coEvery { seerrRepository.search("batman") } returns Result.success(SeerrSearchResponse(results = listOf(seerrItem(1))))

        val state = engine.preview(flowOf("batman")).first { !it.isSearching }

        assertTrue(state.jellyfin.isEmpty())
        assertEquals(listOf(1), state.seerr.map { it.id })
        // No jellyfin results → no history recorded.
        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `a failing seerr source degrades to an empty seerr slot`() = runTest {
        coEvery { mediaRepository.search("batman", limit = 8) } returns Result.success(
            SearchResult(items = listOf(jellyItem("j1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { seerrRepository.search("batman") } returns Result.failure(IllegalStateException("seerr down"))

        val state = engine.preview(flowOf("batman")).first { !it.isSearching }

        assertEquals(listOf("j1"), state.jellyfin.map { it.id })
        assertTrue(state.seerr.isEmpty())
        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("batman", "user-1") }
    }

    // ── Seerr gate ──────────────────────────────────────────────────────

    @Test
    fun `seerr is skipped on a Local network connection`() = runTest {
        networkStatus.value = NetworkStatus.Local
        coEvery { mediaRepository.search("batman", limit = 8) } returns Result.success(
            SearchResult(items = listOf(jellyItem("j1")), totalRecordCount = 1, startIndex = 0)
        )

        val state = engine.preview(flowOf("batman")).first { !it.isSearching }

        assertEquals(listOf("j1"), state.jellyfin.map { it.id })
        assertTrue(state.seerr.isEmpty())
        verify { seerrRepository wasNot Called }
        assertFalse(engine.isSeerrSearchAvailable())
    }

    @Test
    fun `isSeerrSearchAvailable requires connection and search enabled`() = runTest {
        assertTrue(engine.isSeerrSearchAvailable())

        every { seerrRepository.isConnected() } returns flowOf(false)
        assertFalse(engine.isSeerrSearchAvailable())

        every { seerrRepository.isConnected() } returns flowOf(true)
        every { seerrRepository.isSearchEnabled() } returns flowOf(false)
        assertFalse(engine.isSeerrSearchAvailable())
    }

    @Test
    fun `isSeerrSearchAvailable never throws`() = runTest {
        every { seerrRepository.isConnected() } throws RuntimeException("boom")

        assertFalse(engine.isSeerrSearchAvailable())
    }

    // ── Offline round ───────────────────────────────────────────────────

    @Test
    fun `offline mode queries the local library and records history`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.searchOffline("batman", 8) } returns listOf(offlineItem("o1"))

        val state = engine.preview(flowOf("batman")).first { !it.isSearching }

        // The offline item renders in the jellyfin slot via toMediaItem().
        assertEquals(listOf("o1"), state.jellyfin.map { it.id })
        assertTrue(state.seerr.isEmpty())
        verify { mediaRepository wasNot Called }
        verify { seerrRepository wasNot Called }
        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("batman", "user-1") }
    }

    // ── recordHistory policy (public seam) ──────────────────────────────

    @Test
    fun `recordHistory is result-gated and length-gated`() = runTest {
        engine.recordHistory("a", jellyfinHadResults = true) // < 2 chars
        engine.recordHistory("batman", jellyfinHadResults = false) // no results

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }

        engine.recordHistory("batman", jellyfinHadResults = true)
        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("batman", "user-1") }
    }

    @Test
    fun `recordHistory respects the hide-search-history preference`() = runTest {
        experimentalPrefs.value = ExperimentalSlice(hideSearchHistory = true)

        engine.recordHistory("batman", jellyfinHadResults = true)

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `recordHistory is a no-op when signed out`() = runTest {
        every { serverIdentityStore.activeUserId } returns flowOf(null)

        engine.recordHistory("batman", jellyfinHadResults = true)

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    // ── recentHistory / clearHistory ────────────────────────────────────

    @Test
    fun `recentHistory passes the active user's rows through when not hidden`() = runTest {
        val rows = listOf(SearchHistoryItem(id = 1L, query = "batman", searchedAt = 5L))
        every { searchHistoryRepository.getRecent("user-1") } returns flowOf(rows)

        assertEquals(rows, engine.recentHistory().first())
    }

    @Test
    fun `recentHistory hides rows while the preference is on`() = runTest {
        every { searchHistoryRepository.getRecent("user-1") } returns flowOf(
            listOf(SearchHistoryItem(id = 1L, query = "batman", searchedAt = 5L))
        )
        experimentalPrefs.value = ExperimentalSlice(hideSearchHistory = true)

        // The underlying history stays intact — the seam only exposes empty.
        assertTrue(engine.recentHistory().first().isEmpty())
    }

    @Test
    fun `clearHistory clears the active user's rows`() = runTest {
        engine.clearHistory()

        coVerify(exactly = 1) { searchHistoryRepository.clearAll("user-1") }
    }

    @Test
    fun `deleteHistoryItem delegates to the repository`() = runTest {
        engine.deleteHistoryItem(42L)

        coVerify(exactly = 1) { searchHistoryRepository.deleteById(42L) }
    }
}
