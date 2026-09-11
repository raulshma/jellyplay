package com.raulshma.jellyplay.feature.music.albums

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import com.raulshma.jellyplay.feature.music.collection.SortedPagedCollection
import com.raulshma.jellyplay.feature.music.collection.musicArtUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin adapter over the sorted paged collection for the standalone albums
 * route. Kept as its own ViewModel type (not shared with artists/tracks)
 * because nav3 resolves koinViewModels at the Activity owner — one shared
 * type would leak sort state across the three routes.
 */
class AlbumsViewModel(
    mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    /** Sorted paged albums — sort state, pager and declared sort set live in the collection. */
    private val collection = SortedPagedCollection(mediaRepository, scope, MusicCollectionKind.ALBUMS)

    val selectedSort: StateFlow<MusicSortOption> = collection.selectedSort

    val albums: Flow<PagingData<MediaItem>> = collection.items

    fun setSort(sort: MusicSortOption) = collection.setSort(sort)

    fun getImageUrl(itemId: String): String = imageUrlProvider.musicArtUrl(itemId)
}
