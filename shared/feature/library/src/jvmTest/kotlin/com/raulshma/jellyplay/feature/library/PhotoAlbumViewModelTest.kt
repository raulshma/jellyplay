@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.library

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoAlbumViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — same
    // harness as LibraryViewModelTest (runTest reuses the Main TestDispatcher's
    // scheduler, so the VM's viewModelScope coroutines run on this scheduler).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider

    private val photo1 = MediaItem(id = "a1", name = "Album Photo 1", mediaType = MediaType.PHOTO)
    private val photo2 = MediaItem(id = "a2", name = "Album Photo 2", mediaType = MediaType.PHOTO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        // Static paging only dispatches load states when explicit source
        // states are provided (same construction LibraryViewModel uses for its
        // offline grid) — without them the presenter never sees the items. A
        // fresh PagingData per invocation: a single instance can only be
        // presented once, and each filter/parent change starts a new generation.
        val idleStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = false),
            append = LoadState.NotLoading(endOfPaginationReached = false),
        )
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } answers {
            flowOf(PagingData.from(listOf(photo1, photo2), idleStates))
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PhotoAlbumViewModel = PhotoAlbumViewModel(
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
    )

    private class PhotoGrid(val presenter: PagingDataPresenter<MediaItem>, val job: Job) {
        fun cancel() = job.cancel()
    }

    /** Same construction LazyPagingItems performs in the album screen: a fresh
     *  presenter driven by a foreground collector ([advanceUntilIdle] skips
     *  backgroundScope tasks when nothing else is queued, so the collector
     *  must be a regular test-scope child). [PhotoGrid.cancel] at the end of
     *  each test — a still-active child makes runTest wait out its timeout. */
    private fun TestScope.collectPhotos(vm: PhotoAlbumViewModel): PhotoGrid {
        val presenter = object : PagingDataPresenter<MediaItem>(
            mainContext = UnconfinedTestDispatcher(testScheduler),
        ) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<MediaItem>) {}
        }
        val job = launch {
            vm.pagedItems.collect { presenter.collectFrom(it) }
        }
        return PhotoGrid(presenter, job)
    }

    @Test
    fun `pagedItems queries photos with the default DATE_ADDED sort`() = runTest {
        val vm = createViewModel()
        val grid = collectPhotos(vm)
        advanceUntilIdle()

        assertEquals(2, grid.presenter.snapshot().items.size)
        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO), sortBy = SortOption.DATE_ADDED),
            )
        }
        grid.cancel()
    }

    @Test
    fun `setSortOption re-queries the pager with the mapped SortOption`() = runTest {
        val vm = createViewModel()
        val grid = collectPhotos(vm)
        advanceUntilIdle()

        vm.setSortOption(PhotoSortOption.NAME)
        advanceUntilIdle()
        vm.setSortOption(PhotoSortOption.DATE_TAKEN)
        advanceUntilIdle()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO), sortBy = SortOption.SORT_NAME),
            )
        }
        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO), sortBy = SortOption.PREMIERE_DATE),
            )
        }
        grid.cancel()
    }

    @Test
    fun `setParentId re-queries the pager scoped to the album`() = runTest {
        val vm = createViewModel()
        val grid = collectPhotos(vm)
        advanceUntilIdle()

        vm.setParentId("album-9")
        advanceUntilIdle()

        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = "album-9",
                filters = LibraryFilters(mediaTypes = listOf(MediaType.PHOTO), sortBy = SortOption.DATE_ADDED),
            )
        }
        grid.cancel()
    }

    @Test
    fun `sortOption exposes the current selection`() = runTest {
        val vm = createViewModel()

        assertEquals(PhotoSortOption.DATE_ADDED, vm.sortOption.first())
        vm.setSortOption(PhotoSortOption.DATE_TAKEN)
        assertEquals(PhotoSortOption.DATE_TAKEN, vm.sortOption.first())
    }

    @Test
    fun `saveScrollPosition round-trips index and offset`() {
        val vm = createViewModel()
        assertEquals(0 to 0, vm.scrollPosition)

        vm.saveScrollPosition(4, 128)
        assertEquals(4 to 128, vm.scrollPosition)
    }

    @Test
    fun `getImageUrl defaults to a 400px request and honors overrides`() {
        every { imageUrlProvider.getImageUrl("a1", 400) } returns "w400"
        every { imageUrlProvider.getImageUrl("a1", 1280) } returns "w1280"

        val vm = createViewModel()

        assertEquals("w400", vm.getImageUrl("a1"))
        assertEquals("w1280", vm.getImageUrl("a1", 1280))
    }

    @Test
    fun `PhotoSortOption maps to the repository SortOption values`() {
        // The enum is the album screen's entire sort vocabulary — keep it in
        // lockstep with the repository-level options it forwards.
        assertEquals(SortOption.DATE_ADDED, PhotoSortOption.DATE_ADDED.option)
        assertEquals(SortOption.SORT_NAME, PhotoSortOption.NAME.option)
        assertEquals(SortOption.PREMIERE_DATE, PhotoSortOption.DATE_TAKEN.option)
        assertEquals("Recently Added", PhotoSortOption.DATE_ADDED.displayName)
        assertEquals("Name", PhotoSortOption.NAME.displayName)
        assertEquals("Date Taken", PhotoSortOption.DATE_TAKEN.displayName)
    }
}
