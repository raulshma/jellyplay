package com.raulshma.jellyplay.feature.search

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrRequestDialogHolder
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.PagedMediaGridHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import com.raulshma.jellyplay.core.data.util.FilterDimensionsHolder

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class SearchViewModel(
    private val mediaRepository: MediaRepository,
    /** The browse-facet seam (the filter row's tag list — off the union). */
    private val mediaBrowseReads: com.raulshma.jellyplay.core.data.repository.MediaBrowseReads,
    /** The SearchResult-shaped reads (the empty-search suggestions — off the union). */
    private val mediaCollectionReads: com.raulshma.jellyplay.core.data.repository.MediaCollectionReads,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
    private val imageUrlProvider: ImageUrlProvider,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val mediaSearchEngine: MediaSearchEngine,
    private val searchFiltersStore: com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore,
    private val quickDownloadActions: QuickDownloadActions,
) : JellyPlayViewModel() {

    /**
     * The single query holder (the [com.raulshma.jellyplay.feature.home.HomeSearchStateHolder]
     * template): one StateFlow feeds the Compose field read (via the exposed
     * [query]), the debounce chain and the Seerr-retry gate — there is no
     * second composeState mirror to keep in step.
     */
    private val _query = stateFlow("")
    val query: StateFlow<String> = _query.flow

    private val _filters = stateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters.flow

    // Genre/tag filter dimensions live on the shared core:data holder (the
    // library/search/editor triple); the public exposure shape is unchanged.
    private val filterDimensions = FilterDimensionsHolder(
        scope = scope,
        getGenres = { force -> mediaRepository.getGenres(force = force) },
        getTags = { mediaBrowseReads.getTags() },
    )
    val genres: StateFlow<List<Genre>> = filterDimensions.genres
    val tags: StateFlow<List<String>> = filterDimensions.tags

    private val _showFilters = stateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.flow

    private val _seerrResults = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrResults: StateFlow<List<SeerrSearchItem>> = _seerrResults.flow

    private val _seerrSearchError = stateFlow(false)
    val seerrSearchError: StateFlow<Boolean> = _seerrSearchError.flow

    private val _searchHistory = stateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory.flow

    private val seerrPrefs: StateFlow<SeerrPreferences> = seerrRepository.preferences

    val isSeerrConnected: StateFlow<Boolean> = seerrPrefs.map {
        it.serverUrl.isNotBlank()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isSeerrSearchEnabled: StateFlow<Boolean> = seerrPrefs.map {
        it.searchEnabled
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    // Shared by the paged search and the side-searches so both react to the
    // same debounced, de-duplicated query emissions — the SAME flow the
    // Compose field reads ([query] is its asStateFlow view).
    private val debouncedQuery = _query.flow
        .debounce(mediaSearchEngine.debounceMs)
        .distinctUntilChanged()

    // Retry re-kick for the side-search round: the engine re-runs a round per
    // query emission, so bumping the tick re-emits the unchanged query into
    // [sideSearchQueries] and the engine's cancel-and-replace does the rest.
    private val _sideSearchRetryTick = stateFlow(0)

    private val sideSearchQueries: Flow<String> =
        combine(debouncedQuery, _sideSearchRetryTick.flow) { query, _ -> query }

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

    /**
     * The paged results grid, wired through the shared [PagedMediaGridHost]:
     * the host owns the deferred-refresh generation counter, the refresher
     * pairing and the cachedIn sharing; this source splices the generation
     * into the (debounced query, filters) combine and keeps the blank-query
     * short-circuit.
     */
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
            combine(debouncedQuery, _filters.flow, trigger) { currentQuery, filters, _ ->
                currentQuery to filters
            }.flatMapLatest { (query, filters) ->
                if (query.isBlank()) {
                    flowOf(PagingData.empty())
                } else {
                    mediaRepository.searchPaged(
                        query = query,
                        filters = filters,
                    )
                }
            }
        },
    )

    val pagedResults: Flow<PagingData<MediaItem>> get() = pagedGrid.items

    /**
     * User-data changes while another screen is up only mark the results
     * stale; the single regeneration fires when the search screen is next
     * entered (the [DeferredUserDataRefresher] the [PagedMediaGridHost]
     * owns) — never mid-scroll.
     */
    val deferredRefresher: DeferredRefreshHost get() = pagedGrid

    init {
        filterDimensions.load()
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
            runCatchingRethrowingCancellation {
                searchFiltersStore.setSearchFilters(FilterCodec.encodeToString(filters))
            }
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
            _query.flow.collect { q ->
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
            val result = mediaCollectionReads.getSearchSuggestions(limit = 20)
            _suggestions.set(result.getOrElse { SearchResult(emptyList(), 0, 0) }.items)
        }
    }

    /**
     * Seerr + on-device side-searches for the current debounced query, ridden
     * through the engine's [MediaSearchEngine.sideSearch] — the same kernel
     * the home bar uses, so the gate, the take limit and the error semantics
     * live in one place. Driven by the query alone — not the filters — so a
     * sort/status tweak neither re-runs the network + local scans for an
     * unchanged query nor flashes the Seerr row empty.
     */
    private fun loadSideSearches() {
        launch {
            mediaSearchEngine.sideSearch(sideSearchQueries).collect { side ->
                _seerrResults.set(side.seerr)
                _seerrSearchError.set(side.seerrError)
                _offlineResults.set(side.offline)
            }
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

    /**
     * The single command funnel (the HomeViewModel `onEvent` precedent): every
     * user intent arrives as a [SearchUiEvent] and is routed once. Pure
     * forwarding events write their holder/state directly in the `when`; there
     * is no per-action command method to keep in sync with the screen.
     */
    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.Search -> {
                _query.set(event.query)
                _suggestions.set(emptyList())
                if (event.query.isBlank()) {
                    _seerrResults.set(emptyList())
                    _seerrSearchError.set(false)
                    _offlineResults.set(emptyList())
                }
            }
            is SearchUiEvent.UpdateFilters -> {
                _filters.set(event.filters)
                persistFilters(event.filters)
            }
            is SearchUiEvent.ToggleMediaType -> {
                _filters.update { it.withMediaTypeToggled(event.mediaType) }
                persistFilters(_filters.value)
            }
            is SearchUiEvent.SetSortBy -> {
                // Single-select sort — the same LibraryFilters.withSortBy
                // policy the library's Sort sheet uses; persisted to survive
                // navigation/restart.
                _filters.update { it.withSortBy(event.sortBy) }
                persistFilters(_filters.value)
            }
            is SearchUiEvent.SetPlayedStatus -> {
                // Single-select played-status (mirrors Library's Status filter).
                _filters.update { it.withPlayedStatus(event.status) }
                persistFilters(_filters.value)
            }
            is SearchUiEvent.ToggleFilters -> _showFilters.set(!_showFilters.value)
            is SearchUiEvent.ClearFilters -> {
                _filters.update { it.cleared() }
                launch {
                    runCatchingRethrowingCancellation { searchFiltersStore.clearSearchFilters() }
                }
            }
            is SearchUiEvent.DeleteSearchHistoryItem -> launch {
                mediaSearchEngine.deleteHistoryItem(event.id)
            }
            is SearchUiEvent.ClearSearchHistory -> launch { mediaSearchEngine.clearHistory() }
            is SearchUiEvent.SearchResultsShown -> if (event.query.isNotBlank()) {
                launch { mediaSearchEngine.recordHistory(event.query, jellyfinHadResults = true) }
            }
            is SearchUiEvent.RetrySeerrSearch -> {
                // Re-kick the side-search round for the current query: the
                // tick re-emits the unchanged query into the engine and its
                // cancel-and-replace clears + re-fetches the Seerr row —
                // identical visible behavior to the old manual relaunch.
                if (_query.value.isNotBlank()) _sideSearchRetryTick.update { it + 1 }
            }
            is SearchUiEvent.MarkItemPlayed ->
                // Intentionally silent (the mutator's default): the paged
                // results are left untouched so the user keeps their scroll
                // position.
                pagedGrid.markPlayed(event.item, event.played)
            is SearchUiEvent.DownloadItem ->
                pagedGrid.download(event.item, { id, _ -> event.onOpenDetail(id) })
            is SearchUiEvent.RemoveItemDownload -> pagedGrid.removeDownload(event.item)
            is SearchUiEvent.RequestSeerrMedia ->
                seerrRequestState.requestMedia(
                    event.item, event.seasons, event.serverId, event.profileId, event.rootFolder, event.tags,
                )
            is SearchUiEvent.OpenSeerrRequestDialog -> seerrRequestDialog.open(event.item)
            is SearchUiEvent.DismissSeerrRequestDialog -> seerrRequestDialog.dismiss()
            is SearchUiEvent.PrefetchSeerrDetails ->
                seerrRequestState.prefetchDetails(event.tmdbId, event.mediaType, event.onDone)
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /** Ids whose quick actions flip to "Remove download" — see [QuickDownloadActions.downloadedIds]. */
    // Whether this platform has a download pipeline — screens gate the
    // download CTA on it (hidden rather than Failed-toasting).
    val downloadSupported get() = pagedGrid.downloadSupported

    val downloadedIds get() = quickDownloadActions.downloadedIds

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    private val seerrRequestState = SeerrRequestStateHolder(scope, seerrRequestDelegate)

    // The dialog half of the request lifecycle: which item the request dialog
    // is open for (frozen at open) plus the open/dismiss choreography. The
    // data half (service details, seasons, result) stays in the holder above,
    // reached through the two constructor seams.
    private val seerrRequestDialog = SeerrRequestDialogHolder(
        prepare = seerrRequestState::prepare,
        clearRequestResult = seerrRequestState::clearRequestResult,
    )

    /** Seerr request lifecycle state (the holder's single snapshot interface). */
    val seerrSnapshot: StateFlow<SeerrRequestSnapshot> = seerrRequestState.snapshotIn(scope)

    /** The item the request dialog is open for (null = closed) — the render gate. */
    val seerrDialogItem: StateFlow<SeerrSearchItem?> = seerrRequestDialog.item
}
