package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPageInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestMedia
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Requests ViewModel coverage (downloads/syncplay conveyor test style, no
 * legacy suite existed): init polling + first-page load, the no-pending
 * filter fallback, filter/sort/pagination state transitions, the 400 ms
 * debounced search (incl. the init echo of the empty query), bulk-selection
 * fan-out, the DIRECT_ARR_INTEGRATION gate over the *arr enrichment, queue
 * removal, and polling stop on clear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (downloads/music/livetv conveyor port
    // pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore

    /** Backing flow behind ExperimentalStore.experimental (the flag gate). */
    private lateinit var experimentalSlice: MutableStateFlow<ExperimentalSlice>

    /** Every getRequests() invocation, most-recent-last, for param assertions. */
    private val requestCalls = mutableListOf<SeerrCall>()

    private data class SeerrCall(
        val take: Int,
        val skip: Int,
        val filter: String,
        val sort: String,
        val sortDirection: String,
        val requestedBy: Int?,
        val mediaType: String?,
        val search: String?,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        seerrRepository = mockk()
        arrRepository = mockk()
        experimentalStore = mockk()
        experimentalSlice = MutableStateFlow(ExperimentalSlice())
        every { experimentalStore.experimental } returns experimentalSlice
        every { seerrRepository.currentUser } returns MutableStateFlow(null)
        every { seerrRepository.isAdmin() } returns MutableStateFlow(false)
        every { seerrRepository.pendingRequestCount } returns MutableStateFlow(0)
        every { seerrRepository.startPolling() } just Runs
        every { seerrRepository.stopPolling() } just Runs
        coEvery { seerrRepository.getRequestCount() } returns Result.success(SeerrRequestCount(pending = 1))
        coEvery { seerrRepository.getMovieDetails(any()) } returns Result.success(SeerrMovieDetails())
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(SeerrTvDetails())
        stubRequests { Result.success(SeerrRequestListResponse()) }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Routes every getRequests call into [requestCalls] and answers [response]. */
    private fun stubRequests(response: () -> Result<SeerrRequestListResponse>) {
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            requestCalls += SeerrCall(
                take = arg(0),
                skip = arg(1),
                filter = arg(2),
                sort = arg(3),
                sortDirection = arg(4),
                requestedBy = arg(5),
                mediaType = arg(6),
                search = arg(7),
            )
            response()
        }
    }

    private fun newViewModel(): RequestsViewModel = RequestsViewModel(
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        experimentalStore = experimentalStore,
    )

    private fun page(items: List<SeerrRequestItem>, pages: Int = 1) =
        SeerrRequestListResponse(
            pageInfo = SeerrPageInfo(pages = pages, results = items.size),
            results = items,
        )

    private fun item(id: Int, tmdbId: Int = id, type: String = "movie") =
        SeerrRequestItem(
            id = id,
            type = type,
            media = SeerrRequestMedia(id = id, tmdbId = tmdbId),
        )

    // ── init: polling + first load ────────────────────────────────────────

    @Test
    fun init_starts_polling_and_loads_first_page() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1)))) }

        val viewModel = newViewModel()
        advanceUntilIdle()

        verify(exactly = 1) { seerrRepository.startPolling() }
        verify(exactly = 0) { seerrRepository.stopPolling() }
        val state = viewModel.state.value
        assertEquals(listOf(1), state.requests.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.error)
        // First query is the pending filter, page 1 → skip 0.
        assertEquals("pending", requestCalls.first().filter)
        assertEquals(0, requestCalls.first().skip)
        assertEquals(10, requestCalls.first().take)
    }

    @Test
    fun init_without_pending_requests_falls_back_to_all_filter() = runTest(mainDispatcher) {
        coEvery { seerrRepository.getRequestCount() } returns Result.success(SeerrRequestCount(pending = 0))
        stubRequests { Result.success(page(listOf(item(1)))) }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(SeerrRequestFilter.ALL, viewModel.state.value.filter)
        // The count check precedes the load, so the very first query is "all".
        assertEquals("all", requestCalls.first().filter)
    }

    @Test
    fun init_count_failure_keeps_pending_filter_but_still_loads() = runTest(mainDispatcher) {
        coEvery { seerrRepository.getRequestCount() } returns Result.failure(RuntimeException("offline"))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(SeerrRequestFilter.PENDING, viewModel.state.value.filter)
        assertEquals("pending", requestCalls.first().filter)
    }

    @Test
    fun init_load_failure_surfaces_error_message() = runTest(mainDispatcher) {
        stubRequests { Result.failure(RuntimeException("offline")) }

        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("offline", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.requests.isEmpty())
    }

    // ── debounced search ──────────────────────────────────────────────────

    @Test
    fun search_debounce_coalesces_keystrokes_into_one_query() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        // Init load + the debounce collector's initial echo of the empty query.
        assertEquals(2, requestCalls.size)
        assertNull(requestCalls.last().search)

        viewModel.setSearchQuery("ali")
        viewModel.setSearchQuery("aliens")
        // snapshotFlow only re-emits on a global-snapshot apply — flush the
        // compose writes so the collector sees them (on Android the UI
        // dispatcher does this; jvmTest must do it by hand).
        Snapshot.sendApplyNotifications()
        advanceTimeBy(399)
        // Still inside the debounce window → no extra query fired.
        assertEquals(2, requestCalls.size)

        advanceUntilIdle()

        assertEquals(3, requestCalls.size)
        assertEquals("aliens", requestCalls.last().search)
        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun clearSearch_fires_an_immediate_unfiltered_query() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("aliens")
        Snapshot.sendApplyNotifications()
        viewModel.clearSearch()
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.searchQuery)
        assertNull(requestCalls.last().search)
    }

    // ── filter / sort / pagination transitions ────────────────────────────

    @Test
    fun setFilter_resets_page_and_forwards_filter_value() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 2)) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setFilter(SeerrRequestFilter.APPROVED)
        advanceUntilIdle()

        assertEquals(SeerrRequestFilter.APPROVED, viewModel.state.value.filter)
        assertEquals("approved", requestCalls.last().filter)
        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun setSort_and_direction_toggle_forward_new_params() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 2)) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSort(SeerrRequestSort.MODIFIED)
        advanceUntilIdle()
        assertEquals("modified", requestCalls.last().sort)
        assertEquals("desc", requestCalls.last().sortDirection)

        viewModel.toggleSortDirection()
        advanceUntilIdle()
        assertEquals("asc", viewModel.state.value.sortDirection)
        assertEquals("asc", requestCalls.last().sortDirection)
        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun setMediaType_and_my_requests_only_forward_to_query() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 2)) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setMediaType("tv")
        advanceUntilIdle()
        assertEquals("tv", requestCalls.last().mediaType)

        // No logged-in user → requestedBy stays null even with the toggle on.
        viewModel.toggleMyRequestsOnly()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showMyRequestsOnly)
        assertNull(requestCalls.last().requestedBy)
    }

    @Test
    fun nextPage_increments_page_computes_skip_and_clamps_at_last_page() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 3)) }
        val viewModel = newViewModel()
        advanceUntilIdle()
        val callsAfterInit = requestCalls.size

        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(10, requestCalls.last().skip)

        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.currentPage)
        assertEquals(20, requestCalls.last().skip)

        viewModel.nextPage()
        advanceUntilIdle()
        // Clamped at totalPages — no extra query fired.
        assertEquals(3, viewModel.state.value.currentPage)
        assertEquals(callsAfterInit + 2, requestCalls.size)
    }

    @Test
    fun prevPage_decrements_page_and_floors_at_one() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 3)) }
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.nextPage()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.currentPage)

        viewModel.prevPage()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(10, requestCalls.last().skip)

        viewModel.prevPage()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentPage)

        val callsBeforeClamp = requestCalls.size
        viewModel.prevPage()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentPage)
        assertEquals(callsBeforeClamp, requestCalls.size)
    }

    // ── bulk selection ────────────────────────────────────────────────────

    @Test
    fun toggleSelection_selectAll_and_clear_drive_selection_state() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1), item(2)))) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(item(1))
        assertEquals(setOf(1), viewModel.state.value.selectedRequestIds)
        assertTrue(viewModel.state.value.selectionMode)

        viewModel.toggleSelection(item(1))
        assertEquals(emptySet(), viewModel.state.value.selectedRequestIds)
        assertFalse(viewModel.state.value.selectionMode)

        viewModel.selectAll()
        assertEquals(setOf(1, 2), viewModel.state.value.selectedRequestIds)

        viewModel.clearSelection()
        assertEquals(emptySet(), viewModel.state.value.selectedRequestIds)
        assertFalse(viewModel.state.value.selectionMode)
    }

    @Test
    fun approveSelected_fans_out_clears_selection_and_refreshes() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1), item(2)))) }
        coEvery { seerrRepository.approveRequest(any()) } returns Result.success(item(1))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.approveSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrRepository.approveRequest(1) }
        coVerify(exactly = 1) { seerrRepository.approveRequest(2) }
        val state = viewModel.state.value
        assertFalse(state.selectionMode)
        assertEquals(emptySet(), state.selectedRequestIds)
        assertFalse(state.actionInProgress)
        // The post-bulk refresh fired (init + debounce echo + refresh).
        assertTrue(requestCalls.size >= 3)
    }

    @Test
    fun declineSelected_fans_out_to_every_selected_id() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1), item(2)))) }
        coEvery { seerrRepository.declineRequest(any()) } returns Result.success(item(1))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.declineSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrRepository.declineRequest(1) }
        coVerify(exactly = 1) { seerrRepository.declineRequest(2) }
        assertFalse(viewModel.state.value.selectionMode)
    }

    @Test
    fun approveSelected_without_selection_is_a_noop() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val callsAfterInit = requestCalls.size

        viewModel.approveSelected()
        advanceUntilIdle()

        coVerify(exactly = 0) { seerrRepository.approveRequest(any()) }
        assertEquals(callsAfterInit, requestCalls.size)
    }

    // ── single-request actions ────────────────────────────────────────────

    @Test
    fun approveRequest_failure_sets_actionError_then_clears() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1)))) }
        coEvery { seerrRepository.approveRequest(1) } returns Result.failure(RuntimeException("nope"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.approveRequest(1)
        advanceUntilIdle()

        assertEquals("nope", viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.actionInProgress)

        viewModel.clearActionError()
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun removeFromService_forwards_media_id_and_4k_flag() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1)))) }
        coEvery { seerrRepository.deleteMedia(any(), any()) } returns Result.success(Unit)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.removeFromService(mediaId = 7, is4k = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrRepository.deleteMedia(7, true) }
        assertFalse(viewModel.state.value.actionInProgress)
    }

    // ── media enrichment (Semaphore(4)) ───────────────────────────────────

    @Test
    fun enrichRequests_populates_media_info_for_movies_and_tv() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 11), item(2, tmdbId = 30, type = "tv")))) }
        coEvery { seerrRepository.getMovieDetails(11) } returns Result.success(
            SeerrMovieDetails(title = "Movie", overview = "A movie", releaseDate = "2024-05-05"),
        )
        coEvery { seerrRepository.getTvDetails(30) } returns Result.success(
            SeerrTvDetails(name = "Show", overview = "A show", firstAirDate = "2019-01-01"),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val movie = viewModel.state.value.mediaInfo[11]
        assertNotNull(movie)
        assertEquals("Movie", movie.title)
        assertEquals(2024, movie.year)
        val tv = viewModel.state.value.mediaInfo[30]
        assertNotNull(tv)
        assertEquals("Show", tv.title)
        assertEquals(2019, tv.year)
    }

    @Test
    fun enrichRequests_recycles_semaphore_permits_beyond_four_concurrent_items() = runTest(mainDispatcher) {
        val items = (1..6).map { item(it, tmdbId = 100 + it) }
        stubRequests { Result.success(page(items)) }
        coEvery { seerrRepository.getMovieDetails(any()) } answers {
            Result.success(SeerrMovieDetails(title = "T${firstArg<Int>()}"))
        }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(6, viewModel.state.value.mediaInfo.size)
        assertEquals("T101", viewModel.state.value.mediaInfo[101]?.title)
        assertEquals("T106", viewModel.state.value.mediaInfo[106]?.title)
    }

    // ── DIRECT_ARR_INTEGRATION gating + queue management ──────────────────

    @Test
    fun direct_arr_flag_off_skips_queue_enrichment_entirely() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 42)))) }
        coEvery { arrRepository.getQueueForTmdb(any()) } returns null

        val viewModel = newViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.getQueueForTmdb(any()) }
        assertTrue(viewModel.state.value.downloadProgress.isEmpty())
        assertTrue(viewModel.state.value.queueItems.isEmpty())
    }

    @Test
    fun direct_arr_flag_on_enriches_download_progress_and_queue_items() = runTest(mainDispatcher) {
        experimentalSlice.value = ExperimentalSlice(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION),
        )
        // tmdbId=0 is deliberately skipped by the enrichment.
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 42), item(2, tmdbId = 0)))) }
        val queueItem = ArrQueueItem(
            queueId = 9,
            tmdbId = 42,
            title = "Movie 4K",
            status = ArrDownloadStatus.DOWNLOADING,
            progress = 0.5f,
            serverKind = ArrServiceKind.RADARR,
        )
        coEvery { arrRepository.getQueueForTmdb(42) } returns queueItem

        val viewModel = newViewModel()
        advanceUntilIdle()

        // Two loads happen (init + the initial debounce echo) and — unlike
        // mediaInfo — the *arr enrichment has no already-seen guard, so each
        // load re-queries (HEAD behavior).
        coVerify(exactly = 2) { arrRepository.getQueueForTmdb(42) }
        coVerify(exactly = 0) { arrRepository.getQueueForTmdb(0) }
        val summary = viewModel.state.value.downloadProgress[42]
        assertNotNull(summary)
        assertEquals(ArrDownloadStatus.DOWNLOADING, summary.status)
        assertEquals(50, summary.percent)
        assertSame(queueItem, viewModel.state.value.queueItems[42])
    }

    @Test
    fun removeQueueItem_success_drops_entry_and_searches_when_asked() = runTest(mainDispatcher) {
        experimentalSlice.value = ExperimentalSlice(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION),
        )
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 42)))) }
        val queueItem = ArrQueueItem(
            queueId = 9,
            tmdbId = 42,
            title = "Movie 4K",
            status = ArrDownloadStatus.QUEUED,
            serverKind = ArrServiceKind.RADARR,
        )
        coEvery { arrRepository.getQueueForTmdb(42) } returns queueItem
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.success(Unit)
        coEvery { arrRepository.searchForTmdb(any(), any()) } returns Result.success(emptyList())

        val viewModel = newViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.queueItems.containsKey(42))
        // After the deletion the queue no longer holds the row — the refresh
        // following removeQueueItem must not re-populate it.
        coEvery { arrRepository.getQueueForTmdb(42) } returns null

        viewModel.removeQueueItem(42, blocklist = true, searchAgain = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            arrRepository.deleteQueueItem(
                queueItem,
                ArrQueueDeleteOptions(removeFromClient = true, blocklist = true, skipRedownload = false),
            )
        }
        coVerify(exactly = 1) { arrRepository.searchForTmdb(42, ArrServiceKind.RADARR) }
        val state = viewModel.state.value
        assertFalse(state.queueItems.containsKey(42))
        assertFalse(state.downloadProgress.containsKey(42))
        assertFalse(state.actionInProgress)
    }

    @Test
    fun removeQueueItem_without_cached_item_is_a_noop() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.removeQueueItem(42, blocklist = false, searchAgain = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.deleteQueueItem(any(), any()) }
    }

    @Test
    fun removeQueueItem_failure_surfaces_actionError_and_keeps_cached_entry() = runTest(mainDispatcher) {
        experimentalSlice.value = ExperimentalSlice(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION),
        )
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 42)))) }
        val queueItem = ArrQueueItem(
            queueId = 9,
            tmdbId = 42,
            title = "Movie 4K",
            status = ArrDownloadStatus.QUEUED,
            serverKind = ArrServiceKind.RADARR,
        )
        coEvery { arrRepository.getQueueForTmdb(42) } returns queueItem
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.failure(RuntimeException("boom"))

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.removeQueueItem(42, blocklist = false, searchAgain = false)
        advanceUntilIdle()

        assertEquals("boom", viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.actionInProgress)
        // Failure path keeps the cached queue row for a retry.
        assertTrue(viewModel.state.value.queueItems.containsKey(42))
    }

    @Test
    fun searchAgainForTmdb_surfaces_failure_via_actionError() = runTest(mainDispatcher) {
        coEvery { arrRepository.searchForTmdb(any(), any()) } returns Result.failure(RuntimeException("no server"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.searchAgainForTmdb(42, ArrServiceKind.SONARR)
        advanceUntilIdle()

        assertEquals("no server", viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.actionInProgress)
    }

    // ── polling lifecycle ─────────────────────────────────────────────────

    @Test
    fun clearing_the_viewmodel_stops_polling() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(listOf(item(1)))) }
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                newViewModel() as T
        }
        val viewModel = ViewModelProvider.create(store, factory).get(RequestsViewModel::class)
        advanceUntilIdle()
        verify(exactly = 0) { seerrRepository.stopPolling() }

        store.clear()

        verify(exactly = 1) { seerrRepository.stopPolling() }
        assertNotNull(viewModel)
    }
}
