package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.ui.components.UndoableAction
import com.raulshma.jellyplay.core.ui.components.undoActionChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    val audioPlaybackManager: AudioPlaybackManager,
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

    fun load(playlistId: String, playlistName: String? = null) {
        _playlistId.value = playlistId
        launch {
            _isLoading.value = true
            _error.value = null
            mediaRepository.getPlaylistItems(playlistId, limit = 200)
                .onSuccess {
                    _items.value = it
                    if (playlistName != null) {
                        _playlistName.value = playlistName
                    }
                }
                .onFailure { _error.value = it.message }
            if (playlistName == null) {
                mediaRepository.getMediaDetail(playlistId)
                    .onSuccess { _playlistName.value = it.item.name }
            }
            _isLoading.value = false
        }
    }

    fun refreshPlaylist(playlistId: String) {
        launch {
            mediaRepository.invalidateDetailCache(playlistId)
            load(playlistId, playlistName.takeIf { it.isNotEmpty() })
        }
    }

    fun addToQueue(item: PlaylistItem) {
        val queueItem = item.toAudioQueueItem()
        audioPlaybackManager.addToQueue(queueItem)
    }

    fun playAll(startIndex: Int = 0) {
        val queueItems = items.map { item -> item.toAudioQueueItem() }
        audioPlaybackManager.playQueue(queueItems, startIndex)
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
            mediaRepository.removeItemsFromPlaylist(currentId, listOf(entryId))
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
            mediaRepository.addItemsToPlaylist(currentId, listOf(item.id))
                .onSuccess { load(currentId, playlistName) }
                .onFailure { _error.value = it.message ?: "Failed to restore to playlist" }
            _isMutating.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
