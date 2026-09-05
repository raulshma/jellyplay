package com.raulshma.jellyplay.feature.music.tracks

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import com.raulshma.jellyplay.feature.music.collection.SortedPagedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class TracksViewModel(
    mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
) : JellyPlayViewModel() {

    /** Sorted paged tracks — sort state and pager live in the collection. */
    private val collection = SortedPagedCollection(mediaRepository, scope, MediaType.AUDIO)

    val selectedSort: StateFlow<MusicSortOption> = collection.selectedSort

    val tracks: Flow<PagingData<MediaItem>> = collection.items

    fun setSort(sort: MusicSortOption) = collection.setSort(sort)

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
