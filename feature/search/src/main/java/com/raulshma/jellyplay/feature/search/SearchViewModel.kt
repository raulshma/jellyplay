package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/**
 * Maximum number of offline items to surface in the "On-device" search row.
 * Kept small because the row is supplementary to the paginated library grid.
 */
private const val OFFLINE_SEARCH_RESULT_LIMIT: Int = 10

/**
 * Debounce applied to the search query before triggering a library/Seerr
 * lookup. Kept short so results feel immediate while still coalescing rapid
 * keystrokes and avoiding one network round-trip per character.
 */
private const val SEARCH_DEBOUNCE_MS: Long = 300

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val offlineRepository: OfflineRepository,
    private val experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
    private val serverIdentityStore: com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore,
    private val searchFiltersStore: com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore,
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

    private val hideSearchHistoryPref: StateFlow<Boolean> = experimentalStore.experimental
        .map { it.hideSearchHistory }
        .stateIn(scope, SharingStarted.Lazily, false)

    private val seerrPrefs: Flow<com.raulshma.jellyplay.core.model.seerr.SeerrPreferences> =
        seerrRepository.getPreferences()

    val isSeerrConnected: StateFlow<Boolean> = seerrPrefs.map {
        it.serverUrl.isNotBlank()
    }.stateIn(scope, SharingStarted.Lazily, false)

    val isSeerrSearchEnabled: StateFlow<Boolean> = seerrPrefs.map {
        it.searchEnabled
    }.stateIn(scope, SharingStarted.Lazily, false)

    private val queryFlow = stateFlow("")

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

    val pagedResults: Flow<PagingData<MediaItem>> = combine(
        queryFlow.flow.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
        _filters.flow,
    ) { q, f -> q to f }
        .flatMapLatest { (currentQuery, filters) ->
            seerrSearchJob?.cancel()
            _seerrResults.set(emptyList())
            _seerrSearchError.set(false)
            if (currentQuery.isBlank()) {
                _offlineResults.set(emptyList())
                flowOf(PagingData.empty())
            } else {
                seerrSearchJob = launch { searchSeerr(currentQuery) }
                launch { searchOffline(currentQuery) }
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
            serverIdentityStore.activeUserId
                .flatMapLatest { userId ->
                    if (userId != null) searchHistoryRepository.getRecent(userId)
                    else flowOf(emptyList())
                }
                // Respect the user's "hide search history" preference: when
                // enabled we expose an empty list to the UI while still
                // keeping the underlying history intact for when they re-enable.
                .combine(hideSearchHistoryPref) { history, hide ->
                    if (hide) emptyList() else history
                }
                .collect { history -> _searchHistory.set(history) }
        }
    }

    fun deleteHistoryItem(id: Long) {
        launch { searchHistoryRepository.deleteById(id) }
    }

    fun clearHistory() {
        launch {
            val userId = serverIdentityStore.activeUserId.first() ?: return@launch
            searchHistoryRepository.clearAll(userId)
        }
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
        _filters.update { current ->
            val types = current.mediaTypes
            current.copy(
                mediaTypes = if (mediaType in types) types - mediaType else types + mediaType,
            )
        }
        persistFilters(_filters.value)
    }

    /**
     * Single-select sort setter (mirrors [LibraryViewModel.updateFilters]' sort
     * handling). Persists the new sort option so it survives navigation/restart.
     */
    fun setSortBy(sortBy: SortOption) {
        _filters.update { it.copy(sortBy = sortBy) }
        persistFilters(_filters.value)
    }

    /**
     * Single-select played-status setter (mirrors Library's Status filter).
     * Persists the new status so it survives navigation/restart.
     */
    fun setPlayedStatus(status: PlayedStatus) {
        _filters.update { it.copy(playedStatus = status) }
        persistFilters(_filters.value)
    }

    fun toggleShowFilters() {
        _showFilters.set(!_showFilters.value)
    }

    fun clearFilters() {
        _filters.set(LibraryFilters())
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
     * typo with zero hits never pollutes history.
     */
    fun onSearchResultsShown(query: String) {
        if (query.isBlank()) return
        launch { saveQueryIfNeeded(query) }
    }

    private suspend fun saveQueryIfNeeded(query: String) {
        if (query.trim().length < 2) return
        // Skip persistence entirely when the user has hidden search history —
        // avoids surfacing past queries the moment they re-enable the setting.
        if (hideSearchHistoryPref.value) return
        val userId = serverIdentityStore.activeUserId.first() ?: return
        searchHistoryRepository.saveQuery(query, userId)
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /**
     * Marks a result item played/unplayed on the server.
     * Intentionally silent: the paged results are left untouched so the user
     * keeps their scroll position — the badge updates on the next natural data
     * refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            if (played) mediaRepository.markPlayed(item.id) else mediaRepository.markUnplayed(item.id)
        }
    }

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    private suspend fun searchSeerr(query: String) {
        try {
            val prefs = seerrPrefs.first()
            val connected = prefs.serverUrl.isNotBlank()
            val enabled = prefs.searchEnabled
            if (!connected || !enabled) {
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

    private val seerrRequestState = com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder(scope, seerrRequestDelegate)
    val requestResult: StateFlow<SeerrRequestResult?> get() = seerrRequestState.requestResult
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> get() = seerrRequestState.radarrServers
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> get() = seerrRequestState.sonarrServers
    val isLoadingSeerrServices: StateFlow<Boolean> get() = seerrRequestState.isLoadingServices
    val tvSeasons: StateFlow<List<SeerrSeason>> get() = seerrRequestState.tvSeasons

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) = seerrRequestState.requestMedia(item, seasons, serverId, profileId, rootFolder, tags)

    fun clearRequestResult() = seerrRequestState.clearRequestResult()

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) =
        seerrRequestState.prefetchDetails(tmdbId, mediaType, onDone)

    fun loadSeerrServiceDetails(mediaType: String) = seerrRequestState.loadServiceDetails(mediaType)

    fun loadTvSeasons(tmdbId: Int) = seerrRequestState.loadTvSeasons(tmdbId)
}
