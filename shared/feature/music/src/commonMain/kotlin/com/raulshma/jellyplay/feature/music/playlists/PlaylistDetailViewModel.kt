package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.ui.components.UndoableAction
import com.raulshma.jellyplay.core.ui.components.undoActionChannel
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The playlist screen's all-or-nothing content aggregate: the track list plus
 * the header title. [name] is nullable as a KEEP marker — a fetch with no name
 * information (an empty nav hint) publishes `null` and the projection keeps
 * whatever title the header already shows, so a silent regeneration can never
 * blank a loaded title.
 */
private data class PlaylistContent(
    val items: List<PlaylistItem>,
    val name: String?,
)

class PlaylistDetailViewModel(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val audioQueueFacade: MusicQueuePlayer,
) : JellyPlayViewModel() {

    /**
     * User-data changes while another screen is up (a favorite flipped
     * elsewhere, outbox drain landing) only mark the playlist stale; the
     * single silent forced reload fires when the playlist screen is next
     * entered (see [DeferredUserDataRefresher]) — never mid-scroll. The whole
     * load lifecycle — publish policy, back-stack re-entry guard and its
     * reload-after-failure re-arm included — lives in
     * [DeferredFetchCoordinator], same chassis as the album host.
     */
    private val fetchCoordinator = DeferredFetchCoordinator<String, PlaylistContent>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchPlaylistData,
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    private val _items = composeState<List<PlaylistItem>>(emptyList())
    val items: List<PlaylistItem> get() = _items.value

    private val _playlistName = composeState("")
    val playlistName: String get() = _playlistName.value

    private val _playlistId = composeState("")
    val playlistId: String get() = _playlistId.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    // The load half of the screen's one error field, projected from the
    // coordinator state. The mutation half lives beside it (the album host's
    // mix-error split): a mutation error can only land over loaded content —
    // the remove/reorder rows are unreachable otherwise — so the fold never
    // has to choose. A mutation error survives a silent regeneration and a
    // no-op re-entry, and is wiped when an accepted loud load starts (fresh
    // content arriving) — the old ladder cleared its one error field at load
    // start, and the collector's isLoading arm below keeps that.
    private val _loadError = composeState<String?>(null)
    private val _mutationError = composeState<String?>(null)
    val error: String? get() = _mutationError.value ?: _loadError.value

    private val _isMutating = composeState(false)
    val isMutating: Boolean get() = _isMutating.value

    /** Recoverable-action snackbars (e.g. "Removed 'X' — Undo"). Screen collects
     * this and re-runs [UndoableAction.onUndo] if the user taps Undo. */
    private val _undoActions = undoActionChannel()
    val undoActions = _undoActions.receiveAsFlow()

    /**
     * The nav route's title hint for the playlist being loaded: non-null means
     * the load trusts it and never fetches the name (the fast path); null
     * resolves the name from the detail endpoint. Overwritten by every
     * [load] call, so a retry from the error screen (no hint) fetches it.
     */
    private var nameHint: String? = null

    init {
        // The load-state projection: this screen reads plain getters backed
        // by Compose state, so the coordinator's state flow is mirrored into
        // them (spinner, content pair, load error) — mechanically, with no
        // per-host publish decisions left here.
        launch {
            fetchCoordinator.state.collect { st ->
                st.value?.let { content ->
                    _items.value = content.items
                    content.name?.let { _playlistName.value = it }
                }
                _isLoading.value = st.isLoading
                _loadError.value = st.error?.message
                // An accepted loud load wipes the surfaced mutation error.
                // A silent regeneration or a no-op re-entry never gets here,
                // so a standing mutation error survives those (the album
                // host's failed-mix precedent).
                if (st.isLoading) _mutationError.value = null
            }
        }
    }

    /**
     * The loud entry — the screen's `LaunchedEffect`, the error screen's
     * retry. The back-stack re-entry guard (an already-loaded playlist with
     * no failed loud load behind it no-ops; a failed one re-arms) lives in
     * [DeferredFetchCoordinator.load]; [force] is pull-to-refresh (and the
     * mutation rollback/re-restore reloads, which must never no-op).
     */
    fun load(playlistId: String, playlistName: String? = null, force: Boolean = false) {
        _playlistId.value = playlistId
        nameHint = playlistName
        fetchCoordinator.load(playlistId, force)
    }

    fun refreshPlaylist(playlistId: String) {
        load(playlistId, playlistName.takeIf { it.isNotEmpty() }, force = true)
    }

    /**
     * The fetch behind both load paths, returning the whole items+name
     * aggregate and throwing on any failed half so
     * [DeferredFetchCoordinator] owns the publish policy and failure re-arm:
     * all-or-nothing holds for BOTH modes (never one fresh half beside one
     * stale half), and the deferred silent regeneration keeps the last pair
     * on failure (serve-stale-while-revalidate, same philosophy as the album
     * host).
     *
     * Behavior call (the ladder fold): the screen's failure rendering is
     * items-gated — its error branch is `error != null && items.isEmpty()`,
     * and on the load path an error only ever occurred when the ITEMS half
     * failed, so all-or-nothing over the pair preserves every rendered state.
     * The name half is the header title: the normal nav route supplies it
     * (fast path, no fetch) and only a hint-less entry fetched it — with no
     * error attached (the old ladder's `.onSuccess` had no onFailure: a
     * failed name fetch kept the old title silently). Making the title fatal
     * would put hint-less loads on the error screen over a title, so a name
     * failure keeps that old silence: the aggregate publishes `name = null`
     * and the header keeps whatever it showed. The items failure wins when
     * both halves fail (the half whose failure the screen surfaces).
     * `getPlaylistItems` has no force flag, so [force] reaches the name read
     * only, exactly as before.
     */
    private suspend fun fetchPlaylistData(playlistId: String, force: Boolean): PlaylistContent {
        val hint = nameHint
        return coroutineScope {
            val itemsDeferred = async { playlistRepository.getPlaylistItems(playlistId, limit = 200) }
            val nameDeferred =
                if (hint == null) async { mediaRepository.getMediaDetail(playlistId, force = force) } else null
            val itemsResult = itemsDeferred.await()
            val nameResult = nameDeferred?.await()

            // The items error wins when both halves fail (the half whose
            // failure the screen surfaces — the album host's tracks tiebreak).
            (itemsResult.exceptionOrNull() ?: nameResult?.exceptionOrNull())?.let { throw it }
            PlaylistContent(
                items = itemsResult.getOrThrow(),
                name = nameResult?.getOrThrow()?.item?.name ?: hint?.takeIf { it.isNotEmpty() },
            )
        }
    }

    fun addToQueue(item: PlaylistItem) {
        launch { audioQueueFacade.enqueuePlaylistItem(item) }
    }

    fun playAll(startIndex: Int = 0) {
        launch { audioQueueFacade.playPlaylist(items, startIndex = startIndex) }
    }

    fun removeFromPlaylist(item: PlaylistItem) {
        val entryId = item.playlistItemId ?: return
        val currentId = playlistId
        if (currentId.isEmpty()) return
        // Optimistically drop the row so the list reacts instantly, then surface an
        // Undo that re-adds it. The server remove runs fire-and-forget; on undo we
        // re-add by the underlying media id (the entry id is gone server-side).
        _items.value = _items.value.filterNot { it.playlistItemId == entryId }
        launch {
            runPlaylistMutation(
                guard = _isMutating,
                errorState = _mutationError,
                errorOf = { UserErrorMessages.resolve(it, "Failed to remove from playlist") },
                failurePolicy = PlaylistMutationFailurePolicy.KeepOptimistic,
                onSuccess = {
                    _undoActions.trySend(
                        UndoableAction(
                            message = "Removed \"${item.name}\" from playlist",
                            onUndo = { restoreToPlaylist(item) },
                        ),
                    )
                },
                command = { playlistRepository.removeItemsFromPlaylist(currentId, listOf(entryId)) },
            )
        }
    }

    /** Re-adds [item] to the current playlist after an undo. Re-fetches so the
     * restored row carries a fresh entry id. */
    private fun restoreToPlaylist(item: PlaylistItem) {
        val currentId = playlistId
        if (currentId.isEmpty()) return
        launch {
            runPlaylistMutation(
                guard = _isMutating,
                errorState = _mutationError,
                errorOf = { UserErrorMessages.resolve(it, "Failed to restore to playlist") },
                // Nothing new was applied optimistically here, but the list may
                // still reflect an earlier optimistic drop — a failed restore
                // leaves that local state standing rather than reloading.
                failurePolicy = PlaylistMutationFailurePolicy.KeepOptimistic,
                onSuccess = { load(currentId, playlistName, force = true) },
                command = { playlistRepository.addItemsToPlaylist(currentId, listOf(item.id)) },
            )
        }
    }

    fun clearError() {
        _mutationError.value = null
        _loadError.value = null
    }

    /**
     * Reorders the playlist: moves [item] from its current position to
     * [newIndex]. Applies the swap optimistically so the list reacts instantly,
     * then persists it via [MediaRepository.movePlaylistItem]. On failure the
     * list is reloaded from the server so it reflects the true order.
     */
    fun moveItem(item: PlaylistItem, newIndex: Int) {
        val entryId = item.playlistItemId ?: return
        val currentId = playlistId
        if (currentId.isEmpty()) return
        val current = _items.value
        val fromIndex = current.indexOfFirst { it.playlistItemId == entryId }
        if (fromIndex == -1 || fromIndex == newIndex) return
        // Optimistic in-place reorder.
        val reordered = current.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(newIndex.coerceIn(0, size), moved)
        }
        _items.value = reordered
        launch {
            runPlaylistMutation(
                guard = _isMutating,
                errorState = _mutationError,
                errorOf = { UserErrorMessages.resolve(it, "Failed to reorder playlist") },
                // Roll back to the server's authoritative order.
                failurePolicy = PlaylistMutationFailurePolicy.Reload { load(currentId, playlistName, force = true) },
                command = { playlistRepository.movePlaylistItem(currentId, entryId, newIndex) },
            )
        }
    }
}
