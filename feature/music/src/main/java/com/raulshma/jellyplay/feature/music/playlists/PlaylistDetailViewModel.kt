package com.raulshma.jellyplay.feature.music.playlists

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
        launch {
            _isMutating.value = true
            _error.value = null
            mediaRepository.removeItemsFromPlaylist(currentId, listOf(entryId))
                .onSuccess { load(currentId, playlistName) }
                .onFailure { _error.value = it.message ?: "Failed to remove from playlist" }
            _isMutating.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
