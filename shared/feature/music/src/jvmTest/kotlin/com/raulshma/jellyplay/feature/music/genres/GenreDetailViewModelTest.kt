package com.raulshma.jellyplay.feature.music.genres

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the genre detail contract: the nav-entry [SavedStateHandle] supplies
 * the genre name that routes into the paged AUDIO query filter (SORT_NAME),
 * missing keys fall back to empty strings, and enqueue/play delegate with the
 * dense-list [ImageUrlProvider.MUSIC_MAX_WIDTH].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("genreId" to "g1", "genreName" to "Rock"))): GenreDetailViewModel {
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        return GenreDetailViewModel(
            savedStateHandle = handle,
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @Test
    fun init_routesGenreNameIntoPagedAudioFilters() = runTest(mainDispatcher) {
        createViewModel()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = null,
                filters = LibraryFilters(
                    mediaTypes = listOf(MediaType.AUDIO),
                    genres = listOf("Rock"),
                    sortBy = SortOption.SORT_NAME,
                ),
                studioIds = null,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }
    }

    @Test
    fun init_missingHandleKeys_fallBackToEmptyStrings() = runTest(mainDispatcher) {
        createViewModel(SavedStateHandle(emptyMap()))

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = null,
                filters = LibraryFilters(
                    mediaTypes = listOf(MediaType.AUDIO),
                    genres = listOf(""),
                    sortBy = SortOption.SORT_NAME,
                ),
                studioIds = null,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }

    @Test
    fun addToQueue_delegatesSingleTrackWithMusicMaxWidth() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        val track = MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO)
        coEvery { audioQueueFacade.enqueueTrack(any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), -1)

        viewModel.addToQueue(track)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.enqueueTrack(track, null, ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }

    @Test
    fun playAll_delegatesWithMusicMaxWidthAndStartIndex() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        val tracks = listOf(
            MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
            MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
        )
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)

        viewModel.playAll(tracks, startIndex = 2)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(tracks, 2, false, null, ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }
}
