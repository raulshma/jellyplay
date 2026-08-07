package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrDownloadSummary
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class RequestMediaInfo(
    val title: String?,
    val posterUrl: String?,
    val overview: String?,
    val year: Int?,
)

@Immutable
data class RequestsUiState(
    val requests: List<SeerrRequestItem> = emptyList(),
    val mediaInfo: Map<Int, RequestMediaInfo> = emptyMap(),
    /** Direct *arr download progress, keyed by Seerr request `media.tmdbId`. Empty when the feature flag is off or no *arr is configured. */
    val downloadProgress: Map<Int, ArrDownloadSummary> = emptyMap(),
    /** Full queue items (for management actions), keyed by tmdbId. */
    val queueItems: Map<Int, ArrQueueItem> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val pageSize: Int = 10,
    val filter: SeerrRequestFilter = SeerrRequestFilter.PENDING,
    val sort: SeerrRequestSort = SeerrRequestSort.ADDED,
    val sortDirection: String = "desc",
    val mediaType: String? = null,
    val showMyRequestsOnly: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    private val experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
) : JellyPlayViewModel() {

    private val _state = composeState(RequestsUiState())
    val state: State<RequestsUiState> = _state.asState()

    private val enrichSemaphore = Semaphore(4)

    /**
     * Whether the Direct *arr Integration experimental flag is enabled.
     *
     * Eagerly shared (not `WhileSubscribed`) because [enrichDownloadProgress]
     * reads it via `.value` without holding a collector; under
     * `WhileSubscribed` the upstream preferences Flow would never start and
     * `.value` would stay `false` forever, leaving the entire *arr
     * download-progress + queue-management feature unreachable.
     */
    private val directArrEnabled: StateFlow<Boolean> = experimentalStore.experimental
        .map { it.enabledExperimentalFeatures.contains(ExperimentalFeature.DIRECT_ARR_INTEGRATION) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<SeerrCurrentUser?> = seerrRepository.currentUser
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    val isAdmin: StateFlow<Boolean> = seerrRepository.isAdmin()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val pendingRequestCount: StateFlow<Int> = seerrRepository.pendingRequestCount
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Start Seerr background polling while the Requests UI is active so
        // the pending-count badge and current user stay fresh. Polling is
        // stopped in onCleared() to avoid battery drain when the user leaves.
        // The repository is a Singleton, so this is safe across recompositions.
        seerrRepository.startPolling()
        launch {
            seerrRepository.getRequestCount().onSuccess { count ->
                if (count.pending == 0 && _state.value.filter == SeerrRequestFilter.PENDING) {
                    _state.value = _state.value.copy(filter = SeerrRequestFilter.ALL)
                }
            }
            loadRequests(refresh = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        seerrRepository.stopPolling()
    }

    fun loadRequests(refresh: Boolean = false) {
        launch {
            val s = _state.value
            if (s.isLoading) return@launch
            _state.value = s.copy(isLoading = true, error = null)

            val requestedBy = if (s.showMyRequestsOnly) currentUser.value?.id else null
            val skip = if (refresh) 0 else (s.currentPage - 1) * s.pageSize

            seerrRepository.getRequests(
                take = s.pageSize,
                skip = skip,
                filter = s.filter.value,
                sort = s.sort.value,
                sortDirection = s.sortDirection,
                requestedBy = requestedBy,
                mediaType = s.mediaType,
            ).onSuccess { response ->
                _state.value = _state.value.copy(
                    requests = response.results,
                    totalResults = response.pageInfo.results,
                    totalPages = response.pageInfo.pages,
                    isLoading = false,
                )
                enrichRequests(response.results)
                // Direct *arr download progress (no-op when flag off or unconfigured).
                enrichDownloadProgress(response.results)
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    private fun enrichRequests(requests: List<SeerrRequestItem>) {
        requests.forEach { request ->
            val tmdbId = request.media.tmdbId
            if (_state.value.mediaInfo.containsKey(tmdbId)) return@forEach

            launch {
                enrichSemaphore.withPermit {
                    val info = if (request.type.equals("movie", ignoreCase = true)) {
                        seerrRepository.getMovieDetails(tmdbId).getOrNull()?.let {
                            RequestMediaInfo(
                                title = it.title,
                                posterUrl = it.posterUrl,
                                overview = it.overview,
                                year = it.releaseDate?.take(4)?.toIntOrNull(),
                            )
                        }
                    } else {
                        seerrRepository.getTvDetails(tmdbId).getOrNull()?.let {
                            RequestMediaInfo(
                                title = it.name,
                                posterUrl = it.posterUrl,
                                overview = it.overview,
                                year = it.firstAirDate?.take(4)?.toIntOrNull(),
                            )
                        }
                    }

                    info?.let {
                        val current = _state.value.mediaInfo.toMutableMap()
                        current[tmdbId] = it
                        _state.value = _state.value.copy(mediaInfo = current)
                    }
                }
            }
        }
    }

    /**
     * Enriches each request with its direct *arr download progress, mirroring
     * [enrichRequests]'s semaphore-bounded pattern. No-op when the
     * [ExperimentalFeature.DIRECT_ARR_INTEGRATION] flag is off. Per-tmdb
     * failures are swallowed (the *arr repository already degrades to null);
     * a missing download simply leaves the map untouched and the bottom sheet
     * falls back to Seerr's raw `downloadStatus` text.
     */
    private fun enrichDownloadProgress(requests: List<SeerrRequestItem>) {
        if (!directArrEnabled.value) return
        val distinctTmdbIds = requests.mapNotNull { it.media.tmdbId.takeIf { id -> id != 0 } }.distinct()
        if (distinctTmdbIds.isEmpty()) return

        distinctTmdbIds.forEach { tmdbId ->
            launch {
                enrichSemaphore.withPermit {
                    val item = arrRepository.getQueueForTmdb(tmdbId) ?: return@withPermit
                    val summary = ArrDownloadSummary(
                        status = item.status,
                        percent = item.percent,
                        sizeLeft = item.sizeLeft,
                        timeLeft = item.timeLeft,
                    )
                    _state.value = _state.value.let { s ->
                        s.copy(
                            downloadProgress = s.downloadProgress + (tmdbId to summary),
                            queueItems = s.queueItems + (tmdbId to item),
                        )
                    }
                }
            }
        }
    }

    /**
     * Removes the *arr queue row for [tmdbId]. When [blocklist] is true the
     * release is added to the *arr blocklist (won't be grabbed again). When
     * [searchAgain] is true a fresh search command is queued after removal.
     * Updates UI state + triggers a queue refresh on success.
     */
    fun removeQueueItem(tmdbId: Int, blocklist: Boolean, searchAgain: Boolean) {
        val item = _state.value.queueItems[tmdbId] ?: return
        val kind = item.serverKind
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            val options = ArrQueueDeleteOptions(
                removeFromClient = true,
                blocklist = blocklist,
                skipRedownload = !searchAgain,
            )
            arrRepository.deleteQueueItem(item, options)
                .onSuccess {
                    if (searchAgain) {
                        arrRepository.searchForTmdb(tmdbId, kind)
                    }
                    // Drop the cached progress + item; refresh re-populates if still present.
                    _state.value = _state.value.let { s ->
                        s.copy(
                            downloadProgress = s.downloadProgress - tmdbId,
                            queueItems = s.queueItems - tmdbId,
                        )
                    }
                    loadRequests(refresh = true)
                }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    /** Queues a fresh search for [tmdbId] without removing anything. */
    fun searchAgainForTmdb(tmdbId: Int, kind: ArrServiceKind) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            arrRepository.searchForTmdb(tmdbId, kind)
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun setFilter(filter: SeerrRequestFilter) {
        _state.value = _state.value.copy(filter = filter, currentPage = 1)
        loadRequests(refresh = true)
    }

    fun setSort(sort: SeerrRequestSort) {
        _state.value = _state.value.copy(sort = sort, currentPage = 1)
        loadRequests(refresh = true)
    }

    fun toggleSortDirection() {
        val newDir = if (_state.value.sortDirection == "desc") "asc" else "desc"
        _state.value = _state.value.copy(sortDirection = newDir, currentPage = 1)
        loadRequests(refresh = true)
    }

    fun setMediaType(mediaType: String?) {
        _state.value = _state.value.copy(mediaType = mediaType, currentPage = 1)
        loadRequests(refresh = true)
    }

    fun toggleMyRequestsOnly() {
        val newValue = !_state.value.showMyRequestsOnly
        _state.value = _state.value.copy(showMyRequestsOnly = newValue, currentPage = 1)
        loadRequests(refresh = true)
    }

    fun nextPage() {
        val s = _state.value
        if (s.currentPage < s.totalPages) {
            _state.value = s.copy(currentPage = s.currentPage + 1)
            loadRequests()
        }
    }

    fun prevPage() {
        val s = _state.value
        if (s.currentPage > 1) {
            _state.value = s.copy(currentPage = s.currentPage - 1)
            loadRequests()
        }
    }

    fun approveRequest(requestId: Int) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            seerrRepository.approveRequest(requestId)
                .onSuccess { loadRequests(refresh = true) }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun declineRequest(requestId: Int) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            seerrRepository.declineRequest(requestId)
                .onSuccess { loadRequests(refresh = true) }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun retryRequest(requestId: Int) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            seerrRepository.retryRequest(requestId)
                .onSuccess { loadRequests(refresh = true) }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun deleteRequest(requestId: Int) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            seerrRepository.deleteRequest(requestId)
                .onSuccess { loadRequests(refresh = true) }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun removeFromService(mediaId: Int, is4k: Boolean) {
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            seerrRepository.deleteMedia(mediaId, is4k)
                .onSuccess { loadRequests(refresh = true) }
                .onFailure { _state.value = _state.value.copy(actionError = it.message) }
            _state.value = _state.value.copy(actionInProgress = false)
        }
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }
}
