package com.raulshma.jellyplay.feature.music.smartplaylist

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.CriterionOperator
import com.raulshma.jellyplay.core.model.CriterionType
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaylistCriterion
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.core.model.SmartPlaylistSort
import com.raulshma.jellyplay.core.model.SortOption
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertTrue

/**
 * Pins the smart-playlist contract: presets + custom merge on init, the
 * generate pipeline's server-filter split (genre criteria → [LibraryFilters],
 * the `favorites` shortcut → getFavorites, PLAY_COUNT "0" → client-side
 * unplayed filter, DATE_ADDED/PLAY_COUNT sorts handled server-side), and the
 * custom-playlist mutation guards (blank names never upsert, only `custom-`
 * ids ever delete).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()
    private val smartPlaylistRepository: SmartPlaylistRepository = mockk()

    private val customFlow = MutableStateFlow(emptyList<SmartPlaylist>())

    private lateinit var viewModel: SmartPlaylistsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { smartPlaylistRepository.observeSmartPlaylists() } returns customFlow
        viewModel = SmartPlaylistsViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
            smartPlaylistRepository = smartPlaylistRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(
        id: String,
        name: String = id,
        genres: List<String> = emptyList(),
        rating: Float? = null,
        played: Boolean = false,
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.AUDIO,
        genres = genres,
        communityRating = rating,
        isPlayed = played,
    )

    /**
     * generatePlaylist hops the item pipeline onto Dispatchers.Default; the
     * result lands back on the test Main queue asynchronously — poll with real
     * time (the virtual scheduler cannot see the returning continuation).
     */
    private fun TestScope.awaitGenerated(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        advanceUntilIdle()
        assertTrue(condition(), "Condition not met within timeout")
    }

    @Test
    fun init_mergesPresetsWithCustomPlaylists() = runTest(mainDispatcher) {
        val custom = SmartPlaylist(id = "custom-1", name = "Mine", criteria = emptyList())
        customFlow.value = listOf(custom)

        advanceUntilIdle()

        assertEquals(SmartPlaylistsViewModel.defaultPlaylists + listOf(custom), viewModel.playlists)
    }

    @Test
    fun generatePlaylist_favoritesShortcut_queriesFavorites() = runTest(mainDispatcher) {
        val favorites = SmartPlaylistsViewModel.defaultPlaylists.first { it.id == "favorites" }
        val high = track("high", rating = 5f)
        val low = track("low", rating = 3f)
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.AUDIO), limit = 50) } returns
            Result.success(SearchResult(listOf(low, high), 2, 0))

        viewModel.generatePlaylist(favorites)
        awaitGenerated { viewModel.generatedItems.size == 2 }

        assertEquals(listOf(high, low), viewModel.generatedItems)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun generatePlaylist_genreCriteria_filterServerSideAndClientSide() = runTest(mainDispatcher) {
        val playlist = SmartPlaylist(
            id = "rock",
            name = "Rock",
            criteria = listOf(PlaylistCriterion(CriterionType.GENRE, "Rock", CriterionOperator.EQUALS)),
            sortBy = SmartPlaylistSort.TITLE,
        )
        val b = track("g1", name = "B", genres = listOf("Rock"))
        val a = track("g2", name = "A", genres = listOf("ROCK")) // case-insensitive match
        val pop = track("g3", name = "C", genres = listOf("Pop"))
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(b, a, pop), 3, 0))

        viewModel.generatePlaylist(playlist)
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        assertEquals(listOf(a, b), viewModel.generatedItems)
        coVerify(exactly = 1) {
            mediaRepository.getMediaItems(
                null,
                LibraryFilters(
                    mediaTypes = listOf(MediaType.AUDIO),
                    genres = listOf("Rock"),
                    sortBy = SortOption.SORT_NAME,
                ),
                null,
                0,
                50,
                ItemKindFilter.TOP_LEVEL,
            )
        }
    }

    @Test
    fun generatePlaylist_unplayedPreset_filtersPlayedTracks() = runTest(mainDispatcher) {
        val unplayed = SmartPlaylistsViewModel.defaultPlaylists.first { it.id == "unplayed" }
        val played = track("p1", played = true, genres = listOf("x"))
        val fresh = track("p2", played = false, genres = listOf("x"))
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(played, fresh), 2, 0))

        viewModel.generatePlaylist(unplayed)
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        assertEquals(listOf(fresh), viewModel.generatedItems)
    }

    @Test
    fun generatePlaylist_failure_setsErrorAndStopsLoading() = runTest(mainDispatcher) {
        val playlist = SmartPlaylist(id = "rock", name = "Rock", criteria = emptyList())
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))

        viewModel.generatePlaylist(playlist)
        advanceUntilIdle()

        assertEquals("boom", viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun playAll_delegatesGeneratedItemsWithStartIndex() = runTest(mainDispatcher) {
        val playlist = SmartPlaylist(
            id = "rock",
            name = "Rock",
            criteria = listOf(PlaylistCriterion(CriterionType.GENRE, "Rock", CriterionOperator.EQUALS)),
            sortBy = SmartPlaylistSort.TITLE,
        )
        val a = track("g1", name = "A", genres = listOf("Rock"))
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(a), 1, 0))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)

        viewModel.generatePlaylist(playlist)
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }
        viewModel.playAll(startIndex = 1)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(listOf(a), 1, false, null, ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun clearGenerated_resetsItems() = runTest(mainDispatcher) {
        val playlist = SmartPlaylist(
            id = "rock",
            name = "Rock",
            criteria = listOf(PlaylistCriterion(CriterionType.GENRE, "Rock", CriterionOperator.EQUALS)),
            sortBy = SmartPlaylistSort.TITLE,
        )
        val a = track("g1", name = "A", genres = listOf("Rock"))
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(a), 1, 0))
        viewModel.generatePlaylist(playlist)
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        viewModel.clearGenerated()

        assertTrue(viewModel.generatedItems.isEmpty())
    }

    @Test
    fun createCustomPlaylist_validatesName() = runTest(mainDispatcher) {
        val captured = slot<SmartPlaylist>()
        coEvery { smartPlaylistRepository.upsert(capture(captured)) } just Runs

        viewModel.createCustomPlaylist("   ", emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { smartPlaylistRepository.upsert(any()) }

        viewModel.createCustomPlaylist(
            name = "  Chill  ",
            criteria = listOf(PlaylistCriterion(CriterionType.GENRE, "chill")),
            maxItems = 10,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { smartPlaylistRepository.upsert(any()) }
        assertEquals("Chill", captured.captured.name)
        assertTrue(captured.captured.id.startsWith("custom-"))
        assertEquals(10, captured.captured.maxItems)
    }

    @Test
    fun deleteCustomPlaylist_onlyDeletesCustomIds() = runTest(mainDispatcher) {
        coEvery { smartPlaylistRepository.delete(any()) } just Runs

        viewModel.deleteCustomPlaylist(
            SmartPlaylistsViewModel.defaultPlaylists.first(), // preset id → guarded
        )
        advanceUntilIdle()
        coVerify(exactly = 0) { smartPlaylistRepository.delete(any()) }

        viewModel.deleteCustomPlaylist(SmartPlaylist(id = "custom-9", name = "X", criteria = emptyList()))
        advanceUntilIdle()

        coVerify(exactly = 1) { smartPlaylistRepository.delete("custom-9") }
    }
}
