package com.raulshma.jellyplay.feature.music.browse

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import com.raulshma.jellyplay.feature.music.collection.SortedPagedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MusicBrowseViewModel(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    // One collection per tab; each starts on NAME (the previous hard-coded default).
    private val artistCollection = SortedPagedCollection(mediaRepository, scope, MediaType.ARTIST)
    private val albumCollection = SortedPagedCollection(mediaRepository, scope, MediaType.ALBUM)
    private val trackCollection = SortedPagedCollection(mediaRepository, scope, MediaType.AUDIO)

    val artistSort: StateFlow<MusicSortOption> = artistCollection.selectedSort
    val albumSort: StateFlow<MusicSortOption> = albumCollection.selectedSort
    val trackSort: StateFlow<MusicSortOption> = trackCollection.selectedSort

    val artists: Flow<PagingData<MediaItem>> = artistCollection.items
    val albums: Flow<PagingData<MediaItem>> = albumCollection.items
    val tracks: Flow<PagingData<MediaItem>> = trackCollection.items

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
            playlistRepository.getPlaylists()
                .onSuccess { _playlists.set(it) }
        }
    }

    fun setArtistSort(sort: MusicSortOption) = artistCollection.setSort(sort)
    fun setAlbumSort(sort: MusicSortOption) = albumCollection.setSort(sort)
    fun setTrackSort(sort: MusicSortOption) = trackCollection.setSort(sort)

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
}
