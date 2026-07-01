package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.UserPreferences
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
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var mediaRepository: MediaRepository

    private lateinit var viewModel: SeerrDetailViewModel

    @Before
    fun setUp() {
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)

        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())
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
            seerrRepository, seerrRequestDelegate, preferencesStore, seerrPreferencesStore, mediaRepository
        )
    }

    @Test
    fun `resolves jellyfinItemId via tmdb when item is available`() = runTest {
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123)
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success("jellyfin-uuid-1")

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("jellyfin-uuid-1", viewModel.jellyfinItemId.value)
    }

    @Test
    fun `does not attempt resolution when item is not available`() = runTest {
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            pendingMovie(tmdbId = 123)
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.findItemByProviderId(any(), any()) }
        assertNull(viewModel.jellyfinItemId.value)
    }

    @Test
    fun `falls back to tvdb when tmdb lookup returns null`() = runTest {
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123, externalIds = SeerrExternalIds(tvdbId = 456, imdbId = "tt789"))
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success(null)
        coEvery { mediaRepository.findItemByProviderId("tvdb", "456") } returns Result.success("jellyfin-uuid-2")

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("jellyfin-uuid-2", viewModel.jellyfinItemId.value)
    }

    @Test
    fun `leaves jellyfinItemId null when no provider id resolves`() = runTest {
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            availableMovie(tmdbId = 123, externalIds = SeerrExternalIds(tvdbId = 456))
        )
        coEvery { mediaRepository.findItemByProviderId(any(), any()) } returns Result.success(null)

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertNull(viewModel.jellyfinItemId.value)
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
