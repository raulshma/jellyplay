package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.repository.HomeSectionQuery
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.UndoableAction
import com.raulshma.jellyplay.core.ui.components.undoActionChannel
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val userDataMutator: UserDataMutator,
    private val mediaSearchEngine: MediaSearchEngine,
    private val offlineFirstItemResolver: OfflineFirstItemResolver,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val playbackSyncScheduler: PlaybackSyncScheduler,
    private val offlineModeManager: OfflineModeManager,
    private val newsletterTriggerManager: NewsletterTriggerManager,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val appearanceStore: AppearanceStore,
    private val experimentalStore: ExperimentalStore,
    private val playbackStore: PlaybackStore,
    private val preferencesEditor: PreferencesEditor,
    private val widgetDataStore: WidgetDataStore,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val authRepository: AuthRepository,
    private val arrRepository: ArrRepository,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val librarySyncHook: LibrarySyncHook,
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

        /**
         * How long the offline→online fetch will wait for the playback outbox
         * to drain before fetching Continue Watching / Next Up. The drain
         * (PlaybackSyncWorker) replays offline marks to the server; if we fetch
         * before it completes, CW can still list items the user just marked
         * unplayed. The drain is usually near-instant on reconnect, so this is a
         * short cap — on timeout we fetch anyway (the next periodic refresh or
         * pull-to-refresh re-syncs).
         */
        private const val OUTBOX_DRAIN_WAIT_MS = 8_000L
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
     * **offline-first** (owned by [OfflineFirstItemResolver] in the data layer)
     * and falls back to a network `getMediaDetail` lookup only when the item
     * was watched but never downloaded. Entries are populated on demand via
     * [ensurePendingItemDetails] and pruned to the currently-queued ids so the
     * map never grows unbounded. `item == null` (with a network-derived URL)
     * marks a resolved-but-not-found id so we don't refetch it every
     * recomposition.
     */
    private val _pendingItemDetails =
        MutableStateFlow<Map<String, ResolvedMediaRef>>(emptyMap())
    val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>> = _pendingItemDetails

    /** Item ids currently being resolved, to dedupe concurrent callers. */
    private val pendingResolveInFlight = mutableSetOf<String>()

    /**
     * Ensures the [pendingItemDetails] map holds a resolution for every id in
     * [itemIds], pruning any stale entries that are no longer queued. Cheap to
     * call on every recomposition — already-resolved and in-flight ids are
     * skipped. Safe to call with an empty collection (clears the map). When to
     * resolve is a UI-sheet policy that stays here; how to resolve is the
     * resolver's (data-layer) policy.
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
                val resolved = offlineFirstItemResolver.resolveMediaRef(id)
                _pendingItemDetails.update { current -> current + (id to resolved) }
                pendingResolveInFlight -= id
            }
        }
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
    // Discover-sections TTL gate (see DISCOVER_TTL_MS / fetchDiscoverSections).
    private val discoverCache = TtlCacheGate(DISCOVER_TTL_MS)

    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    /**
     * Read-only view of the search query string. Kept in lockstep with
     * [HomeUiState.searchState]'s `query` field (both are written by
     * `updateSearchQuery`/`clearSearch`), but exposed separately so the home
     * screen can read it in a leaf composable without recomposing the whole
     * `MainHomeContent` body on every keystroke — mirrors the `scrollFraction`
     * deferral pattern in `HomeScrollState`.
     */
    val searchQuery: StateFlow<String> = searchQueryFlow

    /**
     * Recent searches for the active user — keyed on the active user and
     * gated by the hide-history preference inside
     * [MediaSearchEngine.recentHistory]. Exposed via stateIn (not an init
     * collector) so the underlying Room flow is only collected while the
     * search overlay is actually on screen.
     */
    val searchHistory: StateFlow<List<SearchHistoryItem>> = mediaSearchEngine.recentHistory()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recoverable-action snackbars for home (search-history delete/clear).
     * Home previously had no SnackbarHost at all */
    private val _undoActions = undoActionChannel()
    val undoActions = _undoActions.receiveAsFlow()

    /**
     * All users persisted for the current server. Backs the home app-bar quick
     * user switcher. Mirrored into [HomeUiState.currentServerUsers] so the
     * UI observes a single state object; the underlying flow is
     * DB-backed ([AuthRepository.currentServerUsers]) so it's already populated
     * after login — no extra fetch.
     */
    val currentServerUsers: StateFlow<List<UserInfo>> = authRepository.currentServerUsers
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                    // Stale-while-revalidate: paint the persisted snapshot (if any)
                    // instead of clearing to empty. The empty+isLoading path drives
                    // a full-screen loading box (DelayedLoadingScreen) that looks like
                    // the splash screen re-appearing — so when we already have cached
                    // content, show it immediately and drop isLoading; the network
                    // fetch below revalidates and overwrites. Only clear+load when
                    // there's genuinely nothing to show.
                    val cachedSections = orderedCachedHomeSections(currentHomeSectionQuery())
                    if (cachedSections != null) {
                        _uiState.update {
                            it.copy(
                                sections = cachedSections,
                                favorites = emptyList(),
                                discoverSections = emptyMap(),
                                error = null,
                                isLoading = false,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null, isLoading = true) }
                    }
                    fetchAndUpdateSections()
                    startPeriodicRefresh()
                }
                previousUserId = userId
            }
        }

        // Mirror the server's persisted user list into UI state so the home
        // app-bar user switcher can decide whether to render (≥2 users)
        // without the UI subscribing to the repo flow directly.
        launch {
            currentServerUsers.collect { users ->
                _uiState.update { it.copy(currentServerUsers = users) }
            }
        }

        launch {
            var hasSeenHomePreferences = false
            combine(
                homeDiscoveryStore.homeDiscovery,
                appearanceStore.appearance,
                experimentalStore.experimental,
                playbackStore.playback,
            ) { home, appearance, experimental, playback ->
                HomePrefs(home, appearance, experimental, playback)
            }.collect { prefs ->
                val homeSectionPrefsChanged = hasSeenHomePreferences && (
                    prefs.home.enabledHomeSectionTypes != enabledHomeSectionTypes ||
                        prefs.home.homeSectionOrder != homeSectionOrder ||
                        prefs.home.libraryHomeSectionOverrides != libraryHomeSectionOverrides ||
                        prefs.home.mergeContinueWatchingAndNextUp != mergeContinueWatchingAndNextUp ||
                        prefs.home.nextUpMaxDays != nextUpMaxDays ||
                        prefs.home.nextUpRewatching != nextUpRewatching ||
                        prefs.home.nextUpExcludedSeriesIds != nextUpExcludedSeriesIds ||
                        prefs.home.hiddenCwItemIds != hiddenCwItemIds ||
                        prefs.home.pinnedHomeSections != pinnedHomeSections
                )

                hasSeenHomePreferences = true
                enabledHomeSectionTypes = prefs.home.enabledHomeSectionTypes
                homeSectionOrder = prefs.home.homeSectionOrder
                libraryHomeSectionOverrides = prefs.home.libraryHomeSectionOverrides
                mergeContinueWatchingAndNextUp = prefs.home.mergeContinueWatchingAndNextUp
                nextUpMaxDays = prefs.home.nextUpMaxDays
                nextUpRewatching = prefs.home.nextUpRewatching
                nextUpExcludedSeriesIds = prefs.home.nextUpExcludedSeriesIds
                hiddenCwItemIds = prefs.home.hiddenCwItemIds
                pinnedHomeSections = prefs.home.pinnedHomeSections
                androidTvWatchNextEnabled = prefs.playback.androidTvWatchNextEnabled
                _uiState.update { it.copy(
                    homeMode = prefs.home.homeMode,
                    dynamicTheming = prefs.appearance.dynamicTheming,
                    oledMode = prefs.appearance.oledMode,
                    colorStyle = prefs.appearance.colorStyle,
                    accentColorSwatch = prefs.appearance.accentColorSwatch,
                    homeHeroEnabled = prefs.home.homeHeroEnabled,
                    homeBackdropEnabled = prefs.home.homeBackdropEnabled,
                    performanceMode = prefs.appearance.performanceMode,
                    showClock = prefs.home.showClockOnHome,
                    showSettingsInHomeSearch = prefs.home.showSettingsInHomeSearch,
                    hideTopHeaderOnScroll = prefs.home.hideTopHeaderOnScroll,
                    continueWatchingClickBehavior = prefs.home.continueWatchingClickBehavior,
                    experimentalCardClippingEnabled = ExperimentalFeature.HOME_CARD_CLIPPING in prefs.experimental.enabledExperimentalFeatures,
                    directArrEnabled = ExperimentalFeature.DIRECT_ARR_INTEGRATION in prefs.experimental.enabledExperimentalFeatures,
                    enabledHomeSectionTypes = prefs.home.enabledHomeSectionTypes,
                    homeSectionOrder = prefs.home.homeSectionOrder,
                    libraryHomeSectionOverrides = prefs.home.libraryHomeSectionOverrides,
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
                            // Let the playback outbox drain before fetching so
                            // Continue Watching / Next Up reflect the server's
                            // post-sync state. Without this the fetch can race
                            // the drain: CW would still list an episode the user
                            // marked unplayed offline, because the server hasn't
                            // processed the mark yet. The drain is fast on
                            // reconnect; on timeout we fetch anyway and the next
                            // periodic refresh / pull-to-refresh re-syncs.
                            awaitOutboxDrained()
                            // Cap the post-toggle fetch so a hung network call
                            // cannot leave isGoingOnline (and isLoading) stuck
                            // on — the symptom was the Go Online button + app
                            // bar spinners never clearing. On timeout we drop the
                            // result; a normal refresh/pull-to-refresh can
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

        // The inline-search kernel (debounce, cancel-and-replace, parallel
        // Jellyfin + gated Seerr fetch, result-gated history save) lives in
        // [MediaSearchEngine]; this collector only folds its emissions into the
        // search slice of HomeUiState. The local settings-search collector that
        // used to sit beside it moved to the UI layer (HomeTopDockScrim) so the
        // VM no longer needs an Android Context.
        launch {
            mediaSearchEngine.preview(searchQueryFlow).collect { state ->
                updateSearch {
                    it.copy(
                        jellyfinResults = state.jellyfin,
                        seerrResults = state.seerr,
                        isSearching = state.isSearching,
                    )
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

    /** Intermediate holder for the four home-preference slices combined above. */
    private data class HomePrefs(
        val home: HomeDiscoverySlice,
        val appearance: AppearanceSlice,
        val experimental: ExperimentalSlice,
        val playback: PlaybackSlice,
    )

    /**
     * Switches the active user. The [AuthRepository.currentUser] collector in
     * [init] re-runs the full home refresh on the resulting user change, so no
     * callback or explicit reload is needed here — the UI observes the flow.
     */
    fun switchUser(userId: String) {
        launch {
            authRepository.switchUser(userId)
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

    /**
     * Marks a home-row item (quick actions) played/unplayed.
     * Flips the item in-place in every section so the card badge updates
     * immediately; the next home refresh reconciles the server truth.
     */
    fun markItemPlayed(item: MediaItem) = setItemPlayed(item, played = true)

    fun markItemUnplayed(item: MediaItem) = setItemPlayed(item, played = false)

    /**
     * Shared offline-delete module (core/data) — the same collapse/defense
     * algorithm the detail screen and downloads library use. Constructed with
     * no `onContentMutated` because Home's reactive `offlineLibrary` Room flow
     * (see the init collector) refreshes on its own once rows are deleted.
     * Providers default to empty: Home's only provider-driven path is
     * [deleteOfflineEpisodes], which captures the sheet snapshot per call (see
     * its KDoc).
     */
    private val offlineDeleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
    )

    /**
     * Deletes a downloaded item from the offline home's quick-action menu.
     * Delegates the series-vs-item routing to
     * [OfflineDeleteActions.deleteDownload]; the reactive
     * [HomeUiState.offlineLibrary] flow refreshes on its own once the row is
     * gone, so no manual state update is needed here.
     */
    fun deleteOfflineMedia(item: MediaItem) {
        offlineDeleteActions.deleteDownload(item)
    }

    /**
     * Opens the advanced "delete downloaded episodes" sheet for a series card
     * on the offline home. Loads the series' seasons and downloaded episodes
     * (the same [OfflineRepository] calls the detail provider uses) and exposes
     * them via [HomeUiState.seriesDelete] for the sheet to render.
     * `getEpisodesForSeason` reads the offline store, so the resulting episode
     * map is already pre-filtered to downloaded episodes — exactly what the
     * sheet expects.
     */
    fun requestSeriesDelete(series: MediaItem) {
        _uiState.update {
            it.copy(seriesDelete = HomeSeriesDeleteState(series.id, emptyList(), emptyMap(), 0L, isLoading = true))
        }
        launch {
            val seasonsOff = offlineRepository.getSeasonsForSeries(series.id).first()
            val episodesOffBySeason = seasonsOff.associate { season ->
                season.id to offlineRepository.getEpisodesForSeason(season.id).first()
            }
            val downloadedBySeason = episodesOffBySeason.filterValues { it.isNotEmpty() }
            val seasons = seasonsOff.filter { it.id in downloadedBySeason }.map { it.toMediaItem() }
            val episodesBySeason = downloadedBySeason.mapValues { (_, eps) -> eps.map { it.toMediaItem() } }
            // Per-episode on-disk sizes from the offline store, so the delete
            // sheet's freed-space figure is exact for partial selections too.
            val episodeSizeBytes = downloadedBySeason.values
                .flatten()
                .associate { it.id to it.totalSizeBytes }
            val totalSizeBytes = episodesOffBySeason.values.flatten().sumOf { it.totalSizeBytes }
            _uiState.update {
                it.copy(
                    seriesDelete = HomeSeriesDeleteState(
                        seriesId = series.id,
                        seasons = seasons,
                        episodesBySeason = episodesBySeason,
                        totalSizeBytes = totalSizeBytes,
                        episodeSizeBytes = episodeSizeBytes,
                        isLoading = false,
                    ),
                )
            }
        }
    }

    fun dismissSeriesDelete() {
        _uiState.update { it.copy(seriesDelete = null) }
    }

    /**
     * Deletes the selected downloaded episodes for the open series sheet via
     * the shared [OfflineDeleteActions] (whole-season collapse + per-episode
     * fallback + unknown-id defense). The sheet snapshot is captured BEFORE
     * dismissal because the shared module reads its content lazily off the
     * providers — after `dismissSeriesDelete()` clears `uiState.seriesDelete`
     * the live providers would return empty and the collapse would silently
     * degrade to per-episode deletes. Clearing the sheet immediately lets it
     * dismiss while the deletes run in the background.
     */
    fun deleteOfflineEpisodes(episodeIds: Set<String>) {
        if (episodeIds.isEmpty()) return
        val state = _uiState.value.seriesDelete ?: return
        dismissSeriesDelete()
        OfflineDeleteActions(
            scope = scope,
            offlineRepository = offlineRepository,
            episodesProvider = { state.episodesBySeason },
            seasonsProvider = { state.seasons },
        ).deleteOfflineEpisodes(episodeIds)
    }

    fun deleteOfflineSeries(seriesId: String) {
        dismissSeriesDelete()
        offlineDeleteActions.deleteOfflineSeries(seriesId)
    }


    /**
     * The home screen's container adapter: maps the mutation over EVERY
     * section, because the same item can appear in several (e.g. Continue
     * Watching and Latest in X) and every visible card must flip together.
     * Everything else about the mutation is owned by [UserDataMutator].
     */
    private val sectionItemContainer = UserDataContainer { itemId, patch ->
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    section.copy(
                        items = section.items.map { if (it.id == itemId) patch(it) else it }
                    )
                }
            )
        }
    }

    private fun setItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(
                itemId = item.id,
                played = played,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(sectionItemContainer),
            )
        }
    }

    fun resetHomeScrollPosition() {
        homeScrollPosition = HomeScrollPosition()
    }

    private fun refresh() {
        launch {
            _uiState.update { it.copy(isLoading = true) }
            resetHomeScrollPosition()
            _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null) }
            invalidateDiscoverCache()
            fetchAndUpdateSections(force = true)
            startPeriodicRefresh()
        }
    }

    private fun pullToRefresh() {
        launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            invalidateDiscoverCache()
            fetchAndUpdateSections(force = true)
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
        // Only the rarely-changing blank/nonblank signal touches _uiState.
        // Writing the per-keystroke query string here would change HomeUiState
        // equality on every keystroke and recompose the whole MainHomeContent
        // body. The live query lives on searchQueryFlow, read in a leaf.
        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(isSearching = if (query.isBlank()) false else it.searchState.isSearching),
                isSearchActive = query.isNotBlank(),
            )
        }
        searchQueryFlow.value = query
    }

    private fun clearSearch() {
        _uiState.update { it.copy(searchState = HomeSearchState(), isSearchActive = false) }
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
        // Capture the query before deleting so Undo can re-save it (the DB row id
        // changes on re-insert; the query text is what matters). Undo is
        // presentation, so it stays here on top of the engine primitives.
        val item = searchHistory.value.firstOrNull { it.id == id }
        launch {
            mediaSearchEngine.deleteHistoryItem(id)
            if (item != null) {
                _undoActions.trySend(
                    UndoableAction(
                        message = "Removed \"${item.query}\" from search history",
                        onUndo = {
                            launch {
                                mediaSearchEngine.recordHistory(item.query, jellyfinHadResults = true)
                            }
                        },
                    ),
                )
            }
        }
    }

    fun clearSearchHistory() {
        launch {
            // Snapshot before clearing so Undo can restore the full set.
            val snapshot = searchHistory.value
            mediaSearchEngine.clearHistory()
            if (snapshot.isNotEmpty()) {
                _undoActions.trySend(
                    UndoableAction(
                        message = "Cleared search history",
                        onUndo = {
                            launch {
                                snapshot.forEach {
                                    mediaSearchEngine.recordHistory(it.query, jellyfinHadResults = true)
                                }
                            }
                        },
                    ),
                )
            }
        }
    }

    /**
     * Called when a settings search result is tapped from the home search bar.
     * If the target is an advanced setting that's currently hidden, enable
     * advanced settings first so the deep-linked screen actually shows it —
     * parity with the Settings screen's own search (see SettingsScreen.kt).
     * Navigation to [ResolvedSettingsItem.route] is performed by the caller.
     */
    fun onSettingsResultClicked(item: ResolvedSettingsItem) {
        if (item.isAdvanced) {
            launch { preferencesEditor.edit { appearance.setShowAdvancedSettings(true) } }
        }
    }

    fun excludeSeriesFromNextUp(seriesId: String) {
        launch {
            homeDiscoveryStore.excludeSeriesFromNextUp(seriesId)
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
        preferencesEditor.edit { homeDiscovery.setHomeSectionOrder(updated) }
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

    /**
     * Waits for the playback outbox to drain (count reaches 0) so the server
     * has processed offline watched/unwatched marks before a home-section fetch
     * reads Continue Watching / Next Up. Returns immediately when nothing is
     * pending; on [OUTBOX_DRAIN_WAIT_MS] timeout it returns regardless so the
     * fetch proceeds (a later periodic refresh re-syncs). Dead-lettered entries
     * are excluded from the count, so a persistently-undeliverable mark won't
     * stall the wait indefinitely.
     */
    private suspend fun awaitOutboxDrained() {
        if (playbackOutboxRepository.count() == 0) return
        withTimeoutOrNull(OUTBOX_DRAIN_WAIT_MS) {
            playbackOutboxRepository.countFlow().first { it == 0 }
        }
    }

    /**
     * Reads + orders the persisted home-sections snapshot for the current
     * query, or null if none is cached. Used to paint cached content *before*
     * clearing sections on a user/refresh transition, so the home screen never
     * shows the full-screen loading box (DelayedLoadingScreen) when we already
     * have stale content to display — stale-while-revalidate without the flash.
     */
    private fun currentHomeSectionQuery(): HomeSectionQuery = HomeSectionQuery(
        enabledSections = enabledHomeSectionTypes,
        libraryHomeSectionOverrides = libraryHomeSectionOverrides,
        nextUpRewatching = nextUpRewatching,
        nextUpMaxDays = nextUpMaxDays,
        nextUpExcludedSeriesIds = nextUpExcludedSeriesIds,
        hiddenCwItemIds = hiddenCwItemIds,
        pinnedSections = pinnedHomeSections,
    )

    /**
     * Reads the persisted SWR snapshot for [query] and orders it for display,
     * or null if nothing is cached / the cached sections are empty. Shared by
     * the user-switch collector (paint snapshot instead of a loading box) and
     * the cold-open path in [fetchAndUpdateSections].
     */
    private suspend fun orderedCachedHomeSections(query: HomeSectionQuery): List<HomeSection>? =
        runCatching { mediaRepository.getCachedHomeSections(query) }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }
            ?.let { cached ->
                orderHomeSections(
                    sections = cached.sections,
                    order = homeSectionOrder,
                    mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
                )
            }

    private suspend fun fetchAndUpdateSections(force: Boolean = false) {
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
            val query = currentHomeSectionQuery()

            // Stale-while-revalidate: on a cold open (sections still empty),
            // paint the persisted snapshot from Room instantly so the home
            // screen renders before the network refresh below resolves. The
            // fresh fetch overwrites it on success; if the network fails we keep
            // showing stale rather than an empty screen. Only when empty — a
            // pull-to-refresh or pref change already has sections on screen and
            // flashing stale would feel worse than the brief spinner.
            if (_uiState.value.sections.isEmpty()) {
                orderedCachedHomeSections(query)?.let { cachedSections ->
                    _uiState.update { it.copy(sections = cachedSections) }
                }
            }

            // The three fetch groups write independent _uiState fields
            // (sections / discoverSections / recentlyGrabbed), so run them
            // concurrently rather than one after another — cold-open latency
            // becomes the max of the three instead of their sum. The CW
            // widget/TV-Watch-Next side-effect stays tied to the main sections
            // result and is invoked after the mutex releases. Discover/arr are
            // runCatching-wrapped so a failure in either can never cancel the
            // main fetch via coroutineScope's structured concurrency.
            coroutineScope {
                val mainDeferred = async {
                    // force = the home screen's manual refresh / pull-to-refresh:
                    // bypass this query's home-sections cache rather than
                    // dropping every cache in the repository (plan 08).
                    mediaRepository.getHomeSections(query, force = force)
                }
                val discoverDeferred = if (_uiState.value.discoverEnabled) {
                    async { runCatching { fetchDiscoverSections(seerrPreferences) } }
                } else null
                // Direct *arr "Recently Grabbed" calendar — gated by the
                // DIRECT_ARR_INTEGRATION flag and the same TTL gate as discover
                // sections so it never adds extra round-trips on every refresh.
                val arrDeferred = if (_uiState.value.directArrEnabled) {
                    async { runCatching { fetchRecentlyGrabbed() } }
                } else null

                mainDeferred.await()
                    .onSuccess { homeResult ->
                        val fetchedSections = homeResult.sections
                        // Surface a non-blocking notice only when a section type
                        // actually failed to load (403/500/network). Sections that
                        // returned zero items (e.g. no watch history, no Next Up) are
                        // NOT failures — previously the size-mismatch heuristic
                        // false-positived on new users and after merges.
                        _uiState.update { it.copy(partialLoadError = homeResult.failedSectionTypes.isNotEmpty()) }
                        // OrderHomeSectionsUseCase is pure and operates on a handful
                        // of sections (sub-microsecond), so no thread offload. The
                        // previous withContext(Dispatchers.Default) hop escaped the
                        // test scheduler and made isRefreshing/isLoading assertions
                        // racy under StandardTestDispatcher.
                        val finalSections = orderHomeSections(
                            sections = fetchedSections,
                            order = homeSectionOrder,
                            mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
                        )

                        _uiState.update { it.copy(sections = finalSections) }

                        val continueWatching = finalSections
                            .find { it.type == HomeSectionType.CONTINUE_WATCHING }
                            ?.items ?: emptyList()
                        val currentIds = continueWatching.map { it.id }.toSet()
                        if (currentIds != lastContinueWatchingIds) {
                            lastContinueWatchingIds = currentIds
                            widgetDataStore.setContinueWatching(continueWatching)
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

                        // A successful foreground library scan is the
                        // shared hook for the auto-download foreground drain and
                        // the widget recommendations refresh. Wrapped in try/catch
                        // (the impl also self-guards) so a downstream failure can
                        // never break the home refresh.
                        runCatching { librarySyncHook.onLibraryScanComplete() }
                    }
                    .onFailure { throwable ->
                        if (_uiState.value.sections.isEmpty()) {
                            _uiState.update { s -> s.copy(error = throwable.message ?: "${throwable::class.simpleName}") }
                        }
                        _uiState.update { it.copy(partialLoadError = false) }
                    }

                // Await the optional groups so coroutineScope doesn't return
                // before their state writes land. Errors are already swallowed
                // by runCatching above; the results are intentionally ignored.
                discoverDeferred?.await()
                arrDeferred?.await()
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
        if (!discoverCache.shouldFetch(now)) return

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

        discoverCache.markFetched(timeSource.nowEpochMillis())
        _uiState.update { it.copy(discoverSections = newSections) }
    }

    /**
     * Resets the discover-sections TTL so the next [fetchDiscoverSections]
     * actually hits the network. Called on user-initiated refresh.
     */
    fun invalidateDiscoverCache() {
        discoverCache.invalidate()
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
}
