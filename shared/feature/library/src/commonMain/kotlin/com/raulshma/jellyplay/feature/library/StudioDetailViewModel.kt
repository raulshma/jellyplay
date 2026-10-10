package com.raulshma.jellyplay.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.PagedMediaGridHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

internal class StudioDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val quickDownloadActions: QuickDownloadActions,
) : JellyPlayViewModel() {

    private val studioId: String = savedStateHandle[Route.StudioDetail::studioId.name] ?: ""
    private val studioName: String = savedStateHandle[Route.StudioDetail::studioName.name] ?: ""

    /**
     * The studio grid, wired through the shared [PagedMediaGridHost]: the
     * host owns the deferred-refresh generation counter + refresher pairing,
     * the cachedIn sharing and the quick-action forwarding; this source keeps
     * the name-sorted studio query.
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
            trigger.flatMapLatest {
                mediaRepository.getMediaItemsPaged(
                    filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                        sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
                    ),
                    studioIds = listOf(studioId),
                )
            }
        },
    )

    val items: Flow<PagingData<MediaItem>> get() = pagedGrid.items

    /**
     * User-data changes while another screen is up only mark the grid stale;
     * the single regeneration fires when the studio screen is next entered
     * (the [DeferredUserDataRefresher] the [PagedMediaGridHost] owns) — never
     * mid-scroll.
     */
    val deferredRefresher: DeferredRefreshHost get() = pagedGrid

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
     * Long-press Download from a studio card (#147): inline start for
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
}
