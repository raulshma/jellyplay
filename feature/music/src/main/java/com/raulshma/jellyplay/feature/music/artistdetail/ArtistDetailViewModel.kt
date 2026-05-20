package com.raulshma.jellyplay.feature.music.artistdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var artistName by mutableStateOf("")
        private set
    var albums by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var tracks by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(artistId) }
                val albumsDeferred = async { mediaRepository.getArtistAlbums(artistId) }
                detailDeferred.await()
                    .onSuccess { detail -> artistName = detail.item.name }
                    .onFailure {
                        error = it.message ?: "Failed to load artist"
                    }
                albumsDeferred.await()
                    .onSuccess { albumList -> albums = albumList }
            }
            isLoading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
