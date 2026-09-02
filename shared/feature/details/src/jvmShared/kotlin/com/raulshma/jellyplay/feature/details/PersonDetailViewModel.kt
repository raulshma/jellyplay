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

class PersonDetailViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<PersonDetailUiState>(PersonDetailUiState.Loading)
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    fun loadPerson(personId: String) {
        _uiState.value = PersonDetailUiState.Loading
        launch {
            coroutineScope {
                // No feature-level retry: the repository paths already retry
                // (and coordinate retry with address failover) in the engine.
                val detailDeferred = async { mediaRepository.getMediaDetail(personId) }
                val itemsDeferred = async { mediaRepository.getItemsByPerson(personId) }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                _uiState.value = if (detailResult.isSuccess && itemsResult.isSuccess) {
                    val detail = detailResult.getOrThrow().item
                    PersonDetailUiState.Success(
                        name = detail.name,
                        filmography = itemsResult.getOrThrow(),
                        biography = detail.overview?.takeIf { it.isNotBlank() },
                        profileImageUrl = imageUrlProvider.getImageUrl(personId).takeIf { it.isNotBlank() },
                    )
                } else {
                    val detailError = detailResult.exceptionOrNull()?.message
                    val itemsError = itemsResult.exceptionOrNull()?.message
                    PersonDetailUiState.Error(itemsError ?: detailError ?: "Failed to load")
                }
            }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /**
     * The screen's container adapter: where the filmography's exposed items
     * live. Everything else about the mutation (write, ordering, resume rule)
     * is owned by [UserDataMutator]; the next load reconciles the server truth.
     */
    private val itemContainer = UserDataContainer { itemId, patch ->
        _uiState.update { state ->
            if (state is PersonDetailUiState.Success) {
                state.copy(
                    filmography = state.filmography.map { if (it.id == itemId) patch(it) else it },
                )
            } else {
                state
            }
        }
    }

    /**
     * Marks a filmography item played/unplayed and flips it in-place in
     * [PersonDetailUiState.Success.filmography] so the card's badge updates
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
     * Long-press Download from a filmography card (#147): inline start for
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
