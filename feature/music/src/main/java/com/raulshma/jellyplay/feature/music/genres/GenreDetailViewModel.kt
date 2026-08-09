package com.raulshma.jellyplay.feature.music.genres

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    val audioPlaybackManager: AudioPlaybackManager,
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
            val imageUrl = getImageUrl(track.id)
            val queueItem = track.toAudioQueueItem(imageUrl = imageUrl)
            audioPlaybackManager.addToQueue(queueItem)
        }
    }

    fun playAll(tracks: List<MediaItem>, startIndex: Int) {
        launch {
            val queueItems = tracks.map { track ->
                track.toAudioQueueItem(imageUrl = getImageUrl(track.id))
            }
            audioPlaybackManager.playQueue(queueItems, startIndex)
        }
    }
}
