package com.raulshma.jellyplay.feature.library

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Semaphore

private const val PHOTO_FOLDER_PREFETCH_CONCURRENCY = 4

class FavoritesViewModel(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel(), DeferredRefreshHost {

    private val _mediaTypeFilter = stateFlow<MediaType?>(null)
    private val _refreshTrigger = stateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> =
        combine(_mediaTypeFilter.flow, _refreshTrigger.flow) { type, trigger -> type to trigger }
            .flatMapLatest { (type, _) ->
                mediaRepository.getFavoritesPaged(
                    mediaTypes = type?.let { listOf(it) },
                )
            }.cachedIn(scope)

    val mediaTypeFilter = _mediaTypeFilter.flow

    /**
     * User-data changes while another screen is up only mark the pager stale;
     * the single regeneration fires when the favorites screen is next entered
     * (see [DeferredUserDataRefresher]) — never mid-scroll.
     */
    private val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        onRefresh = { _refreshTrigger.set(_refreshTrigger.value + 1) },
    )

    override fun onScreenActiveChanged(active: Boolean) {
        deferredRefresher.onScreenActiveChanged(active)
    }

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

    /** Ids whose quick actions flip to "Remove download" — see [MediaDownloadActions.downloadedIds]. */
    val downloadedIds = mediaDownloadActions.downloadedIds

    /**
     * Long-press Download from a favorites card (#147): inline start for
     * single-stream items; series selection and richer flows open the detail
     * screen plainly — this host's navigation cannot pre-present the series
     * sheet (unlike the library grid).
     */
    fun downloadItem(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) {
        launch { mediaDownloadActions.downloadAndReport(item, onOpenDetail) }
    }

    /** Long-press Remove download — deletes the local copy only. */
    fun removeItemDownload(item: MediaItem) {
        mediaDownloadActions.removeDownload(item)
    }

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            val toFetch = items.filter { it.mediaType == MediaType.PHOTO_FOLDER && it.id !in current }
            if (toFetch.isEmpty()) return@launch
            val results = Semaphore(PHOTO_FOLDER_PREFETCH_CONCURRENCY).mapConcurrent(toFetch) { folder ->
                folder.id to mediaRepository.getPhotoFolderChildImageUrls(folder.id)
            }
            _photoFolderChildUrls.set(_photoFolderChildUrls.value + results)
        }
    }
}
