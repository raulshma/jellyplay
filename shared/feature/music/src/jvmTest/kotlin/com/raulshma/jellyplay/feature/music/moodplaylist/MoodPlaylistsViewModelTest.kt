package com.raulshma.jellyplay.feature.music.moodplaylist

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistPreference
import com.raulshma.jellyplay.core.model.MoodPlaylistSort
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import com.raulshma.jellyplay.core.model.SearchResult
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
 * Pins the mood-playlist contract: presets + custom + favorite ids merged on
 * init, the generate pipeline (genre keyword contains-match, excluded-genre
 * veto, minRating gate — null rating counts as 0f — then client sort and
 * maxItems take), favorite toggling via setPreference(!isFavorite), and the
 * custom-playlist mutation guards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodPlaylistsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()
    private val moodPlaylistRepository: MoodPlaylistRepository = mockk()

    private val customFlow = MutableStateFlow(emptyList<MoodPlaylist>())
    private val preferencesFlow = MutableStateFlow(emptyList<MoodPlaylistPreference>())

    private lateinit var viewModel: MoodPlaylistsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { moodPlaylistRepository.observeMoodPlaylists() } returns customFlow
        every { moodPlaylistRepository.observePreferences() } returns preferencesFlow
        viewModel = MoodPlaylistsViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
            moodPlaylistRepository = moodPlaylistRepository,
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
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.AUDIO,
        genres = genres,
        communityRating = rating,
    )

    private fun moodPlaylist(
        id: String = "mood",
        keywords: List<String> = listOf("rock"),
        excluded: List<String> = emptyList(),
        minRating: Float? = null,
        sortBy: MoodPlaylistSort = MoodPlaylistSort.TITLE,
        maxItems: Int = 50,
    ) = MoodPlaylist(
        id = id,
        name = "Mood",
        emoji = "🎸",
        description = "",
        genreKeywords = keywords,
        excludedGenres = excluded,
        minRating = minRating,
        sortBy = sortBy,
        maxItems = maxItems,
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
    fun init_mergesPresetsCustomAndFavoriteIds() = runTest(mainDispatcher) {
        val custom = moodPlaylist(id = "custom-1")
        customFlow.value = listOf(custom)
        preferencesFlow.value = listOf(MoodPlaylistPreference(playlistId = "happy", isFavorite = true))

        advanceUntilIdle()

        assertEquals(MoodPlaylistsPreset.all + listOf(custom), viewModel.playlists)
        assertEquals(setOf("happy"), viewModel.favoritePlaylistIds)
    }

    @Test
    fun generatePlaylist_filtersByKeywordExclusionAndRating() = runTest(mainDispatcher) {
        val playlist = moodPlaylist(keywords = listOf("rock"), excluded = listOf("metal"), minRating = 4f)
        val keep = track("k", name = "Keep", genres = listOf("Rock"), rating = 5f)
        val belowMin = track("l", name = "Lower", genres = listOf("rock"), rating = 3f)
        val excluded = track("e", name = "Excluded", genres = listOf("Rock", "Metal"), rating = 5f)
        val noMatch = track("n", name = "NoMatch", genres = listOf("Pop"), rating = 5f)
        val unrated = track("nc", name = "NoRating", genres = listOf("Rock"), rating = null)
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(keep, belowMin, excluded, noMatch, unrated), 5, 0))

        viewModel.generatePlaylist(playlist)
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        assertEquals(listOf(keep), viewModel.generatedItems)
        assertEquals(playlist, viewModel.selectedPlaylist)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun generatePlaylist_sortsByTitleAndTakesMaxItems() = runTest(mainDispatcher) {
        val playlist = moodPlaylist(sortBy = MoodPlaylistSort.TITLE, maxItems = 2)
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(
                SearchResult(
                    listOf(
                        track("c", name = "C", genres = listOf("Rock"), rating = 5f),
                        track("a", name = "A", genres = listOf("rock"), rating = 5f),
                        track("b", name = "B", genres = listOf("Rock"), rating = 5f),
                    ),
                    3,
                    0,
                ),
            )

        viewModel.generatePlaylist(playlist)
        awaitGenerated { viewModel.generatedItems.size == 2 }

        assertEquals(listOf("a", "b"), viewModel.generatedItems.map { it.id })
    }

    @Test
    fun generatePlaylist_failure_setsErrorAndStopsLoading() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))

        viewModel.generatePlaylist(moodPlaylist())
        advanceUntilIdle()

        assertEquals("boom", viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun clearGenerated_resetsItemsAndSelection() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(track("a", genres = listOf("rock"))), 1, 0))
        viewModel.generatePlaylist(moodPlaylist())
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        viewModel.clearGenerated()

        assertTrue(viewModel.generatedItems.isEmpty())
        assertNull(viewModel.selectedPlaylist)
    }

    @Test
    fun playAll_delegatesGeneratedItemsWithStartIndex() = runTest(mainDispatcher) {
        val kept = track("a", genres = listOf("rock"))
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.success(SearchResult(listOf(kept), 1, 0))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        viewModel.generatePlaylist(moodPlaylist())
        awaitGenerated { viewModel.generatedItems.isNotEmpty() }

        viewModel.playAll(startIndex = 1)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(listOf(kept), 1, false, null, ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun toggleFavorite_flipsTheCurrentFavoriteState() = runTest(mainDispatcher) {
        preferencesFlow.value = listOf(MoodPlaylistPreference(playlistId = "happy", isFavorite = true))
        advanceUntilIdle()
        coEvery { moodPlaylistRepository.setPreference(any(), any(), any()) } just Runs

        viewModel.toggleFavorite(MoodPlaylistsPreset.all.first { it.id == "happy" })
        viewModel.toggleFavorite(MoodPlaylistsPreset.all.first { it.id == "chill" })
        advanceUntilIdle()

        coVerify(exactly = 1) { moodPlaylistRepository.setPreference("happy", true, false) }
        coVerify(exactly = 1) { moodPlaylistRepository.setPreference("chill", true, true) }
    }

    @Test
    fun createCustomPlaylist_validatesNameAndKeywords() = runTest(mainDispatcher) {
        val captured = slot<MoodPlaylist>()
        coEvery { moodPlaylistRepository.upsert(capture(captured)) } just Runs

        viewModel.createCustomPlaylist(name = "   ", emoji = "🎸", description = "", genreKeywords = listOf("rock"))
        viewModel.createCustomPlaylist(name = "Focus", emoji = "🎸", description = "", genreKeywords = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { moodPlaylistRepository.upsert(any()) }

        viewModel.createCustomPlaylist(
            name = "  Chill  ",
            emoji = "  ",
            description = " desc ",
            genreKeywords = listOf("chill"),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { moodPlaylistRepository.upsert(any()) }
        assertEquals("Chill", captured.captured.name)
        assertEquals("🎵", captured.captured.emoji)
        assertTrue(captured.captured.id.startsWith("custom-"))
    }

    @Test
    fun deleteCustomPlaylist_onlyDeletesCustomIds() = runTest(mainDispatcher) {
        coEvery { moodPlaylistRepository.delete(any()) } just Runs

        viewModel.deleteCustomPlaylist(MoodPlaylistsPreset.all.first()) // preset id → guarded
        advanceUntilIdle()
        coVerify(exactly = 0) { moodPlaylistRepository.delete(any()) }

        viewModel.deleteCustomPlaylist(moodPlaylist(id = "custom-9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { moodPlaylistRepository.delete("custom-9") }
    }
}
