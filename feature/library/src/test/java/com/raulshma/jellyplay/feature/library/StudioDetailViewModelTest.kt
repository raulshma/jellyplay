package com.raulshma.jellyplay.feature.library

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.library.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StudioDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)

        every { playbackRepository.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
    }

    private fun createViewModel(
        studioId: String = "studio-1",
        studioName: String = "Test Studio",
    ): StudioDetailViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Route.StudioDetail::studioId.name to studioId,
                Route.StudioDetail::studioName.name to studioName,
            )
        )
        return StudioDetailViewModel(
            savedStateHandle = savedStateHandle,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
        )
    }

    @Test
    fun `items flow calls getMediaItemsPaged with correct studioIds`() = runTest {
        val expectedStudioId = "studio-123"
        createViewModel(studioId = expectedStudioId)

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = listOf(expectedStudioId),
                sortBy = "SortName",
            )
        }
    }

    @Test
    fun `getImageUrl delegates to playbackRepository with correct params`() {
        val viewModel = createViewModel()
        val imageUrl = viewModel.getImageUrl("item-1")

        verify { playbackRepository.getImageUrl("item-1", maxWidth = 400) }
        assertEquals("https://example.com/image.jpg", imageUrl)
    }

    @Test
    fun `viewModel extracts studioId from savedStateHandle`() = runTest {
        val viewModel = createViewModel(studioId = "my-studio-id")

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = listOf("my-studio-id"),
                sortBy = any(),
            )
        }
    }

    @Test
    fun `viewModel uses default sort by SortName`() = runTest {
        createViewModel()

        verify {
            mediaRepository.getMediaItemsPaged(
                studioIds = any(),
                sortBy = "SortName",
            )
        }
    }
}
