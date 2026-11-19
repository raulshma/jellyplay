package com.raulshma.jellyplay.feature.music

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import com.raulshma.jellyplay.feature.music.albums.AlbumsViewModel
import com.raulshma.jellyplay.feature.music.artists.ArtistsViewModel
import com.raulshma.jellyplay.feature.music.browse.MusicBrowseViewModel
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import com.raulshma.jellyplay.feature.music.genres.GenresViewModel
import com.raulshma.jellyplay.feature.music.playlists.PlaylistCommandError
import com.raulshma.jellyplay.feature.music.playlists.PlaylistDialogState
import com.raulshma.jellyplay.feature.music.playlists.PlaylistsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule

/**
 * Shared harness for the small music list ViewModels (playlists, browse,
 * albums, artists, genres): each pins its load/error/imageUrl state and its
 * sort/image seams — dense-list artwork always resolves through
 * [ImageUrlProvider.MUSIC_MAX_WIDTH].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository: PlaylistRepository = mockk()

    private lateinit var viewModel: PlaylistsViewModel

    private val playlists = listOf(
        Playlist(id = "pl1", name = "My Playlist"),
        Playlist(id = "pl2", name = "Other", canEdit = false, canDelete = false),
    )

    @BeforeTest
    fun setUp() {
        coEvery { playlistRepository.getPlaylists(100) } returns Result.success(playlists)
        viewModel = PlaylistsViewModel(playlistRepository)
    }

    @Test
    fun init_loadsPlaylists() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        assertEquals(playlists, viewModel.playlists.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.commandError)
    }

    @Test
    fun load_failure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { playlistRepository.getPlaylists(100) } returns Result.failure(RuntimeException("boom"))

        viewModel.load()
        advanceUntilIdle()

        assertEquals("boom", viewModel.loadError.value?.message)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun openEditDialog_readOnlyPlaylist_setsErrorAndKeepsDialogClosed() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.openEditDialog(playlists[1])

        assertEquals(PlaylistCommandError.Declared.ReadOnly, viewModel.commandError)
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
    }

    @Test
    fun openEditDialog_editablePlaylist_opensDialog() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.openEditDialog(playlists[0])

        assertEquals(PlaylistDialogState.Edit(playlists[0]), viewModel.dialogState)
    }

    @Test
    fun createPlaylist_blankName_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.createPlaylist("   ", "overview")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.createPlaylist(any(), any(), any(), any()) }
    }

    @Test
    fun createPlaylist_success_closesDialogAndReloads() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.createPlaylist("New", null, any(), any()) } returns Result.success("id1")

        viewModel.openCreateDialog()
        viewModel.createPlaylist("New", "  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepository.createPlaylist("New", null, any(), any()) }
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
        assertFalse(viewModel.isMutating)
    }

    @Test
    fun createPlaylist_failure_setsFallbackErrorAndKeepsDialogOpen() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.createPlaylist("New", null, any(), any()) } returns
            Result.failure(RuntimeException())

        viewModel.openCreateDialog()
        viewModel.createPlaylist("New", "")
        advanceUntilIdle()

        assertEquals(PlaylistCommandError.Declared.CreateFailed, viewModel.commandError)
        assertEquals(PlaylistDialogState.Create(), viewModel.dialogState)
        assertFalse(viewModel.isMutating)
    }

    // ── Delete dialog + mutation ─────────────────────────────────────────────

    @Test
    fun openDeleteDialog_readOnlyPlaylist_setsErrorAndKeepsDialogClosed() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.openDeleteDialog(playlists[1])

        assertEquals(PlaylistCommandError.Declared.NotDeletable, viewModel.commandError)
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
    }

    @Test
    fun openDeleteDialog_deletablePlaylist_opensDialog() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.openDeleteDialog(playlists[0])

        assertEquals(PlaylistDialogState.Delete(playlists[0]), viewModel.dialogState)
    }

    @Test
    fun dismissDialog_returnsToNone() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        viewModel.openCreateDialog()

        viewModel.dismissDialog()

        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
    }

    @Test
    fun deletePlaylist_success_closesDialogAndReloads() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.deletePlaylist("pl1") } returns Result.success(Unit)

        viewModel.openDeleteDialog(playlists[0])
        viewModel.deletePlaylist(playlists[0])
        advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepository.deletePlaylist("pl1") }
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
        assertFalse(viewModel.isMutating)
    }

    @Test
    fun deletePlaylist_failure_setsFallbackError() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.deletePlaylist("pl1") } returns Result.failure(RuntimeException("locked"))

        viewModel.deletePlaylist(playlists[0])
        advanceUntilIdle()

        assertEquals(PlaylistCommandError.Reported("locked"), viewModel.commandError)
        assertFalse(viewModel.isMutating)
    }

    // ── Update (rename) ──────────────────────────────────────────────────────

    @Test
    fun updatePlaylist_blankName_isIgnored() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.updatePlaylist("pl1", "   ", "overview")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.updatePlaylist(any(), any(), any(), any()) }
    }

    @Test
    fun updatePlaylist_success_trimsWritesClosesDialogAndReloads() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.updatePlaylist("pl1", "Renamed", null, any()) } returns Result.success(Unit)

        viewModel.openEditDialog(playlists[0])
        viewModel.updatePlaylist("pl1", "  Renamed  ", "   ")
        advanceUntilIdle()

        // Blank overview collapses to null; the dialog closes and the list reloads.
        coVerify(exactly = 1) { playlistRepository.updatePlaylist("pl1", "Renamed", null, any()) }
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
        assertFalse(viewModel.isMutating)
    }

    @Test
    fun updatePlaylist_failure_setsFallbackError() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { playlistRepository.updatePlaylist(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException())

        viewModel.updatePlaylist("pl1", "Renamed", "")
        advanceUntilIdle()

        assertEquals(PlaylistCommandError.Declared.UpdateFailed, viewModel.commandError)
        assertFalse(viewModel.isMutating)
    }

    // ── Error lifecycle ──────────────────────────────────────────────────────

    @Test
    fun clearError_resetsTheErrorState() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        viewModel.openEditDialog(playlists[1]) // read-only → error
        assertEquals(PlaylistCommandError.Declared.ReadOnly, viewModel.commandError)

        viewModel.clearError()

        assertEquals(null, viewModel.commandError)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MusicBrowseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraryFeed: LibraryFeed = mockk()
    private val playlistRepository: PlaylistRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: MusicBrowseViewModel

    @BeforeTest
    fun setUp() {
        every { libraryFeed.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        coEvery { libraryFeed.getGenres(null, false) } returns
            Result.success(listOf(Genre(id = "g1", name = "Rock")))
        coEvery { playlistRepository.getPlaylists() } returns Result.success(emptyList())
        viewModel = MusicBrowseViewModel(libraryFeed, playlistRepository, imageUrlProvider, ArtworkUrls.EMPTY)
    }

    @Test
    fun init_loadsGenres() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        assertEquals(listOf(Genre(id = "g1", name = "Rock")), viewModel.genres.value)
    }

    @Test
    fun init_loadsPlaylists() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.playlists.value)
    }

    @Test
    fun setAlbumSort_updatesSortFlow() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.setAlbumSort(MusicSortOption.DATE_ADDED)

        assertEquals(MusicSortOption.DATE_ADDED, viewModel.albumSort.value)
    }

    @Test
    fun perTabSorts_stayIndependent() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.setArtistSort(MusicSortOption.YEAR)
        viewModel.setTrackSort(MusicSortOption.RANDOM)

        // Mutating one tab's sort must never leak into the other tabs'.
        assertEquals(MusicSortOption.YEAR, viewModel.artistSort.value)
        assertEquals(MusicSortOption.NAME, viewModel.albumSort.value)
        assertEquals(MusicSortOption.RANDOM, viewModel.trackSort.value)
    }

    @Test
    fun pagedStreams_queryPerMediaTypeWithTheTabSort() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        // Each tab is a distinct flatMapLatest generation over its own media
        // type; the NAME default travels as SORT_NAME.
        viewModel.artists.first()
        viewModel.albums.first()
        viewModel.tracks.first()

        verify(exactly = 1) {
            libraryFeed.getMediaItemsPaged(
                any(),
                com.raulshma.jellyplay.core.model.LibraryFilters(
                    mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.ARTIST),
                    sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
                ),
                any(),
                any(),
            )
        }
        verify(exactly = 1) {
            libraryFeed.getMediaItemsPaged(
                any(),
                com.raulshma.jellyplay.core.model.LibraryFilters(
                    mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.ALBUM),
                    sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
                ),
                any(),
                any(),
            )
        }
        verify(exactly = 1) {
            libraryFeed.getMediaItemsPaged(
                any(),
                com.raulshma.jellyplay.core.model.LibraryFilters(
                    mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                    sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
                ),
                any(),
                any(),
            )
        }
    }

    @Test
    fun setTrackSort_requeriesTheTrackPager() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.setTrackSort(MusicSortOption.YEAR)
        viewModel.tracks.first()

        verify(exactly = 1) {
            libraryFeed.getMediaItemsPaged(
                any(),
                com.raulshma.jellyplay.core.model.LibraryFilters(
                    mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                    sortBy = com.raulshma.jellyplay.core.model.SortOption.YEAR_DESC,
                ),
                any(),
                any(),
            )
        }
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }

    // ── List-sourced tab ladders (SimpleListCollection; the pages' declared gain) ──

    @Test
    fun genresFailure_surfacesTheFailureAndClearsLoading() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { libraryFeed.getGenres(null, false) } returns Result.failure(RuntimeException())
        val failing = MusicBrowseViewModel(libraryFeed, playlistRepository, imageUrlProvider, ArtworkUrls.EMPTY)
        advanceUntilIdle()

        // Message-less failures surface the Throwable itself; the ladder
        // renders the kind's declared errorFallbackRes under a null message.
        val genresError = failing.genresError.value
        assertNotNull(genresError)
        assertNull(genresError.message)
        assertFalse(failing.genresLoading.value)
    }

    @Test
    fun playlistsFailure_setsErrorAndClearsLoading() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { playlistRepository.getPlaylists() } returns Result.failure(RuntimeException("no pl"))
        val failing = MusicBrowseViewModel(libraryFeed, playlistRepository, imageUrlProvider, ArtworkUrls.EMPTY)
        advanceUntilIdle()

        assertEquals("no pl", failing.playlistsError.value?.message)
        assertFalse(failing.playlistsLoading.value)
    }

    @Test
    fun refreshGenres_forcesTheServerRead() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coEvery { libraryFeed.getGenres(null, true) } returns Result.success(emptyList())

        viewModel.refreshGenres()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryFeed.getGenres(null, true) }
        assertFalse(viewModel.genresLoading.value)
        assertNull(viewModel.genresError.value)
    }

    @Test
    fun refreshPlaylists_requeriesThePlaylistSource() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()

        viewModel.refreshPlaylists()
        advanceUntilIdle()

        coVerify(exactly = 2) { playlistRepository.getPlaylists() } // init load + forced refresh
        assertFalse(viewModel.playlistsLoading.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraryFeed: LibraryFeed = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: AlbumsViewModel

    @BeforeTest
    fun setUp() {
        every { libraryFeed.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        viewModel = AlbumsViewModel(libraryFeed, imageUrlProvider, ArtworkUrls.EMPTY)
    }

    @Test
    fun defaultSortIsName_andSetSortUpdatesSelection() {
        assertEquals(MusicSortOption.NAME, viewModel.selectedSort.value)

        viewModel.setSort(MusicSortOption.YEAR)

        assertEquals(MusicSortOption.YEAR, viewModel.selectedSort.value)
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraryFeed: LibraryFeed = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: ArtistsViewModel

    @BeforeTest
    fun setUp() {
        every { libraryFeed.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        viewModel = ArtistsViewModel(libraryFeed, imageUrlProvider, ArtworkUrls.EMPTY)
    }

    @Test
    fun defaultSortIsName_andSetSortUpdatesSelection() {
        assertEquals(MusicSortOption.NAME, viewModel.selectedSort.value)

        viewModel.setSort(MusicSortOption.DATE_ADDED)

        assertEquals(MusicSortOption.DATE_ADDED, viewModel.selectedSort.value)
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GenresViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraryFeed: LibraryFeed = mockk()

    private lateinit var viewModel: GenresViewModel

    private val genres = listOf(Genre(id = "g1", name = "Rock"), Genre(id = "g2", name = "Jazz"))


    /**
     * viewModelScope runs on `Dispatchers.Main.immediate`, which under the test
     * Main dispatcher executes the init load INLINE — the getGenres answer must
     * be recorded before construction.
     */
    private fun createViewModel(getGenres: Result<List<Genre>>) {
        coEvery { libraryFeed.getGenres(null, false) } returns getGenres
        viewModel = GenresViewModel(libraryFeed)
    }

    @Test
    fun init_loadsGenresAndClearsLoading() = runTest(mainDispatcherRule.testDispatcher) {
        createViewModel(Result.success(genres))
        advanceUntilIdle()

        assertEquals(genres, viewModel.genres.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun load_failure_surfacesTheThrowable() = runTest(mainDispatcherRule.testDispatcher) {
        // Null exception message → the failure itself surfaces; the ladder
        // renders the GENRES kind's declared fallback at render time.
        createViewModel(Result.failure(RuntimeException()))
        advanceUntilIdle()

        val error = viewModel.error.value
        assertNotNull(error)
        assertNull(error.message)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun refresh_forcesReload() = runTest(mainDispatcherRule.testDispatcher) {
        createViewModel(Result.success(genres))
        advanceUntilIdle()
        coEvery { libraryFeed.getGenres(null, true) } returns Result.success(genres)

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryFeed.getGenres(null, true) }
    }
}
