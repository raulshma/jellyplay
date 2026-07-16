package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchMatcher
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchRegistry
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val offlineModeManager: OfflineModeManager,
    private val newsletterTriggerManager: NewsletterTriggerManager,
    private val preferencesStore: UserPreferencesStore,
    private val preferencesEditor: PreferencesEditor,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val authRepository: AuthRepository,
    private val arrRepository: com.raulshma.jellyplay.core.data.repository.ArrRepository,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : JellyPlayViewModel(), DefaultLifecycleObserver {

    companion object {
        private const val REFRESH_INTERVAL_FOREGROUND_MS = 60_000L
        // Background polling is slowed to a 15-minute cadence. The Home VM
        // survives while its nav entry is in the back stack, so a short
        // background interval kept fanning out up to 5 Seerr requests while
        // the user was on a different screen entirely. The existing
        // onResume-if-stale refresh (see onStart) re-syncs immediately when
        // the user returns to the foreground, so the longer background
        // interval costs nothing in freshness.
        private const val REFRESH_INTERVAL_BACKGROUND_MS = 15 * 60_000L
        private const val MIN_REFRESH_INTERVAL_MS = 30_000L
        /** TTL for Seerr discover sections (trending/popular change slowly). */
        private const val DISCOVER_TTL_MS = 10 * 60_000L
        /**
         * Cap on cached photo-folder child-URL entries. Photo folders are a
         * fixed, small set per server, but the map is append-only (`+`) and a
         * long-lived VM could accumulate stale entries across library changes.
         * Evict oldest entries beyond this cap.
         */
        private const val PHOTO_FOLDER_CACHE_CAP = 50
    }

    private val _uiState = stateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.flow

    val activeDownloadCount: StateFlow<Int> = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private var enabledHomeSectionTypes = HomeSectionType.CONFIGURABLE.toSet()
    private var homeSectionOrder = HomeSectionType.CONFIGURABLE
    private var libraryHomeSectionOverrides = emptyMap<String, Set<HomeSectionType>>()
    private var mergeContinueWatchingAndNextUp = false
    private var nextUpMaxDays = 0
    private var nextUpRewatching = false
    private var nextUpExcludedSeriesIds = emptySet<String>()
    private var hiddenCwItemIds = emptySet<String>()
    private var pinnedHomeSections = emptyList<PinnedHomeSection>()
    private var androidTvWatchNextEnabled = true
    private var lastContinueWatchingIds: Set<String> = emptySet()
    private var seerrPreferences = SeerrPreferences()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var homeScrollPosition = HomeScrollPosition()
    private var homeFocusPosition = HomeFocusPosition()
    private var lastRefreshTime = 0L
    private var isAppInForeground = true
    // Discover-sections TTL bookkeeping (see DISCOVER_TTL_MS / fetchDiscoverSections).
    private var lastDiscoverFetchEpochMs = 0L
    private var discoverCacheInvalidated = true

    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")
    private var searchJob: Job? = null

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory

    /**
     * Encapsulates all Seerr request UI state (result, servers, loading, seasons).
     * SearchViewModel uses the same holder — this avoids Home duplicating that
     * logic inline. The holder's five StateFlows are folded into
     * [HomeUiState.seerrRequestState] below so the UI observes a single object.
     */
    private val seerrRequestStateHolder = SeerrRequestStateHolder(scope, seerrRequestDelegate)

    init {
        // Guard against environments where the process LifecycleOwner isn't initialised
        // (e.g. JVM unit tests). addObserver is best-effort and must not crash construction.
        runCatching { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }

        launch {
            var previousUserId: String? = null
            authRepository.currentUser.collectLatest { user ->
                _uiState.update { it.copy(currentUser = user) }

                val userId = user?.id
                if (userId == null) {
                    if (previousUserId != null) {
                        refreshJob?.cancel()
                        resetHomeScrollPosition()
                        resetHomeFocusPosition()
                        _uiState.update {
                            it.copy(
                                sections = emptyList(),
                                favorites = emptyList(),
                                discoverSections = emptyMap(),
                                error = null,
                                isLoading = true,
                            )
                        }
                    }
                } else if (previousUserId != userId) {
                    refreshJob?.cancel()
                    resetHomeScrollPosition()
                    resetHomeFocusPosition()
                    _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null, isLoading = true) }
                    fetchAndUpdateSections()
                    startPeriodicRefresh()
                }
                previousUserId = userId
            }
        }

        launch {
            var hasSeenHomePreferences = false
            preferencesStore.preferences.collect { prefs ->
                val homeSectionPrefsChanged = hasSeenHomePreferences && (
                    prefs.enabledHomeSectionTypes != enabledHomeSectionTypes ||
                        prefs.homeSectionOrder != homeSectionOrder ||
                        prefs.libraryHomeSectionOverrides != libraryHomeSectionOverrides ||
                        prefs.mergeContinueWatchingAndNextUp != mergeContinueWatchingAndNextUp ||
                        prefs.nextUpMaxDays != nextUpMaxDays ||
                        prefs.nextUpRewatching != nextUpRewatching ||
                        prefs.nextUpExcludedSeriesIds != nextUpExcludedSeriesIds ||
                        prefs.hiddenCwItemIds != hiddenCwItemIds ||
                        prefs.pinnedHomeSections != pinnedHomeSections
                    )

                hasSeenHomePreferences = true
                enabledHomeSectionTypes = prefs.enabledHomeSectionTypes
                homeSectionOrder = prefs.homeSectionOrder
                libraryHomeSectionOverrides = prefs.libraryHomeSectionOverrides
                mergeContinueWatchingAndNextUp = prefs.mergeContinueWatchingAndNextUp
                nextUpMaxDays = prefs.nextUpMaxDays
                nextUpRewatching = prefs.nextUpRewatching
                nextUpExcludedSeriesIds = prefs.nextUpExcludedSeriesIds
                hiddenCwItemIds = prefs.hiddenCwItemIds
                pinnedHomeSections = prefs.pinnedHomeSections
                androidTvWatchNextEnabled = prefs.androidTvWatchNextEnabled
                _uiState.update { it.copy(
                    homeMode = prefs.homeMode,
                    dynamicTheming = prefs.dynamicTheming,
                    oledMode = prefs.oledMode,
                    colorStyle = prefs.colorStyle,
                    accentColorSwatch = prefs.accentColorSwatch,
                    homeHeroEnabled = prefs.homeHeroEnabled,
                    showClock = prefs.showClockOnHome,
                    showSettingsInHomeSearch = prefs.showSettingsInHomeSearch,
                    continueWatchingClickBehavior = prefs.continueWatchingClickBehavior,
                    experimentalCardClippingEnabled = com.raulshma.jellyplay.core.model.ExperimentalFeature.HOME_CARD_CLIPPING in prefs.enabledExperimentalFeatures,
                    directArrEnabled = com.raulshma.jellyplay.core.model.ExperimentalFeature.DIRECT_ARR_INTEGRATION in prefs.enabledExperimentalFeatures,
                ) }

                if (homeSectionPrefsChanged) {
                    fetchAndUpdateSections()
                }
            }
        }

        launch {
            seerrPreferencesStore.preferences.collect { prefs ->
                val wasEnabled = _uiState.value.discoverEnabled
                seerrPreferences = prefs
                val nowEnabled = prefs.enabled && prefs.discoverEnabled
                _uiState.update { it.copy(discoverEnabled = nowEnabled) }
                if (nowEnabled && !wasEnabled) {
                    fetchDiscoverSections(prefs)
                }
            }
        }

        launch {
            offlineModeManager.offlineMode.collect { mode ->
                val wasOffline = _uiState.value.offlineMode != OfflineMode.ONLINE
                _uiState.update { it.copy(offlineMode = mode) }
                if (mode != OfflineMode.ONLINE && !wasOffline) {
                    _uiState.update { it.copy(sections = emptyList(), discoverSections = emptyMap()) }
                } else if (mode == OfflineMode.ONLINE && wasOffline) {
                    fetchAndUpdateSections()
                }
            }
        }

        // Only collect the offline library while actually in an offline mode.
        // The underlying Flow re-emits on every download progress write, so
        // collecting it unconditionally caused the whole home tree to
        // re-invalidated during downloads even in online mode (where the
        // offline branch never renders). flatMapLatest on offlineMode means
        // the upstream collection is cancelled entirely while online.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        launch {
            offlineModeManager.offlineMode
                .flatMapLatest { mode ->
                    if (mode != OfflineMode.ONLINE) offlineRepository.getOfflineLibrary()
                    else kotlinx.coroutines.flow.flowOf(emptyList())
                }
                .collect { items ->
                    _uiState.update { it.copy(offlineLibrary = items) }
                }
        }

        launch {
            newsletterTriggerManager.shouldShowBanner().collect { showBanner ->
                _uiState.update { it.copy(newsletterBannerVisible = showBanner) }
            }
        }

        launch {
            preferencesStore.activeUserId
                .flatMapLatest { userId ->
                    if (userId != null) searchHistoryRepository.getRecent(userId)
                    else kotlinx.coroutines.flow.flowOf(emptyList())
                }
                .collect { history -> _searchHistory.value = history }
        }

        @OptIn(FlowPreview::class)
        launch {
            searchQueryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    searchJob?.cancel()
                    if (query.isBlank()) {
                        _uiState.update { it.copy(searchState = it.searchState.copy(jellyfinResults = emptyList(), seerrResults = emptyList(), settingsResults = emptyList(), isSearching = false)) }
                    } else {
                        searchJob = launch { performSearch(query) }
                    }
                }
        }

        // Local settings search runs on a shorter debounce than the networked
        // media search above: the fuzzy matcher is pure and sub-millisecond, so
        // results feel instant while the Jellyfin/Seerr requests are still in
        // flight. Gated entirely behind the user's Appearance toggle; when off
        // the collector still drains but emits nothing and clears results.
        @OptIn(FlowPreview::class)
        launch {
            searchQueryFlow
                .debounce(120)
                .distinctUntilChanged()
                .map { query ->
                    if (query.isBlank() || !_uiState.value.showSettingsInHomeSearch) {
                        emptyList()
                    } else {
                        SettingsSearchMatcher.search(query, SettingsSearchRegistry.items)
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { results ->
                    _uiState.update {
                        it.copy(searchState = it.searchState.copy(settingsResults = results))
                    }
                }
        }

        // Fold the shared SeerrRequestStateHolder into HomeUiState so the UI
        // observes a single object. The holder's five StateFlows are combined
        // into one SeerrRequestState so concurrent emissions (e.g. loading
        // services + seasons firing together when the request dialog opens)
        // coalesce into a single _uiState update instead of five back-to-back
        // copies. `requestItem` is set separately (selectSeerrRequestItem) and
        // is preserved by copying only the five merged fields here.
        launch {
            combine(
                seerrRequestStateHolder.requestResult,
                seerrRequestStateHolder.radarrServers,
                seerrRequestStateHolder.sonarrServers,
                seerrRequestStateHolder.isLoadingServices,
                seerrRequestStateHolder.tvSeasons,
            ) { result, radarr, sonarr, loading, seasons ->
                SeerrRequestSlice(result, radarr, sonarr, loading, seasons)
            }.distinctUntilChanged().collect { slice ->
                _uiState.update {
                    it.copy(
                        seerrRequestState = it.seerrRequestState.copy(
                            result = slice.result,
                            radarrServers = slice.radarrServers,
                            sonarrServers = slice.sonarrServers,
                            isLoadingServices = slice.isLoadingServices,
                            tvSeasons = slice.tvSeasons,
                        ),
                    )
                }
            }
        }
    }

    /** Intermediate holder for the five combined Seerr flows. */
    private data class SeerrRequestSlice(
        val result: com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult?,
        val radarrServers: List<com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail>,
        val sonarrServers: List<com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail>,
        val isLoadingServices: Boolean,
        val tvSeasons: List<com.raulshma.jellyplay.core.model.seerr.SeerrSeason>,
    )

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.Refresh -> refresh()
            is HomeUiEvent.PullToRefresh -> pullToRefresh()
            is HomeUiEvent.ToggleOfflineMode -> toggleOfflineMode()
            is HomeUiEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is HomeUiEvent.ClearSearch -> clearSearch()
            is HomeUiEvent.SelectSeerrRequestItem -> selectSeerrRequestItem(event.item)
            is HomeUiEvent.RequestSeerrMedia -> requestSeerrMedia(event)
            is HomeUiEvent.ClearRequestResult -> clearRequestResult()
            is HomeUiEvent.LoadSeerrServiceDetails -> loadSeerrServiceDetails(event.mediaType)
            is HomeUiEvent.LoadTvSeasons -> loadTvSeasons(event.tmdbId)
            is HomeUiEvent.DismissNewsletterBanner -> _uiState.update { it.copy(newsletterBannerVisible = false) }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    private val _photoFolderChildUrls = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls: StateFlow<Map<String, List<String>>> = _photoFolderChildUrls

    fun prefetchPhotoFolderChildUrls(items: List<com.raulshma.jellyplay.core.model.MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            val results = photoFolderPrefetcher.prefetch(items, alreadyFetched = current.keys)
            if (results.isNotEmpty()) {
                // Merge then evict the oldest entries beyond PHOTO_FOLDER_CACHE_CAP
                // so the map stays bounded for the VM's lifetime.
                val merged = _photoFolderChildUrls.value + results
                _photoFolderChildUrls.value =
                    if (merged.size <= PHOTO_FOLDER_CACHE_CAP) merged
                    else merged.entries.drop(merged.size - PHOTO_FOLDER_CACHE_CAP).associate { it.key to it.value }
            }
        }
    }

    fun getHomeScrollPosition(): HomeScrollPosition = homeScrollPosition

    fun saveHomeScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        homeScrollPosition = HomeScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
    }

    fun resetHomeScrollPosition() {
        homeScrollPosition = HomeScrollPosition()
    }

    // Note: HomeFocusPosition is still reset on user/refresh transitions
    // (below) and pinned by HomeScrollFocusPositionTest, but no UI ever reads
    // or writes a per-row/per-card focus index — focus restoration is handled
    // entirely in the composable layer via rememberInt (homeFocusRow). The
    // getter/save accessor methods were unused and have been removed.

    private fun resetHomeFocusPosition() {
        homeFocusPosition = HomeFocusPosition()
    }

    private fun refresh() {
        launch {
            _uiState.update { it.copy(isLoading = true) }
            resetHomeScrollPosition()
            resetHomeFocusPosition()
            _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null) }
            mediaRepository.invalidateCaches()
            invalidateDiscoverCache()
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun pullToRefresh() {
        launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            mediaRepository.invalidateCaches()
            invalidateDiscoverCache()
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun toggleOfflineMode() {
        offlineModeManager.toggleManualOffline()
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchState = it.searchState.copy(query = query, isSearching = if (query.isBlank()) false else it.searchState.isSearching)) }
        searchQueryFlow.value = query
    }

    private fun clearSearch() {
        _uiState.update { it.copy(searchState = HomeSearchState()) }
        searchQueryFlow.value = ""
    }

    private fun selectSeerrRequestItem(item: SeerrSearchItem?) {
        _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(requestItem = item)) }
    }

    private fun requestSeerrMedia(event: HomeUiEvent.RequestSeerrMedia) {
        seerrRequestStateHolder.requestMedia(
            item = event.item,
            seasons = event.seasons,
            serverId = event.serverId,
            profileId = event.profileId,
            rootFolder = event.rootFolder,
            tags = event.tags,
        )
    }

    private fun clearRequestResult() {
        seerrRequestStateHolder.clearRequestResult()
    }

    private fun loadSeerrServiceDetails(mediaType: String) {
        seerrRequestStateHolder.loadServiceDetails(mediaType)
    }

    private fun loadTvSeasons(tmdbId: Int) {
        seerrRequestStateHolder.loadTvSeasons(tmdbId)
    }

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        seerrRequestStateHolder.prefetchDetails(tmdbId, mediaType, onDone)
    }

    fun deleteSearchHistoryItem(id: Long) {
        launch { searchHistoryRepository.deleteById(id) }
    }

    fun clearSearchHistory() {
        launch {
            val userId = preferencesStore.activeUserId.first() ?: return@launch
            searchHistoryRepository.clearAll(userId)
        }
    }

    /**
     * Called when a settings search result is tapped from the home search bar.
     * If the target is an advanced setting that's currently hidden, enable
     * advanced settings first so the deep-linked screen actually shows it —
     * parity with the Settings screen's own search (see SettingsScreen.kt).
     * Navigation to [SettingsSearchItem.route] is performed by the caller.
     */
    fun onSettingsResultClicked(item: SettingsSearchItem) {
        if (item.isAdvanced) {
            launch { preferencesEditor.edit { setShowAdvancedSettings(true) } }
        }
    }

    fun excludeSeriesFromNextUp(seriesId: String) {
        launch {
            preferencesStore.excludeSeriesFromNextUp(seriesId)
        }
    }

    private suspend fun fetchAndUpdateSections() {
        // Do not drop a refresh that arrives while another request is running.
        // In particular, a sign-in can complete while Home's earlier request is
        // still failing with the just-cleared session. Waiting for the lock
        // ensures the authenticated follow-up request runs and clears that
        // transient error without requiring the user to tap Retry.
        refreshMutex.lock()
        // Deferred widget / TV Watch Next side-effects. Captured inside the
        // mutex (where we can detect a Continue-Watching change) but fired
        // after unlock so a slow broadcast IPC or WorkManager enqueue can't
        // hold the refresh lock.
        var pendingCwSideEffect: (() -> Unit)? = null
        try {
            offlineModeManager.checkNetworkAndAutoDetect()

            if (offlineModeManager.isOffline) {
                lastRefreshTime = System.currentTimeMillis()
                return
            }

            lastRefreshTime = System.currentTimeMillis()
            val enabledSections = enabledHomeSectionTypes
            val overrides = libraryHomeSectionOverrides
            mediaRepository.getHomeSections(
                enabledSections,
                overrides,
                nextUpRewatching,
                nextUpMaxDays,
                nextUpExcludedSeriesIds,
                hiddenCwItemIds,
                pinnedHomeSections,
            )
                .onSuccess { homeResult ->
                    val fetchedSections = homeResult.sections
                    // Surface a non-blocking notice only when a section type
                    // actually failed to load (403/500/network). Sections that
                    // returned zero items (e.g. no watch history, no Next Up) are
                    // NOT failures — previously the size-mismatch heuristic
                    // false-positived on new users and after merges.
                    _uiState.update { it.copy(partialLoadError = homeResult.failedSectionTypes.isNotEmpty()) }
                    val finalSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val orderIndex = homeSectionOrder.withIndex().associate { it.value to it.index }
                        val ordered = fetchedSections
                            .mapIndexed { index, section -> index to section }
                            .sortedWith(
                                compareBy<Pair<Int, com.raulshma.jellyplay.core.model.HomeSection>> {
                                    orderIndex[it.second.type] ?: Int.MAX_VALUE
                                }.thenBy { it.first },
                            )
                            .map { it.second }
                        if (mergeContinueWatchingAndNextUp) {
                            val cw = ordered.firstOrNull { it.type == HomeSectionType.CONTINUE_WATCHING }
                            val nextUp = ordered.firstOrNull { it.type == HomeSectionType.NEXT_UP }?.items.orEmpty()
                            if (cw != null) {
                                val seen = cw.items.mapTo(mutableSetOf()) { it.id }
                                val mergedItems = cw.items + nextUp.filter { seen.add(it.id) }
                                ordered.mapNotNull { section ->
                                    when (section.type) {
                                        HomeSectionType.CONTINUE_WATCHING -> section.copy(items = mergedItems)
                                        HomeSectionType.NEXT_UP -> null
                                        else -> section
                                    }
                                }
                            } else {
                                val nextUpSection = ordered.firstOrNull { it.type == HomeSectionType.NEXT_UP }
                                if (nextUpSection != null) {
                                    ordered.mapNotNull { section ->
                                        when (section.type) {
                                            HomeSectionType.NEXT_UP -> section.copy(type = HomeSectionType.CONTINUE_WATCHING)
                                            else -> section
                                        }
                                    }
                                } else {
                                    ordered
                                }
                            }
                        } else {
                            ordered
                        }
                    }

                    _uiState.update { it.copy(sections = finalSections) }

                    val continueWatching = finalSections
                        .find { it.type == HomeSectionType.CONTINUE_WATCHING }
                        ?.items ?: emptyList()
                    val currentIds = continueWatching.map { it.id }.toSet()
                    if (currentIds != lastContinueWatchingIds) {
                        lastContinueWatchingIds = currentIds
                        preferencesStore.setContinueWatching(continueWatching)
                        // Defer the widget broadcast + TV Watch Next refresh until
                        // after the mutex is released (see pendingCwSideEffect above).
                        pendingCwSideEffect = {
                            // Explicit-component broadcast: implicit broadcasts to
                            // manifest-registered receivers are blocked on Android O+,
                            // and the widget's intent-filter only carries
                            // APPWIDGET_UPDATE, so we target the receiver class directly
                            // to guarantee delivery in-process.
                            val intent = android.content.Intent(
                                "com.raulshma.jellyplay.widget.ACTION_REFRESH_CONTINUE_WATCHING",
                            ).apply {
                                setClassName(
                                    context.packageName,
                                    "com.raulshma.jellyplay.widget.ContinueWatchingWidget",
                                )
                            }
                            context.sendBroadcast(intent)
                            // Refresh the Android TV "Watch Next" OS row so the
                            // system home stays in sync with the user's progress.
                            // Worker is a no-op on phones and respects its preference.
                            if (androidTvWatchNextEnabled) {
                                tvWatchNextScheduler.scheduleRefresh()
                            }
                        }
                    }

                    _uiState.update { it.copy(error = null) }
                }
                .onFailure { throwable ->
                    if (_uiState.value.sections.isEmpty()) {
                        _uiState.update { s -> s.copy(error = throwable.message ?: "${throwable::class.simpleName}") }
                    }
                    _uiState.update { it.copy(partialLoadError = false) }
                }

            if (_uiState.value.discoverEnabled) {
                fetchDiscoverSections(seerrPreferences)
            }
            // Direct *arr "Recently Grabbed" calendar — gated by the
            // DIRECT_ARR_INTEGRATION flag and the same TTL gate as discover
            // sections so it never adds extra round-trips on every refresh.
            if (_uiState.value.directArrEnabled) {
                fetchRecentlyGrabbed()
            }
        } finally {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            refreshMutex.unlock()
        }
        pendingCwSideEffect?.invoke()
    }

    /**
     * Refreshes the *arr calendar window and pushes the merged list into
     * [HomeUiState.recentlyGrabbed] as [SeerrSearchItem]s (reusing the TMDB
     * card model so no new card UI is needed). Window is now → +30 days so
     * "coming soon" + freshly-grabbed items both surface. Failures degrade to
     * empty; the *arr repository already swallows per-server errors.
     */
    private suspend fun fetchRecentlyGrabbed() {
        val now = java.time.LocalDate.now(java.time.ZoneOffset.systemDefault())
        val end = now.plusDays(30)
        arrRepository.refreshCalendar(now, end)
        val items = arrRepository.calendar(now, end).first()
        _uiState.update { it.copy(recentlyGrabbed = items.map { it.toSeerrSearchItem() }) }
    }

    private suspend fun fetchDiscoverSections(prefs: SeerrPreferences) {
        if (!prefs.enabled || !prefs.discoverEnabled) return
        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return
        // Trending/popular change slowly; cache discover results for
        // DISCOVER_TTL_MS so "just sitting on Home" doesn't fan out up to 5
        // Seerr round-trips per minute (C2 periodic refresh + per pref change).
        // A user-initiated refresh (swipe-to-refresh) sets `force = true` which
        // bypasses this gate via [invalidateDiscoverCache].
        val now = System.currentTimeMillis()
        if (!discoverCacheInvalidated && now - lastDiscoverFetchEpochMs < DISCOVER_TTL_MS) return

        val today = LocalDate.now(ZoneOffset.systemDefault()).toString()

        val deferredResults = mutableListOf<Pair<DiscoverSectionType, kotlinx.coroutines.Deferred<Result<SeerrSearchResponse>>>>()

        if (prefs.discoverTrending) {
            deferredResults.add(DiscoverSectionType.TRENDING to scope.async { seerrRepository.getTrending() })
        }
        if (prefs.discoverPopularMovies) {
            deferredResults.add(DiscoverSectionType.POPULAR_MOVIES to scope.async { seerrRepository.getDiscoverMovies() })
        }
        if (prefs.discoverPopularTv) {
            deferredResults.add(DiscoverSectionType.POPULAR_TV to scope.async { seerrRepository.getDiscoverTv() })
        }
        if (prefs.discoverUpcomingMovies) {
            deferredResults.add(DiscoverSectionType.UPCOMING_MOVIES to scope.async { seerrRepository.getDiscoverMovies(primaryReleaseDateGte = today) })
        }
        if (prefs.discoverUpcomingTv) {
            deferredResults.add(DiscoverSectionType.UPCOMING_TV to scope.async { seerrRepository.getDiscoverTv(firstAirDateGte = today) })
        }

        val newSections = mutableMapOf<DiscoverSectionType, List<SeerrSearchItem>>()
        for ((type, deferred) in deferredResults) {
            deferred.await().onSuccess { response ->
                newSections[type] = response.results
            }
        }

        lastDiscoverFetchEpochMs = System.currentTimeMillis()
        discoverCacheInvalidated = false
        _uiState.update { it.copy(discoverSections = newSections) }
    }

    /**
     * Resets the discover-sections TTL so the next [fetchDiscoverSections]
     * actually hits the network. Called on user-initiated refresh.
     */
    fun invalidateDiscoverCache() {
        discoverCacheInvalidated = true
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = launch {
            while (true) {
                val interval = if (isAppInForeground) REFRESH_INTERVAL_FOREGROUND_MS else REFRESH_INTERVAL_BACKGROUND_MS
                // ±10% jitter avoids synchronized refresh storms when multiple
                // devices hit the server on the same fixed mark.
                val jitter = (interval * 0.1f * (kotlin.random.Random.nextFloat() * 2f - 1f)).toLong()
                delay(interval + jitter)

                // Skip while device has no network: fetchAndUpdateSections
                // would no-op after acquiring the refresh mutex anyway, so this
                // avoids the mutex churn and the lastRefreshTime bookkeeping.
                if (offlineModeManager.isOffline) continue

                val now = System.currentTimeMillis()
                if (now - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) continue

                fetchAndUpdateSections()
                lastRefreshTime = System.currentTimeMillis()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isAppInForeground = true
        launch {
            offlineModeManager.checkNetworkAndAutoDetect()
            val now = System.currentTimeMillis()
            if (now - lastRefreshTime >= REFRESH_INTERVAL_FOREGROUND_MS) {
                fetchAndUpdateSections()
            }
        }
        startPeriodicRefresh()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isAppInForeground = false
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        refreshJob?.cancel()
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(searchState = it.searchState.copy(isSearching = true)) }
        try {
            coroutineScope {
                val jellyfinDeferred = async { mediaRepository.search(query, limit = 8) }
                val seerrDeferred = async {
                    try {
                        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) {
                            null
                        } else {
                            val connected = seerrRepository.isConnected().first()
                            val enabled = seerrRepository.isSearchEnabled().first()
                            if (connected && enabled) {
                                seerrRepository.search(query).getOrNull()?.results?.take(8)
                            } else null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                jellyfinDeferred.await().onSuccess { result ->
                    _uiState.update { it.copy(searchState = it.searchState.copy(jellyfinResults = result.items)) }
                    if (result.items.isNotEmpty()) {
                        val userId = preferencesStore.activeUserId.first()
                        if (userId != null) {
                            searchHistoryRepository.saveQuery(query, userId)
                        }
                    }
                }
                seerrDeferred.await()?.let { results ->
                    _uiState.update { it.copy(searchState = it.searchState.copy(seerrResults = results)) }
                } ?: run {
                    _uiState.update { it.copy(searchState = it.searchState.copy(seerrResults = emptyList())) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.update { it.copy(searchState = it.searchState.copy(jellyfinResults = emptyList(), seerrResults = emptyList())) }
        } finally {
            _uiState.update { it.copy(searchState = it.searchState.copy(isSearching = false)) }
        }
    }

}
