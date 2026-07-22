package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.repository.HomeSectionQuery
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.usecase.GetHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.toMediaItem
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val getHomeSections: GetHomeSectionsUseCase,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val playbackSyncScheduler: PlaybackSyncScheduler,
    private val offlineModeManager: OfflineModeManager,
    private val newsletterTriggerManager: NewsletterTriggerManager,
    private val preferencesStore: UserPreferencesStore,
    private val preferencesEditor: PreferencesEditor,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val authRepository: AuthRepository,
    private val arrRepository: ArrRepository,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val timeSource: TimeSource,
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
        /**
         * Hard deadline on the offline→online fetch. There is no withTimeout
         * anywhere down the getHomeSections / fetchDiscoverSections /
         * fetchRecentlyGrabbed chain — only OkHttp's per-call read timeout
         * (which a half-open socket or a hung Seerr await can defeat). Without
         * this cap a stuck fetch parks on refreshMutex forever and
         * isGoingOnline never clears, leaving the Go Online button + app bar
         * spinners spinning until the app is restarted.
         */
        private const val GOING_ONLINE_TIMEOUT_MS = 30_000L
    }

    private val _uiState = stateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.flow

    /**
     * Applies [transform] to the nested [HomeSearchState] in one update, so the
     * ~12 search-mutation sites don't each repeat
     * `it.copy(searchState = it.searchState.copy(...))`.
     */
    private fun updateSearch(transform: (HomeSearchState) -> HomeSearchState) {
        _uiState.update { it.copy(searchState = transform(it.searchState)) }
    }

    val activeDownloadCount: StateFlow<Int> = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Count of playback events queued in the offline outbox. Surfaced to the
     * home header so the user can see that their offline watch progress is
     * pending sync (and that it will flush automatically on reconnect).
     */
    val pendingSyncCount: StateFlow<Int> = playbackOutboxRepository.countFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Reactive snapshot of pending outbox entries (oldest-first), for the
     * sync details sheet. Only collected while the sheet is open, so it does
     * not add steady-state flow cost.
     */
    val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>> =
        playbackOutboxRepository.getAllFlow()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Resolved media metadata (title + poster URL) keyed by outbox `itemId`,
     * for rendering per-row context in the sync details sheet. Resolution is
     * **offline-first** — the sheet is most relevant when offline — and falls
     * back to a network `getMediaDetail` lookup only when the item was watched
     * but never downloaded. Entries are populated on demand via
     * [ensurePendingItemDetails] and pruned to the currently-queued ids so the
     * map never grows unbounded. `item == null` (with a network-derived URL)
     * marks a resolved-but-not-found id so we don't refetch it every
     * recomposition.
     */
    private val _pendingItemDetails =
        MutableStateFlow<Map<String, ResolvedSyncMedia>>(emptyMap())
    val pendingItemDetails: StateFlow<Map<String, ResolvedSyncMedia>> = _pendingItemDetails

    /** Item ids currently being resolved, to dedupe concurrent callers. */
    private val pendingResolveInFlight = mutableSetOf<String>()

    /**
     * Ensures the [pendingItemDetails] map holds a resolution for every id in
     * [itemIds], pruning any stale entries that are no longer queued. Cheap to
     * call on every recomposition — already-resolved and in-flight ids are
     * skipped. Safe to call with an empty collection (clears the map).
     */
    fun ensurePendingItemDetails(itemIds: Collection<String>) {
        val keep = itemIds.toSet()
        // Drop resolutions for ids that are no longer queued.
        if (_pendingItemDetails.value.keys.any { it !in keep }) {
            _pendingItemDetails.value = _pendingItemDetails.value.filterKeys { it in keep }
        }
        for (id in keep) {
            if (_pendingItemDetails.value.containsKey(id) || id in pendingResolveInFlight) continue
            pendingResolveInFlight += id
            launch {
                val resolved = resolveSyncMedia(id)
                _pendingItemDetails.update { current -> current + (id to resolved) }
                pendingResolveInFlight -= id
            }
        }
    }

    /**
     * Resolves a single item offline-first. Returns the offline row adapted to
     * [MediaItem] (with its local poster path) when available, else falls back
     * to a network `getMediaDetail` lookup guarded by the current offline mode.
     * The poster URL always falls back to the id-derived server URL so the row
     * can attempt to load it once back online even if neither store had a row.
     */
    private suspend fun resolveSyncMedia(id: String): ResolvedSyncMedia {
        val offline = offlineRepository.getOfflineItem(id)
        if (offline != null) {
            val url = offline.posterPath ?: imageUrlProvider.getImageUrl(id)
            return ResolvedSyncMedia(item = offline.toMediaItem(), posterUrl = url)
        }
        // Online-only fallback for items watched but never downloaded. Skipped
        // while offline to avoid a guaranteed-failing network call.
        if (offlineModeManager.offlineMode.value == OfflineMode.ONLINE) {
            mediaRepository.getMediaDetail(id)
                .getOrNull()
                ?.item
                ?.let { item ->
                    return ResolvedSyncMedia(item = item, posterUrl = imageUrlProvider.getImageUrl(id))
                }
        }
        return ResolvedSyncMedia(item = null, posterUrl = imageUrlProvider.getImageUrl(id))
    }

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
                    experimentalCardClippingEnabled = ExperimentalFeature.HOME_CARD_CLIPPING in prefs.enabledExperimentalFeatures,
                    directArrEnabled = ExperimentalFeature.DIRECT_ARR_INTEGRATION in prefs.enabledExperimentalFeatures,
                    enabledHomeSectionTypes = prefs.enabledHomeSectionTypes,
                    homeSectionOrder = prefs.homeSectionOrder,
                    libraryHomeSectionOverrides = prefs.libraryHomeSectionOverrides,
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
                // Capture the previous mode before overwriting so transition
                // detection is stable across the rapid manual+auto+network
                // re-emissions a single toggle can produce.
                val previousMode = _uiState.value.offlineMode
                _uiState.update { it.copy(offlineMode = mode) }
                when {
                    previousMode == OfflineMode.ONLINE && mode != OfflineMode.ONLINE -> {
                        // Online → offline: drop cached online sections. Also clear
                        // any pending going-online spinner — e.g. the user (or an
                        // auto-detect flip) took us back offline while the prior
                        // online fetch was still parked on the refresh mutex.
                        _uiState.update {
                            it.copy(sections = emptyList(), discoverSections = emptyMap(), isGoingOnline = false)
                        }
                    }
                    previousMode != OfflineMode.ONLINE && mode == OfflineMode.ONLINE -> {
                        // Offline → online: show the full-screen loader during the
                        // post-toggle fetch so the online branch doesn't flash
                        // blank between the mode flip and sections arriving.
                        // isGoingOnline MUST clear in finally — previously the
                        // clear ran after fetchAndUpdateSections() with no guard,
                        // so any throw/cancellation left it stuck on forever and
                        // the user had to restart the app to recover.
                        _uiState.update { it.copy(isLoading = true) }
                        try {
                            // Cap the post-toggle fetch so a hung network call
                            // cannot leave isGoingOnline (and isLoading) stuck
                            // on — the symptom was the Go Online button + app
                            // bar spinners never clearing. On timeout we drop
                            // the result; a normal refresh/pull-to-refresh can
                            // still repopulate sections once the network
                            // recovers. isLoading is force-cleared below.
                            withTimeoutOrNull(GOING_ONLINE_TIMEOUT_MS) {
                                fetchAndUpdateSections()
                            }
                        } finally {
                            _uiState.update { it.copy(isGoingOnline = false, isLoading = false) }
                        }
                    }
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
                    else flowOf(emptyList())
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
                    else flowOf(emptyList())
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
                        updateSearch { it.copy(jellyfinResults = emptyList(), seerrResults = emptyList(), settingsResults = emptyList(), isSearching = false) }
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
                    updateSearch { it.copy(settingsResults = results) }
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
            is HomeUiEvent.SyncNow -> syncNow()
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

    private fun refresh() {
        launch {
            _uiState.update { it.copy(isLoading = true) }
            resetHomeScrollPosition()
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
        // Going online is async (preference write → mode flip → network fetch)
        // and previously gave zero feedback. Flip a busy flag the UI can show a
        // spinner on; it is cleared once the offline→online transition resolves.
        // Going offline is instantaneous and needs no indicator.
        val goingOnline = _uiState.value.offlineMode != OfflineMode.ONLINE
        if (goingOnline) {
            _uiState.update { it.copy(isGoingOnline = true) }
        }
        offlineModeManager.toggleManualOffline()
    }

    /**
     * Manually drain the playback outbox. The drain worker requires a network
     * connection (NetworkType.CONNECTED constraint), so the button is a no-op
     * while offline — the user is told this in the sheet rather than firing a
     * work request that can't run. On reconnect the worker drains anyway.
     */
    private fun syncNow() {
        if (offlineModeManager.offlineMode.value != OfflineMode.ONLINE) return
        playbackSyncScheduler.enqueueNow()
    }

    private fun updateSearchQuery(query: String) {
        updateSearch { it.copy(query = query, isSearching = if (query.isBlank()) false else it.isSearching) }
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

    /**
     * Toggles a home section's visibility from the inline section-config sheet.
     * Writes through [preferencesEditor] exactly as the Settings screen does —
     * the prefs collector above then triggers [fetchAndUpdateSections] so the
     * row appears/disappears with no extra wiring.
     */
    fun setSectionVisible(type: HomeSectionType, visible: Boolean) {
        val updated = enabledHomeSectionTypes.toMutableSet().apply {
            if (visible) add(type) else remove(type)
        }
        preferencesEditor.setEnabledHomeSectionTypes(updated)
    }

    /**
     * Moves a home section up/down within the user's ordering, from the inline
     * section-config sheet. Swaps with the neighbour in the cached order and
     * persists via [preferencesEditor]; the prefs collector + ordering use case
     * re-apply it on the next emission.
     */
    fun moveSection(type: HomeSectionType, up: Boolean) {
        val index = homeSectionOrder.indexOf(type)
        if (index == -1) return
        val target = if (up) index - 1 else index + 1
        if (target !in homeSectionOrder.indices) return
        val updated = homeSectionOrder.toMutableList().apply {
            val removed = removeAt(index)
            add(target, removed)
        }
        preferencesEditor.edit { setHomeSectionOrder(updated) }
    }

    /**
     * Toggles a per-library section (currently LATEST_MEDIA) from the inline
     * section-config sheet, mirroring Settings → Configure Libraries. The
     * override map is keyed by library id with the DISABLED types as its value
     * set; an empty set removes the key (restoring default-enabled state).
     */
    fun setLibrarySectionVisible(libraryId: String, type: HomeSectionType, visible: Boolean) {
        val current = libraryHomeSectionOverrides.toMutableMap()
        val disabled = current[libraryId].orEmpty().toMutableSet()
        if (visible) disabled.remove(type) else disabled.add(type)
        if (disabled.isEmpty()) current.remove(libraryId) else current[libraryId] = disabled
        preferencesEditor.setLibraryHomeSectionOverrides(current)
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
                lastRefreshTime = timeSource.nowEpochMillis()
                return
            }

            lastRefreshTime = timeSource.nowEpochMillis()
            val query = HomeSectionQuery(
                enabledSections = enabledHomeSectionTypes,
                libraryHomeSectionOverrides = libraryHomeSectionOverrides,
                nextUpRewatching = nextUpRewatching,
                nextUpMaxDays = nextUpMaxDays,
                nextUpExcludedSeriesIds = nextUpExcludedSeriesIds,
                hiddenCwItemIds = hiddenCwItemIds,
                pinnedSections = pinnedHomeSections,
            )
            getHomeSections(query)
                .onSuccess { homeResult ->
                    val fetchedSections = homeResult.sections
                    // Surface a non-blocking notice only when a section type
                    // actually failed to load (403/500/network). Sections that
                    // returned zero items (e.g. no watch history, no Next Up) are
                    // NOT failures — previously the size-mismatch heuristic
                    // false-positived on new users and after merges.
                    _uiState.update { it.copy(partialLoadError = homeResult.failedSectionTypes.isNotEmpty()) }
                    val finalSections = withContext(Dispatchers.Default) {
                        orderHomeSections(
                            sections = fetchedSections,
                            order = homeSectionOrder,
                            mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
                        )
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
                            // Push the CW change to the home-screen widget. The
                            // broadcaster owns the explicit-component broadcast so
                            // this VM no longer needs an Android Context.
                            continueWatchingBroadcaster.refreshContinueWatching()
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
            // isGoingOnline is also cleared in the offlineMode collector's
            // finally, but resetting it here too guarantees the spinner cannot
            // survive any code path into this method (e.g. sign-in-triggered
            // fetch, periodic refresh, manual retry) leaving it stuck on.
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, isGoingOnline = false) }
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
        val now = timeSource.today(ZoneOffset.systemDefault())
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
        // Seerr round-trips per minute (periodic refresh + per pref change).
        // A user-initiated refresh (swipe-to-refresh) sets `force = true` which
        // bypasses this gate via [invalidateDiscoverCache].
        val now = timeSource.nowEpochMillis()
        if (!discoverCacheInvalidated && now - lastDiscoverFetchEpochMs < DISCOVER_TTL_MS) return

        val today = timeSource.today(ZoneOffset.systemDefault()).toString()

        val deferredResults = mutableListOf<Pair<DiscoverSectionType, Deferred<Result<SeerrSearchResponse>>>>()

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

        lastDiscoverFetchEpochMs = timeSource.nowEpochMillis()
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

                val now = timeSource.nowEpochMillis()
                if (now - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) continue

                fetchAndUpdateSections()
                lastRefreshTime = timeSource.nowEpochMillis()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isAppInForeground = true
        launch {
            offlineModeManager.checkNetworkAndAutoDetect()
            val now = timeSource.nowEpochMillis()
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
        updateSearch { it.copy(isSearching = true) }
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
                    updateSearch { it.copy(jellyfinResults = result.items) }
                    if (result.items.isNotEmpty()) {
                        val userId = preferencesStore.activeUserId.first()
                        if (userId != null) {
                            searchHistoryRepository.saveQuery(query, userId)
                        }
                    }
                }
                seerrDeferred.await()?.let { results ->
                    updateSearch { it.copy(seerrResults = results) }
                } ?: run {
                    updateSearch { it.copy(seerrResults = emptyList()) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            updateSearch { it.copy(jellyfinResults = emptyList(), seerrResults = emptyList()) }
        } finally {
            updateSearch { it.copy(isSearching = false) }
        }
    }

}

/**
 * Resolved media context for a single pending-sync outbox row.
 *
 * @property item the resolved [MediaItem] (title, type, episode context), or
 *   `null` if neither the offline store nor the server had a row for the id.
 * @property posterUrl the URL to load the poster from. For offline items this
 *   is the locally-saved [OfflineMediaItem.posterPath]; otherwise it is the
 *   id-derived server URL, which will only resolve once back online.
 */
data class ResolvedSyncMedia(
    val item: com.raulshma.jellyplay.core.model.MediaItem?,
    val posterUrl: String,
)
