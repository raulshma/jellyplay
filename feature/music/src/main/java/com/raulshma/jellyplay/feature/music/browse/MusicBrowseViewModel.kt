package com.raulshma.jellyplay.feature.music.browse

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MusicBrowseViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    val artists: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.ARTIST),
        sortBy = "SortName",
    ).cachedIn(scope)

    val albums: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.ALBUM),
        sortBy = "SortName",
    ).cachedIn(scope)

    val tracks: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.AUDIO),
        sortBy = "SortName",
    ).cachedIn(scope)

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres = _genres.flow

    private val _playlists = stateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.flow

    init {
        launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.set(it) }
        }
        launch {
            mediaRepository.getPlaylists()
                .onSuccess { _playlists.set(it) }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 300)
}
