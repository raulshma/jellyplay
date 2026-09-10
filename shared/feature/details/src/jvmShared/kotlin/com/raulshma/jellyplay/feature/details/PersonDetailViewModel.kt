package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
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

/** The person detail's all-or-nothing content aggregate ([PersonDetailUiState.Success]'s fields). */
private data class PersonDetailContent(
    val name: String,
    val filmography: List<MediaItem>,
    val biography: String? = null,
    val profileImageUrl: String? = null,
)

class PersonDetailViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel() {

    private val fetchCoordinator = DeferredFetchCoordinator<String, PersonDetailContent>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchPerson,
    )

    /**
     * The screen state as a projection of the coordinator's load lifecycle
     * through [wholeScreenPhase] — this screen's loud phase is a whole
     * Loading/Error screen, so the last content is never read while a load
     * is in flight or has failed. Shared eagerly so bare `.value` reads
     * (error-screen retry checks, tests) stay total; the projection trails
     * the coordinator's state by one collection hop (the MutableStateFlow
     * it replaced was written synchronously inside load), so a read in the
     * same dispatch as [loadPerson] may still see the previous frame.
     */
    val uiState: StateFlow<PersonDetailUiState> = fetchCoordinator.state
        .map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, PersonDetailUiState.Loading)

    private fun DeferredFetchState<PersonDetailContent>.toUiState(): PersonDetailUiState =
        wholeScreenPhase(
            loading = { PersonDetailUiState.Loading },
            error = { PersonDetailUiState.Error(it.message ?: "Failed to load") },
            content = { content ->
                PersonDetailUiState.Success(
                    name = content.name,
                    filmography = content.filmography,
                    biography = content.biography,
                    profileImageUrl = content.profileImageUrl,
                )
            },
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
    fun loadPerson(personId: String) {
        fetchCoordinator.load(personId)
    }

    /**
     * (Re)fetches the person's detail + filmography, returning the whole
     * aggregate and throwing on any failed half so
     * [DeferredFetchCoordinator] owns the publish policy and failure
     * re-arm. [force] reaches the detail read as the repository
     * cache-bypass flag (the silent regeneration always runs forced —
     * getItemsByPerson is uncached, so force has no bearing on it).
     */
    private suspend fun fetchPerson(personId: String, force: Boolean): PersonDetailContent {
        return coroutineScope {
            // No feature-level retry: the repository paths already retry
            // (and coordinate retry with address failover) in the engine.
            val detailDeferred = async { mediaRepository.getMediaDetail(personId, force = force) }
            val itemsDeferred = async { mediaRepository.getItemsByPerson(personId) }

            val detailResult = detailDeferred.await()
            val itemsResult = itemsDeferred.await()

            // All-or-nothing: a failed half fails the whole fetch (the
            // filmography error wins when both fail), keeping whatever is
            // on screen whole instead of mixing fresh and stale halves.
            (itemsResult.exceptionOrNull() ?: detailResult.exceptionOrNull())?.let { throw it }
            val detail = detailResult.getOrThrow().item
            PersonDetailContent(
                name = detail.name,
                filmography = itemsResult.getOrThrow(),
                biography = detail.overview?.takeIf { it.isNotBlank() },
                profileImageUrl = imageUrlProvider.getImageUrl(personId).takeIf { it.isNotBlank() },
            )
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
        // In-place optimistic flip of the shown aggregate; the projection
        // carries it into the Success state the screen renders.
        fetchCoordinator.updateValue { content ->
            content.copy(
                filmography = content.filmography.map { if (it.id == itemId) patch(it) else it },
            )
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
