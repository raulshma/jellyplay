package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolder
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
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
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

    val activeDownloadCount: StateFlow<Int> = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The home screen's pending-sync surface — outbox badge count, sync
     * details sheet entries and per-id metadata, the manual drain trigger and
     * the offline→online drain gate. Core/data holder (see
     * [SyncStatusStateHolder]); re-exposed SearchViewModel-style so the
     * header/sheet call sites observe the same flows as before.
     */
    private val syncStatus = SyncStatusStateHolder(
        scope = scope,
        playbackOutboxRepository = playbackOutboxRepository,
        playbackSyncScheduler = playbackSyncScheduler,
        offlineFirstItemResolver = offlineFirstItemResolver,
        offlineModeManager = offlineModeManager,
    )

    /** Count of queued playback events — see [SyncStatusStateHolder.pendingSyncCount]. */
    val pendingSyncCount: StateFlow<Int> get() = syncStatus.pendingSyncCount

    /** Pending outbox rows for the sync details sheet — see [SyncStatusStateHolder.pendingSyncEntries]. */
    val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>> get() = syncStatus.pendingSyncEntries

    /** Per-row resolved metadata for the sheet — see [SyncStatusStateHolder.pendingItemDetails]. */
    val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>> get() = syncStatus.pendingItemDetails

    /** See [SyncStatusStateHolder.ensurePendingItemDetails]. */
    fun ensurePendingItemDetails(itemIds: Collection<String>) =
        syncStatus.ensurePendingItemDetails(itemIds)

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
    private var seerrPreferences = SeerrPreferences()

    /** Saved home-list scroll anchor (see [ScrollPositionStore]); the VM's get/save/reset methods are delegates. */
    private val scrollPositionStore = ScrollPositionStore()

    /**
     * The home screen's entire refresh policy — fetch cadence, throttles,
     * mutex/job choreography, discover TTL, user-data-push deferral — behind
     * one small interface (see [HomeRefresher]). This VM keeps only the
     * UI-shaped orchestration: folding [HomeRefresher.state] into
     * [HomeUiState] (the collector below, same fold pattern as
     * [SeerrRequestStateHolder]), the scroll reset on manual refresh, and
     * the offline→online timeout wrapper.
     *
     * Constructed here rather than injected: its collaborators are exactly
     * this VM's own constructor collaborators, and its per-call inputs are
     * the preference mirrors above, exposed as read-only providers so the
     * mirrors stay owned by the prefs collector in one place.
     */
    private val refresher = HomeRefresher(
        scope = scope,
        timeSource = timeSource,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        orderHomeSections = orderHomeSections,
        widgetDataStore = widgetDataStore,
        continueWatchingBroadcaster = continueWatchingBroadcaster,
        tvWatchNextScheduler = tvWatchNextScheduler,
        librarySyncHook = librarySyncHook,
        offlineModeManager = offlineModeManager,
        planProvider = {
            HomeSectionPlan(
                query = currentHomeSectionQuery(),
                order = homeSectionOrder,
                mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
            )
        },
        seerrPreferencesProvider = { seerrPreferences },
        discoverEnabledProvider = { _uiState.value.discoverEnabled },
        directArrEnabledProvider = { _uiState.value.directArrEnabled },
        androidTvWatchNextEnabledProvider = { androidTvWatchNextEnabled },
    )

    /**
     * The home search bar's entire state surface — live query, results slice,
     * active flag, recent history and the undo channel — behind one holder
     * (see [HomeSearchStateHolder]). The query/history/undo flows are
     * re-exposed directly (SearchViewModel style); [HomeSearchStateHolder.searchState]
     * and [HomeSearchStateHolder.isSearchActive] are folded into
     * [HomeUiState] by the two collectors in [init].
     */
    private val searchStateHolder = HomeSearchStateHolder(scope, mediaSearchEngine)

    /**
     * Read-only view of the live search query — see
     * [HomeSearchStateHolder.searchQuery] for why it is NOT part of uiState.
     */
    val searchQuery: StateFlow<String> get() = searchStateHolder.searchQuery

    /** Recent searches for the active user — see [HomeSearchStateHolder.searchHistory]. */
    val searchHistory: StateFlow<List<SearchHistoryItem>> get() = searchStateHolder.searchHistory

    /** Recoverable-action snackbars — see [HomeSearchStateHolder.undoActions]. */
    val undoActions get() = searchStateHolder.undoActions

    /**
     * The photo-folder child-URL cache (see [PhotoFolderChildUrlsStore]).
     * Re-exposed so the photo-row call sites observe the same flow as before.
     */
    private val photoFolderChildUrlsStore = PhotoFolderChildUrlsStore(scope, photoFolderPrefetcher)

    /** Cached folder-id → child-image-URLs map for the photo rows. */
    val photoFolderChildUrls: StateFlow<Map<String, List<String>>> get() = photoFolderChildUrlsStore.childUrls

    fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) =
        photoFolderChildUrlsStore.prefetch(items)

    /**
     * All users persisted for the current server. Backs the home app-bar quick
     * user switcher, which collects this flow directly (leaf-collected, like
     * `searchQuery`, rather than mirrored into [HomeUiState] — the mirror was
     * never read by the orchestrator and only churned uiState equality). The
     * underlying flow is DB-backed ([AuthRepository.currentServerUsers]) so
     * it's already populated after login — no extra fetch.
     */
    val currentServerUsers: StateFlow<List<UserInfo>> = authRepository.currentServerUsers
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The offline home's advanced "delete downloaded episodes" sheet for a
     * series card — sheet state plus its four actions, behind one holder (see
     * [SeriesDeleteStateHolder]; the snapshot-before-dismiss invariant is
     * documented and pinned there). [state][SeriesDeleteStateHolder.state] is
     * folded into [HomeUiState.seriesDelete] by the init collector; the four
     * methods below are one-line delegates so the sheet's call sites are
     * unchanged.
     */
    private val seriesDeleteStateHolder = SeriesDeleteStateHolder(scope, offlineRepository)

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
                        resetHomeScrollPosition()
                        refresher.onSignedOut()
                    }
                } else if (previousUserId != userId) {
                    resetHomeScrollPosition()
                    // SWR snapshot paint, sign-in fetch outside the refresh
                    // job, loop restart — all refresh policy lives in the
                    // refresher (see refreshForUserSwitch).
                    refresher.refreshForUserSwitch()
                }
                previousUserId = userId
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
                    refresher.request(RefreshTrigger.PrefsChanged)
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
                    refresher.fetchDiscover()
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
                        // Online → offline: drop cached online sections (refresh-
                        // owned state, cleared through the refresher so its next
                        // emission can't re-populate them). Also clear any
                        // pending going-online spinner — e.g. the user (or an
                        // auto-detect flip) took us back offline while the prior
                        // online fetch was still parked on the refresh mutex.
                        refresher.dropOnlineContent()
                        _uiState.update { it.copy(isGoingOnline = false) }
                    }
                    previousMode != OfflineMode.ONLINE && mode == OfflineMode.ONLINE -> {
                        // Offline → online: show the full-screen loader during the
                        // post-toggle fetch so the online branch doesn't flash
                        // blank between the mode flip and sections arriving.
                        // isGoingOnline MUST clear in finally — previously the
                        // clear ran after the fetch with no guard, so any
                        // throw/cancellation left it stuck on forever and the
                        // user had to restart the app to recover.
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
                            syncStatus.awaitOutboxDrained()
                            // Cap the post-toggle fetch so a hung network call
                            // cannot leave isGoingOnline (and isLoading) stuck
                            // on — the symptom was the Go Online button + app
                            // bar spinners never clearing. On timeout we drop the
                            // result; a normal refresh/pull-to-refresh can
                            // still repopulate sections once the network
                            // recovers. isLoading is force-cleared below.
                            withTimeoutOrNull(GOING_ONLINE_TIMEOUT_MS) {
                                refresher.fetchOnce()
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

        // Fold the search holder's two UI-shaped slices into HomeUiState (same
        // fold pattern as the Seerr/refresher collectors below) so the UI
        // observes a single state object. The per-keystroke query stays on the
        // holder's own flow (re-exposed as `searchQuery`), NOT in uiState —
        // see HomeSearchStateHolder's KDoc for the recomposition contract.
        // The search kernel itself (debounce, cancel-and-replace, parallel
        // Jellyfin + gated Seerr fetch, history policy) lives in the holder /
        // MediaSearchEngine; these collectors only fold emissions.
        launch {
            searchStateHolder.searchState.collect { search ->
                _uiState.update { it.copy(searchState = search) }
            }
        }
        launch {
            searchStateHolder.isSearchActive.collect { active ->
                _uiState.update { it.copy(isSearchActive = active) }
            }
        }

        // Fold the shared SeerrRequestStateHolder into HomeUiState so the UI
        // observes a single object. The holder's five StateFlows are combined
        // into one SeerrRequestState so concurrent emissions (e.g. loading
        // services + seasons firing together when the request dialog opens)
        // coalesce into a single _uiState update instead of five back-to-back
        // copies. `requestItem` is set separately (selectSeerrRequestItem) and
        // is preserved by re-copying the existing value over the merged slice.
        launch {
            combine(
                seerrRequestStateHolder.requestResult,
                seerrRequestStateHolder.radarrServers,
                seerrRequestStateHolder.sonarrServers,
                seerrRequestStateHolder.isLoadingServices,
                seerrRequestStateHolder.tvSeasons,
            ) { result, radarr, sonarr, loading, seasons ->
                SeerrRequestState(
                    result = result,
                    radarrServers = radarr,
                    sonarrServers = sonarr,
                    isLoadingServices = loading,
                    tvSeasons = seasons,
                )
            }.distinctUntilChanged().collect { merged ->
                _uiState.update {
                    it.copy(seerrRequestState = merged.copy(requestItem = it.seerrRequestState.requestItem))
                }
            }
        }

        // Fold the series-delete sheet holder into HomeUiState.seriesDelete
        // (same fold pattern) so the sheet keeps observing one state object.
        launch {
            seriesDeleteStateHolder.state.collect { seriesDelete ->
                _uiState.update { it.copy(seriesDelete = seriesDelete) }
            }
        }

        // Fold the refresher's state slice into HomeUiState (same fold
        // pattern as the SeerrRequestStateHolder collector above) so the UI
        // observes a single state object. The refresher is the only writer of
        // these seven fields; the VM's offline→online branch is the one
        // deliberate exception, raising isLoading directly for the
        // going-online loader and force-clearing it in its finally — exactly
        // the pre-extraction behavior.
        launch {
            refresher.state.collect { refresh ->
                _uiState.update {
                    it.copy(
                        sections = refresh.sections,
                        isLoading = refresh.isLoading,
                        isRefreshing = refresh.isRefreshing,
                        error = refresh.error,
                        partialLoadError = refresh.partialLoadError,
                        discoverSections = refresh.discoverSections,
                        recentlyGrabbed = refresh.recentlyGrabbed,
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

    fun getHomeScrollPosition(): HomeScrollPosition = scrollPositionStore.get()

    fun saveHomeScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) =
        scrollPositionStore.save(firstVisibleItemIndex, firstVisibleItemScrollOffset)

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
     * Used only for the quick-action delete; the series delete-episodes sheet
     * goes through [seriesDeleteStateHolder], which captures its snapshot
     * providers per call.
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

    /** Opens the delete-episodes sheet for [series] — see [SeriesDeleteStateHolder.requestSeriesDelete]. */
    fun requestSeriesDelete(series: MediaItem) = seriesDeleteStateHolder.requestSeriesDelete(series)

    /** Closes the sheet — see [SeriesDeleteStateHolder.dismiss]. */
    fun dismissSeriesDelete() = seriesDeleteStateHolder.dismiss()

    /**
     * Deletes the selected episodes for the open sheet — see
     * [SeriesDeleteStateHolder.deleteOfflineEpisodes]. The sheet snapshot is
     * captured BEFORE dismissal there (the shared module reads its providers
     * lazily), so the sheet can dismiss while the deletes run in background.
     */
    fun deleteOfflineEpisodes(episodeIds: Set<String>) =
        seriesDeleteStateHolder.deleteOfflineEpisodes(episodeIds)

    /** Deletes the entire series and closes the sheet — see [SeriesDeleteStateHolder.deleteOfflineSeries]. */
    fun deleteOfflineSeries(seriesId: String) = seriesDeleteStateHolder.deleteOfflineSeries(seriesId)


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
        scrollPositionStore.reset()
    }

    /**
     * Manual refresh. The scroll reset is the only preamble piece that lives
     * here — pure VM-owned state the refresher cannot see. Everything else
     * (loader raise, content/error clear, discover-cache invalidation, forced
     * fetch, loop restart) is [RefreshTrigger.Manual] policy inside the
     * refresher; see its KDoc.
     */
    private fun refresh() {
        resetHomeScrollPosition()
        refresher.request(RefreshTrigger.Manual)
    }

    private fun pullToRefresh() {
        refresher.request(RefreshTrigger.PullToRefresh)
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

    /** Manually drain the playback outbox — see [SyncStatusStateHolder.syncNow]. */
    private fun syncNow() = syncStatus.syncNow()

    private fun updateSearchQuery(query: String) = searchStateHolder.updateSearchQuery(query)

    private fun clearSearch() = searchStateHolder.clearSearch()

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

    /** Deletes one history row (undo re-records it) — see [HomeSearchStateHolder.deleteSearchHistoryItem]. */
    fun deleteSearchHistoryItem(id: Long) = searchStateHolder.deleteSearchHistoryItem(id)

    /** Clears the history (undo re-records the snapshot) — see [HomeSearchStateHolder.clearSearchHistory]. */
    fun clearSearchHistory() = searchStateHolder.clearSearchHistory()

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
     * the prefs collector above then triggers a refresher request so the
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
     * Snapshots the section-preference mirrors into the query half of the
     * [HomeSectionPlan] handed to [HomeRefresher] on every fetch (the
     * ordering half comes from the same mirrors). Keeping this a single
     * snapshot per fetch is what lets the refresher never observe a
     * half-applied preference change.
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

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Order matters inside start(): network check → stale fetch → loop
        // start → deferred user-data flush. See HomeRefresher.start.
        refresher.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        refresher.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        refresher.stop()
    }
}
