package com.raulshma.jellyplay.feature.music.genres

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    val audioPlaybackManager: AudioPlaybackManager,
) : ViewModel() {

    private val genreId: String = savedStateHandle[Route.GenreDetail::genreId.name] ?: ""
    private val genreName: String = savedStateHandle[Route.GenreDetail::genreName.name] ?: ""

    val tracks: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.AUDIO),
        genres = listOf(genreName),
        sortBy = "SortName",
    ).cachedIn(viewModelScope)

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 300)

    fun addToQueue(track: MediaItem) {
        viewModelScope.launch {
            val imageUrl = getImageUrl(track.id)
            val queueItem = AudioQueueItem(
                id = track.id,
                name = track.name,
                artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                album = track.album,
                imageUrl = imageUrl,
                mediaSourceId = null,
                durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                normalizationGain = track.normalizationGain,
            )
            audioPlaybackManager.addToQueue(queueItem)
        }
    }

    fun playAll(tracks: List<MediaItem>, startIndex: Int) {
        viewModelScope.launch {
            val queueItems = tracks.map { track ->
                AudioQueueItem(
                    id = track.id,
                    name = track.name,
                    artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                    album = track.album,
                    imageUrl = getImageUrl(track.id),
                    mediaSourceId = null,
                    durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                    normalizationGain = track.normalizationGain,
                )
            }
            audioPlaybackManager.playQueue(queueItems, startIndex)
        }
    }
}
