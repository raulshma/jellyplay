package com.raulshma.jellyplay.feature.library

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PHOTO_FOLDER_PREFETCH_CONCURRENCY = 4

class FavoritesViewModel(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _mediaTypeFilter = stateFlow<MediaType?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = _mediaTypeFilter.flow.flatMapLatest { type ->
        mediaRepository.getFavoritesPaged(
            mediaTypes = type?.let { listOf(it) },
        )
    }.cachedIn(scope)

    val mediaTypeFilter = _mediaTypeFilter.flow

    private val _photoFolderChildUrls = stateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls = _photoFolderChildUrls.flow

    fun setMediaTypeFilter(type: MediaType?) {
        _mediaTypeFilter.set(type)
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /**
     * Marks the item played/unplayed. Intentionally silent (the mutator's
     * default): the paged grid is left untouched so the user keeps their scroll
     * position — the badge updates on the next natural data refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(item.id, played)
        }
    }

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            val toFetch = items.filter { it.mediaType == MediaType.PHOTO_FOLDER && it.id !in current }
            if (toFetch.isEmpty()) return@launch
            val permits = Semaphore(PHOTO_FOLDER_PREFETCH_CONCURRENCY)
            val results = coroutineScope {
                toFetch.map { folder ->
                    async {
                        permits.withPermit { folder.id to mediaRepository.getPhotoFolderChildImageUrls(folder.id) }
                    }
                }.awaitAll()
            }
            _photoFolderChildUrls.set(_photoFolderChildUrls.value + results)
        }
    }
}
