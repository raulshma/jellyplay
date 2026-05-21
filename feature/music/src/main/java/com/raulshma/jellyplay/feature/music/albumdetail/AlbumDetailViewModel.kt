package com.raulshma.jellyplay.feature.music.albumdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
) : ViewModel() {

    var detail by mutableStateOf<MediaDetail?>(null)
        private set
    var tracks by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getMediaDetail(albumId)
                .onSuccess { detail = it }
                .onFailure { error = it.message ?: "Failed to load album" }
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { tracks = it }
                .onFailure { error = it.message ?: "Failed to load tracks" }
            isLoading = false
        }
    }

    fun playAlbum(tracks: List<MediaItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val queueItems = tracks.map { track ->
            AudioQueueItem(
                id = track.id,
                name = track.name,
                artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                album = track.album ?: detail?.item?.name,
                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                mediaSourceId = null,
                durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                normalizationGain = track.normalizationGain,
            )
        }
        audioPlaybackManager.playQueue(queueItems, startIndex)
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
