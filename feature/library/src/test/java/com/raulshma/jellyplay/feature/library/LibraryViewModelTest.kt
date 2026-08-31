@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.library

import androidx.paging.LoadState
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var offlineRepository: com.raulshma.jellyplay.core.data.repository.OfflineRepository
    private lateinit var downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository
    private lateinit var downloadIntake: com.raulshma.jellyplay.core.data.download.DownloadIntake
    private lateinit var offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var libraryStore: LibraryStore
    private lateinit var userMessageBus: com.raulshma.jellyplay.core.ui.feedback.UserMessageBus

    /** Real flow behind offlineModeManager.offlineMode — tests drive offline
     *  transitions (#147 auto downloaded filter) by setting its value. */
    private val offlineModeFlow =
        MutableStateFlow(com.raulshma.jellyplay.core.model.OfflineMode.ONLINE)

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        // The VM collects this in init for quick-action download gating.
        every {
            downloadRepository.observeDownloadedIdsIncludingSeries()
        } returns MutableStateFlow(emptySet())

        // Stub the init-block repository calls with real Result/Flow values so
        // the relaxed mock's default Result mock doesn't ClassCast inside the
        // VM's loadFolders()/loadGenres()/loadTags() collectors.
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList<LibraryFolder>())
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
    }

    private fun createViewModel(): LibraryViewModel = LibraryViewModel(
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        offlineModeManager = offlineModeManager,
        userDataMutator = userDataMutator,
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        libraryStore = libraryStore,
        userMessageBus = userMessageBus,
    )

    /** Browser-state snapshot, the single source of truth post-refactor. */
    private fun LibraryViewModel.state() = browserState.value

    @Test
    fun `configureSection scopes selectedFolder to parentId and pre-applies Date Added sort`() = runTest {
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Movies",
                parentId = "lib-movies",
                collectionType = "movies",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )

        val state = vm.state()
        assertEquals("lib-movies", state.folder?.id)
        assertEquals("Latest Movies", state.title)
        assertEquals(SortOption.DATE_ADDED, state.filters.sortBy)
    }

    @Test
    fun `configureSection with null parentId leaves global scope and still applies sort`() = runTest {
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Recently Added",
                parentId = null,
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )

        val state = vm.state()
        assertNull(state.folder)
        assertEquals(SortOption.DATE_ADDED, state.filters.sortBy)
    }

    @Test
    fun `configureSection for tvshows mirrors the default library view with no mediaType scoping`() = runTest {
        // Issue #113: "See All" from a home Latest row should mirror the default
        // library tab (top-level series), sorted by latest — NOT scope to flat
        // episodes. mediaTypes stays empty so pagedItems queries TOP_LEVEL items.
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Shows",
                parentId = "lib-tv",
                collectionType = "tvshows",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )

        val state = vm.state()
        assertEquals(emptyList<MediaType>(), state.filters.mediaTypes)
        assertEquals(SortOption.DATE_ADDED, state.filters.sortBy)
    }

    @Test
    fun `configureSection for movies mirrors the default library view with no mediaType scoping`() = runTest {
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Movies",
                parentId = "lib-movies",
                collectionType = "movies",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )

        assertEquals(emptyList<MediaType>(), vm.state().filters.mediaTypes)
    }

    @Test
    fun `configureSection for untyped collectionType leaves mediaTypes empty`() = runTest {
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Misc",
                parentId = "lib-misc",
                collectionType = "unknown",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )

        assertEquals(emptyList<MediaType>(), vm.state().filters.mediaTypes)
    }

    @Test
    fun `configureSection honors explicitly-passed mediaTypes over the default`() = runTest {
        // Explicit ctx.mediaTypes still win (e.g. a future home row that targets
        // a specific leaf type).
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Shows",
                parentId = "lib-tv",
                collectionType = "tvshows",
                sortBy = SortOption.DATE_ADDED.apiValue,
                mediaTypes = listOf(MediaType.MOVIE),
            )
        )

        assertEquals(listOf(MediaType.MOVIE), vm.state().filters.mediaTypes)
    }

    @Test
    fun `clearSectionMode resets section state so the Library tab shows its default view`() = runTest {
        // Issue #113: the VM is shared across the Library tab and the section
        // deep-link. After a "See All" visit, clearing section mode must restore
        // the default browsing view (no synthetic folder, no leftover filters).
        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Shows",
                parentId = "lib-tv",
                collectionType = "tvshows",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )
        assertNotNull(vm.state().sectionContext)
        assertEquals("Latest Shows", vm.state().title)
        assertNotNull(vm.state().folder)

        vm.clearSectionMode()

        val state = vm.state()
        assertNull(state.sectionContext)
        assertNull(state.title)
        assertNull(state.folder)
        assertEquals(LibraryFilters(), state.filters)
    }

    @Test
    fun `clearSectionMode is a no-op when not in section mode`() = runTest {
        val vm = createViewModel()
        // Not in section mode — clearing should be a safe no-op.
        vm.clearSectionMode()
        val state = vm.state()
        assertNull(state.sectionContext)
        assertEquals(LibraryFilters(), state.filters)
    }

    @Test
    fun `updateFilters does not persist in section mode`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        coEvery { libraryStore.setDefaultLibrarySortOrder(any(), any()) } returns Unit

        val vm = createViewModel()
        vm.configureSection(LibrarySectionContext(title = "Section", parentId = "lib-1"))
        vm.updateFilters(LibraryFilters(sortBy = SortOption.RATING))
        // The persistence call runs in a viewModelScope.launch; advance the test
        // scheduler so it would complete before we verify it did NOT fire.
        advanceUntilIdle()

        // No persistence calls should fire for a synthetic (section) folder.
        coVerify(exactly = 0) { libraryStore.setLibraryFilters(any(), any()) }
        coVerify(exactly = 0) { libraryStore.setDefaultLibrarySortOrder(any(), any()) }
        assertEquals(SortOption.RATING, vm.state().filters.sortBy)
    }

    @Test
    fun `setGroupBy persists via store`() = runTest {
        coEvery { libraryStore.setLibraryGroupBy(any()) } returns Unit

        val vm = createViewModel()
        vm.setGroupBy(GroupBy.GENRE)
        // The persistence call runs in a viewModelScope.launch; advance the test
        // scheduler so it completes before we verify.
        advanceUntilIdle()

        coVerify { libraryStore.setLibraryGroupBy(GroupBy.GENRE) }
    }

    @Test
    fun `selectFolder decodes saved filters from store`() = runTest {
        // Persisted-blob shape the store holds — the exact JSON the deleted
        // SavedLibraryFilters mirror used to emit (enums by .name).
        val savedBlob = """
            {"mediaTypes":["MOVIE"],"genres":[],"years":[],
             "sortBy":"RATING","playedStatus":"UNPLAYED",
             "tags":["fav"],"minRating":4.0}
        """.trimIndent()
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(libraryFilters = mapOf("lib-1" to savedBlob)),
        )

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "Movies"))

        val filters = vm.state().filters
        assertEquals(listOf(MediaType.MOVIE), filters.mediaTypes)
        assertEquals(SortOption.RATING, filters.sortBy)
        assertEquals(com.raulshma.jellyplay.core.model.PlayedStatus.UNPLAYED, filters.playedStatus)
        assertEquals(listOf("fav"), filters.tags)
    }

    @Test
    fun `selectFolder applies collectionType default view mode with no saved override`() = runTest {
        // Regression for the divergence the refactor fixes: the old sync
        // selectFolder() path omitted defaultViewMode(), so a music folder with
        // no saved view-mode override rendered as a grid instead of a list.
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(libraryViewMode = LibraryViewMode.GRID),
        )
        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-music", name = "Music", collectionType = "music"))
        assertEquals(LibraryViewMode.LIST, vm.state().viewMode)
    }

    @Test
    fun `setPosterSize updates memory only and persistPosterSize writes the store`() = runTest {
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()
        vm.setPosterSize(1.2f)
        advanceUntilIdle()

        assertEquals(1.2f, vm.state().posterSize)
        coVerify(exactly = 0) { libraryStore.setLibraryPosterSize(any()) }

        vm.persistPosterSize()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryStore.setLibraryPosterSize(1.2f) }
    }

    @Test
    fun `default tab mode has null title`() = runTest {
        val vm = createViewModel()
        val state = vm.state()
        assertNull(state.title)
        assertNull(state.sectionContext)
    }

    @Test
    fun `resetToDefault clears filters folder and layout and persists the defaults`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        coEvery { libraryStore.setDefaultLibrarySortOrder(any(), any()) } returns Unit
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit
        coEvery { libraryStore.setLibraryGroupBy(any()) } returns Unit

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.updateFilters(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE), sortBy = SortOption.RATING))
        vm.setPosterSize(1.3f)
        vm.setGroupBy(GroupBy.GENRE)
        vm.setViewMode(LibraryViewMode.LIST)
        advanceUntilIdle()

        vm.resetToDefault()
        advanceUntilIdle()

        val state = vm.state()
        assertNull(state.folder)
        assertEquals(LibraryFilters(), state.filters)
        assertEquals(1.0f, state.posterSize)
        assertEquals(GroupBy.NONE, state.groupBy)
        // Folder is null, so the view mode derives back to the global default.
        assertEquals(LibraryViewMode.GRID, state.viewMode)

        coVerify { libraryStore.setLibraryPosterSize(1.0f) }
        coVerify { libraryStore.setLibraryGroupBy(GroupBy.NONE) }
        // The previously selected folder's stale saved filters are overwritten.
        coVerify { libraryStore.setLibraryFilters("lib-1", any()) }
        coVerify { libraryStore.setDefaultLibrarySortOrder("lib-1", SortOption.YEAR_DESC.name) }
    }

    @Test
    fun `resetToDefault does not persist folder filters in section mode`() = runTest {
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit
        coEvery { libraryStore.setLibraryGroupBy(any()) } returns Unit

        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Shows",
                parentId = "lib-tv",
                collectionType = "tvshows",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )
        advanceUntilIdle()

        vm.resetToDefault()
        advanceUntilIdle()

        coVerify(exactly = 0) { libraryStore.setLibraryFilters(any(), any()) }
        coVerify(exactly = 0) { libraryStore.setDefaultLibrarySortOrder(any(), any()) }
        val state = vm.state()
        assertNull(state.folder)
        assertEquals(LibraryFilters(), state.filters)
    }

    @Test
    fun `section view-mode mutation does not leak into real library prefs`() = runTest {
        // Issue #113 regression: enter a section ("See All" deep-link), mutate
        // the view mode while inside it, leave the section, and assert the real
        // library's per-folder view-mode prefs were never written (the section's
        // synthetic parentId must never be persisted as a library key).
        coEvery { libraryStore.setLibraryViewMode(any()) } returns Unit
        coEvery { libraryStore.setLibraryViewMode(any(), any()) } returns Unit

        val vm = createViewModel()
        vm.configureSection(
            LibrarySectionContext(
                title = "Latest Shows",
                parentId = "lib-tv",
                collectionType = "tvshows",
                sortBy = SortOption.DATE_ADDED.apiValue,
            )
        )
        // Mutate view mode while inside the section.
        vm.setViewMode(LibraryViewMode.LIST)
        advanceUntilIdle()

        // The global default write is fine; the per-folder write for the
        // section's synthetic parentId must NOT happen.
        coVerify(exactly = 0) { libraryStore.setLibraryViewMode("lib-tv", any()) }

        // Leaving the section restores the default browsing view.
        vm.clearSectionMode()
        assertNull(vm.state().sectionContext)
    }

    /** Delegation one-liner (plan 03): silent grid mutations route through the mutator. */
    @Test
    fun `markItemPlayed delegates to the mutator silently`() = runTest {
        val vm = createViewModel()
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        vm.markItemPlayed(item, played = true)
        advanceUntilIdle()

        coVerify {
            userDataMutator.setPlayed("m1", true, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
    }

    // ── Offline auto downloaded filter (#147) ────────────────────────────────

    @Test
    fun `offline static paging settles refresh load state so pull-to-refresh spinner clears`() = runTest {
        // Offline BEFORE the tab opens: the VM's first (and only) paging
        // generation is the static offline PagingData.from(...) path.
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        every {
            offlineRepository.getOfflineLibrary()
        } returns flowOf(
            listOf(
                OfflineMediaItem(
                    id = "dl-1",
                    name = "Downloaded Movie",
                    mediaType = MediaType.MOVIE,
                )
            )
        )
        val vm = createViewModel()

        // Same construction LazyPagingItems performs in LibraryScreen: a fresh
        // presenter with no cached paging data, whose initial load state is
        // refresh = Loading.
        val presenter = object : PagingDataPresenter<MediaItem>(
            mainContext = UnconfinedTestDispatcher(testScheduler),
        ) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<MediaItem>) {}
        }

        val collectJob = backgroundScope.launch {
            vm.pagedItems.collect { presenter.collectFrom(it) }
        }
        advanceUntilIdle()

        // The grid renders the offline item (mirrors pagedItems.itemCount > 0)…
        assertEquals(1, presenter.snapshot().items.size)
        // …but PullToRefreshBox stays spinning unless loadState.refresh leaves
        // Loading — a static PagingData.from without explicit source load states
        // never dispatches them (dispatchLoadStates = false).
        val refreshState = presenter.loadStateFlow.value?.refresh
        assertTrue(
            "refresh stuck at $refreshState — pull-to-refresh spinner would never clear",
            refreshState is LoadState.NotLoading,
        )

        collectJob.cancel()
    }

    @Test
    fun `offlineAutoFilter mirrors offline mode transitions`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.offlineAutoFilter.collect { }
        }
        assertFalse(vm.offlineAutoFilter.value)

        offlineModeFlow.value = com.raulshma.jellyplay.core.model.OfflineMode.OFFLINE_AUTO
        advanceUntilIdle()
        assertTrue(vm.offlineAutoFilter.value)

        offlineModeFlow.value = com.raulshma.jellyplay.core.model.OfflineMode.ONLINE
        advanceUntilIdle()
        assertFalse(vm.offlineAutoFilter.value)
    }

    @Test
    fun `offline mode serves the grid from the offline store without server paging`() = runTest {
        every {
            offlineRepository.getOfflineLibrary()
        } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.raulshma.jellyplay.core.model.OfflineMediaItem(
                    id = "dl-1",
                    name = "Downloaded Movie",
                    mediaType = MediaType.MOVIE,
                )
            )
        )

        val vm = createViewModel()
        offlineModeFlow.value = com.raulshma.jellyplay.core.model.OfflineMode.OFFLINE_MANUAL

        val firstPage = backgroundScope.async { vm.pagedItems.first() }
        advanceUntilIdle()

        assertTrue(firstPage.isCompleted)
        // The local store is the source, the server pager is never touched…
        verify(exactly = 0) { mediaRepository.getMediaItemsPaged(any(), any(), any()) }
        // …and the auto filter never mutated the user's filters.
        assertNull(vm.state().filters.isDownloaded)
    }
}
