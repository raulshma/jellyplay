package com.raulshma.jellyplay.feature.music.artistdetail

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
) : JellyPlayViewModel() {

    private val _artistName = composeState("")
    val artistName: String get() = _artistName.value

    private val _albums = composeState<List<MediaItem>>(emptyList())
    val albums: List<MediaItem> get() = _albums.value

    private val _tracks = composeState<List<MediaItem>>(emptyList())
    val tracks: List<MediaItem> get() = _tracks.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _isStartingMix = composeState(false)
    val isStartingMix: Boolean get() = _isStartingMix.value

    private val _mixFirstTrackId = composeState<String?>(null)
    val mixFirstTrackId: String? get() = _mixFirstTrackId.value

    fun loadArtist(artistId: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(artistId) }
                val albumsDeferred = async { mediaRepository.getArtistAlbums(artistId) }
                detailDeferred.await()
                    .onSuccess { detail -> _artistName.value = detail.item.name }
                    .onFailure {
                        _error.value = it.message ?: "Failed to load artist"
                    }
                albumsDeferred.await()
                    .onSuccess { albumList -> _albums.value = albumList }
            }
            _isLoading.value = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    fun startInstantMix(artistId: String) {
        launch {
            _isStartingMix.value = true
            _error.value = null
            mediaRepository.getInstantMix(artistId)
                .onSuccess { mix ->
                    if (mix.isEmpty()) {
                        _error.value = "No mix tracks available for this artist"
                    } else {
                        val queueItems = mix.map { track ->
                            track.toAudioQueueItem(
                                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                                albumFallback = track.album,
                            )
                        }
                        audioPlaybackManager.playQueue(queueItems, 0)
                        _mixFirstTrackId.value = mix.first().id
                    }
                }
                .onFailure { _error.value = it.message ?: "Failed to start Instant Mix" }
            _isStartingMix.value = false
        }
    }

    fun consumeMixEvent() {
        _mixFirstTrackId.value = null
    }
}
