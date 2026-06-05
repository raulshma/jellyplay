package com.raulshma.jellyplay.feature.music.artistdetail

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

    suspend fun loadArtist(artistId: String) {
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
}
