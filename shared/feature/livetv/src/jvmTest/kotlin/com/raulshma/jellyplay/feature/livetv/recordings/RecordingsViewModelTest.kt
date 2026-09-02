package com.raulshma.jellyplay.feature.livetv.recordings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: RecordingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(emptyList())
        viewModel = RecordingsViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_fetches_recordings_with_limit() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecordings(limit = 24, isInProgress = null) }
    }

    @Test
    fun load_populates_state_from_results() = runTest(mainDispatcher) {
        val recordings = listOf(LiveTvRecording(id = "r1", name = "Recording 1"))
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(recordings)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(recordings, viewModel.uiState.value.recordings)
    }

    @Test
    fun load_surfaces_error_when_getRecordings_fails() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("boom") == true)
    }
}
