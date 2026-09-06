package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.raulshma.jellyplay.core.data.util.FilterCodec
import com.raulshma.jellyplay.core.data.util.loadListWithRetry

/**
 * Maximum number of offline items to surface in the "On-device" search row.
 * Kept small because the row is supplementary to the paginated library grid.
 */
private const val OFFLINE_SEARCH_RESULT_LIMIT: Int = 10

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val mediaRepository: MediaRepository,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val mediaSearchEngine: MediaSearchEngine,
    private val offlineRepository: OfflineRepository,
    private val searchFiltersStore: com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore,
    private val mediaDownloadActions: MediaDownloadActions,
) : JellyPlayViewModel() {

    private val _query = composeState("")
    var query: String
        get() = _query.value
        private set(value) { _query.value = value }

    private val _filters = stateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters.flow

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.flow

    private val _tags = stateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.flow

    private val _showFilters = stateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.flow

    private val _seerrResults = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrResults: StateFlow<List<SeerrSearchItem>> = _seerrResults.flow

    private val _seerrSearchError = stateFlow(false)
    val seerrSearchError: StateFlow<Boolean> = _seerrSearchError.flow

    private val _searchHistory = stateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory.flow

    private val seerrPrefs: StateFlow<SeerrPreferences> =
        seerrRepository.getPreferences().stateIn(scope, SharingStarted.Lazily, SeerrPreferences())

    val isSeerrConnected: StateFlow<Boolean> = seerrPrefs.map {
        it.serverUrl.isNotBlank()
    }.stateIn(scope, SharingStarted.Lazily, false)

    val isSeerrSearchEnabled: StateFlow<Boolean> = seerrPrefs.map {
        it.searchEnabled
    }.stateIn(scope, SharingStarted.Lazily, false)

    private val queryFlow = stateFlow("")

    // Shared by the paged search and the side-searches so both react to the
    // same debounced, de-duplicated query emissions.
    private val debouncedQuery = queryFlow.flow
        .debounce(mediaSearchEngine.debounceMs)
        .distinctUntilChanged()

    private val _suggestions = stateFlow<List<MediaItem>>(emptyList())
    val suggestions: StateFlow<List<MediaItem>> = _suggestions.flow

    /**
     * Tracks whether discovery suggestions have been loaded for the current
     * empty state. Avoids re-fetching on every recomposition-driven re-entry
     * while still reloading once the user clears their query back to blank.
     */
    private var suggestionsLoaded: Boolean = false

    private val _offlineResults = stateFlow<List<OfflineMediaItem>>(emptyList())
    val offlineResults: StateFlow<List<OfflineMediaItem>> = _offlineResults.flow

    private var seerrSearchJob: Job? = null

    // Tracked like seerrSearchJob: an untracked offline scan could complete
    // after a newer query published its results and overwrite them with
    // stale rows (plus a wasted duplicate DB scan per keystroke burst).
    private var offlineSearchJob: Job? = null

    val pagedResults: Flow<PagingData<MediaItem>> = combine(
        debouncedQuery,
        _filters.flow,
    ) { q, f -> q to f }
        .flatMapLatest { (currentQuery, filters) ->
            if (currentQuery.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                mediaRepository.searchPaged(
                    query = currentQuery,
                    filters = filters,
                )
            }
        }
        .cachedIn(scope)

    init {
        loadGenres()
        loadTags()
        loadSearchHistory()
        loadSuggestions()
        loadSideSearches()
        loadPersistedFilters()
    }

    /**
     * Seeds [_filters] from the persisted snapshot (single-key JSON blob in the
     * shared DataStore). Decode failures fall back to the default filter set so a
     * corrupt or forward-incompatible blob never blocks search. Mirrors how
     * [LibraryViewModel.selectFolder] decodes its per-folder filter blob.
     */
    private fun loadPersistedFilters() {
        launch {
            val raw = searchFiltersStore.searchFiltersJson.first() ?: return@launch
            val restored = runCatching { FilterCodec.decodeFromString<LibraryFilters>(raw) }
                .getOrNull() ?: return@launch
            _filters.set(restored)
        }
    }

    /**
     * Writes the current filter snapshot to the DataStore. Best-effort: a write
     * failure is swallowed so a preference-store hiccup never disrupts the
     * in-memory search session.
     */
    private fun persistFilters(filters: LibraryFilters) {
        launch {
            runCatching { searchFiltersStore.setSearchFilters(FilterCodec.encodeToString(filters)) }
        }
    }

    /**
     * Discovery suggestions for the empty search state — favorited/liked items
     * surfaced in random order, matching the official jellyfin-web behavior.
     * Unlike the previous while-typing autocomplete dropdown, suggestions only
     * appear when the query is blank; typing clears them, and clearing the
     * query reloads them. Clicking a suggestion navigates to its detail page.
     */
    private fun loadSuggestions() {
        launch {
            queryFlow.flow.collect { q ->
                    if (q.isBlank()) {
                        // Reload discovery suggestions each time we return to the
                        // empty state so the random selection stays fresh.
                        suggestionsLoaded = false
                        loadDiscoverySuggestions()
                    } else {
                        // Any typed query hides suggestions (no autocomplete).
                        _suggestions.set(emptyList())
                    }
                }
        }
    }

    private fun loadDiscoverySuggestions() {
        if (suggestionsLoaded) return
        suggestionsLoaded = true
        launch {
            val result = mediaRepository.getSearchSuggestions(limit = 20)
            _suggestions.set(result.getOrElse { SearchResult(emptyList(), 0, 0) }.items)
        }
    }

    /**
     * Seerr + on-device side-searches for the current debounced query. Driven
     * by the query alone — not the filters — so a sort/status tweak neither
     * re-runs the network + local scans for an unchanged query nor flashes
     * the Seerr row empty.
     */
    private fun loadSideSearches() {
        launch {
            debouncedQuery.collect { currentQuery ->
                seerrSearchJob?.cancel()
                offlineSearchJob?.cancel()
                _seerrResults.set(emptyList())
                _seerrSearchError.set(false)
                if (currentQuery.isBlank()) {
                    _offlineResults.set(emptyList())
                } else {
                    seerrSearchJob = launch { searchSeerr(currentQuery) }
                    offlineSearchJob = launch { searchOffline(currentQuery) }
                }
            }
        }
    }

    private suspend fun searchOffline(query: String) {
        try {
            val results = offlineRepository.searchOffline(query, limit = OFFLINE_SEARCH_RESULT_LIMIT)
            _offlineResults.set(results)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline search is best-effort; never surface errors to the user
            // since the library/Seerr results may still be relevant.
            _offlineResults.set(emptyList())
        }
    }

    private fun loadSearchHistory() {
        launch {
            // Keyed on the active user and gated by the hide-history
            // preference inside the engine — the same policy the home bar's
            // inline search now uses.
            mediaSearchEngine.recentHistory().collect { history -> _searchHistory.set(history) }
        }
    }

    fun deleteHistoryItem(id: Long) {
        launch { mediaSearchEngine.deleteHistoryItem(id) }
    }

    fun clearHistory() {
        launch { mediaSearchEngine.clearHistory() }
    }

    fun search(newQuery: String) {
        _query.value = newQuery
        queryFlow.set(newQuery)
        _suggestions.set(emptyList())
        if (newQuery.isBlank()) {
            _seerrResults.set(emptyList())
            _seerrSearchError.set(false)
            _offlineResults.set(emptyList())
        }
    }

    fun updateFilters(newFilters: LibraryFilters) {
        _filters.set(newFilters)
        persistFilters(newFilters)
    }

    fun toggleMediaType(mediaType: MediaType) {
        _filters.update { it.withMediaTypeToggled(mediaType) }
        persistFilters(_filters.value)
    }

    /**
     * Single-select sort setter — writes through the [LibraryFilters] algebra
     * (the same [LibraryFilters.withSortBy] policy the library's Sort sheet
     * uses). Persists the new sort option so it survives navigation/restart.
     */
    fun setSortBy(sortBy: SortOption) {
        _filters.update { it.withSortBy(sortBy) }
        persistFilters(_filters.value)
    }

    /**
     * Single-select played-status setter (mirrors Library's Status filter).
     * Persists the new status so it survives navigation/restart.
     */
    fun setPlayedStatus(status: PlayedStatus) {
        _filters.update { it.withPlayedStatus(status) }
        persistFilters(_filters.value)
    }

    fun toggleShowFilters() {
        _showFilters.set(!_showFilters.value)
    }

    fun clearFilters() {
        _filters.update { it.cleared() }
        launch { runCatching { searchFiltersStore.clearSearchFilters() } }
    }

    private fun loadGenres() {
        launch {
            // Retry once after a short delay so a transient network blip doesn't
            // leave the filter sheet permanently missing its Genres section.
            loadListWithRetry(mediaRepository::getGenres) { _genres.set(it) }
        }
    }

    private fun loadTags() {
        launch {
            loadListWithRetry(mediaRepository::getTags) { _tags.set(it) }
        }
    }

    /**
     * Called by the UI once the paged search confirms a non-empty result set for
     * [query] (refresh finished with ≥1 item). This defers persisting the query
     * to "Recent Searches" until we know it actually matched something, so a
     * typo with zero hits never pollutes history. The result-gate itself
     * (≥2 chars, hide-history preference, active user) lives in
     * [MediaSearchEngine.recordHistory].
     */
    fun onSearchResultsShown(query: String) {
        if (query.isBlank()) return
        launch { mediaSearchEngine.recordHistory(query, jellyfinHadResults = true) }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /**
     * Marks a result item played/unplayed. Intentionally silent (the mutator's
     * default): the paged results are left untouched so the user keeps their
     * scroll position — the badge updates on the next natural data refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(item.id, played)
        }
    }

    /** Ids whose quick actions flip to "Remove download" — see [MediaDownloadActions.downloadedIds]. */
    val downloadedIds = mediaDownloadActions.downloadedIds

    /**
     * Long-press Download from a search result card (#147): inline start for
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

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    private suspend fun searchSeerr(query: String) {
        try {
            // The Seerr gate (connected + search-enabled + not on a Local
            // network) is owned by the engine — the same gate the home bar's
            // inline search uses. The error row below stays screen-local.
            if (!mediaSearchEngine.isSeerrSearchAvailable()) {
                _seerrResults.set(emptyList())
                _seerrSearchError.set(false)
                return
            }
            seerrRepository.search(query)
                .onSuccess { response ->
                    _seerrResults.set(response.results.take(10))
                    _seerrSearchError.set(false)
                }
                .onFailure {
                    _seerrResults.set(emptyList())
                    _seerrSearchError.set(true)
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            _seerrResults.set(emptyList())
            _seerrSearchError.set(true)
        }
    }

    fun retrySeerrSearch() {
        val currentQuery = query
        if (currentQuery.isBlank()) return
        seerrSearchJob?.cancel()
        seerrSearchJob = launch {
            _seerrSearchError.set(false)
            searchSeerr(currentQuery)
        }
    }

    private val seerrRequestState = SeerrRequestStateHolder(scope, seerrRequestDelegate)

    /** Seerr request lifecycle state (the holder's single snapshot interface). */
    val seerrSnapshot: StateFlow<SeerrRequestSnapshot> = seerrRequestState.snapshotIn(scope)

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) = seerrRequestState.requestMedia(item, seasons, serverId, profileId, rootFolder, tags)

    /**
     * Opens the Seerr request dialog for [item]: the item plus the open
     * cascade (service details, TV seasons for tv) are owned by the holder —
     * the screen's dialog renders from the snapshot's `dialogItem`.
     */
    fun openSeerrRequestDialog(item: SeerrSearchItem) = seerrRequestState.openRequestDialog(item)

    /** Closes the dialog and clears the last request result (holder-owned ordering). */
    fun dismissSeerrRequestDialog() = seerrRequestState.dismissRequestDialog()

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) =
        seerrRequestState.prefetchDetails(tmdbId, mediaType, onDone)
}
