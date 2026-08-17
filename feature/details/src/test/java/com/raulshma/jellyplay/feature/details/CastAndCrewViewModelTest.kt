package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastAndCrewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: CastAndCrewViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        viewModel = CastAndCrewViewModel(mediaRepository, imageUrlProvider)
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(CastAndCrewUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load success partitions cast and crew`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(
                item = person,
                people = listOf(
                    PersonInfo(id = "p1", name = "Actor", type = "Actor"),
                    PersonInfo(id = "p2", name = "Director", type = "Director"),
                ),
            ),
        )

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CastAndCrewUiState.Success)
        val success = state as CastAndCrewUiState.Success
        assertEquals("Movie 1", success.title)
        assertEquals(listOf("p1"), success.cast.map { it.id })
        assertEquals(listOf("p2"), success.crew.map { it.id })
    }

    @Test
    fun `load failure emits Error and calls the repository exactly once`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))

        viewModel.load("m1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CastAndCrewUiState.Error)
        // Retry is owned by the data layer; this layer must not re-issue calls.
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("m1") }
    }
}
