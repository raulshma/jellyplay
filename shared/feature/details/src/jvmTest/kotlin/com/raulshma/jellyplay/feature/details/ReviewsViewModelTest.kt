package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reviews Reviews screen's VM: the item resolution rides the cached
 * `getMediaDetail` path (the CastAndCrewViewModel pattern) and the reviews
 * ride the SAME cached TMDB seam the detail screen's reviews leg uses
 * ([SeerrRepository.getTmdbReviews]) — a cache hit, not a refetch, and the
 * full uncapped list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelTest {

    // Legacy :core:testing MainDispatcherRule, inlined (conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private lateinit var detailFeed: DetailFeed
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var viewModel: ReviewsViewModel

    @BeforeTest
    fun setUp() {
        detailFeed = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        viewModel = ReviewsViewModel(detailFeed, seerrRepository)
    }

    private fun movieDetail(tmdbId: String? = "123") = MediaDetail(
        item = MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
        providerIds = if (tmdbId != null) mapOf("tmdb" to tmdbId) else emptyMap(),
    )

    @Test
    fun `initial state is Loading`() {
        assertEquals(ReviewsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load success resolves the tmdb id and fetches the full review list`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("m1", any()) } returns Result.success(movieDetail())
        val reviews = (1..8).map { TmdbReview(id = "r$it", author = "A$it", content = "B$it") }
        coEvery { seerrRepository.getTmdbReviews(123, MediaType.MOVIE) } returns Result.success(reviews)

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReviewsUiState.Success)
        assertEquals("Movie 1", state.title)
        // The FULL list — unlike the detail screen's take-5 preview leg.
        assertEquals(8, state.reviews.size)
    }

    @Test
    fun `a failed reviews leg degrades to an empty list, not an error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("m1", any()) } returns Result.success(movieDetail())
        coEvery { seerrRepository.getTmdbReviews(any(), any()) } returns Result.failure(RuntimeException("tmdb down"))

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReviewsUiState.Success)
        assertTrue(state.reviews.isEmpty())
    }

    @Test
    fun `a series resolves through the tv wire media type`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("s1", any()) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "s1", name = "Series 1", mediaType = MediaType.SERIES),
                providerIds = mapOf("tmdbid" to "555"),
            ),
        )
        coEvery { seerrRepository.getTmdbReviews(555, MediaType.SERIES) } returns
            Result.success(listOf(TmdbReview(id = "r1", author = "A", content = "B")))

        viewModel.load("s1")
        advanceUntilIdle()

        assertTrue((viewModel.uiState.value as ReviewsUiState.Success).reviews.map { it.id } == listOf("r1"))
    }

    @Test
    fun `detail failure emits Error and never reaches the reviews fetch`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("m1", any()) } returns Result.failure(RuntimeException("boom"))

        viewModel.load("m1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReviewsUiState.Error)
        coVerify(exactly = 0) { seerrRepository.getTmdbReviews(any(), any()) }
    }

    @Test
    fun `an item without a tmdb id yields an empty success without a reviews fetch`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("m1", any()) } returns Result.success(movieDetail(tmdbId = null))

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReviewsUiState.Success)
        assertTrue(state.reviews.isEmpty())
        coVerify(exactly = 0) { seerrRepository.getTmdbReviews(any(), any()) }
    }

    @Test
    fun `a non-rated media type (episode) yields an empty success without a reviews fetch`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("e1", any()) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "e1", name = "Episode 1", mediaType = MediaType.EPISODE),
                providerIds = mapOf("tmdb" to "123"),
            ),
        )

        viewModel.load("e1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReviewsUiState.Success)
        assertTrue(state.reviews.isEmpty())
        coVerify(exactly = 0) { seerrRepository.getTmdbReviews(any(), any()) }
    }

    @Test
    fun `load resets a prior Success state to Loading before resolving`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { detailFeed.getMediaDetail("m1", any()) } returns Result.success(movieDetail())
        coEvery { seerrRepository.getTmdbReviews(any(), any()) } returns
            Result.success(listOf(TmdbReview(id = "r1", author = "A", content = "B")))

        viewModel.load("m1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ReviewsUiState.Success)

        // A second load for another item must flip back to Loading first —
        // the previous item's reviews must never bleed into the new screen.
        val gate = kotlinx.coroutines.CompletableDeferred<Result<MediaDetail>>()
        coEvery { detailFeed.getMediaDetail("m2", any()) } coAnswers { gate.await() }
        viewModel.load("m2")
        advanceUntilIdle()

        assertEquals(ReviewsUiState.Loading, viewModel.uiState.value)

        gate.complete(Result.success(movieDetail()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReviewsUiState.Success)
    }
}
