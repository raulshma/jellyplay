package com.raulshma.jellyplay.feature.music

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.feature.music.albums.AlbumsViewModel
import com.raulshma.jellyplay.feature.music.albums.MusicSortOption
import com.raulshma.jellyplay.feature.music.artists.ArtistsViewModel
import com.raulshma.jellyplay.feature.music.browse.MusicBrowseViewModel
import com.raulshma.jellyplay.feature.music.genres.GenresViewModel
import com.raulshma.jellyplay.feature.music.playlists.PlaylistDialogState
import com.raulshma.jellyplay.feature.music.playlists.PlaylistsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Shared harness for the small music list ViewModels (playlists, browse,
 * albums, artists, genres): each pins its load/error/imageUrl state and its
 * sort/image seams — dense-list artwork always resolves through
 * [ImageUrlProvider.MUSIC_MAX_WIDTH].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()

    private lateinit var viewModel: PlaylistsViewModel

    private val playlists = listOf(
        Playlist(id = "pl1", name = "My Playlist"),
        Playlist(id = "pl2", name = "Other", canEdit = false, canDelete = false),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery { mediaRepository.getPlaylists(100) } returns Result.success(playlists)
        viewModel = PlaylistsViewModel(mediaRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsPlaylists() = runTest(mainDispatcher) {
        advanceUntilIdle()

        assertEquals(playlists, viewModel.playlists)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun load_failure_setsError() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getPlaylists(100) } returns Result.failure(RuntimeException("boom"))

        viewModel.load()
        advanceUntilIdle()

        assertEquals("boom", viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun openEditDialog_readOnlyPlaylist_setsErrorAndKeepsDialogClosed() = runTest(mainDispatcher) {
        advanceUntilIdle()

        viewModel.openEditDialog(playlists[1])

        assertEquals("This playlist is read-only", viewModel.error)
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
    }

    @Test
    fun openEditDialog_editablePlaylist_opensDialog() = runTest(mainDispatcher) {
        advanceUntilIdle()

        viewModel.openEditDialog(playlists[0])

        assertEquals(PlaylistDialogState.Edit(playlists[0]), viewModel.dialogState)
    }

    @Test
    fun createPlaylist_blankName_isIgnored() = runTest(mainDispatcher) {
        advanceUntilIdle()

        viewModel.createPlaylist("   ", "overview")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createPlaylist(any(), any(), any(), any()) }
    }

    @Test
    fun createPlaylist_success_closesDialogAndReloads() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coEvery { mediaRepository.createPlaylist("New", null, any(), any()) } returns Result.success("id1")

        viewModel.openCreateDialog()
        viewModel.createPlaylist("New", "  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createPlaylist("New", null, any(), any()) }
        assertEquals(PlaylistDialogState.None, viewModel.dialogState)
        assertFalse(viewModel.isMutating)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MusicBrowseViewModelTest {
    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: MusicBrowseViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        coEvery { mediaRepository.getGenres() } returns
            Result.success(listOf(Genre(id = "g1", name = "Rock")))
        coEvery { mediaRepository.getPlaylists() } returns Result.success(emptyList())
        viewModel = MusicBrowseViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsGenres() = runTest(mainDispatcher) {
        advanceUntilIdle()

        assertEquals(listOf(Genre(id = "g1", name = "Rock")), viewModel.genres.value)
    }

    @Test
    fun setAlbumSort_updatesSortFlow() = runTest(mainDispatcher) {
        advanceUntilIdle()

        viewModel.setAlbumSort(MusicSortOption.DATE_ADDED)

        assertEquals(MusicSortOption.DATE_ADDED, viewModel.albumSort.value)
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: AlbumsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        viewModel = AlbumsViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var viewModel: ArtistsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaRepository.getMediaItemsPaged(any(), any(), any(), any()) } returns
            flowOf(PagingData.empty<MediaItem>())
        viewModel = ArtistsViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultSortIsName_andSetSortUpdatesSelection() {
        assertEquals(MusicSortOption.NAME, viewModel.selectedSort)

        viewModel.setSort(MusicSortOption.DATE_ADDED)

        assertEquals(MusicSortOption.DATE_ADDED, viewModel.selectedSort)
    }

    @Test
    fun getImageUrl_delegatesWithMusicMaxWidth() {
        every { imageUrlProvider.getImageUrl("i1", ImageUrlProvider.MUSIC_MAX_WIDTH) } returns "img"

        assertEquals("img", viewModel.getImageUrl("i1"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GenresViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()

    private lateinit var viewModel: GenresViewModel

    private val genres = listOf(Genre(id = "g1", name = "Rock"), Genre(id = "g2", name = "Jazz"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * viewModelScope runs on `Dispatchers.Main.immediate`, which under the test
     * Main dispatcher executes the init load INLINE — the getGenres answer must
     * be recorded before construction.
     */
    private fun createViewModel(getGenres: Result<List<Genre>>) {
        coEvery { mediaRepository.getGenres(null, false) } returns getGenres
        viewModel = GenresViewModel(mediaRepository)
    }

    @Test
    fun init_loadsGenresAndClearsLoading() = runTest(mainDispatcher) {
        createViewModel(Result.success(genres))
        advanceUntilIdle()

        assertEquals(genres, viewModel.genres.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun load_failure_setsFallbackError() = runTest(mainDispatcher) {
        // Null exception message → the "Failed to load genres" fallback pins.
        createViewModel(Result.failure(RuntimeException()))
        advanceUntilIdle()

        assertEquals("Failed to load genres", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun refresh_forcesReload() = runTest(mainDispatcher) {
        createViewModel(Result.success(genres))
        advanceUntilIdle()
        coEvery { mediaRepository.getGenres(null, true) } returns Result.success(genres)

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getGenres(null, true) }
    }
}
