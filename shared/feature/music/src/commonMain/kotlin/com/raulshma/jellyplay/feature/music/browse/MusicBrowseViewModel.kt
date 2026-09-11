package com.raulshma.jellyplay.feature.music.browse

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import com.raulshma.jellyplay.feature.music.collection.SimpleListCollection
import com.raulshma.jellyplay.feature.music.collection.SortedPagedCollection
import com.raulshma.jellyplay.feature.music.collection.musicArtUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The MusicBrowse tab's five collections, each bound through the chassis:
 * the three paged tabs via [SortedPagedCollection] over their
 * [MusicCollectionKind], genres/playlists via [SimpleListCollection]. Each
 * tab owns an independent sort state (pinned by `MusicListViewModelsTest`).
 * Declared addition: the genres/playlists tabs now carry loading/error state
 * (the ladder the browse pages render through needs it; the previous
 * fire-and-forget loads had neither).
 */
class MusicBrowseViewModel(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    // One collection per tab; each starts on NAME (the previous hard-coded default).
    private val artistCollection = SortedPagedCollection(mediaRepository, scope, MusicCollectionKind.ARTISTS)
    private val albumCollection = SortedPagedCollection(mediaRepository, scope, MusicCollectionKind.ALBUMS)
    private val trackCollection = SortedPagedCollection(mediaRepository, scope, MusicCollectionKind.TRACKS)

    private val genreCollection = SimpleListCollection<Genre>(scope = scope) { force ->
        mediaRepository.getGenres(force = force)
    }

    private val playlistCollection = SimpleListCollection<Playlist>(scope) { _ ->
        playlistRepository.getPlaylists()
    }

    val artistSort: StateFlow<MusicSortOption> = artistCollection.selectedSort
    val albumSort: StateFlow<MusicSortOption> = albumCollection.selectedSort
    val trackSort: StateFlow<MusicSortOption> = trackCollection.selectedSort

    val artists: Flow<PagingData<MediaItem>> = artistCollection.items
    val albums: Flow<PagingData<MediaItem>> = albumCollection.items
    val tracks: Flow<PagingData<MediaItem>> = trackCollection.items

    val genres = genreCollection.items
    val genresLoading = genreCollection.isLoading
    val genresError = genreCollection.error

    val playlists = playlistCollection.items
    val playlistsLoading = playlistCollection.isLoading
    val playlistsError = playlistCollection.error

    fun setArtistSort(sort: MusicSortOption) = artistCollection.setSort(sort)
    fun setAlbumSort(sort: MusicSortOption) = albumCollection.setSort(sort)
    fun setTrackSort(sort: MusicSortOption) = trackCollection.setSort(sort)

    fun refreshGenres() = genreCollection.refresh(force = true)
    fun refreshPlaylists() = playlistCollection.refresh(force = true)

    fun getImageUrl(itemId: String): String = imageUrlProvider.musicArtUrl(itemId)
}
