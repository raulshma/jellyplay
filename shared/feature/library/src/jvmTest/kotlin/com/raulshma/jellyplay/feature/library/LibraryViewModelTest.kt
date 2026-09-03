@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.library

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.feature.library.generated.resources.data_download_started
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LibraryViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var offlineRepository: com.raulshma.jellyplay.core.data.repository.OfflineRepository
    private lateinit var mediaDownloadActions: com.raulshma.jellyplay.core.data.download.MediaDownloadActions
    private lateinit var offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var libraryStore: LibraryStore

    /** Real flow behind offlineModeManager.offlineMode — tests drive offline
     *  transitions (#147 auto downloaded filter) by setting its value. */
    private val offlineModeFlow =
        MutableStateFlow(com.raulshma.jellyplay.core.model.OfflineMode.ONLINE)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        mediaDownloadActions = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        // The VM re-exposes this for quick-action download gating.
        every { mediaDownloadActions.downloadedIds } returns MutableStateFlow(emptySet())

        // Stub the init-block repository calls with real Result/Flow values so
        // the relaxed mock's default Result mock doesn't ClassCast inside the
        // VM's loadFolders()/loadGenres()/loadTags() collectors. The matchers
        // must cover BOTH argument shapes the VM uses: the init block's cached
        // read (force = false) and refresh()'s force = true read — an
        // unmatched call falls through to the relaxed mock whose Result mock
        // ClassCasts inside the inline onSuccess/onFailure.
        coEvery { mediaRepository.getLibraryFolders(any()) } returns Result.success(emptyList<LibraryFolder>())
        coEvery { mediaRepository.getGenres(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Download-started/failed feedback posts through the shared (commonMain)
    // UserMessageBus — a plain bus instance by default; the download-routing
    // tests inject their own so they can collect the posted messages.
    private fun createViewModel(
        userMessageBus: com.raulshma.jellyplay.core.ui.message.UserMessageBus =
            com.raulshma.jellyplay.core.ui.message.UserMessageBus(),
    ): LibraryViewModel = LibraryViewModel(
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
        mediaDownloadActions = mediaDownloadActions,
        offlineModeManager = offlineModeManager,
        userMessageBus = userMessageBus,
        userDataMutator = userDataMutator,
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        libraryStore = libraryStore,
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
            refreshState is LoadState.NotLoading,
            "refresh stuck at $refreshState — pull-to-refresh spinner would never clear",
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

    @Test
    fun `online paging queries top-level items scoped to the selected folder`() = runTest {
        // Issue #113 companion: the server pager must ask for TOP_LEVEL items
        // (series for TV, movies for movies) so section "See All" and the tab
        // render the same card grid.
        val idleStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = false),
            append = LoadState.NotLoading(endOfPaginationReached = false),
        )
        every {
            mediaRepository.getMediaItemsPaged(any(), any(), any(), any())
        } answers { flowOf(PagingData.from(emptyList<MediaItem>(), idleStates)) }

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-tv", name = "TV"))
        val firstPage = backgroundScope.async { vm.pagedItems.first() }
        advanceUntilIdle()

        assertTrue(firstPage.isCompleted)
        verify(exactly = 1) {
            mediaRepository.getMediaItemsPaged(
                parentId = "lib-tv",
                filters = LibraryFilters(),
                kindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL,
            )
        }
    }

    // ── Download quick actions (library grid) ────────────────────────────────

    /** Collects the bus so one-shot (Channel, no-replay) messages are captured.
     *  backgroundScope is auto-cancelled at test end — the bus's receiveAsFlow
     *  collector never completes, so a plain `launch` child would trip
     *  runTest's UncompletedCoroutinesError. */
    private fun TestScope.collectBus(
        bus: com.raulshma.jellyplay.core.ui.message.UserMessageBus,
    ): MutableList<com.raulshma.jellyplay.core.ui.message.UserMessage> {
        val received =
            mutableListOf<com.raulshma.jellyplay.core.ui.message.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.messages.collect { received.add(it) }
        }
        return received
    }

    @Test
    fun `downloadItem with Started posts the started info message and never routes`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        coEvery { mediaDownloadActions.download(item) } returns DownloadRequestResult.Started
        val bus = com.raulshma.jellyplay.core.ui.message.UserMessageBus()
        val received = collectBus(bus)
        val vm = createViewModel(userMessageBus = bus)

        var routedTo: Pair<String, Boolean>? = null
        vm.downloadItem(item) { itemId, openSheet -> routedTo = itemId to openSheet }
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertTrue(received[0] is com.raulshma.jellyplay.core.ui.message.UserMessage.Info)
        assertEquals(
            com.raulshma.jellyplay.core.ui.message.UiText.Resource(
                com.raulshma.jellyplay.feature.library.generated.resources.Res.string.data_download_started
            ),
            received[0].text,
        )
        assertNull(routedTo)
    }

    @Test
    fun `downloadItem for a series routes to the detail screen with the sheet pre-presented`() = runTest {
        val series = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)
        coEvery { mediaDownloadActions.download(series) } returns
            DownloadRequestResult.SeriesSelectionRequired(seriesId = "s1")
        val vm = createViewModel()

        var routedTo: Pair<String, Boolean>? = null
        vm.downloadItem(series) { itemId, openSheet -> routedTo = itemId to openSheet }
        advanceUntilIdle()

        assertEquals("s1" to true, routedTo)
    }

    @Test
    fun `downloadItem needing the detail screen routes plainly without the sheet`() = runTest {
        val album = MediaItem(id = "al-1", name = "Album", mediaType = MediaType.MUSIC)
        coEvery { mediaDownloadActions.download(album) } returns
            DownloadRequestResult.NeedsDetailScreen(itemId = "al-1")
        val vm = createViewModel()

        var routedTo: Pair<String, Boolean>? = null
        vm.downloadItem(album) { itemId, openSheet -> routedTo = itemId to openSheet }
        advanceUntilIdle()

        assertEquals("al-1" to false, routedTo)
    }

    @Test
    fun `downloadItem failure posts the failed error message and never routes`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        coEvery { mediaDownloadActions.download(item) } returns
            DownloadRequestResult.Failed(message = "no space")
        val bus = com.raulshma.jellyplay.core.ui.message.UserMessageBus()
        val received = collectBus(bus)
        val vm = createViewModel(userMessageBus = bus)

        var routedTo: Pair<String, Boolean>? = null
        vm.downloadItem(item) { itemId, openSheet -> routedTo = itemId to openSheet }
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertTrue(received[0] is com.raulshma.jellyplay.core.ui.message.UserMessage.Error)
        assertNull(routedTo)
    }

    @Test
    fun `removeItemDownload routes through the shared delete actions without touching the server`() = runTest {
        val vm = createViewModel()
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        vm.removeItemDownload(item)

        verify(exactly = 1) { mediaDownloadActions.removeDownload(item) }
    }

    // ── Reset-all confirmation dialog ────────────────────────────────────────

    @Test
    fun `onResetClick shows the dialog while confirmations are enabled`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle() // let loadResetConfirmPref apply the persisted true

        vm.onResetClick()

        assertTrue(vm.resetDialogVisible.value)
        // Nothing was reset yet — the dialog is a gate, not the action.
        assertNull(vm.state().folder)
    }

    @Test
    fun `dismissResetDialog hides the dialog without resetting anything`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.updateFilters(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE)))
        vm.onResetClick()
        assertTrue(vm.resetDialogVisible.value)

        vm.dismissResetDialog()

        assertFalse(vm.resetDialogVisible.value)
        // Filters untouched by the dismissal.
        assertEquals(listOf(MediaType.MOVIE), vm.state().filters.mediaTypes)
    }

    @Test
    fun `confirmResetAll resets and persists the don't-show-again opt-out`() = runTest {
        coEvery { libraryStore.setConfirmLibraryReset(any()) } returns Unit
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        coEvery { libraryStore.setDefaultLibrarySortOrder(any(), any()) } returns Unit
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit
        coEvery { libraryStore.setLibraryGroupBy(any()) } returns Unit
        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.updateFilters(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE)))

        vm.confirmResetAll(dontShowAgain = true)
        advanceUntilIdle()

        assertFalse(vm.resetDialogVisible.value)
        assertEquals(LibraryFilters(), vm.state().filters)
        coVerify(exactly = 1) { libraryStore.setConfirmLibraryReset(false) }

        // The real store would re-emit the flipped pref; simulate it and the
        // next reset tap must skip the dialog entirely.
        val prefsFlow = libraryStore.library as MutableStateFlow<LibrarySlice>
        prefsFlow.value = prefsFlow.value.copy(confirmLibraryReset = false)
        advanceUntilIdle()
        vm.onResetClick()
        assertFalse(vm.resetDialogVisible.value)
    }

    @Test
    fun `confirmResetAll without opt-out keeps future confirmations enabled`() = runTest {
        coEvery { libraryStore.setConfirmLibraryReset(any()) } returns Unit
        val vm = createViewModel()

        vm.confirmResetAll(dontShowAgain = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { libraryStore.setConfirmLibraryReset(any()) }
        // The dialog is gone but the gate stays armed.
        vm.onResetClick()
        assertTrue(vm.resetDialogVisible.value)
    }

    @Test
    fun `onResetClick resets immediately when the persisted pref disabled confirmations`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        coEvery { libraryStore.setDefaultLibrarySortOrder(any(), any()) } returns Unit
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit
        coEvery { libraryStore.setLibraryGroupBy(any()) } returns Unit
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(confirmLibraryReset = false),
        )

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.updateFilters(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE)))
        advanceUntilIdle()

        vm.onResetClick()
        advanceUntilIdle()

        assertFalse(vm.resetDialogVisible.value)
        assertEquals(LibraryFilters(), vm.state().filters)
        assertNull(vm.state().folder)
    }

    // ── Transient filter actions ─────────────────────────────────────────────

    @Test
    fun `shuffleLibrary applies RANDOM in memory only and keeps the saved sort`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        coEvery { libraryStore.setDefaultLibrarySortOrder(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.updateFilters(LibraryFilters(sortBy = SortOption.RATING))
        advanceUntilIdle()

        vm.shuffleLibrary()
        advanceUntilIdle()

        assertEquals(SortOption.RANDOM, vm.state().filters.sortBy)
        // Shuffle is transient: the ONLY persistence is the updateFilters setup
        // call above (RATING) — the shuffle itself writes nothing, so the saved
        // sort order is never overwritten with RANDOM and the next visit
        // restores the user's chosen sort.
        coVerify(exactly = 1) { libraryStore.setLibraryFilters(any(), any()) }
        coVerify(exactly = 1) { libraryStore.setDefaultLibrarySortOrder("lib-1", SortOption.RATING.name) }
        coVerify(exactly = 0) { libraryStore.setDefaultLibrarySortOrder("lib-1", SortOption.RANDOM.name) }
    }

    @Test
    fun `clearFilters restores default filters`() = runTest {
        coEvery { libraryStore.setLibraryFilters(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.updateFilters(
            LibraryFilters(mediaTypes = listOf(MediaType.MOVIE), sortBy = SortOption.RATING),
        )

        vm.clearFilters()

        assertEquals(LibraryFilters(), vm.state().filters)
    }

    @Test
    fun `toggleShowFilters flips the sheet visibility`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.showFilters.value)

        vm.toggleShowFilters()
        assertTrue(vm.showFilters.value)

        vm.toggleShowFilters()
        assertFalse(vm.showFilters.value)
    }

    // ── Folder loading failure semantics ─────────────────────────────────────

    @Test
    fun `loadFolders failure with no cached folders surfaces the error and clears loading`() = runTest {
        coEvery { mediaRepository.getLibraryFolders(any()) } returns
            Result.failure(RuntimeException("boom"))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("boom", vm.error.value)
        assertTrue(vm.folders.value.isEmpty())
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `loadFolders failure with a null message falls back to the exception class name`() = runTest {
        coEvery { mediaRepository.getLibraryFolders(any()) } returns Result.failure(RuntimeException())

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("RuntimeException", vm.error.value)
    }

    @Test
    fun `loadFolders failure after success keeps the stale folders and stays silent`() = runTest {
        // A transient 403 mid-session must not blank the library: previously
        // loaded folders stay browsable and no blocking error is raised.
        coEvery { mediaRepository.getLibraryFolders(any()) } returnsMany listOf(
            Result.success(listOf(LibraryFolder(id = "lib-1", name = "Movies"))),
            Result.failure(RuntimeException("403")),
        )

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(1, vm.folders.value.size)
        assertNull(vm.error.value)

        vm.refresh()
        advanceUntilIdle()

        // Folders survive, no error even though the refresh failed.
        assertEquals(1, vm.folders.value.size)
        assertNull(vm.error.value)
    }

    @Test
    fun `loadFolders success after failure clears the stale error`() = runTest {
        coEvery { mediaRepository.getLibraryFolders(any()) } returnsMany listOf(
            Result.failure(RuntimeException("boom")),
            Result.success(listOf(LibraryFolder(id = "lib-1", name = "Movies"))),
        )

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals("boom", vm.error.value)

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.error.value)
        assertEquals("lib-1", vm.folders.value.single().id)
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    @Test
    fun `refresh reloads folders and genres bypassing the caches`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        // Initial load is cached; the manual pull-to-refresh is not.
        coVerify(exactly = 1) { mediaRepository.getLibraryFolders(force = true) }
        coVerify(exactly = 1) { mediaRepository.getGenres(force = true) }
        // Tags are an uncached passthrough: both loads hit them.
        coVerify(exactly = 2) { mediaRepository.getTags(any(), any(), any()) }
    }

    // ── Saved sort fallbacks ─────────────────────────────────────────────────

    @Test
    fun `selectFolder with corrupt saved filters falls back to defaults`() = runTest {
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(libraryFilters = mapOf("lib-1" to "{not json!!!")),
        )

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "Movies"))

        assertEquals(LibraryFilters(), vm.state().filters)
    }

    @Test
    fun `selectFolder with an unknown saved sort name falls back to YEAR_DESC`() = runTest {
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(defaultLibrarySortOrders = mapOf("lib-1" to "NotARealSort")),
        )

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "Movies"))

        assertEquals(SortOption.YEAR_DESC, vm.state().filters.sortBy)
    }

    @Test
    fun `selectFolder accepts the saved sort by apiValue`() = runTest {
        every { libraryStore.library } returns MutableStateFlow(
            LibrarySlice(defaultLibrarySortOrders = mapOf("lib-1" to SortOption.DATE_ADDED.apiValue)),
        )

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "Movies"))

        assertEquals(SortOption.DATE_ADDED, vm.state().filters.sortBy)
    }

    // ── View-mode precedence: user tap wins over async store re-emission ─────

    @Test
    fun `setViewMode write survives a stale store re-emission until the folder changes`() = runTest {
        val prefsFlow = MutableStateFlow(LibrarySlice(libraryViewMode = LibraryViewMode.GRID))
        every { libraryStore.library } returns prefsFlow
        coEvery { libraryStore.setLibraryViewMode(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle() // initial derived mode = GRID
        assertEquals(LibraryViewMode.GRID, vm.state().viewMode)

        vm.setViewMode(LibraryViewMode.LIST)
        advanceUntilIdle()
        assertEquals(LibraryViewMode.LIST, vm.state().viewMode)

        // The async store write re-emits the stale persisted value — the tap
        // must not snap back (the "changes then switches back" bug).
        prefsFlow.value = prefsFlow.value.copy(libraryViewMode = LibraryViewMode.GRID)
        advanceUntilIdle()
        assertEquals(LibraryViewMode.LIST, vm.state().viewMode)

        // A folder/section change clears the guard so the new folder loads
        // its own derived mode again (music defaults to LIST).
        vm.selectFolder(
            LibraryFolder(id = "lib-music", name = "Music", collectionType = "music"),
        )
        advanceUntilIdle()
        assertEquals(LibraryViewMode.LIST, vm.state().viewMode)
    }

    @Test
    fun `setViewMode persists the per-folder override for a real folder`() = runTest {
        coEvery { libraryStore.setLibraryViewMode(any()) } returns Unit
        coEvery { libraryStore.setLibraryViewMode(any(), any()) } returns Unit

        val vm = createViewModel()
        vm.selectFolder(LibraryFolder(id = "lib-1", name = "TV"))
        vm.setViewMode(LibraryViewMode.LIST)
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryStore.setLibraryViewMode(LibraryViewMode.LIST) }
        coVerify(exactly = 1) { libraryStore.setLibraryViewMode("lib-1", "LIST") }
    }

    // ── Photo-folder child-url prefetch ──────────────────────────────────────

    @Test
    fun `prefetchPhotoFolderChildUrls merges results and skips already-fetched folders`() = runTest {
        val folder1 = MediaItem(id = "pf-1", name = "Folder 1", mediaType = MediaType.PHOTO_FOLDER)
        val folder2 = MediaItem(id = "pf-2", name = "Folder 2", mediaType = MediaType.PHOTO_FOLDER)
        val movie = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        coEvery { photoFolderPrefetcher.prefetch(any(), any()) } returnsMany listOf(
            mapOf("pf-1" to listOf("u1", "u2")),
            emptyMap<String, List<String>>(),
        )

        val vm = createViewModel()
        vm.prefetchPhotoFolderChildUrls(listOf(folder1, folder2, movie))
        advanceUntilIdle()

        assertEquals(mapOf("pf-1" to listOf("u1", "u2")), vm.photoFolderChildUrls.value)

        // Recomposition re-fires with the same items: the already-fetched
        // folder is in alreadyFetched this time, and an empty result never
        // clobbers the merged state.
        vm.prefetchPhotoFolderChildUrls(listOf(folder1, folder2, movie))
        advanceUntilIdle()
        coVerify {
            photoFolderPrefetcher.prefetch(listOf(folder1, folder2, movie), alreadyFetched = emptySet())
        }
        coVerify {
            photoFolderPrefetcher.prefetch(listOf(folder1, folder2, movie), alreadyFetched = setOf("pf-1"))
        }
        assertEquals(mapOf("pf-1" to listOf("u1", "u2")), vm.photoFolderChildUrls.value)
    }

    @Test
    fun `photoFolderChildUrlsFor emits only the folder's own urls`() = runTest {
        coEvery { photoFolderPrefetcher.prefetch(any(), any()) } returns
            mapOf("pf-1" to listOf("u1"), "pf-2" to listOf("u2", "u3"))
        val vm = createViewModel()
        vm.prefetchPhotoFolderChildUrls(
            listOf(
                MediaItem(id = "pf-1", name = "F1", mediaType = MediaType.PHOTO_FOLDER),
                MediaItem(id = "pf-2", name = "F2", mediaType = MediaType.PHOTO_FOLDER),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("u1"), vm.photoFolderChildUrlsFor("pf-1").first())
        assertEquals(listOf("u2", "u3"), vm.photoFolderChildUrlsFor("pf-2").first())
        // Unknown ids see an empty list, never a crash.
        assertEquals(emptyList(), vm.photoFolderChildUrlsFor("nope").first())
    }

    // ── Image url delegation ─────────────────────────────────────────────────

    @Test
    fun `getImageUrl and getBackdropUrl delegate to the provider`() {
        every { imageUrlProvider.getImageUrl("i1") } returns "img"
        every { imageUrlProvider.getBackdropUrl("i1") } returns "bd"

        val vm = createViewModel()

        assertEquals("img", vm.getImageUrl("i1"))
        assertEquals("bd", vm.getBackdropUrl("i1"))
    }
}
