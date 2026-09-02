package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CollectionDetailViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    fun loadCollection(collectionId: String) {
        _uiState.value = CollectionDetailUiState.Loading
        launch {
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(collectionId) }
                val itemsDeferred = async { mediaRepository.getCollectionItems(collectionId, limit = 100) }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                val failure = detailResult.exceptionOrNull() ?: itemsResult.exceptionOrNull()
                _uiState.value = if (failure == null) {
                    CollectionDetailUiState.Success(
                        detail = detailResult.getOrThrow(),
                        items = itemsResult.getOrThrow().items,
                    )
                } else {
                    CollectionDetailUiState.Error(failure.message ?: "Failed to load collection")
                }
            }
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
        _uiState.update { state ->
            if (state is CollectionDetailUiState.Success) {
                state.copy(
                    items = state.items.map { if (it.id == itemId) patch(it) else it },
                )
            } else {
                state
            }
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
