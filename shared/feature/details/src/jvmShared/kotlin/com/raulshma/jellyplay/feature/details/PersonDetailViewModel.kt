package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.FetchMode
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

    private val fetchCoordinator = DeferredFetchCoordinator<String>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchPerson,
        onLoudStart = { _uiState.value = PersonDetailUiState.Loading },
        onLoudError = { e ->
            // Same contract as a `false` from a loud [fetchPerson]: a thrown
            // repo path must not strand the Loading state published above —
            // swap to Error (silent keeps the last Success).
            _uiState.value = PersonDetailUiState.Error(e.message ?: "Failed to load")
        },
    )

    /**
     * User-data changes while another screen is up (watched flip elsewhere,
     * outbox drain landing) only mark this list stale; the single silent
     * reload fires when the screen is next entered (see
     * [DeferredUserDataRefresher]) — never mid-scroll. The whole load
     * lifecycle — including the back-stack re-entry guard (an
     * already-showing Success no-ops; an Error re-arms) — lives in
     * [DeferredFetchCoordinator].
     */
    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    /** The loud entry — the screen's `LaunchedEffect` and the error screen's retry. */
    fun loadPerson(personId: String) {
        fetchCoordinator.load(personId)
    }

    /**
     * (Re)fetches the person's detail + filmography, keyed on the
     * coordinator's mode and reporting plain success so
     * [DeferredFetchCoordinator] owns the failure re-arm. [force] reaches
     * the detail read as the repository cache-bypass flag — the silent
     * regeneration always runs forced, the loud entry on pull-to-refresh.
     * [FetchMode.SILENT] serves the deferred-refresh path: a fetch failure
     * keeps the last Success instead of flashing an Error screen over
     * content the user was just looking at — serve-stale-while-revalidate,
     * same philosophy as the home refresher.
     */
    private suspend fun fetchPerson(personId: String, mode: FetchMode, force: Boolean): Boolean {
        return coroutineScope {
            // No feature-level retry: the repository paths already retry
            // (and coordinate retry with address failover) in the engine.
            // getItemsByPerson is uncached, so force has no bearing on it.
            val detailDeferred = async { mediaRepository.getMediaDetail(personId, force = force) }
            val itemsDeferred = async { mediaRepository.getItemsByPerson(personId) }

            val detailResult = detailDeferred.await()
            val itemsResult = itemsDeferred.await()

            if (detailResult.isSuccess && itemsResult.isSuccess) {
                val detail = detailResult.getOrThrow().item
                _uiState.value = PersonDetailUiState.Success(
                    name = detail.name,
                    filmography = itemsResult.getOrThrow(),
                    biography = detail.overview?.takeIf { it.isNotBlank() },
                    profileImageUrl = imageUrlProvider.getImageUrl(personId).takeIf { it.isNotBlank() },
                )
                true
            } else {
                if (mode == FetchMode.LOUD) {
                    val detailError = detailResult.exceptionOrNull()?.message
                    val itemsError = itemsResult.exceptionOrNull()?.message
                    _uiState.value = PersonDetailUiState.Error(itemsError ?: detailError ?: "Failed to load")
                }
                false
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
