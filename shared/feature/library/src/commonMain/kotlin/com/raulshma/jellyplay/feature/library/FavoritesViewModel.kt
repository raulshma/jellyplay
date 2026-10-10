package com.raulshma.jellyplay.feature.library

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderChildUrlsStore
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.PagedMediaGridHost
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

    /**
     * The favorites grid, wired through the shared [PagedMediaGridHost]: the
     * host owns the deferred-refresh generation counter + refresher pairing,
     * the cachedIn sharing and the quick-action forwarding; this source keeps
     * the media-type key.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagedGrid = PagedMediaGridHost(
        scope = scope,
        userDataChanges = mediaRepository.userDataChanges,
        downloadedIds = quickDownloadActions.downloadedIds,
        downloadSupported = quickDownloadActions.isSupported,
        setPlayedSilently = { itemId, played -> userDataMutator.setPlayed(itemId, played) },
        requestDownload = { item, onOpenDetail, seriesOpensSheet ->
            quickDownloadActions.downloadAndReport(item, onOpenDetail, seriesOpensSheet)
        },
        removeDownloadItem = quickDownloadActions::removeDownload,
        source = { trigger ->
            combine(_mediaTypeFilter.flow, trigger) { type, _ -> type }
                .flatMapLatest { type ->
                    mediaRepository.getFavoritesPaged(
                        mediaTypes = type?.let { listOf(it) },
                    )
                }
        },
    )

    val pagedItems: Flow<PagingData<MediaItem>> get() = pagedGrid.items

    val mediaTypeFilter = _mediaTypeFilter.flow

    /**
     * User-data changes while another screen is up only mark the pager stale;
     * the single regeneration fires when the favorites screen is next entered
     * (the [DeferredUserDataRefresher] the [PagedMediaGridHost] owns) — never
     * mid-scroll.
     */
    val deferredRefresher: DeferredRefreshHost get() = pagedGrid

    /**
     * The photo-folder child-URL cache — the shared [PhotoFolderChildUrlsStore]
     * adopted from the home feature, replacing this class's former hand-rolled
     * `Semaphore(4).mapConcurrent` fan-out over the repository. Same contract
     * (photo folders only, already-fetched skip, merge) plus the bounded
     * oldest-first eviction the inline copy lacked.
     */
    private val photoFolderChildUrlsStore = PhotoFolderChildUrlsStore(scope, photoFolderPrefetcher)

    /**
     * Per-item slice of the photo-folder child-URL cache — the fold lives on
     * the store ([PhotoFolderChildUrlsStore.childUrlsFor]). Each photo-folder
     * card collects only its own urls, so a prefetch merge (a new Map
     * reference) invalidates only the card whose urls changed, not the whole
     * favorites grid.
     */
    fun photoFolderChildUrlsFor(itemId: String): Flow<List<String>> =
        photoFolderChildUrlsStore.childUrlsFor(itemId)

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
        pagedGrid.markPlayed(item, played)
    }

    /** Ids whose quick actions flip to "Remove download" — see [QuickDownloadActions.downloadedIds]. */
    // Whether this platform has a download pipeline — screens gate the
    // download CTA on it (hidden rather than Failed-toasting).
    val downloadSupported get() = pagedGrid.downloadSupported

    val downloadedIds get() = quickDownloadActions.downloadedIds

    /**
     * Long-press Download from a favorites card (#147): inline start for
     * single-stream items; series selection and richer flows open the detail
     * screen plainly — this host's navigation cannot pre-present the series
     * sheet (unlike the library grid).
     */
    fun downloadItem(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) {
        pagedGrid.download(item, { id, _ -> onOpenDetail(id) })
    }

    /** Long-press Remove download — deletes the local copy only. */
    fun removeItemDownload(item: MediaItem) {
        pagedGrid.removeDownload(item)
    }

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) {
        photoFolderChildUrlsStore.prefetch(items)
    }
}
