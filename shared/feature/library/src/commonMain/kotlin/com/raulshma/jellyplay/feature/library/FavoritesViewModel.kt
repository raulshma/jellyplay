package com.raulshma.jellyplay.feature.library

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderChildUrlsStore
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

internal class FavoritesViewModel(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val quickDownloadActions: QuickDownloadActions,
    photoFolderPrefetcher: PhotoFolderPrefetcher,
) : JellyPlayViewModel() {

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
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        trigger = _refreshTrigger,
    )

    /**
     * The photo-folder child-URL cache — the shared [PhotoFolderChildUrlsStore]
     * adopted from the home feature, replacing this class's former hand-rolled
     * `Semaphore(4).mapConcurrent` fan-out over the repository. Same contract
     * (photo folders only, already-fetched skip, merge) plus the bounded
     * oldest-first eviction the inline copy lacked.
     */
    private val photoFolderChildUrlsStore = PhotoFolderChildUrlsStore(scope, photoFolderPrefetcher)

    val photoFolderChildUrls = photoFolderChildUrlsStore.childUrls

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

    /** Ids whose quick actions flip to "Remove download" — see [QuickDownloadActions.downloadedIds]. */
    // Whether this platform has a download pipeline — screens gate the
    // download CTA on it (hidden rather than Failed-toasting on web).
    val downloadSupported = quickDownloadActions.isSupported

    val downloadedIds = quickDownloadActions.downloadedIds

    /**
     * Long-press Download from a favorites card (#147): inline start for
     * single-stream items; series selection and richer flows open the detail
     * screen plainly — this host's navigation cannot pre-present the series
     * sheet (unlike the library grid).
     */
    fun downloadItem(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) {
        launch { quickDownloadActions.downloadAndReport(item, onOpenDetail) }
    }

    /** Long-press Remove download — deletes the local copy only. */
    fun removeItemDownload(item: MediaItem) {
        quickDownloadActions.removeDownload(item)
    }

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        photoFolderChildUrlsStore.prefetch(items)
    }
}
