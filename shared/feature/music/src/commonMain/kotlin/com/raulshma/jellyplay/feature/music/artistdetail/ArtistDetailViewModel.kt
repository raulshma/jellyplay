package com.raulshma.jellyplay.feature.music.artistdetail

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.toInstantMixOutcome
import com.raulshma.jellyplay.core.data.playback.InstantMixState
import com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.MixErrorMessage
import com.raulshma.jellyplay.feature.music.toMixErrorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

class ArtistDetailViewModel(
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

    private val _error = composeState<MixErrorMessage?>(null)
    val error: MixErrorMessage? get() = _error.value

    // Instant-mix choreography (isStarting flag + first-track one-shot +
    // outcome → error mapping) lives in the shared holder; the VM only adapts
    // the facade call (no album fallback — see the note in startInstantMix)
    // and folds holder errors into the screen's one `error` field.
    private val instantMix = InstantMixStateHolder(
        scope = scope,
        startMix = { seedItemId, _ ->
            audioQueueFacade.startInstantMix(seedItemId).toInstantMixOutcome()
        },
    )

    val mixState: StateFlow<InstantMixState> = instantMix.state

    val isStartingMix: Boolean get() = instantMix.state.value.isStarting
    val mixFirstTrackId: String? get() = instantMix.state.value.firstTrackId

    init {
        launch {
            instantMix.errorFlow.collect { mixError -> _error.value = mixError.toMixErrorMessage() }
        }
    }

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
                        _error.value = MixErrorMessage.Raw(it.message ?: "Failed to load artist")
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
        // No album fallback: the former `track.album` fallback was a no-op
        // (the mapper keeps the track's own album whenever it is set).
        instantMix.start(artistId, fallbackName = null)
    }

    fun consumeMixEvent() {
        instantMix.consumeStartedEvent()
    }
}
