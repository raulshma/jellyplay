@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.raulshma.jellyplay.core.model.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var libraryStore: LibraryStore

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)

        every { imageUrlProvider.getImageUrl(any(), any()) } returns "https://example.com/image.jpg"
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())

        // Stub the init-block repository calls with real Result/Flow values so
        // the relaxed mock's default Result mock doesn't ClassCast inside the
        // VM's loadFolders()/loadGenres()/loadTags() collectors.
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList<LibraryFolder>())
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
    }

    private fun createViewModel(): LibraryViewModel = LibraryViewModel(
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        libraryStore = libraryStore,
    )

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

        val folder = vm.selectedFolder.value
        assertEquals("lib-movies", folder?.id)
        assertEquals("Latest Movies", vm.title.value)
        assertEquals(SortOption.DATE_ADDED, vm.filters.value.sortBy)
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

        assertNull(vm.selectedFolder.value)
        assertEquals(SortOption.DATE_ADDED, vm.filters.value.sortBy)
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

        assertEquals(emptyList<MediaType>(), vm.filters.value.mediaTypes)
        assertEquals(SortOption.DATE_ADDED, vm.filters.value.sortBy)
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

        assertEquals(emptyList<MediaType>(), vm.filters.value.mediaTypes)
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

        assertEquals(emptyList<MediaType>(), vm.filters.value.mediaTypes)
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

        assertEquals(listOf(MediaType.MOVIE), vm.filters.value.mediaTypes)
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
        assertNotNull(vm.sectionContext.value)
        assertEquals("Latest Shows", vm.title.value)
        assertNotNull(vm.selectedFolder.value)

        vm.clearSectionMode()

        assertNull(vm.sectionContext.value)
        assertNull(vm.title.value)
        assertNull(vm.selectedFolder.value)
        assertEquals(LibraryFilters(), vm.filters.value)
    }

    @Test
    fun `clearSectionMode is a no-op when not in section mode`() = runTest {
        val vm = createViewModel()
        // Not in section mode — clearing should be a safe no-op.
        vm.clearSectionMode()
        assertNull(vm.sectionContext.value)
        assertEquals(LibraryFilters(), vm.filters.value)
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
        assertEquals(SortOption.RATING, vm.filters.value.sortBy)
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

        assertEquals(listOf(MediaType.MOVIE), vm.filters.value.mediaTypes)
        assertEquals(SortOption.RATING, vm.filters.value.sortBy)
        assertEquals(PlayedStatus.UNPLAYED, vm.filters.value.playedStatus)
        assertEquals(listOf("fav"), vm.filters.value.tags)
    }

    @Test
    fun `setPosterSize persists via store`() = runTest {
        coEvery { libraryStore.setLibraryPosterSize(any()) } returns Unit

        val vm = createViewModel()
        vm.setPosterSize(1.2f)
        advanceUntilIdle()

        coVerify { libraryStore.setLibraryPosterSize(1.2f) }
    }

    @Test
    fun `default tab mode has null title`() = runTest {
        val vm = createViewModel()
        assertNull(vm.title.value)
        assertNull(vm.sectionContext.value)
    }
}
