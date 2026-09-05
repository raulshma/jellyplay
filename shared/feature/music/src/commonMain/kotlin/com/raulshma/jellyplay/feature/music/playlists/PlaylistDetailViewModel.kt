package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.ui.components.UndoableAction
import com.raulshma.jellyplay.core.ui.components.undoActionChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.receiveAsFlow

class PlaylistDetailViewModel(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val audioQueueFacade: AudioQueueFacade,
) : JellyPlayViewModel() {

    private val _items = composeState<List<PlaylistItem>>(emptyList())
    val items: List<PlaylistItem> get() = _items.value

    private val _playlistName = composeState("")
    val playlistName: String get() = _playlistName.value

    private val _playlistId = composeState("")
    val playlistId: String get() = _playlistId.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _isMutating = composeState(false)
    val isMutating: Boolean get() = _isMutating.value

    /** Recoverable-action snackbars (e.g. "Removed 'X' — Undo"). Screen collects
     * this and re-runs [UndoableAction.onUndo] if the user taps Undo. */
    private val _undoActions = undoActionChannel()
    val undoActions = _undoActions.receiveAsFlow()

    fun load(playlistId: String, playlistName: String? = null, force: Boolean = false) {
        _playlistId.value = playlistId
        launch {
            _isLoading.value = true
            _error.value = null
            if (playlistName == null) {
                val itemsDeferred = async { playlistRepository.getPlaylistItems(playlistId, limit = 200) }
                val nameDeferred = async { mediaRepository.getMediaDetail(playlistId, force = force) }
                itemsDeferred.await()
                    .onSuccess { _items.value = it }
                    .onFailure { _error.value = it.message }
                nameDeferred.await().onSuccess { _playlistName.value = it.item.name }
            } else {
                playlistRepository.getPlaylistItems(playlistId, limit = 200)
                    .onSuccess {
                        _items.value = it
                        _playlistName.value = playlistName
                    }
                    .onFailure { _error.value = it.message }
            }
            _isLoading.value = false
        }
    }

    fun refreshPlaylist(playlistId: String) {
        launch {
            load(playlistId, playlistName.takeIf { it.isNotEmpty() }, force = true)
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
            _isMutating.value = true
            _error.value = null
            playlistRepository.removeItemsFromPlaylist(currentId, listOf(entryId))
                .onFailure { _error.value = it.message ?: "Failed to remove from playlist" }
                .onSuccess {
                    _undoActions.trySend(
                        UndoableAction(
                            message = "Removed \"${item.name}\" from playlist",
                            onUndo = { restoreToPlaylist(item) },
                        ),
                    )
                }
            _isMutating.value = false
        }
    }

    /** Re-adds [item] to the current playlist after an undo. Re-fetches so the
     * restored row carries a fresh entry id. */
    private fun restoreToPlaylist(item: PlaylistItem) {
        val currentId = playlistId
        if (currentId.isEmpty()) return
        launch {
            _isMutating.value = true
            _error.value = null
            playlistRepository.addItemsToPlaylist(currentId, listOf(item.id))
                .onSuccess { load(currentId, playlistName) }
                .onFailure { _error.value = it.message ?: "Failed to restore to playlist" }
            _isMutating.value = false
        }
    }

    fun clearError() {
        _error.value = null
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
            _isMutating.value = true
            _error.value = null
            playlistRepository.movePlaylistItem(currentId, entryId, newIndex)
                .onFailure {
                    _error.value = it.message ?: "Failed to reorder playlist"
                    // Roll back to the server's authoritative order.
                    load(currentId, playlistName)
                }
            _isMutating.value = false
        }
    }
}
