package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    /** Free-text search term forwarded to the Seerr `search` query param. */
    val searchQuery: String = "",
    val actionInProgress: Boolean = false,
    /** Selection-mode state for bulk approve/decline. */
    val selectionMode: Boolean = false,
    val selectedRequestIds: Set<Int> = emptySet(),
    val actionError: String? = null,
) {
    /** The filter-axis fields, bundled for hand-off to [RequestsFilterBar]. */
    val filters: RequestsFilterState
        get() = RequestsFilterState(
            filter = filter,
            mediaType = mediaType,
            sort = sort,
            sortDirection = sortDirection,
            showMyRequestsOnly = showMyRequestsOnly,
            searchQuery = searchQuery,
        )
}

/**
 * The six request-filter fields that travel together from
 * [RequestsUiState] into [RequestsFilterBar]. Kept as a value so the bar's
 * signature is one parameter (plus the callbacks) rather than six loose ones.
 */
@Immutable
data class RequestsFilterState(
    val filter: SeerrRequestFilter = SeerrRequestFilter.PENDING,
    val mediaType: String? = null,
    val sort: SeerrRequestSort = SeerrRequestSort.ADDED,
    val sortDirection: String = "desc",
    val showMyRequestsOnly: Boolean = false,
    val searchQuery: String = "",
)

@OptIn(FlowPreview::class)
class RequestsViewModel(
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

    // Eagerly shared (not `WhileSubscribed`): [loadRequests] reads it via
    // `.value` without holding a collector (same trap as [directArrEnabled]
    // above) and no screen collects it — under `WhileSubscribed` the value
    // would stay `null` forever and "My Requests" would never filter.
    val currentUser: StateFlow<SeerrCurrentUser?> = seerrRepository.currentUser
        .stateIn(scope, SharingStarted.Eagerly, null)

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
        // Debounced search: re-runs the query 400ms after the user stops typing,
        // so each keystroke doesn't hit the Seerr API.
        launch {
            snapshotFlow { _state.value.searchQuery }
                .distinctUntilChanged()
                .debounce(400)
                .collect { loadRequests(refresh = true) }
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
                search = s.searchQuery.takeIf { it.isNotBlank() },
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

    /**
     * Updates the free-text [searchQuery]. The actual request is fired on a
     * 400ms debounce (collected in [init]) so each keystroke doesn't hit the
     * Seerr API. Clears back to page 1.
     */
    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query, currentPage = 1)
    }

    fun clearSearch() {
        if (_state.value.searchQuery.isBlank()) return
        _state.value = _state.value.copy(searchQuery = "", currentPage = 1)
        loadRequests(refresh = true)
    }

    // ── Bulk selection ──────────────────────────────────────────────────────

    /** Toggles [request]'s membership in the selection; enters selection mode on first pick. */
    fun toggleSelection(request: SeerrRequestItem) {
        val current = _state.value.selectedRequestIds
        val next = if (request.id in current) current - request.id else current + request.id
        _state.value = _state.value.copy(
            selectedRequestIds = next,
            selectionMode = next.isNotEmpty(),
        )
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedRequestIds = _state.value.requests.map { it.id }.toSet(),
            selectionMode = true,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedRequestIds = emptySet(), selectionMode = false)
    }

    /** Approves every selected request; clears selection + refreshes on completion. */
    fun approveSelected() = runBulk { id -> seerrRepository.approveRequest(id) }

    /** Declines every selected request; clears selection + refreshes on completion. */
    fun declineSelected() = runBulk { id -> seerrRepository.declineRequest(id) }

    /**
     * Shared body for [approveSelected] / [declineSelected]: flips
     * [actionInProgress], runs [action] against each selected id, then clears
     * selection and refreshes the list. Per-item failures are surfaced inside
     * the Seerr result by the repository; here we only drive the fan-out.
     */
    private fun runBulk(action: suspend (Int) -> Unit) {
        val ids = _state.value.selectedRequestIds.toList()
        if (ids.isEmpty()) return
        launch {
            _state.value = _state.value.copy(actionInProgress = true, actionError = null)
            ids.forEach { id -> action(id) }
            _state.value = _state.value.copy(actionInProgress = false, selectedRequestIds = emptySet(), selectionMode = false)
            loadRequests(refresh = true)
        }
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
