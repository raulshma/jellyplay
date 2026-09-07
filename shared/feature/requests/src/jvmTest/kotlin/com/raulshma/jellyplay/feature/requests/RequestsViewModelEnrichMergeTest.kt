package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The enrichment fan-out core shared by media-details + *arr download-progress
 * enrichment, pins the merge contract of the single-choreography fold:
 *
 * 1. No lost updates: two completions resolving out of order BOTH land — each
 *    folds into the latest ui state (atomic snapshot merge), never over a
 *    stale copy.
 * 2. Dedupe: requests sharing a (tmdbId, type) pair fetch once — and the
 *    movie/tv namespaces collide (same numeric id, both types fetch), while
 *    once an id is cached the debounce-echo's re-enrichment re-fetches
 *    nothing.
 * 3. Per-item failures are swallowed: a failing id leaves its map untouched
 *    and never disturbs the sibling enrichment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelEnrichMergeTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (RequestsViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore

    /** Per-tmdb suspension gates + call log for getMovieDetails. */
    private val movieGates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private val movieCalls = mutableListOf<Int>()

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
        coEvery { arrRepository.getQueueForTmdb(any()) } returns null
        stubRequests { Result.success(SeerrRequestListResponse()) }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubRequests(response: () -> Result<SeerrRequestListResponse>) {
        coEvery {
            seerrRepository.getRequests(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { response() }
    }

    private fun newViewModel(): RequestsViewModel = RequestsViewModel(
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        experimentalStore = experimentalStore,
    )

    private fun page(items: List<SeerrRequestItem>) = SeerrRequestListResponse(
        pageInfo = SeerrPageInfo(pages = 1, results = items.size),
        results = items,
    )

    private fun item(id: Int, tmdbId: Int = id, type: String = "movie") = SeerrRequestItem(
        id = id,
        type = type,
        media = SeerrRequestMedia(id = id, tmdbId = tmdbId),
    )

    /** Gates getMovieDetails(tmdbId) behind a [CompletableDeferred] and logs the call. */
    private fun gateMovieDetails() {
        coEvery { seerrRepository.getMovieDetails(any()) } coAnswers {
            val tmdbId = firstArg<Int>()
            movieCalls += tmdbId
            movieGates.getOrPut(tmdbId) { CompletableDeferred() }.await()
            Result.success(SeerrMovieDetails(title = "Dune $tmdbId"))
        }
    }

    private fun queueItem(tmdbId: Int, progress: Float) = ArrQueueItem(
        queueId = tmdbId,
        tmdbId = tmdbId,
        title = "Q$tmdbId",
        status = ArrDownloadStatus.DOWNLOADING,
        progress = progress,
    )

    @Test
    fun out_of_order_enrich_completions_both_land() = runTest(mainDispatcher) {
        gateMovieDetails()
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 11), item(2, tmdbId = 22)))) }
        val viewModel = newViewModel()
        runCurrent() // fan-out: both fetches suspend on their gates

        // One fetch per distinct id.
        assertEquals(listOf(11, 22), movieCalls)

        // Resolve out of order; each completion must fold into the latest
        // state — the second must not clobber the first's map entry.
        movieGates.getValue(22).complete(Unit)
        runCurrent()
        assertEquals("Dune 22", viewModel.state.value.mediaInfo.getValue(22).title)
        movieGates.getValue(11).complete(Unit)
        advanceUntilIdle() // also runs the debounce-echo load

        assertEquals("Dune 11", viewModel.state.value.mediaInfo.getValue(11).title)
        assertEquals("Dune 22", viewModel.state.value.mediaInfo.getValue(22).title)
        // The echo's re-enrichment found both ids cached — no re-fetches.
        assertEquals(listOf(11, 22), movieCalls)
    }

    @Test
    fun duplicate_tmdb_ids_across_requests_fetch_once() = runTest(mainDispatcher) {
        coEvery { seerrRepository.getMovieDetails(any()) } coAnswers {
            movieCalls += firstArg<Int>()
            Result.success(SeerrMovieDetails())
        }
        stubRequests {
            Result.success(page(listOf(item(1, tmdbId = 10), item(2, tmdbId = 10), item(3, tmdbId = 20))))
        }
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf(10, 20), movieCalls)
        assertEquals(setOf(10, 20), viewModel.state.value.mediaInfo.keys)
    }

    @Test
    fun same_tmdb_id_across_movie_and_tv_fetches_BOTH_endpoints() = runTest(mainDispatcher) {
        // tmdb ids collide across the movie/tv namespaces: one fetch per
        // (tmdbId, type) pair, each hitting its own endpoint — never one
        // endpoint chosen by whichever row came last.
        val tvCalls = mutableListOf<Int>()
        coEvery { seerrRepository.getMovieDetails(any()) } coAnswers {
            movieCalls += firstArg<Int>()
            Result.success(SeerrMovieDetails(title = "Movie 7"))
        }
        coEvery { seerrRepository.getTvDetails(any()) } coAnswers {
            tvCalls += firstArg<Int>()
            Result.success(SeerrTvDetails(name = "Tv 7"))
        }
        stubRequests {
            Result.success(page(listOf(item(1, tmdbId = 7, type = "movie"), item(2, tmdbId = 7, type = "tv"))))
        }
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf(7), movieCalls)
        assertEquals(listOf(7), tvCalls)
        // mediaInfo is tmdbId-keyed (the read side's shape): the later
        // completion owns the entry — the inherited write-side conflation.
        assertEquals("Tv 7", viewModel.state.value.mediaInfo.getValue(7).title)
    }

    @Test
    fun per_item_enrich_failures_are_swallowed() = runTest(mainDispatcher) {
        stubRequests {
            Result.success(page(listOf(item(1, tmdbId = 11, type = "movie"), item(2, tmdbId = 22, type = "tv"))))
        }
        coEvery { seerrRepository.getMovieDetails(any()) } returns Result.failure(IllegalStateException("boom"))
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(SeerrTvDetails(name = "Show 22"))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.mediaInfo[11])
        assertEquals("Show 22", viewModel.state.value.mediaInfo.getValue(22).title)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun out_of_order_queue_completions_fill_both_maps() = runTest(mainDispatcher) {
        every {
            experimentalStore.experimental
        } returns MutableStateFlow(ExperimentalSlice(enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION)))
        val q11 = CompletableDeferred<ArrQueueItem?>()
        val q22 = CompletableDeferred<ArrQueueItem?>()
        coEvery { arrRepository.getQueueForTmdb(11) } coAnswers { q11.await() }
        coEvery { arrRepository.getQueueForTmdb(22) } coAnswers { q22.await() }
        stubRequests { Result.success(page(listOf(item(1, tmdbId = 11), item(2, tmdbId = 22)))) }

        val viewModel = newViewModel()
        runCurrent()

        q22.complete(queueItem(tmdbId = 22, progress = 0.4f))
        runCurrent()
        assertEquals(40, viewModel.state.value.downloadProgress.getValue(22).percent)
        q11.complete(queueItem(tmdbId = 11, progress = 0.9f))
        advanceUntilIdle()

        // Both completions landed in BOTH maps — the two-key fold dropped none.
        assertEquals(setOf(11, 22), viewModel.state.value.downloadProgress.keys)
        assertEquals(setOf(11, 22), viewModel.state.value.queueItems.keys)
        assertNotNull(viewModel.state.value.queueItems.getValue(11))
    }
}
