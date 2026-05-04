package com.raulshma.jellyplay.feature.music.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicBrowseViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    val artists: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.ARTIST),
        sortBy = "SortName",
    ).cachedIn(viewModelScope)

    val albums: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.ALBUM),
        sortBy = "SortName",
    ).cachedIn(viewModelScope)

    val tracks: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        mediaTypes = listOf(MediaType.AUDIO),
        sortBy = "SortName",
    ).cachedIn(viewModelScope)

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _playlists = MutableStateFlow<List<com.raulshma.jellyplay.core.model.Playlist>>(emptyList())
    val playlists: StateFlow<List<com.raulshma.jellyplay.core.model.Playlist>> = _playlists.asStateFlow()

    init {
        viewModelScope.launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.value = it }
        }
        viewModelScope.launch {
            mediaRepository.getPlaylists()
                .onSuccess { _playlists.value = it }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 300)
}
