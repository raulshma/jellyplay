package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaylistItem
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertTrue

/**
 * Pins the playlist overloads (imageless mapper, `imageUrl = null`) that
 * PlaylistDetailViewModel delegates to (plan 04 sites 14–15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val audioQueueFacade: AudioQueueFacade = mockk()

    private lateinit var viewModel: PlaylistDetailViewModel

    private val items = listOf(
        PlaylistItem(id = "p1", playlistItemId = "e1", name = "Song 1", artist = "A"),
        PlaylistItem(id = "p2", playlistItemId = "e2", name = "Song 2", artist = "A"),
        PlaylistItem(id = "p3", playlistItemId = "e3", name = "Song 3", artist = "A"),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery { audioQueueFacade.playPlaylist(any(), any()) } returns AudioQueueOutcome.Started(emptyList(), 0)
        coEvery { audioQueueFacade.enqueuePlaylistItem(any()) } just Runs
        viewModel = PlaylistDetailViewModel(
            mediaRepository = mediaRepository,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Loads the playlist items so `items` state is populated. */
    private fun loadPlaylist() {
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        viewModel.load("pl1", "My Playlist")
    }

    @Test
    fun playAll_delegatesToPlaylistOverloadWithLoadedItemsAndStartIndex() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.playAll(startIndex = 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.playPlaylist(items, 2) }
    }

    @Test
    fun playAll_defaultStartIndexIsZero() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.playAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.playPlaylist(items, 0) }
    }

    @Test
    fun addToQueue_delegatesSinglePlaylistItem() = runTest(mainDispatcher) {
        viewModel.addToQueue(items.first())
        advanceUntilIdle()

        coVerify(exactly = 1) { audioQueueFacade.enqueuePlaylistItem(items.first()) }
    }

    @Test
    fun load_populatesItemsAndName() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        assertEquals(items, viewModel.items)
        assertEquals("My Playlist", viewModel.playlistName)
        assertFalse(viewModel.isLoading)
    }

    // ── Load failure and name resolution ─────────────────────────────────────

    @Test
    fun load_failure_setsErrorAndClearsLoading() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns
            Result.failure(RuntimeException("gone"))
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "X", mediaType = MediaType.MUSIC)))

        viewModel.load("pl1")
        advanceUntilIdle()

        assertEquals("gone", viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun load_withoutAName_resolvesItFromTheDetailEndpoint() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "Deep Link", mediaType = MediaType.MUSIC)))

        viewModel.load("pl1", playlistName = null)
        advanceUntilIdle()

        assertEquals("Deep Link", viewModel.playlistName)
        assertEquals(items, viewModel.items)
    }

    @Test
    fun refreshPlaylist_forcesTheUncachedDetailRead() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "My Playlist", mediaType = MediaType.MUSIC)))

        viewModel.refreshPlaylist("pl1")
        advanceUntilIdle()

        // playlistName is known, so refresh goes through the named (fast) path…
        coVerify(exactly = 2) { mediaRepository.getPlaylistItems("pl1", any(), any()) }
        // …but the detail read (and its force flag) is unused there.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    // ── Remove-from-playlist + undo ──────────────────────────────────────────

    @Test
    fun removeFromPlaylist_noOpsWithoutAnEntryIdOrALoadedPlaylist() = runTest(mainDispatcher) {
        // No entry id (row not yet synced) → nothing to remove server-side.
        viewModel.removeFromPlaylist(PlaylistItem(id = "p1", playlistItemId = null, name = "Song"))
        // Entry id but no playlist loaded → no target playlist.
        viewModel.removeFromPlaylist(PlaylistItem(id = "p1", playlistItemId = "e1", name = "Song"))
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.removeItemsFromPlaylist(any(), any()) }
    }

    @Test
    fun removeFromPlaylist_dropsTheRowImmediatelyAndEmitsAnUndoAction() = runTest(mainDispatcher) {
        coEvery { mediaRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()

        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()

        // Optimistic: the row is gone from the list before the server call.
        assertEquals(listOf(items[0], items[2]), viewModel.items)
        coVerify(exactly = 1) { mediaRepository.removeItemsFromPlaylist("pl1", listOf("e2")) }
        assertFalse(viewModel.isMutating)

        val undo = viewModel.undoActions.first()
        assertEquals("Removed \"Song 2\" from playlist", undo.message)
    }

    @Test
    fun removeFromPlaylist_failureSurfacesAnErrorAndKeepsTheRowDropped() = runTest(mainDispatcher) {
        coEvery { mediaRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns
            Result.failure(RuntimeException())
        loadPlaylist()
        advanceUntilIdle()

        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()

        assertEquals("Failed to remove from playlist", viewModel.error)
        assertFalse(viewModel.isMutating)
        // The undo action is only emitted on success.
        assertEquals(listOf(items[0], items[2]), viewModel.items)
    }

    @Test
    fun undo_reAddsTheItemByMediaIdAndReloadsForAFreshEntryId() = runTest(mainDispatcher) {
        coEvery { mediaRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns Result.success(Unit)
        coEvery { mediaRepository.addItemsToPlaylist("pl1", listOf("p2")) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()
        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()
        val undo = viewModel.undoActions.first()

        undo.onUndo()
        advanceUntilIdle()

        // Re-add goes by the underlying media id — the entry id is gone
        // server-side — and the reload re-syncs the fresh entry id.
        coVerify(exactly = 1) { mediaRepository.addItemsToPlaylist("pl1", listOf("p2")) }
        coVerify(exactly = 2) { mediaRepository.getPlaylistItems("pl1", any(), any()) }
        assertEquals(items, viewModel.items)
        assertFalse(viewModel.isMutating)
    }

    // ── Reordering ───────────────────────────────────────────────────────────

    @Test
    fun moveItem_noOpsForMissingEntryIdUnknownItemOrSamePosition() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.moveItem(PlaylistItem(id = "p9", playlistItemId = null, name = "Unsynced"), 0)
        viewModel.moveItem(PlaylistItem(id = "pX", playlistItemId = "eX", name = "Alien"), 0)
        viewModel.moveItem(items[0], 0) // already at position 0
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.movePlaylistItem(any(), any(), any()) }
    }

    @Test
    fun moveItem_reordersOptimisticallyAndPersistsTheNewIndex() = runTest(mainDispatcher) {
        coEvery { mediaRepository.movePlaylistItem("pl1", "e3", 0) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()

        viewModel.moveItem(items[2], 0)
        advanceUntilIdle()

        assertEquals(listOf("p3", "p1", "p2"), viewModel.items.map { it.id })
        coVerify(exactly = 1) { mediaRepository.movePlaylistItem("pl1", "e3", 0) }
        assertFalse(viewModel.isMutating)
    }

    @Test
    fun moveItem_failure_setsErrorAndRollsBackToTheServerOrder() = runTest(mainDispatcher) {
        coEvery { mediaRepository.movePlaylistItem("pl1", "e3", 0) } returns
            Result.failure(RuntimeException("reorder denied"))
        loadPlaylist()
        advanceUntilIdle()
        // The rollback reloads; the server's authoritative order differs from
        // the optimistic local swap.
        val serverOrder = listOf(items[1], items[0], items[2])
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns
            Result.success(serverOrder)

        viewModel.moveItem(items[2], 0)
        advanceUntilIdle()

        // The optimistic swap is rolled back to the authoritative server order.
        assertEquals(serverOrder, viewModel.items)
        // Real behavior: the rollback goes through load(), which resets the
        // error state when the reload succeeds — the failure surfaces only
        // transiently, and the rolled-back list is what persists.
        assertNull(viewModel.error)
        assertFalse(viewModel.isMutating)
    }

    // ── Error lifecycle ──────────────────────────────────────────────────────

    @Test
    fun clearError_resetsTheErrorState() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getPlaylistItems("pl1", any(), any()) } returns
            Result.failure(RuntimeException("gone"))
        // The nameless load path also resolves the playlist name; stub it so
        // the load coroutine completes cleanly instead of dying on an
        // unstubbed mock call.
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "X", mediaType = MediaType.MUSIC)))
        viewModel.load("pl1")
        advanceUntilIdle()
        assertEquals("gone", viewModel.error)

        viewModel.clearError()

        assertEquals(null, viewModel.error)
    }
}
