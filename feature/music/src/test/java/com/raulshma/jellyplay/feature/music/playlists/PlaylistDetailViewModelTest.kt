package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pins the playlist overloads (imageless mapper, `imageUrl = null`) that
 * PlaylistDetailViewModel delegates to (plan 04 sites 14–15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mediaRepository: MediaRepository = mockk()
    private val audioQueueFacade: AudioQueueFacade = mockk()

    private lateinit var viewModel: PlaylistDetailViewModel

    private val items = listOf(
        PlaylistItem(id = "p1", playlistItemId = "e1", name = "Song 1", artist = "A"),
        PlaylistItem(id = "p2", playlistItemId = "e2", name = "Song 2", artist = "A"),
        PlaylistItem(id = "p3", playlistItemId = "e3", name = "Song 3", artist = "A"),
    )

    @Before
    fun setUp() {
        coEvery { audioQueueFacade.playPlaylist(any(), any()) } returns AudioQueueOutcome.Started(emptyList(), 0)
        coEvery { audioQueueFacade.enqueuePlaylistItem(any()) } just Runs
        viewModel = PlaylistDetailViewModel(
            mediaRepository = mediaRepository,
            audioQueueFacade = audioQueueFacade,
        )
    }

    /** Loads the playlist items so `items` state is populated. */
    private fun loadPlaylist() {
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        viewModel.load("pl1", "My Playlist")
    }

    @Test
    fun playAll_delegatesToPlaylistOverloadWithLoadedItemsAndStartIndex() = runTest(mainDispatcherRule.testDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.playAll(startIndex = 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.playPlaylist(items, 2) }
    }

    @Test
    fun playAll_defaultStartIndexIsZero() = runTest(mainDispatcherRule.testDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.playAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.playPlaylist(items, 0) }
    }

    @Test
    fun addToQueue_delegatesSinglePlaylistItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.addToQueue(items.first())
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.enqueuePlaylistItem(items.first()) }
    }

    @Test
    fun load_populatesItemsAndName() = runTest(mainDispatcherRule.testDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        assertEquals(items, viewModel.items)
        assertEquals("My Playlist", viewModel.playlistName)
    }
}
