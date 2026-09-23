package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.feature.music.MusicQueueOutcome
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.UserDataChange
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * Pins the playlist detail contract on the [com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator]
 * chassis (the album host's suite shape) plus the playlist-specific halves:
 * the title hint fast path (a hinted load never fetches the name), the
 * items-gated all-or-nothing load, the re-entry guard and its
 * reload-after-failure re-arm, the mutation-error split (survives no-op
 * re-entries, wiped when an accepted loud load starts), and the deferred
 * silent regeneration (serve-stale, no flash).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val playlistRepository: PlaylistRepository = mockk()
    private val audioQueueFacade: MusicQueuePlayer = mockk()

    private lateinit var viewModel: PlaylistDetailViewModel

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    private val items = listOf(
        PlaylistItem(id = "p1", playlistItemId = "e1", name = "Song 1", artist = "A"),
        PlaylistItem(id = "p2", playlistItemId = "e2", name = "Song 2", artist = "A"),
        PlaylistItem(id = "p3", playlistItemId = "e3", name = "Song 3", artist = "A"),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery { audioQueueFacade.playPlaylist(any(), any()) } returns MusicQueueOutcome.Started(emptyList(), 0)
        coEvery { audioQueueFacade.enqueuePlaylistItem(any()) } just Runs
        // The deferred refresher collects this for the whole VM lifetime.
        every { mediaRepository.userDataChanges } returns userDataEvents
        viewModel = PlaylistDetailViewModel(
            mediaRepository = mediaRepository,
            playlistRepository = playlistRepository,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Loads the playlist items so `items` state is populated. */
    private fun loadPlaylist() {
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
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
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns
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
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
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
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "My Playlist", mediaType = MediaType.MUSIC)))

        viewModel.refreshPlaylist("pl1")
        advanceUntilIdle()

        // playlistName is known, so refresh goes through the named (fast) path…
        coVerify(exactly = 2) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        // …but the detail read (and its force flag) is unused there.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    // ── Re-entry guard + deferred refresh (the album host's fixture shape) ───

    @Test
    fun load_onAnAlreadyLoadedPlaylistIsANoOp() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        // Back-stack re-entry re-runs the screen's LaunchedEffect; a second
        // loud load must not refetch on top of the deferred refresh's silent
        // regeneration.
        viewModel.load("pl1", "My Playlist")
        advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun load_afterAFailedLoudLoadReloads() = runTest(mainDispatcher) {
        // First loud load fails (items half): nothing on screen behind the
        // error, so the re-entry guard must not skip.
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns
            Result.failure(RuntimeException("gone"))
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.success(MediaDetail(item = MediaItem(id = "pl1", name = "X", mediaType = MediaType.MUSIC)))
        viewModel.load("pl1")
        advanceUntilIdle()
        assertEquals("gone", viewModel.error)

        // Re-entry retries the loud load and heals.
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        viewModel.load("pl1")
        advanceUntilIdle()

        coVerify(exactly = 2) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        assertNull(viewModel.error)
        assertEquals(items, viewModel.items)
    }

    @Test
    fun load_nameFailure_failsTheWholeLoadWithoutPublishingItems() = runTest(mainDispatcher) {
        // All-or-nothing: the failed name half fails the whole fetch, so the
        // error owns the screen (the screen renders ErrorScreen whenever
        // error != null && items.isEmpty()) and no half-pair is published.
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(items)
        coEvery { mediaRepository.getMediaDetail("pl1", any()) } returns
            Result.failure(RuntimeException("no title"))

        viewModel.load("pl1")
        advanceUntilIdle()

        assertEquals("no title", viewModel.error)
        assertEquals(emptyList(), viewModel.items)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun deferredRefresh_rerunsSilentlyWithoutBlankingContent() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()
        assertFalse(viewModel.isLoading)

        // A write confirmed while the playlist screen is NOT on screen only
        // marks the playlist stale.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        val freshItems = listOf(items[0], items[2])
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(freshItems)
        userDataEvents.emit(UserDataChange("user-1", listOf("p1")))
        advanceUntilIdle()

        // Re-entry fires the single deferred regeneration — force + silent:
        // the fetch runs but never drops the content into a loading state,
        // and the hinted title is kept (a silent regen must not blank it).
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 2) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals(freshItems, viewModel.items)
        assertEquals("My Playlist", viewModel.playlistName)
    }

    @Test
    fun deferredRefresh_failureKeepsLastContentInsteadOfFlashingError() = runTest(mainDispatcher) {
        loadPlaylist()
        advanceUntilIdle()

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns
            Result.failure(RuntimeException("offline blip"))
        userDataEvents.emit(UserDataChange("user-1", listOf("p1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The silent refetch failed — serve-stale-while-revalidate keeps the
        // last items/title on screen instead of flashing an error…
        coVerify(exactly = 2) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals(items, viewModel.items)

        // …and the failed silent half re-arms: the next re-entry retries.
        val freshItems = listOf(items[1])
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns Result.success(freshItems)
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        assertEquals(freshItems, viewModel.items)
    }

    // The no-stack/skip-re-arm choreography pair the album suite used to
    // re-pin is module behaviour now — DeferredFetchCoordinatorTest owns that
    // table; this suite pins the host adapter's own surfaces.

    // ── Remove-from-playlist + undo ──────────────────────────────────────────

    @Test
    fun removeFromPlaylist_noOpsWithoutAnEntryIdOrALoadedPlaylist() = runTest(mainDispatcher) {
        // No entry id (row not yet synced) → nothing to remove server-side.
        viewModel.removeFromPlaylist(PlaylistItem(id = "p1", playlistItemId = null, name = "Song"))
        // Entry id but no playlist loaded → no target playlist.
        viewModel.removeFromPlaylist(PlaylistItem(id = "p1", playlistItemId = "e1", name = "Song"))
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.removeItemsFromPlaylist(any(), any()) }
    }

    @Test
    fun removeFromPlaylist_dropsTheRowImmediatelyAndEmitsAnUndoAction() = runTest(mainDispatcher) {
        coEvery { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()

        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()

        // Optimistic: the row is gone from the list before the server call.
        assertEquals(listOf(items[0], items[2]), viewModel.items)
        coVerify(exactly = 1) { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) }
        assertFalse(viewModel.isMutating)

        val undo = viewModel.undoActions.first()
        assertEquals("Removed \"Song 2\" from playlist", undo.message)
    }

    @Test
    fun removeFromPlaylist_failureSurfacesAnErrorAndKeepsTheRowDropped() = runTest(mainDispatcher) {
        coEvery { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns
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
        coEvery { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns Result.success(Unit)
        coEvery { playlistRepository.addItemsToPlaylist("pl1", listOf("p2")) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()
        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()
        val undo = viewModel.undoActions.first()

        undo.onUndo()
        advanceUntilIdle()

        // Re-add goes by the underlying media id — the entry id is gone
        // server-side — and the reload re-syncs the fresh entry id.
        coVerify(exactly = 1) { playlistRepository.addItemsToPlaylist("pl1", listOf("p2")) }
        coVerify(exactly = 2) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
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

        coVerify(exactly = 0) { playlistRepository.movePlaylistItem(any(), any(), any()) }
    }

    @Test
    fun moveItem_reordersOptimisticallyAndPersistsTheNewIndex() = runTest(mainDispatcher) {
        coEvery { playlistRepository.movePlaylistItem("pl1", "e3", 0) } returns Result.success(Unit)
        loadPlaylist()
        advanceUntilIdle()

        viewModel.moveItem(items[2], 0)
        advanceUntilIdle()

        assertEquals(listOf("p3", "p1", "p2"), viewModel.items.map { it.id })
        coVerify(exactly = 1) { playlistRepository.movePlaylistItem("pl1", "e3", 0) }
        assertFalse(viewModel.isMutating)
    }

    @Test
    fun moveItem_failure_setsErrorAndRollsBackToTheServerOrder() = runTest(mainDispatcher) {
        coEvery { playlistRepository.movePlaylistItem("pl1", "e3", 0) } returns
            Result.failure(RuntimeException("reorder denied"))
        loadPlaylist()
        advanceUntilIdle()
        // The rollback reloads; the server's authoritative order differs from
        // the optimistic local swap.
        val serverOrder = listOf(items[1], items[0], items[2])
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns
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
    fun mutationError_survivesANoOpReEntry() = runTest(mainDispatcher) {
        coEvery { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns
            Result.failure(RuntimeException())
        loadPlaylist()
        advanceUntilIdle()
        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()
        assertEquals("Failed to remove from playlist", viewModel.error)

        // Back-stack re-entry re-runs the screen's LaunchedEffect; the guard
        // no-ops the loud load, so the standing mutation error survives it
        // (the album host's failed-mix precedent — no flash reload to hide).
        viewModel.load("pl1", "My Playlist")
        advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepository.getPlaylistItems("pl1", any(), any()) }
        assertEquals("Failed to remove from playlist", viewModel.error)
    }

    @Test
    fun mutationError_isWipedWhenAnAcceptedLoudLoadStarts() = runTest(mainDispatcher) {
        coEvery { playlistRepository.removeItemsFromPlaylist("pl1", listOf("e2")) } returns
            Result.failure(RuntimeException())
        loadPlaylist()
        advanceUntilIdle()
        viewModel.removeFromPlaylist(items[1])
        advanceUntilIdle()
        assertEquals("Failed to remove from playlist", viewModel.error)

        // A forced (accepted) load wipes the surfaced mutation error — fresh
        // content is arriving; the old ladder cleared its one error field at
        // load start.
        viewModel.refreshPlaylist("pl1")
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun clearError_resetsTheErrorState() = runTest(mainDispatcher) {
        coEvery { playlistRepository.getPlaylistItems("pl1", any(), any()) } returns
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
