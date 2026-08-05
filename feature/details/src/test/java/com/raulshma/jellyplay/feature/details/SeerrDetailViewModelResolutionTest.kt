package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.SeerrDetailPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrExternalIds
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrDetailViewModelResolutionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var projections: PreferenceProjections
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var mediaRepository: MediaRepository

    private lateinit var viewModel: SeerrDetailViewModel

    @Before
    fun setUp() {
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)

        every { projections.seerrDetailPreferences } returns MutableStateFlow(SeerrDetailPreferences())
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(SeerrPreferences())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.getPreferences() } returns flowOf(SeerrPreferences())
        coEvery { seerrRepository.getRatings(any(), any()) } returns Result.failure(NullPointerException())
        coEvery { seerrRepository.getRecommendations(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = emptyList(), page = 1, totalPages = 1, totalResults = 0)
        )
        coEvery { seerrRepository.getSimilar(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = emptyList(), page = 1, totalPages = 1, totalResults = 0)
        )

        viewModel = SeerrDetailViewModel(
            seerrRepository, seerrRequestDelegate, projections, seerrPreferencesStore, mediaRepository
        )
    }

    @Test
    fun `resolves jellyfinItemId via tmdb when item is available`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123)
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success("jellyfin-uuid-1")

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("jellyfin-uuid-1", viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `does not attempt resolution when item is not available`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            pendingMovie(tmdbId = 123)
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.findItemByProviderId(any(), any()) }
        assertNull(viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `falls back to tvdb when tmdb lookup returns null`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123, externalIds = SeerrExternalIds(tvdbId = 456, imdbId = "tt789"))
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success(null)
        coEvery { mediaRepository.findItemByProviderId("tvdb", "456") } returns Result.success("jellyfin-uuid-2")

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("jellyfin-uuid-2", viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `leaves jellyfinItemId null when no provider id resolves`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123, externalIds = SeerrExternalIds(tvdbId = 456))
        )
        coEvery { mediaRepository.findItemByProviderId(any(), any()) } returns Result.success(null)

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `requestMedia flips status to PENDING when mediaInfo was absent`() = runTest {
        // Regression: Overseerr omits mediaInfo from /movie/{id} for media that
        // has never been requested. The optimistic update previously keyed on
        // mediaInfo.tmdbId, which was null, so neither branch matched and the
        // action button stayed on "Request" even after a successful request.
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Movie with NO mediaInfo — the never-requested case.
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(id = 123)
        )
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(
            io.mockk.mockk(relaxed = true)
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        viewModel.requestMedia(
            item = com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(id = 123, mediaType = "movie"),
        )
        advanceUntilIdle()

        val status = viewModel.uiState.value.movieDetails?.mediaInfo?.status
        assertEquals(SeerrMediaStatus.PENDING.value, status)
    }

    private fun availableMovie(
        tmdbId: Int,
        externalIds: SeerrExternalIds? = null,
    ) = SeerrMovieDetails(
        id = tmdbId,
        mediaInfo = SeerrMediaInfo(tmdbId = tmdbId, status = SeerrMediaStatus.AVAILABLE.value),
        externalIds = externalIds,
    )

    private fun pendingMovie(tmdbId: Int) = SeerrMovieDetails(
        id = tmdbId,
        mediaInfo = SeerrMediaInfo(tmdbId = tmdbId, status = SeerrMediaStatus.PENDING.value),
    )
}
