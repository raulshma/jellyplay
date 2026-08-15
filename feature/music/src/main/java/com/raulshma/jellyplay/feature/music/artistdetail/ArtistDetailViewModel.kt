package com.raulshma.jellyplay.feature.music.artistdetail

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.toMixErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
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

    fun loadArtist(artistId: String, force: Boolean = false) {
        launch {
            _isLoading.value = true
            _error.value = null
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(artistId, force = force) }
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

    fun refreshArtist(artistId: String) {
        launch {
            loadArtist(artistId, force = true)
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    fun startInstantMix(artistId: String) {
        launch {
            _isStartingMix.value = true
            _error.value = null
            // No album fallback: the former `track.album` fallback was a no-op
            // (the mapper keeps the track's own album whenever it is set).
            val outcome = audioQueueFacade.startInstantMix(artistId)
            if (outcome is AudioQueueOutcome.Started) {
                _mixFirstTrackId.value = outcome.queue.first().id
            } else {
                outcome.toMixErrorMessage(context)?.let { _error.value = it }
            }
            _isStartingMix.value = false
        }
    }

    fun consumeMixEvent() {
        _mixFirstTrackId.value = null
    }
}
