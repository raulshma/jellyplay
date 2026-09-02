package com.raulshma.jellyplay.feature.music.genres

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.Flow

class GenreDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
) : JellyPlayViewModel() {

    private val genreId: String = savedStateHandle[Route.GenreDetail::genreId.name] ?: ""
    private val genreName: String = savedStateHandle[Route.GenreDetail::genreName.name] ?: ""

    val tracks: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        filters = com.raulshma.jellyplay.core.model.LibraryFilters(
            mediaTypes = listOf(MediaType.AUDIO),
            genres = listOf(genreName),
            sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
        ),
    ).cachedIn(scope)

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)

    fun addToQueue(track: MediaItem) {
        launch {
            audioQueueFacade.enqueueTrack(track, imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }

    fun playAll(tracks: List<MediaItem>, startIndex: Int) {
        launch {
            audioQueueFacade.playTracks(tracks, startIndex = startIndex, imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }
}
