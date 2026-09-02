package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaInfoViewModelTest {

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

    private lateinit var mediaRepository: MediaRepository
    private lateinit var viewModel: MediaInfoViewModel

    @BeforeTest
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        viewModel = MediaInfoViewModel(mediaRepository)
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(MediaInfoUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load success emits Success with detail`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(detail)

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MediaInfoUiState.Success)
        assertEquals("m1", (state as MediaInfoUiState.Success).detail.item.id)
    }

    @Test
    fun `load failure emits Error with message`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MediaInfoUiState.Error)
        assertEquals("boom", (state as MediaInfoUiState.Error).message)
    }

    @Test
    fun `load failure with null message falls back to generic error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException())

        viewModel.load("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as MediaInfoUiState.Error
        assertEquals("Failed to load media info", state.message)
    }

    @Test
    fun `load resets prior Success state to Loading then resolves`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )
        coEvery { mediaRepository.getMediaDetail("m2") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)),
        )

        viewModel.load("m1")
        advanceUntilIdle()
        assertEquals("m1", (viewModel.uiState.value as MediaInfoUiState.Success).detail.item.id)

        viewModel.load("m2")
        // Synchronously observes the reset before the repo call resolves.
        assertEquals(MediaInfoUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals("m2", (viewModel.uiState.value as MediaInfoUiState.Success).detail.item.id)
    }
}
