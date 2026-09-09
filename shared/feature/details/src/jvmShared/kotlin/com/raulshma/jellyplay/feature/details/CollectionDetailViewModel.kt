package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchState
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.wholeScreenPhase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The collection detail's all-or-nothing content aggregate ([CollectionDetailUiState.Success]'s pair). */
private data class CollectionDetailContent(
    val detail: MediaDetail,
    val items: List<MediaItem>,
)

class CollectionDetailViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel() {

    private val fetchCoordinator = DeferredFetchCoordinator<String, CollectionDetailContent>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchCollection,
    )

    /**
     * The screen state as a projection of the coordinator's load lifecycle
     * through [wholeScreenPhase] — this screen's loud phase is a whole
     * Loading/Error screen, so the last content is never read while a load
     * is in flight or has failed. Shared eagerly so bare `.value` reads
     * (error-screen retry checks, tests) stay total; the projection trails
     * the coordinator's state by one collection hop (the MutableStateFlow
     * it replaced was written synchronously inside load), so a read in the
     * same dispatch as [loadCollection] may still see the previous frame.
     */
    val uiState: StateFlow<CollectionDetailUiState> = fetchCoordinator.state
        .map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, CollectionDetailUiState.Loading)

    private fun DeferredFetchState<CollectionDetailContent>.toUiState(): CollectionDetailUiState =
        wholeScreenPhase(
            loading = { CollectionDetailUiState.Loading },
            error = { CollectionDetailUiState.Error(it.message ?: "Failed to load collection") },
            content = { CollectionDetailUiState.Success(detail = it.detail, items = it.items) },
        )

    /**
     * User-data changes while another screen is up (watched flip elsewhere,
     * outbox drain landing) only mark this list stale; the single silent
     * reload fires when the screen is next entered (see
     * [DeferredUserDataRefresher]) — never mid-scroll. The whole load
     * lifecycle — publish policy, back-stack re-entry guard (an
     * already-showing Success no-ops; an Error re-arms) included — lives in
     * [DeferredFetchCoordinator].
     */
    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    /** The loud entry — the screen's `LaunchedEffect` and the error screen's retry. */
    fun loadCollection(collectionId: String) {
        fetchCoordinator.load(collectionId)
    }

    /**
     * (Re)fetches the collection's detail + items, returning the whole
     * pair and throwing on any failed half so
     * [DeferredFetchCoordinator] owns the publish policy and failure
     * re-arm. Forcing both halves is the coordinator's silent-regeneration
     * rule in action: the announce heals now — a member flip's badges via
     * the items read (the flip evicts the member's detail, never this
     * collection's page key), and a write touching the collection item
     * itself (favorite from elsewhere) via the detail read.
     */
    private suspend fun fetchCollection(collectionId: String, force: Boolean): CollectionDetailContent {
        return coroutineScope {
            val detailDeferred = async { mediaRepository.getMediaDetail(collectionId, force = force) }
            val itemsDeferred = async {
                mediaRepository.getCollectionItems(collectionId, limit = 100, force = force)
            }

            val detailResult = detailDeferred.await()
            val itemsResult = itemsDeferred.await()

            // All-or-nothing: a failed half fails the whole fetch (the
            // detail error wins when both fail), keeping whatever pair is
            // on screen whole instead of mixing fresh and stale halves.
            (detailResult.exceptionOrNull() ?: itemsResult.exceptionOrNull())?.let { throw it }
            CollectionDetailContent(
                detail = detailResult.getOrThrow(),
                items = itemsResult.getOrThrow().items,
            )
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    /**
     * The screen's container adapter: where the collection's exposed items
     * live. Everything else about the mutation (write, ordering, resume rule)
     * is owned by [UserDataMutator]; the next load reconciles the server truth.
     */
    private val itemContainer = UserDataContainer { itemId, patch ->
        // In-place optimistic flip of the shown aggregate; the projection
        // carries it into the Success state the screen renders.
        fetchCoordinator.updateValue { content ->
            content.copy(
                items = content.items.map { if (it.id == itemId) patch(it) else it },
            )
        }
    }

    /**
     * Marks a collection row item played/unplayed and flips it in-place in
     * [CollectionDetailUiState.Success.items] so the card's badge updates
     * immediately.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(
                itemId = item.id,
                played = played,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(itemContainer),
            )
        }
    }

    /** Ids whose quick actions flip to "Remove download" — see [MediaDownloadActions.downloadedIds]. */
    val downloadedIds = mediaDownloadActions.downloadedIds

    /**
     * Long-press Download from a collection row card (#147): inline start for
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
}
