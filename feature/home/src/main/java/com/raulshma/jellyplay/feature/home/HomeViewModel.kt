package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val offlineModeManager: OfflineModeManager,
    private val newsletterTriggerManager: NewsletterTriggerManager,
    private val preferencesStore: UserPreferencesStore,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val authRepository: AuthRepository,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : JellyPlayViewModel(), DefaultLifecycleObserver {

    companion object {
        private const val REFRESH_INTERVAL_FOREGROUND_MS = 60_000L
        private const val REFRESH_INTERVAL_BACKGROUND_MS = 120_000L
        private const val MIN_REFRESH_INTERVAL_MS = 30_000L
    }

    private val _uiState = stateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.flow

    val activeDownloadCount: StateFlow<Int> = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private var enabledHomeSectionTypes = HomeSectionType.CONFIGURABLE.toSet()
    private var homeSectionOrder = HomeSectionType.CONFIGURABLE
    private var hiddenLibrarySectionIds = emptySet<String>()
    private var mergeContinueWatchingAndNextUp = false
    private var nextUpMaxDays = 0
    private var nextUpRewatching = false
    private var nextUpExcludedSeriesIds = emptySet<String>()
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

    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")
    private var searchJob: Job? = null

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        launch {
            var previousUserId: String? = null
            preferencesStore.activeUserId.collect { userId ->
                if (previousUserId != null && previousUserId != userId) {
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
                        prefs.hiddenLibrarySectionIds != hiddenLibrarySectionIds ||
                        prefs.mergeContinueWatchingAndNextUp != mergeContinueWatchingAndNextUp ||
                        prefs.nextUpMaxDays != nextUpMaxDays ||
                        prefs.nextUpRewatching != nextUpRewatching ||
                        prefs.nextUpExcludedSeriesIds != nextUpExcludedSeriesIds ||
                        prefs.pinnedHomeSections != pinnedHomeSections
                    )

                hasSeenHomePreferences = true
                enabledHomeSectionTypes = prefs.enabledHomeSectionTypes
                homeSectionOrder = prefs.homeSectionOrder
                hiddenLibrarySectionIds = prefs.hiddenLibrarySectionIds
                mergeContinueWatchingAndNextUp = prefs.mergeContinueWatchingAndNextUp
                nextUpMaxDays = prefs.nextUpMaxDays
                nextUpRewatching = prefs.nextUpRewatching
                nextUpExcludedSeriesIds = prefs.nextUpExcludedSeriesIds
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
                    continueWatchingClickBehavior = prefs.continueWatchingClickBehavior,
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

        launch {
            offlineRepository.getOfflineLibrary().collect { items ->
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
                        _uiState.update { it.copy(searchState = it.searchState.copy(jellyfinResults = emptyList(), seerrResults = emptyList(), isSearching = false)) }
                    } else {
                        searchJob = launch { performSearch(query) }
                    }
                }
        }

        loadInitial()
    }

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
            is HomeUiEvent.PrefetchSeerrDetails -> prefetchSeerrDetails(event.tmdbId, event.mediaType, event.onDone)
            is HomeUiEvent.DismissNewsletterBanner -> _uiState.update { it.copy(newsletterBannerVisible = false) }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    private val _photoFolderChildUrls = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val photoFolderChildUrls: StateFlow<Map<String, List<String>>> = _photoFolderChildUrls

    fun prefetchPhotoFolderChildUrls(items: List<com.raulshma.jellyplay.core.model.MediaItem>) {
        launch {
            val current = _photoFolderChildUrls.value
            items.filter { it.mediaType == com.raulshma.jellyplay.core.model.MediaType.PHOTO_FOLDER && it.id !in current }
                .forEach { folder ->
                    val urls = mediaRepository.getPhotoFolderChildImageUrls(folder.id)
                    _photoFolderChildUrls.value = _photoFolderChildUrls.value + (folder.id to urls)
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

    fun getHomeFocusPosition(): HomeFocusPosition = homeFocusPosition

    fun saveHomeFocusPosition(sectionIndex: Int, itemIndex: Int) {
        homeFocusPosition = HomeFocusPosition(
            sectionIndex = sectionIndex.coerceAtLeast(0),
            itemIndex = itemIndex.coerceAtLeast(0),
        )
    }

    fun resetHomeFocusPosition() {
        homeFocusPosition = HomeFocusPosition()
    }

    private fun loadInitial() {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun refresh() {
        launch {
            _uiState.update { it.copy(isLoading = true) }
            resetHomeScrollPosition()
            resetHomeFocusPosition()
            _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null) }
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun pullToRefresh() {
        launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
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
        launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = SeerrRequestResult(isLoading = true))) }
            seerrRequestDelegate.requestMedia(
                mediaType = event.item.mediaType,
                tmdbId = event.item.id,
                seasons = event.seasons,
                serverId = event.serverId,
                profileId = event.profileId,
                rootFolder = event.rootFolder,
                tags = event.tags,
            ).onSuccess {
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = SeerrRequestResult(success = true))) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = SeerrRequestResult(error = throwable.message ?: "Request failed"))) }
            }
        }
    }

    private fun clearRequestResult() {
        _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = null)) }
    }

    private fun loadSeerrServiceDetails(mediaType: String) {
        launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(isLoadingServices = true)) }
            try {
                val result = seerrRequestDelegate.fetchServiceDetails(mediaType)
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(radarrServers = result.radarrServers, sonarrServers = result.sonarrServers)) }
            } finally {
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(isLoadingServices = false)) }
            }
        }
    }

    private fun loadTvSeasons(tmdbId: Int) {
        launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(tvSeasons = emptyList())) }
            val seasons = seerrRequestDelegate.fetchTvSeasons(tmdbId)
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(tvSeasons = seasons)) }
        }
    }

    private fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        launch {
            seerrRequestDelegate.prefetchDetails(tmdbId, mediaType)
            onDone()
        }
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

    fun excludeSeriesFromNextUp(seriesId: String) {
        launch {
            preferencesStore.excludeSeriesFromNextUp(seriesId)
        }
    }

    private suspend fun fetchAndUpdateSections() {
        if (!refreshMutex.tryLock()) return
        try {
            offlineModeManager.checkNetworkAndAutoDetect()

            if (offlineModeManager.isOffline) {
                lastRefreshTime = System.currentTimeMillis()
                return
            }

            lastRefreshTime = System.currentTimeMillis()
            val enabledSections = enabledHomeSectionTypes
            val hiddenLibIds = hiddenLibrarySectionIds
            mediaRepository.getHomeSections(
                enabledSections,
                hiddenLibIds,
                nextUpRewatching,
                nextUpMaxDays,
                nextUpExcludedSeriesIds,
                pinnedHomeSections,
            )
                .onSuccess { fetchedSections ->
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
                        // Explicit-component broadcast: implicit broadcasts to
                        // manifest-registered receivers are blocked on
                        // Android O+, and the widget's intent-filter only
                        // carries APPWIDGET_UPDATE, so we target the receiver
                        // class directly to guarantee delivery in-process.
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

                    _uiState.update { it.copy(error = null) }
                }
                .onFailure { throwable ->
                    if (_uiState.value.sections.isEmpty()) {
                        _uiState.update { s -> s.copy(error = throwable.message ?: "${throwable::class.simpleName}") }
                    }
                }

            if (_uiState.value.discoverEnabled) {
                fetchDiscoverSections(seerrPreferences)
            }
        } finally {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            refreshMutex.unlock()
        }
    }

    private suspend fun fetchDiscoverSections(prefs: SeerrPreferences) {
        if (!prefs.enabled || !prefs.discoverEnabled) return
        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return

        val today = LocalDate.now(ZoneOffset.systemDefault())
            .atStartOfDay(ZoneOffset.systemDefault())
            .toLocalDate()
            .toString()

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

        _uiState.update { it.copy(discoverSections = newSections) }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = launch {
            while (true) {
                val interval = if (isAppInForeground) REFRESH_INTERVAL_FOREGROUND_MS else REFRESH_INTERVAL_BACKGROUND_MS
                delay(interval)

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
