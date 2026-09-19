package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPageInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestMedia
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The append-page interleaving pins for [RequestsViewModel] (the
 * `PageAppender` site tests): the fetch's `skip` is the page-index math,
 * a Next tap landing mid-flight is suppressed ENTIRELY (declared delta —
 * the page field no longer bumps ahead of the suppressed fetch), a
 * suppressed tap cannot reorder onto a completed load, and the pager is
 * terminal at `totalPages`. The plain `isLoading` re-entrancy guard is
 * pinned by [RequestsViewModelLoadGuardTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelPagingInterleaveTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (RequestsViewModelLoadGuardTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore

    /** Every getRequests() skip, most-recent-last. */
    private val requestSkips = mutableListOf<Int>()

    /** Non-null while the in-flight getRequests() call should park on it. */
    private var gate: CompletableDeferred<Unit>? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        seerrRepository = mockk()
        arrRepository = mockk()
        experimentalStore = mockk()
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { seerrRepository.currentUser } returns MutableStateFlow(null)
        every { seerrRepository.isAdmin() } returns MutableStateFlow(false)
        every { seerrRepository.pendingRequestCount } returns MutableStateFlow(0)
        every { seerrRepository.startPolling() } just Runs
        every { seerrRepository.stopPolling() } just Runs
        coEvery { seerrRepository.getRequestCount() } returns Result.success(SeerrRequestCount(pending = 1))
        coEvery { seerrRepository.getMovieDetails(any()) } returns Result.success(SeerrMovieDetails())
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(SeerrTvDetails())
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val skip: Int = arg(1)
            requestSkips += skip
            gate?.await()
            // Item ids encode the page (skip / pageSize + 1) so each landing
            // payload is identifiable.
            Result.success(
                SeerrRequestListResponse(
                    pageInfo = SeerrPageInfo(pages = 3, results = 1),
                    results = listOf(item(skip / 10 + 1)),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: Int) = SeerrRequestItem(
        id = id,
        type = "movie",
        media = SeerrRequestMedia(id = id, tmdbId = id),
    )

    private fun newViewModel(): RequestsViewModel = RequestsViewModel(
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        experimentalStore = experimentalStore,
    )

    @Test
    fun a_nextPage_tap_landing_mid_flight_is_suppressed_entirely() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle() // init load + the debounce echo: both skip 0
        assertEquals(listOf(0, 0), requestSkips)

        gate = CompletableDeferred()
        viewModel.nextPage()
        advanceUntilIdle() // the page-2 fetch parks on the gate
        assertTrue(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(listOf(0, 0, 10), requestSkips)

        // Double-fire suppression: the tap neither bumps the page (the
        // declared delta — the field used to advance past the suppressed
        // fetch) nor issues a second query.
        viewModel.nextPage()
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(listOf(0, 0, 10), requestSkips)

        gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(listOf(2), viewModel.state.value.requests.map { it.id })
    }

    @Test
    fun a_suppressed_tap_cannot_reorder_onto_a_completed_load() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        gate = CompletableDeferred()
        viewModel.nextPage() // page-2 fetch parks on the gate
        advanceUntilIdle() // the fetch is in flight: isLoading guards the tap below
        viewModel.nextPage() // suppressed mid-flight
        gate!!.complete(Unit)
        advanceUntilIdle() // the page-2 fetch completes AFTER the suppressed tap

        // The completed page-2 load owns the state; the suppressed tap left
        // no deferred page bump behind.
        assertEquals(listOf(0, 0, 10), requestSkips)
        assertEquals(2, viewModel.state.value.currentPage)
        assertEquals(listOf(2), viewModel.state.value.requests.map { it.id })
        assertFalse(viewModel.state.value.isLoading)

        // The next tap lands on the settled state and fetches page 3.
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(listOf(0, 0, 10, 20), requestSkips)
        assertEquals(3, viewModel.state.value.currentPage)
        assertEquals(listOf(3), viewModel.state.value.requests.map { it.id })
    }

    @Test
    fun a_nextPage_at_the_last_page_is_terminal() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.nextPage()
        advanceUntilIdle()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.currentPage)
        val callsAtLastPage = requestSkips.size

        viewModel.nextPage()
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.currentPage)
        assertEquals(callsAtLastPage, requestSkips.size)
        assertFalse(viewModel.state.value.isLoading)
    }
}
