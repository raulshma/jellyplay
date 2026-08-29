package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.NextEpisode
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
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.HomeSessionTransition
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
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.core.ui.settingssearch.settingsSearchResults
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val episodeCatalogue: EpisodeCatalogue,
    private val userDataMutator: UserDataMutator,
    private val mediaSearchEngine: MediaSearchEngine,
    private val offlineFirstItemResolver: OfflineFirstItemResolver,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val imageUrlProvider: ImageUrlProvider,
    private val photoFolderPrefetcher: PhotoFolderPrefetcher,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
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
    /**
     * The single owner of identity transitions (replaces this VM's own
     * previousUserId mirror). Drives the scroll reset + refresh routing in
     * init; `authRepository.currentUser` is still collected separately below
     * purely as the uiState.currentUser mirror.
     */
    private val homeSession: HomeSession,
    private val arrRepository: ArrRepository,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val librarySyncHook: LibrarySyncHook,
    private val timeSource: TimeSource,
    private val userMessageBus: UserMessageBus,
    /**
     * The settings-search catalog, injected through the core/ui seam. The
     * binding itself lives in feature/settings (see its `SettingsSearchModule`)
     * and resolves at app level — this module keeps no dependency on
     * feature/settings, only on the core/ui interface.
     */
    private val settingsSearchProvider: SettingsSearchProvider,
) : JellyPlayViewModel(), DefaultLifecycleObserver {

    private val _uiState = stateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.flow

    val activeDownloadCount: StateFlow<Int> = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Ids of download-complete items, for quick-action gating: a downloaded
     * item's long-press offers "Remove download" instead of "Download".
     * Collected unconditionally — unlike [HomeUiState.offlineLibrary], which is
     * gated to offline modes because every download-progress tick re-invalidates
     * it — because the ONLINE home's action sheet needs this set too. The
     * repository collapses equal id sets, so transfers don't churn it.
     */
    val downloadedIds: StateFlow<Set<String>> = combine(
        downloadRepository.observeCompletedDownloadedIds(),
        // Series ids ride the same set so a series card's Download action
        // flips to Remove download once the series has a downloaded episode —
        // REMOVE_DOWNLOAD then opens the delete-episodes sheet.
        downloadRepository.observeDownloadedSeriesIds(),
    ) { itemIds, seriesIds -> itemIds + seriesIds }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * The home screen's pending-sync surface — outbox badge count, sync
     * details sheet entries and per-id metadata, the manual drain trigger and
     * the offline→online drain gate (handed to [HomeRefresher] below as its
     * `awaitOutboxDrained` seam). Core/data holder (see
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
    private fun ensurePendingItemDetails(itemIds: Collection<String>) =
        syncStatus.ensurePendingItemDetails(itemIds)

    /**
     * The section-preference mirrors, bundled in one [HomeSectionPrefs] value
     * so the prefs collector below diffs and adopts each emission with a
     * single comparison/assignment; the refresher consumes the snapshot
     * directly via its planProvider.
     */
    private var sectionPrefs = HomeSectionPrefs()
    private var androidTvWatchNextEnabled = true
    private var seerrPreferences = SeerrPreferences()

    /** Saved home-list scroll anchor (see [ScrollPositionStore]); the VM's get/save/reset methods are delegates. */
    private val scrollPositionStore = ScrollPositionStore()

    /**
     * The home screen's entire refresh policy — fetch cadence, throttles,
     * mutex/job choreography, discover TTL, user-data-push deferral, the
     * offline transitions and the going-online handshake — behind one small
     * interface (see [HomeRefresher]). This VM keeps only the UI-shaped
     * orchestration: folding [HomeRefresher.state] into [HomeUiState] (the
     * collector below, same fold pattern as [SeerrRequestStateHolder]) and
     * the scroll resets on manual refresh and identity changes.
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
        awaitOutboxDrained = syncStatus::awaitOutboxDrained,
        planProvider = { sectionPrefs },
        seerrPreferencesProvider = { seerrPreferences },
        discoverEnabledProvider = { _uiState.value.discoverEnabled },
        directArrEnabledProvider = { _uiState.value.directArrEnabled },
        androidTvWatchNextEnabledProvider = { androidTvWatchNextEnabled },
    )

    /**
     * The offline collection gate — ONE flow shared by the library and
     * episodes collectors in [init] (the predicate used to be copy-pasted into
     * each, free to drift), and the input side of [computeHomeRenderSource]
     * via [offlineGateState]. Downloads collect while any offline mode is
     * active or an online fetch failed with nothing to show.
     */
    private val offlineGate: Flow<OfflineGate> = combine(
        offlineModeManager.offlineMode,
        refresher.state.map { it.fetchFailedEmpty },
    ) { mode, fetchFailedEmpty -> OfflineGate(mode, fetchFailedEmpty) }
        .distinctUntilChanged()
        .onEach { offlineGateState = it }

    /**
     * Latest [offlineGate] emission. Read by the library collector's
     * render-source fold so [computeHomeRenderSource] sees the same gate
     * emission the collection keys on (not the uiState mirrors, which lag a
     * hop behind). `onEach` upstream of `flatMapLatest` keeps it fresh for
     * every activation.
     */
    private var offlineGateState = OfflineGate(OfflineMode.ONLINE, fetchFailedEmpty = false)

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
     * Local settings search for the header search bar, delegated to the core/ui
     * pipeline with the injected [SettingsSearchProvider] (the feature/settings
     * catalog). Kept as a VM-exposed function — not a direct call from the
     * screen — so the provider seam is injected in exactly one place and JVM
     * tests can swap in a fake catalog. The Appearance-level "settings in home
     * search" gate stays with the caller: it simply doesn't collect when off.
     */
    fun settingsSearchResults(
        queries: Flow<String>,
        context: android.content.Context,
    ): Flow<List<ResolvedSettingsItem>> =
        settingsSearchResults(queries, context, settingsSearchProvider)

    /**
     * The photo-folder child-URL cache (see [PhotoFolderChildUrlsStore]).
     * Re-exposed so the photo-row call sites observe the same flow as before.
     */
    private val photoFolderChildUrlsStore = PhotoFolderChildUrlsStore(scope, photoFolderPrefetcher)

    /** Cached folder-id → child-image-URLs map for the photo rows. */
    val photoFolderChildUrls: StateFlow<Map<String, List<String>>> get() = photoFolderChildUrlsStore.childUrls

    private fun prefetchPhotoFolderChildUrls(items: List<MediaItem>) =
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
     * The series download sheet opened from a series card's quick-action
     * Download — the same `SeriesDownloadSheet` the media-detail screen hosts,
     * fed here from the [EpisodeCatalogue] seam instead of a detail session.
     * [state][SeriesDownloadStateHolder.state] is folded into
     * [HomeUiState.seriesDownload] by the init collector; the methods below
     * are one-line delegates for the sheet's callbacks.
     */
    private val seriesDownloadStateHolder = SeriesDownloadStateHolder(
        scope = scope,
        episodeCatalogue = episodeCatalogue,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        userMessageBus = userMessageBus,
    )

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
            // uiState.currentUser mirror only — the refresh/scroll routing on
            // identity changes moved to the HomeSession collector below.
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        launch {
            // Identity-change routing, now owned by HomeSession (the single
            // detector — previously this VM's own previousUserId mirror over
            // currentUser).
            //
            // `transitions` replays its latest emission, so a sign-in that
            // happened before this VM existed is delivered to this collector
            // directly. The subscribe-first-then-check handshake below still
            // covers the never-transitioned start state (fresh install /
            // signed out), where nothing exists to replay and reading BEFORE
            // subscribing could miss a transition emitted between the read
            // and the subscription (home stuck on the spinner). The collector
            // signals via [onSubscription] + a [CompletableDeferred] that it
            // is live, and only then does the fallback read run. A sign-in
            // racing the handshake may trigger both the transition handler
            // and the fallback refresh — the refresher coalesces them through
            // its transition-job replacement, so that overlap is benign.
            val subscribed = CompletableDeferred<Unit>()
            launch {
                // collectLatest-shaped routing: the refresher's
                // UserSwitched/SignedOut handlers replace (and cancel) the
                // previous identity transition's in-flight work, so a
                // transition arriving mid-refresh cancels the stale handler
                // instead of queueing behind it.
                homeSession.transitions
                    .onSubscription { subscribed.complete(Unit) }
                    .collectLatest { transition ->
                        when (transition) {
                            HomeSessionTransition.SignedIn,
                            is HomeSessionTransition.UserSwitched,
                            is HomeSessionTransition.ServerSwitched -> {
                                resetHomeScrollPosition()
                                // SWR snapshot paint, sign-in fetch outside
                                // the refresh job, loop restart — all refresh
                                // policy lives in the refresher.
                                refresher.request(RefreshTrigger.UserSwitched)
                            }
                            is HomeSessionTransition.SignedOut -> {
                                resetHomeScrollPosition()
                                refresher.request(RefreshTrigger.SignedOut)
                            }
                        }
                    }
            }
            subscribed.await()
            if (homeSession.currentIdentity() != null) {
                resetHomeScrollPosition()
                refresher.request(RefreshTrigger.UserSwitched)
            }
            // No join() on the collector: it never completes (hot flow for the
            // VM's lifetime), and structured concurrency already keeps this
            // coroutine from completing while the child collector runs.
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
                val newSectionPrefs = HomeSectionPrefs(
                    query = HomeSectionQuery(
                        enabledSections = prefs.home.enabledHomeSectionTypes,
                        libraryHomeSectionOverrides = prefs.home.libraryHomeSectionOverrides,
                        nextUpRewatching = prefs.home.nextUpRewatching,
                        nextUpMaxDays = prefs.home.nextUpMaxDays,
                        nextUpExcludedSeriesIds = prefs.home.nextUpExcludedSeriesIds,
                        hiddenCwItemIds = prefs.home.hiddenCwItemIds,
                        pinnedSections = prefs.home.pinnedHomeSections,
                    ),
                    homeSectionOrder = prefs.home.homeSectionOrder,
                    mergeContinueWatchingAndNextUp = prefs.home.mergeContinueWatchingAndNextUp,
                )
                val homeSectionPrefsChanged = hasSeenHomePreferences && newSectionPrefs != sectionPrefs

                hasSeenHomePreferences = true
                sectionPrefs = newSectionPrefs
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
                    offlineSectionPrefs = OfflineHomeSectionPrefs(
                        continueWatchingEnabled = HomeSectionType.CONTINUE_WATCHING in prefs.home.enabledHomeSectionTypes,
                        nextUpEnabled = HomeSectionType.NEXT_UP in prefs.home.enabledHomeSectionTypes,
                        hiddenCwItemIds = prefs.home.hiddenCwItemIds,
                        nextUpExcludedSeriesIds = prefs.home.nextUpExcludedSeriesIds,
                        mergeCwAndNextUp = prefs.home.mergeContinueWatchingAndNextUp,
                    ),
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
                    refresher.request(RefreshTrigger.DiscoverEnabled)
                }
            }
        }

        // Collect the offline library (and episodes, collector below) whenever
        // the offline branch can render them: any offline mode, or while an
        // online fetch failed with nothing to show (implicit offline — the
        // home falls back to downloads plus a status banner). The underlying
        // Flows re-emit on every download progress write, so collecting them
        // unconditionally re-invalidated the whole home tree during downloads
        // even in normal online browsing (where the offline branch never
        // renders); the gate keeps the upstream collection cancelled there.
        //
        // ONE gate flow ([offlineGate]) feeds both collectors — the gate
        // predicate used to be copy-pasted into each, and the copies were
        // free to drift. The same gate emission also computes
        // [HomeUiState.renderSource] (via [computeHomeRenderSource]), so the
        // offline-render predicate has exactly one fold.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        launch {
            offlineGate
                .flatMapLatest { gate ->
                    if (gate.isCollecting) {
                        offlineRepository.getOfflineLibrary()
                            .map { OfflineLibraryEmission(items = it) }
                            .onStart { emit(OfflineLibraryEmission(pending = true)) }
                    } else {
                        flowOf(OfflineLibraryEmission())
                    }
                }
                .collect { emission ->
                    _uiState.update { state ->
                        state.copy(
                            offlineLibrary = emission.items,
                            renderSource = computeHomeRenderSource(
                                offlineMode = offlineGateState.mode,
                                fetchFailedEmpty = offlineGateState.fetchFailedEmpty,
                                offlineLibrary = emission.items,
                                fallbackPending = emission.pending,
                            ),
                        )
                    }
                }
        }

        // Downloaded episodes ride the SAME gate as the library above but are
        // collected independently — they feed only the offline CW/Next Up rows
        // via [buildOfflineHomeSections], so their (potentially large, artwork-
        // resolving) emissions must not delay the library's pending→loaded
        // transition. First emission while the gate is closed is empty.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        launch {
            offlineGate
                .flatMapLatest { gate ->
                    if (gate.isCollecting) {
                        offlineRepository.getOfflineEpisodes()
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { episodes ->
                    _uiState.update { it.copy(offlineEpisodes = episodes) }
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
        // observes a single object: the holder's snapshot flow already
        // coalesces its six sub-flows into one emission, so a dialog-open
        // burst (services + seasons firing together) lands as a single
        // _uiState update. The snapshot is embedded as-is (no per-field
        // mirror); `requestItem` is set separately (selectSeerrRequestItem)
        // and survives the merge because only the snapshot slice changes.
        launch {
            seerrRequestStateHolder.snapshot.collect { snap ->
                _uiState.update {
                    it.copy(seerrRequestState = it.seerrRequestState.copy(snapshot = snap))
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

        // Fold the series download sheet holder into HomeUiState.seriesDownload
        // (same fold pattern).
        launch {
            seriesDownloadStateHolder.state.collect { seriesDownload ->
                _uiState.update { it.copy(seriesDownload = seriesDownload) }
            }
        }

        // Fold the refresher's state slice into HomeUiState (same fold
        // pattern as the SeerrRequestStateHolder collector above) so the UI
        // observes a single state object. The refresher is the SOLE writer of
        // these nine fields — including `sections` (in-place item patches go
        // through HomeRefresher.patchItems, never a direct _uiState write),
        // the going-online flag/loader, and the offline-mode mirror with its
        // transition policy. The VM only folds emissions.
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
                        isGoingOnline = refresh.isGoingOnline,
                        offlineMode = refresh.offlineMode,
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
     * The VM's single command surface: every user intent arrives here as a
     * [HomeUiEvent] and is routed to a private handler below. The VM's other
     * public members are flows and sync getters only — there is no per-action
     * command method to keep in sync with the screen.
     */
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
            is HomeUiEvent.SwitchUser -> switchUser(event.userId)
            is HomeUiEvent.MarkItemPlayed -> setItemPlayed(event.item, played = true)
            is HomeUiEvent.MarkItemUnplayed -> setItemPlayed(event.item, played = false)
            is HomeUiEvent.DeleteOfflineMedia -> deleteOfflineMedia(event.item)
            is HomeUiEvent.RequestSeriesDownload -> requestSeriesDownload(event.series)
            is HomeUiEvent.LoadSeriesDownloadEpisodes -> loadSeriesDownloadEpisodes(event.seasonId)
            is HomeUiEvent.DownloadSeries -> downloadSeries(event.selectedEpisodes)
            is HomeUiEvent.DismissSeriesDownload -> dismissSeriesDownload()
            is HomeUiEvent.RequestSeriesDelete -> requestSeriesDelete(event.series)
            is HomeUiEvent.DismissSeriesDelete -> dismissSeriesDelete()
            is HomeUiEvent.DeleteOfflineEpisodes -> deleteOfflineEpisodes(event.episodeIds)
            is HomeUiEvent.DeleteOfflineSeries -> deleteOfflineSeries(event.seriesId)
            is HomeUiEvent.PrefetchSeerrDetails -> prefetchSeerrDetails(event.tmdbId, event.mediaType, event.onDone)
            is HomeUiEvent.DeleteSearchHistoryItem -> deleteSearchHistoryItem(event.id)
            is HomeUiEvent.ClearSearchHistory -> clearSearchHistory()
            is HomeUiEvent.SettingsResultClicked -> onSettingsResultClicked(event.item)
            is HomeUiEvent.ExcludeSeriesFromNextUp -> excludeSeriesFromNextUp(event.seriesId)
            is HomeUiEvent.SetSectionVisible -> setSectionVisible(event.type, event.visible)
            is HomeUiEvent.MoveSection -> moveSection(event.type, event.up)
            is HomeUiEvent.SetLibrarySectionVisible -> setLibrarySectionVisible(event.libraryId, event.type, event.visible)
            is HomeUiEvent.PrefetchPhotoFolderChildUrls -> prefetchPhotoFolderChildUrls(event.items)
            is HomeUiEvent.EnsurePendingItemDetails -> ensurePendingItemDetails(event.itemIds)
            is HomeUiEvent.PlaySeries -> resolveSeriesPlay(event)
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    fun getHomeScrollPosition(): HomeScrollPosition = scrollPositionStore.get()

    fun saveHomeScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) =
        scrollPositionStore.save(firstVisibleItemIndex, firstVisibleItemScrollOffset)

    /** Single-flight latch for [resolveSeriesPlay] — main-thread only. */
    private var seriesPlayResolveInFlight = false

    /**
     * Resolves which EPISODE of a home section SERIES card's play affordance
     * should start: the shared catalogue-side smart-play decision
     * ([NextEpisode.forSorted] over the playback-sorted snapshot — resume →
     * next unplayed → replay first), i.e. the same rule the detail screen's
     * primary button applies. A series folder must never be handed to the
     * player itself.
     *
     * Cards resolve against the server catalogue; when the screen is
     * rendering downloaded content ([isRenderingDownloads]) the catalogue
     * reads local episodes instead — so an implicit-offline session (fetch
     * failed, downloads shown) never pokes the server that just failed.
     * Single-flight: taps arriving while a resolve is in flight are dropped.
     * [HomeUiEvent.PlaySeries.onResolved] fires on the main thread exactly
     * once per accepted request.
     */
    private fun resolveSeriesPlay(event: HomeUiEvent.PlaySeries) {
        // Single-flight: rapid double-taps must not stack catalogue loads or
        // double-navigate.
        if (seriesPlayResolveInFlight) return
        seriesPlayResolveInFlight = true
        launch {
            try {
                val target = episodeCatalogue.loadSeriesEpisodes(
                    event.series.id,
                    offline = isRenderingDownloads(),
                ).getOrNull()?.let { NextEpisode.forSorted(it.sortedEpisodes) }
                val episode = target?.episode
                event.onResolved(
                    if (episode != null) {
                        SeriesPlayResolution.Episode(episode, target.startPositionTicks)
                    } else {
                        SeriesPlayResolution.Details(event.series)
                    },
                )
            } finally {
                seriesPlayResolveInFlight = false
            }
        }
    }

    /**
     * True when the home screen renders (or would render) downloaded content
     * rather than server content: explicit offline mode, or the implicit one —
     * the online fetch failed leaving only downloads to show. Reads the same
     * [HomeUiState.renderSource] the screen branches on (one fold — see
     * [computeHomeRenderSource]); previously this re-derived the predicate
     * with subtly different terms from the screen's copy. The known-empty
     * corner (fetch failed, downloads confirmed absent →
     * [HomeRenderSource.Online], hard-error screen) is deliberately excluded:
     * no series card can fire this while the error screen shows.
     */
    private fun isRenderingDownloads(): Boolean =
        _uiState.value.renderSource != HomeRenderSource.Online

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
    private fun deleteOfflineMedia(item: MediaItem) {
        offlineDeleteActions.deleteDownload(item)
    }

    /** Opens the delete-episodes sheet for [series] — see [SeriesDeleteStateHolder.requestSeriesDelete]. */
    private fun requestSeriesDelete(series: MediaItem) = seriesDeleteStateHolder.requestSeriesDelete(series)

    /** Opens the series download sheet for [series] — see [SeriesDownloadStateHolder.requestSeriesDownload]. */
    private fun requestSeriesDownload(series: MediaItem) = seriesDownloadStateHolder.requestSeriesDownload(series)

    /** Lazily expands one season in the open series download sheet — see [SeriesDownloadStateHolder.loadSeasonEpisodes]. */
    private fun loadSeriesDownloadEpisodes(seasonId: String) = seriesDownloadStateHolder.loadSeasonEpisodes(seasonId)

    /** Queues the selected episodes and closes the sheet — see [SeriesDownloadStateHolder.downloadSeries]. */
    private fun downloadSeries(selectedEpisodes: Map<String, List<String>>) =
        seriesDownloadStateHolder.downloadSeries(selectedEpisodes)

    /** Closes the series download sheet — see [SeriesDownloadStateHolder.dismiss]. */
    private fun dismissSeriesDownload() = seriesDownloadStateHolder.dismiss()

    /**
     * Long-press Download from an online home card. Single-stream items
     * (movie/episode/music track) start inline at the default quality; series
     * route to the detail screen with the download sheet pre-presented via
     * [onOpenDetail] (their flow needs the user's season/episode selection),
     * and other non-inline types open the detail screen plainly. Failures
     * surface on the message bus.
     */
    fun downloadItem(item: MediaItem, onOpenDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit) {
        launch {
            when (val result = downloadIntake.startFromItem(item)) {
                DownloadRequestResult.Started ->
                    userMessageBus.info(
                        UiText.Resource(com.raulshma.jellyplay.core.data.R.string.data_download_started)
                    )
                is DownloadRequestResult.SeriesSelectionRequired -> onOpenDetail(result.seriesId, true)
                is DownloadRequestResult.NeedsDetailScreen -> onOpenDetail(result.itemId, false)
                is DownloadRequestResult.Failed ->
                    userMessageBus.error(
                        UiText.Resource(com.raulshma.jellyplay.core.data.R.string.data_download_start_failed)
                    )
            }
        }
    }

    /** Closes the sheet — see [SeriesDeleteStateHolder.dismiss]. */
    private fun dismissSeriesDelete() = seriesDeleteStateHolder.dismiss()

    /**
     * Deletes the selected episodes for the open sheet — see
     * [SeriesDeleteStateHolder.deleteOfflineEpisodes]. The sheet snapshot is
     * read BEFORE dismissal there (passed per call into the shared module), so
     * the sheet can dismiss while the deletes run in background.
     */
    private fun deleteOfflineEpisodes(episodeIds: Set<String>) =
        seriesDeleteStateHolder.deleteOfflineEpisodes(episodeIds)

    /** Deletes the entire series and closes the sheet — see [SeriesDeleteStateHolder.deleteOfflineSeries]. */
    private fun deleteOfflineSeries(seriesId: String) = seriesDeleteStateHolder.deleteOfflineSeries(seriesId)


    /**
     * The home screen's container adapter: forwards the optimistic mutation
     * to [HomeRefresher.patchItems], the single sanctioned writer of the
     * cached sections — see the fold contract comment in [init].
     * Everything else about the mutation is owned by [UserDataMutator].
     */
    private val sectionItemContainer = UserDataContainer { itemId, patch ->
        refresher.patchItems(itemId, patch)
    }

    /**
     * Marks a home-row item (quick actions) played/unplayed. Flips the item
     * in-place in every section (via [HomeRefresher.patchItems]) so the card
     * badge updates immediately; the next home refresh reconciles the server
     * truth.
     */
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

    private fun resetHomeScrollPosition() {
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
        // Going online is async (preference write → mode flip → drain +
        // fetch) and gives no feedback without help. The whole choreography —
        // busy flag, mode toggle, full-screen loader, outbox drain, capped
        // fetch — lives behind [RefreshTrigger.GoingOnline] in the refresher
        // now. Going offline is instantaneous and needs no indicator: toggle
        // the manager directly and let the refresher's offline-mode observer
        // drop the online content.
        if (refresher.state.value.offlineMode != OfflineMode.ONLINE) {
            refresher.request(RefreshTrigger.GoingOnline)
        } else {
            offlineModeManager.toggleManualOffline()
        }
    }

    /** Manually drain the playback outbox — see [SyncStatusStateHolder.syncNow]. */
    private fun syncNow() = syncStatus.syncNow()

    /**
     * Switches the active user. The atomic session publish this triggers is
     * classified by [HomeSession] as a `UserSwitched` transition, and the
     * `homeSession.transitions` collector in [init] re-runs the home refresh
     * on it — no callback or explicit reload is needed here; the UI observes
     * the flow.
     */
    private fun switchUser(userId: String) {
        launch {
            authRepository.switchUser(userId)
        }
    }

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

    private fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        seerrRequestStateHolder.prefetchDetails(tmdbId, mediaType, onDone)
    }

    /** Deletes one history row (undo re-records it) — see [HomeSearchStateHolder.deleteSearchHistoryItem]. */
    private fun deleteSearchHistoryItem(id: Long) = searchStateHolder.deleteSearchHistoryItem(id)

    /** Clears the history (undo re-records the snapshot) — see [HomeSearchStateHolder.clearSearchHistory]. */
    private fun clearSearchHistory() = searchStateHolder.clearSearchHistory()

    /**
     * Called when a settings search result is tapped from the home search bar.
     * If the target is an advanced setting that's currently hidden, enable
     * advanced settings first so the deep-linked screen actually shows it —
     * parity with the Settings screen's own search (see SettingsScreen.kt).
     * Navigation to [ResolvedSettingsItem.route] is performed by the caller.
     */
    private fun onSettingsResultClicked(item: ResolvedSettingsItem) {
        if (item.isAdvanced) {
            launch { preferencesEditor.edit { appearance.setShowAdvancedSettings(true) } }
        }
    }

    private fun excludeSeriesFromNextUp(seriesId: String) {
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
    private fun setSectionVisible(type: HomeSectionType, visible: Boolean) {
        preferencesEditor.setEnabledHomeSectionTypes(
            sectionPrefs.withSectionVisible(type, visible).query.enabledSections,
        )
    }

    /**
     * Moves a home section up/down within the user's ordering, from the inline
     * section-config sheet. Swaps with the neighbour in the cached order and
     * persists via [preferencesEditor]; the prefs collector + ordering use case
     * re-apply it on the next emission.
     */
    private fun moveSection(type: HomeSectionType, up: Boolean) {
        val updated = sectionPrefs.withSectionMoved(type, up) ?: return
        preferencesEditor.edit { homeDiscovery.setHomeSectionOrder(updated.homeSectionOrder) }
    }

    /**
     * Toggles a per-library section (currently LATEST_MEDIA) from the inline
     * section-config sheet, mirroring Settings → Configure Libraries.
     */
    private fun setLibrarySectionVisible(libraryId: String, type: HomeSectionType, visible: Boolean) {
        preferencesEditor.setLibraryHomeSectionOverrides(
            sectionPrefs.withLibrarySectionVisible(libraryId, type, visible)
                .query.libraryHomeSectionOverrides,
        )
    }

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
        // Symmetric with the runCatching-wrapped addObserver in init: JVM unit
        // tests lack the process LifecycleOwner, so removal must not crash
        // onCleared for a VM whose addObserver was swallowed.
        runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(this) }
        refresher.stop()
    }
}

/**
 * The offline collection gate value: [isCollecting] is the predicate both
 * offline collectors key on, and [mode]/[fetchFailedEmpty] feed
 * [computeHomeRenderSource] so the render-source fold reads the same gate
 * emission that opened/closed the collection.
 */
private data class OfflineGate(
    val mode: OfflineMode,
    val fetchFailedEmpty: Boolean,
) {
    val isCollecting: Boolean get() = mode != OfflineMode.ONLINE || fetchFailedEmpty
}

/**
 * Emission envelope for the offline-library collection gate: [pending] marks
 * the window after the gate opens but before the first real library emission,
 * while the implicit-offline fallback is still deciding whether any downloads
 * exist. Maps onto [HomeUiState.offlineLibrary] +
 * [HomeRenderSource.FallbackPending].
 */
private data class OfflineLibraryEmission(
    val items: List<OfflineMediaItem> = emptyList(),
    val pending: Boolean = false,
)
