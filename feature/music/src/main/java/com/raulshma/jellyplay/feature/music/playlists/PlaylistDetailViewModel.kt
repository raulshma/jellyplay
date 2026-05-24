package com.raulshma.jellyplay.feature.music.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    val audioPlaybackManager: AudioPlaybackManager,
) : ViewModel() {

    var items by mutableStateOf<List<PlaylistItem>>(emptyList())
        private set

    var playlistName by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun load(playlistId: String, playlistName: String? = null) {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getPlaylistItems(playlistId, limit = 200)
                .onSuccess {
                    items = it
                    this@PlaylistDetailViewModel.playlistName = playlistName ?: ""
                }
                .onFailure { error = it.message }
            if (playlistName == null) {
                mediaRepository.getMediaDetail(playlistId)
                    .onSuccess { this@PlaylistDetailViewModel.playlistName = it.item.name }
            }
            isLoading = false
        }
    }

    fun addToQueue(item: PlaylistItem) {
        val queueItem = AudioQueueItem(
            id = item.id,
            name = item.name,
            artist = item.artist ?: "",
            album = item.album,
            imageUrl = null,
            mediaSourceId = null,
            durationMs = item.runTimeTicks?.let { it / 10_000 } ?: 0L,
        )
        audioPlaybackManager.addToQueue(queueItem)
    }
}
