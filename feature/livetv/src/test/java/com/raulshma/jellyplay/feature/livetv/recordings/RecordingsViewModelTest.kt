package com.raulshma.jellyplay.feature.livetv.recordings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.RecordingFolder
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: RecordingsViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getRecordingFolders() } returns Result.success(emptyList())
        viewModel = RecordingsViewModel(mediaRepository, imageUrlProvider)
    }

    @Test
    fun `load fetches recordings with limit and folders`() = runTest {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecordings(limit = 24, isInProgress = null) }
        coVerify { mediaRepository.getRecordingFolders() }
    }

    @Test
    fun `load populates state from results`() = runTest {
        val recordings = listOf(LiveTvRecording(id = "r1", name = "Recording 1"))
        val folders = listOf(RecordingFolder(id = "f1", name = "Folder 1"))
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(recordings)
        coEvery { mediaRepository.getRecordingFolders() } returns Result.success(folders)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(recordings, viewModel.uiState.value.recordings)
        assertEquals(folders, viewModel.uiState.value.folders)
    }

    @Test
    fun `load surfaces error when getRecordings fails`() = runTest {
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("boom") == true)
    }
}
