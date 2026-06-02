package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val preferencesStore: UserPreferencesStore,
    private val seerrRepository: SeerrRepository,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel(), DefaultLifecycleObserver {

    companion object {
        private const val REFRESH_INTERVAL_FOREGROUND_MS = 60_000L
        private const val REFRESH_INTERVAL_BACKGROUND_MS = 120_000L
        private const val MIN_REFRESH_INTERVAL_MS = 30_000L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var enabledHomeSectionTypes = HomeSectionType.CONFIGURABLE.toSet()
    private var homeSectionOrder = HomeSectionType.CONFIGURABLE
    private var hiddenLibrarySectionIds = emptySet<String>()
    private var lastContinueWatchingIds: Set<String> = emptySet()
    private var seerrPreferences = SeerrPreferences()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var homeScrollPosition = HomeScrollPosition()
    private var homeFocusPosition = HomeFocusPosition()
    private var lastRefreshTime = 0L
    private var isAppInForeground = true

    private val searchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        viewModelScope.launch {
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

        viewModelScope.launch {
            var hasSeenHomePreferences = false
            preferencesStore.preferences.collect { prefs ->
                val homeSectionPrefsChanged = hasSeenHomePreferences && (
                    prefs.enabledHomeSectionTypes != enabledHomeSectionTypes ||
                        prefs.homeSectionOrder != homeSectionOrder ||
                        prefs.hiddenLibrarySectionIds != hiddenLibrarySectionIds
                    )

                hasSeenHomePreferences = true
                enabledHomeSectionTypes = prefs.enabledHomeSectionTypes
                homeSectionOrder = prefs.homeSectionOrder
                hiddenLibrarySectionIds = prefs.hiddenLibrarySectionIds
                _uiState.update { it.copy(
                    homeMode = prefs.homeMode,
                    dynamicTheming = prefs.dynamicTheming,
                    oledMode = prefs.oledMode,
                    homeHeroEnabled = prefs.homeHeroEnabled,
                ) }

                if (homeSectionPrefsChanged) {
                    fetchAndUpdateSections()
                }
            }
        }

        viewModelScope.launch {
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

        viewModelScope.launch {
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

        viewModelScope.launch {
            offlineRepository.getOfflineLibrary().collect { items ->
                _uiState.update { it.copy(offlineLibrary = items) }
            }
        }

        viewModelScope.launch {
            @OptIn(FlowPreview::class)
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
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            resetHomeScrollPosition()
            resetHomeFocusPosition()
            _uiState.update { it.copy(sections = emptyList(), favorites = emptyList(), discoverSections = emptyMap(), error = null) }
            fetchAndUpdateSections()
            startPeriodicRefresh()
        }
    }

    private fun pullToRefresh() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = DiscoverRequestResult(isLoading = true))) }
            seerrRepository.requestMedia(
                mediaType = event.item.mediaType,
                tmdbId = event.item.id,
                seasons = event.seasons,
                serverId = event.serverId,
                profileId = event.profileId,
                rootFolder = event.rootFolder,
                tags = event.tags,
            ).onSuccess {
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = DiscoverRequestResult(success = true))) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = DiscoverRequestResult(error = throwable.message ?: "Request failed"))) }
            }
        }
    }

    private fun clearRequestResult() {
        _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(result = null)) }
    }

    private fun loadSeerrServiceDetails(mediaType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(isLoadingServices = true)) }
            try {
                if (mediaType == "movie") {
                    seerrRepository.getServiceRadarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceRadarrDetail(server.id).getOrNull()
                        }
                        _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(radarrServers = details)) }
                    }
                } else {
                    seerrRepository.getServiceSonarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceSonarrDetail(server.id).getOrNull()
                        }
                        _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(sonarrServers = details)) }
                    }
                }
            } finally {
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(isLoadingServices = false)) }
            }
        }
    }

    private fun loadTvSeasons(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(tvSeasons = emptyList())) }
            seerrRepository.getTvDetails(tmdbId).onSuccess { details ->
                _uiState.update { it.copy(seerrRequestState = it.seerrRequestState.copy(tvSeasons = details.seasons.filter { it.seasonNumber > 0 })) }
            }
        }
    }

    private fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                coroutineScope {
                    if (mediaType == "movie") {
                        seerrRepository.getMovieDetails(tmdbId)
                    } else {
                        seerrRepository.getTvDetails(tmdbId)
                    }
                    val type = if (mediaType == "movie") com.raulshma.jellyplay.core.model.MediaType.MOVIE else com.raulshma.jellyplay.core.model.MediaType.SERIES
                    launch { seerrRepository.getRatings(tmdbId, mediaType) }
                    launch { seerrRepository.getRecommendations(tmdbId, type) }
                    launch { seerrRepository.getSimilar(tmdbId, type) }
                }
            } catch (_: Exception) {
            }
            onDone()
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
            mediaRepository.getHomeSections(enabledSections, hiddenLibIds)
                .onSuccess { fetchedSections ->
                    val orderIndex = homeSectionOrder.withIndex().associate { it.value to it.index }
                    val orderedSections = fetchedSections
                        .mapIndexed { index, section -> index to section }
                        .sortedWith(
                            compareBy<Pair<Int, com.raulshma.jellyplay.core.model.HomeSection>> {
                                orderIndex[it.second.type] ?: Int.MAX_VALUE
                            }.thenBy { it.first },
                        )
                        .map { it.second }

                    _uiState.update { it.copy(sections = orderedSections) }

                    val continueWatching = orderedSections
                        .find { it.type == HomeSectionType.CONTINUE_WATCHING }
                        ?.items ?: emptyList()
                    val currentIds = continueWatching.map { it.id }.toSet()
                    if (currentIds != lastContinueWatchingIds) {
                        lastContinueWatchingIds = currentIds
                        preferencesStore.setContinueWatching(continueWatching)
                        val intent = android.content.Intent("com.raulshma.jellyplay.widget.ACTION_REFRESH_CONTINUE_WATCHING")
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
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

        val today = LocalDate.now(ZoneOffset.systemDefault())
            .atStartOfDay(ZoneOffset.systemDefault())
            .toLocalDate()
            .toString()

        val deferredResults = mutableListOf<Pair<DiscoverSectionType, kotlinx.coroutines.Deferred<Result<SeerrSearchResponse>>>>()

        if (prefs.discoverTrending) {
            deferredResults.add(DiscoverSectionType.TRENDING to viewModelScope.async { seerrRepository.getTrending() })
        }
        if (prefs.discoverPopularMovies) {
            deferredResults.add(DiscoverSectionType.POPULAR_MOVIES to viewModelScope.async { seerrRepository.getDiscoverMovies() })
        }
        if (prefs.discoverPopularTv) {
            deferredResults.add(DiscoverSectionType.POPULAR_TV to viewModelScope.async { seerrRepository.getDiscoverTv() })
        }
        if (prefs.discoverUpcomingMovies) {
            deferredResults.add(DiscoverSectionType.UPCOMING_MOVIES to viewModelScope.async { seerrRepository.getDiscoverMovies(primaryReleaseDateGte = today) })
        }
        if (prefs.discoverUpcomingTv) {
            deferredResults.add(DiscoverSectionType.UPCOMING_TV to viewModelScope.async { seerrRepository.getDiscoverTv(firstAirDateGte = today) })
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
        refreshJob = viewModelScope.launch {
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
        viewModelScope.launch {
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
        startPeriodicRefresh()
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
                        val connected = seerrRepository.isConnected().first()
                        val enabled = seerrRepository.isSearchEnabled().first()
                        if (connected && enabled) {
                            seerrRepository.search(query).getOrNull()?.results?.take(8)
                        } else null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                jellyfinDeferred.await().onSuccess { result ->
                    _uiState.update { it.copy(searchState = it.searchState.copy(jellyfinResults = result.items)) }
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
