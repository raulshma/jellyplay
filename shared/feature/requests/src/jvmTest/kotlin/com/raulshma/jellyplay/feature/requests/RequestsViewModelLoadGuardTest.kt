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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Load-pipeline guard gaps in [RequestsViewModel] NOT pinned by
 * [RequestsViewModelTest] / [RequestsViewModelQueryGapsTest]:
 *
 * 1. The `isLoading` re-entrancy guard: a [RequestsViewModel.loadRequests]
 *    call while another load is in flight returns immediately — one repository
 *    query per settle, and the skipped caller never flips the loading state.
 * 2. The mediaInfo already-seen guard in enrichment: once a tmdbId's details
 *    are in [RequestsUiState.mediaInfo], a subsequent list load must not
 *    re-fetch them (the *arr download-progress enrichment deliberately has no
 *    such guard — pinned the other way in the main suite).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelLoadGuardTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (RequestsViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore

    /** getRequests invocation count, incremented inside the stub. */
    private val requestCalls = mutableListOf<Int>()

    /** getMovieDetails invocation count, incremented inside the stub. */
    private val movieDetailCalls = mutableListOf<Int>()

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
        coEvery {
            seerrRepository.getMovieDetails(any())
        } coAnswers {
            movieDetailCalls += firstArg<Int>()
            Result.success(SeerrMovieDetails(title = "Movie 42"))
        }
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(SeerrTvDetails())
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            requestCalls += 1
            Result.success(
                SeerrRequestListResponse(
                    pageInfo = SeerrPageInfo(pages = 1, results = 1),
                    results = listOf(item(1, tmdbId = 42)),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: Int, tmdbId: Int = id, type: String = "movie") = SeerrRequestItem(
        id = id,
        type = type,
        media = SeerrRequestMedia(id = id, tmdbId = tmdbId),
    )

    private fun newViewModel(): RequestsViewModel = RequestsViewModel(
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        experimentalStore = experimentalStore,
    )

    @Test
    fun loadRequests_whileALoadIsInFlight_isSkippedByTheLoadingGuard() = runTest(mainDispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            requestCalls += 1
            gate.await()
            Result.success(SeerrRequestListResponse())
        }

        val viewModel = newViewModel()
        advanceUntilIdle() // init load suspends on the gate; the debounce echo hits the guard

        viewModel.loadRequests(refresh = true)
        advanceUntilIdle()

        // Only the init load ever queried; the overlapping call was dropped.
        assertEquals(1, requestCalls.size)
        // The loading flag still belongs to the in-flight init load — the
        // skipped caller neither clobbered it nor reset it.
        assertTrue(viewModel.state.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)

        // A post-settle load runs normally again.
        viewModel.loadRequests(refresh = true)
        advanceUntilIdle()
        assertEquals(2, requestCalls.size)
    }

    @Test
    fun enrichRequests_skipsAlreadyEnrichedTmdbIds_onSubsequentLoads() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        // Init load + debounce echo both ran; only the FIRST one fetched the
        // details — by the time the echo's enrichment checks mediaInfo, the
        // initial enrichment has already landed (single-threaded test
        // dispatcher, non-suspending stubs).
        assertEquals(1, movieDetailCalls.size)
        assertNotNull(viewModel.state.value.mediaInfo[42])

        viewModel.loadRequests(refresh = true)
        advanceUntilIdle()

        // The seen-guard: mediaInfo already holds tmdb 42 → no re-fetch.
        assertEquals(1, movieDetailCalls.size)
        assertNotNull(viewModel.state.value.mediaInfo[42])
    }
}
