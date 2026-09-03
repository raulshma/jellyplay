package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.snapshots.Snapshot
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrPageInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestMedia
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
 * Requests ViewModel boundary gaps NOT pinned by [RequestsViewModelTest]:
 *
 * 1. `showMyRequestsOnly` WITH a logged-in user forwards that user's id as
 *    the `requestedBy` query param (the existing suite only covers the
 *    no-user → null path).
 * 2. [RequestsViewModel.clearSearch] is a no-op while the query is already
 *    blank (no reload fired) — the early-return guard.
 * 3. Filter-axis setters reset pagination to page 1 (setSort covered in the
 *    main suite; [RequestsViewModel.setMediaType] /
 *    [RequestsViewModel.toggleMyRequestsOnly] page resets pinned here).
 * 4. [RequestsUiState.filters] bundles the six filter-axis fields verbatim.
 * 5. Blank search strings are sent as null (the `takeIf { isNotBlank() }`
 *    gate) rather than empty-string params.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelQueryGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (RequestsViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var currentUserFlow: MutableStateFlow<SeerrCurrentUser?>
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
        currentUserFlow = MutableStateFlow(null)
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { seerrRepository.currentUser } returns currentUserFlow
        every { seerrRepository.isAdmin() } returns MutableStateFlow(false)
        every { seerrRepository.pendingRequestCount } returns MutableStateFlow(0)
        every { seerrRepository.startPolling() } just Runs
        every { seerrRepository.stopPolling() } just Runs
        coEvery { seerrRepository.getRequestCount() } returns Result.success(SeerrRequestCount(pending = 1))
        coEvery { seerrRepository.getMovieDetails(any()) } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails(),
        )
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails(),
        )
        stubRequests { Result.success(SeerrRequestListResponse()) }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubRequests(response: () -> Result<SeerrRequestListResponse>) {
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            requestCalls += SeerrCall(
                take = arg(0), skip = arg(1), filter = arg(2), sort = arg(3),
                sortDirection = arg(4), requestedBy = arg(5), mediaType = arg(6), search = arg(7),
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

    @Test
    fun my_requests_only_forwards_the_logged_in_user_id() = runTest(mainDispatcher) {
        currentUserFlow.value = SeerrCurrentUser(id = 7, displayName = "Requester")
        stubRequests { Result.success(page(listOf(item(1)))) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleMyRequestsOnly()
        advanceUntilIdle()

        assertEquals(7, requestCalls.last().requestedBy)
        assertTrue(viewModel.state.value.showMyRequestsOnly)
    }

    @Test
    fun clearSearch_with_a_blank_query_is_a_noop() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList())) }
        val viewModel = newViewModel()
        advanceUntilIdle()
        val callsAfterInit = requestCalls.size

        viewModel.clearSearch()
        advanceUntilIdle()

        // Early-return guard: no refresh query fired.
        assertEquals(callsAfterInit, requestCalls.size)
        assertEquals("", viewModel.state.value.searchQuery)
    }

    @Test
    fun media_type_and_my_requests_setters_reset_to_page_one() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList(), pages = 3)) }
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.currentPage)

        viewModel.setMediaType("tv")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentPage)

        viewModel.nextPage()
        advanceUntilIdle()
        viewModel.toggleMyRequestsOnly()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun blank_search_strings_travel_as_null_not_empty() = runTest(mainDispatcher) {
        stubRequests { Result.success(page(emptyList())) }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("   ")
        Snapshot.sendApplyNotifications()
        advanceTimeBy(400)
        advanceUntilIdle()

        assertNull(requestCalls.last().search)
    }

    @Test
    fun ui_state_filters_bundle_the_six_axis_fields() {
        val state = RequestsUiState(
            filter = SeerrRequestFilter.APPROVED,
            mediaType = "tv",
            sort = SeerrRequestSort.MODIFIED,
            sortDirection = "asc",
            showMyRequestsOnly = true,
            searchQuery = "dune",
        )
        assertEquals(
            RequestsFilterState(
                filter = SeerrRequestFilter.APPROVED,
                mediaType = "tv",
                sort = SeerrRequestSort.MODIFIED,
                sortDirection = "asc",
                showMyRequestsOnly = true,
                searchQuery = "dune",
            ),
            state.filters,
        )
        // Defaults for the rest of the state stay untouched by the bundling.
        assertFalse(state.selectionMode)
        assertEquals(1, state.currentPage)
    }
}
