package com.raulshma.jellyplay.feature.music.collection

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.feature.music.albums.AlbumsViewModel
import com.raulshma.jellyplay.feature.music.artists.ArtistsViewModel
import com.raulshma.jellyplay.feature.music.tracks.TracksViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Per-collection adapter wiring: each standalone ViewModel binds its
 * [MusicCollectionKind] to the ONE sorted paged source path — the kind's
 * media type travels as the `LibraryFilters` filter and the selected sort as
 * the Jellyfin [SortOption]. The chassis rejects list-sourced kinds (pinned
 * in `MusicCollectionSortTableTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicCollectionAdapterWiringTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun artistsAdapter_queriesArtistsWithTheKindAndDefaultNameSort() = runTest(mainDispatcher) {
        val viewModel = ArtistsViewModel(mediaRepository, imageUrlProvider)
        advanceUntilIdle()

        viewModel.artists.first()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                any(),
                LibraryFilters(mediaTypes = listOf(MediaType.ARTIST), sortBy = SortOption.SORT_NAME),
                any(),
                any(),
            )
        }
    }

    @Test
    fun albumsAdapter_setSortRequeriesWithTheMappedJellyfinSort() = runTest(mainDispatcher) {
        val viewModel = AlbumsViewModel(mediaRepository, imageUrlProvider)
        advanceUntilIdle()

        viewModel.setSort(MusicSortOption.YEAR)
        viewModel.albums.first()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                any(),
                LibraryFilters(mediaTypes = listOf(MediaType.ALBUM), sortBy = SortOption.YEAR_DESC),
                any(),
                any(),
            )
        }
    }

    @Test
    fun tracksAdapter_queriesAudioWithTheKindAndDefaultNameSort() = runTest(mainDispatcher) {
        val viewModel = TracksViewModel(mediaRepository, imageUrlProvider, audioQueueFacade)
        advanceUntilIdle()

        viewModel.tracks.first()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                any(),
                LibraryFilters(mediaTypes = listOf(MediaType.AUDIO), sortBy = SortOption.SORT_NAME),
                any(),
                any(),
            )
        }
    }

    @Test
    fun imageUrl_resolvesThroughTheSharedMusicMaxWidthSeam() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"
        val viewModel = AlbumsViewModel(mediaRepository, imageUrlProvider)

        assertEquals("img", viewModel.getImageUrl("i1"))
    }
}
