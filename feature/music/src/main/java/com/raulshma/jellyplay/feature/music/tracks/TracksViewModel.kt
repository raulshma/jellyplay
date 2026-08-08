package com.raulshma.jellyplay.feature.music.tracks

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.albums.MusicSortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TracksViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    val audioPlaybackManager: AudioPlaybackManager,
) : JellyPlayViewModel() {

    private val _selectedSort = composeState(MusicSortOption.NAME)
    val selectedSort: MusicSortOption get() = _selectedSort.value

    private val sortFlow = MutableStateFlow(_selectedSort.value)

    val tracks: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.AUDIO),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
        )
    }.cachedIn(scope)

    fun setSort(sort: MusicSortOption) {
        _selectedSort.value = sort
        sortFlow.value = sort
    }

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
