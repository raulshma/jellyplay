package com.raulshma.jellyplay.feature.music.browse

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.albums.MusicSortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicBrowseViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    // Per-tab sort state. Each starts on NAME (the previous hard-coded default).
    private val artistSortFlow = MutableStateFlow(MusicSortOption.NAME)
    private val albumSortFlow = MutableStateFlow(MusicSortOption.NAME)
    private val trackSortFlow = MutableStateFlow(MusicSortOption.NAME)

    val artistSort: StateFlow<MusicSortOption> = artistSortFlow.asStateFlow()
    val albumSort: StateFlow<MusicSortOption> = albumSortFlow.asStateFlow()
    val trackSort: StateFlow<MusicSortOption> = trackSortFlow.asStateFlow()

    val artists: Flow<PagingData<MediaItem>> = artistSortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ARTIST),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
        )
    }.cachedIn(scope)

    val albums: Flow<PagingData<MediaItem>> = albumSortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ALBUM),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
        )
    }.cachedIn(scope)

    val tracks: Flow<PagingData<MediaItem>> = trackSortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.AUDIO),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
        )
    }.cachedIn(scope)

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

    fun setArtistSort(sort: MusicSortOption) { artistSortFlow.value = sort }
    fun setAlbumSort(sort: MusicSortOption) { albumSortFlow.value = sort }
    fun setTrackSort(sort: MusicSortOption) { trackSortFlow.value = sort }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
}
