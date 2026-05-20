package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
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

    var sections by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    var favorites by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var kidsModeEnabled by mutableStateOf(false)
        private set
    var homeMode by mutableStateOf(HomeMode.VIDEO)
        private set
    var dynamicTheming by mutableStateOf(true)
        private set
    var oledMode by mutableStateOf(false)
        private set

    // Seerr Discover state
    var discoverSections by mutableStateOf<Map<DiscoverSectionType, List<SeerrSearchItem>>>(emptyMap())
        private set
    var discoverEnabled by mutableStateOf(false)
        private set
    private var seerrPreferences by mutableStateOf(SeerrPreferences())

    private var lastContinueWatchingIds: Set<String> = emptySet()

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var homeScrollPosition = HomeScrollPosition()
    private var homeFocusPosition = HomeFocusPosition()
    private var lastRefreshTime = 0L
    private var isAppInForeground = true

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // First, listen for user changes 
        viewModelScope.launch {
            var previousUserId: String? = null
            preferencesStore.activeUserId.collect { userId ->
                if (previousUserId != null && previousUserId != userId) {
                    // User switched, force a full refresh with cleared state
                    refreshJob?.cancel()
                    resetHomeScrollPosition()
                    resetHomeFocusPosition()
                    sections = emptyList()
                    favorites = emptyList()
                    discoverSections = emptyMap()
                    error = null
                    isLoading = true
                    fetchAndUpdateSections()
                    isLoading = false
                    startPeriodicRefresh()
                }
                previousUserId = userId
            }
        }
        
        // Listen for preference changes
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                kidsModeEnabled = prefs.kidsModeEnabled
                homeMode = prefs.homeMode
                dynamicTheming = prefs.dynamicTheming
                oledMode = prefs.oledMode
            }
        }

        // Listen for Seerr preference changes
        viewModelScope.launch {
            seerrPreferencesStore.preferences.collect { prefs ->
                val wasEnabled = discoverEnabled
                seerrPreferences = prefs
                discoverEnabled = prefs.enabled && prefs.discoverEnabled
                if (discoverEnabled && !wasEnabled) {
                    fetchDiscoverSections(prefs)
                }
            }
        }
        
        // Finally, load initial data
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            isLoading = true
            error = null
            fetchAndUpdateSections()
            isLoading = false
            startPeriodicRefresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            resetHomeScrollPosition()
            resetHomeFocusPosition()
            sections = emptyList()
            favorites = emptyList()
            discoverSections = emptyMap()
            error = null
            fetchAndUpdateSections()
            isLoading = false
            startPeriodicRefresh()
        }
    }

    private suspend fun fetchAndUpdateSections() {
        if (!refreshMutex.tryLock()) return
        try {
            lastRefreshTime = System.currentTimeMillis()
            val prefs = preferencesStore.preferences.first()
            mediaRepository.getHomeSections()
                .onSuccess { fetchedSections ->
                    val filteredSections = if (prefs.kidsModeEnabled) {
                        fetchedSections.map { section ->
                            section.copy(items = section.items.filter { isAllowedForKids(it, prefs.kidsModeMaxRating) })
                        }.filter { it.items.isNotEmpty() }
                    } else fetchedSections

                    if (this@HomeViewModel.sections != filteredSections) {
                        this@HomeViewModel.sections = filteredSections
                    }

                    val continueWatching = filteredSections
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

                    if (prefs.kidsModeEnabled) {
                        mediaRepository.getFavorites(limit = 20)
                            .onSuccess { result ->
                                val filteredFavorites = result.items.filter { item ->
                                    isAllowedForKids(item, prefs.kidsModeMaxRating)
                                }
                                if (this@HomeViewModel.favorites != filteredFavorites) {
                                    this@HomeViewModel.favorites = filteredFavorites
                                }
                            }
                    }

                    error = null
                }
                .onFailure {
                    if (sections.isEmpty()) {
                        error = it.message ?: "${it::class.simpleName}"
                    }
                }

            // Fetch discover sections if enabled
            if (discoverEnabled) {
                fetchDiscoverSections(seerrPreferences)
            }
        } finally {
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

        if (newSections != discoverSections) {
            discoverSections = newSections
        }
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
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isAppInForeground = false
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        refreshJob?.cancel()
    }

    private fun isAllowedForKids(item: MediaItem, maxRating: String): Boolean {
        if (item.officialRating == null) return true
        val kidRatings = listOf("G", "TV-Y", "TV-Y7", "TV-G", "PG", "TV-PG")
        val maxIndex = kidRatings.indexOf(maxRating)
        val itemIndex = kidRatings.indexOf(item.officialRating)
        return if (itemIndex >= 0 && maxIndex >= 0) itemIndex <= maxIndex else true
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

    // ── Seerr request support ──

    private val _requestResult = MutableStateFlow<DiscoverRequestResult?>(null)
    val requestResult: StateFlow<DiscoverRequestResult?> = _requestResult.asStateFlow()

    private val _radarrServers = MutableStateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> = _radarrServers.asStateFlow()

    private val _sonarrServers = MutableStateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> = _sonarrServers.asStateFlow()

    private val _isLoadingSeerrServices = MutableStateFlow(false)
    val isLoadingSeerrServices: StateFlow<Boolean> = _isLoadingSeerrServices.asStateFlow()

    private val _tvSeasons = MutableStateFlow<List<SeerrSeason>>(emptyList())
    val tvSeasons: StateFlow<List<SeerrSeason>> = _tvSeasons.asStateFlow()

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        viewModelScope.launch {
            _requestResult.value = DiscoverRequestResult(isLoading = true)
            seerrRepository.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _requestResult.value = DiscoverRequestResult(success = true)
            }.onFailure {
                _requestResult.value = DiscoverRequestResult(error = it.message ?: "Request failed")
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    fun loadSeerrServiceDetails(mediaType: String) {
        viewModelScope.launch {
            _isLoadingSeerrServices.value = true
            try {
                if (mediaType == "movie") {
                    seerrRepository.getServiceRadarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceRadarrDetail(server.id).getOrNull()
                        }
                        _radarrServers.value = details
                    }
                } else {
                    seerrRepository.getServiceSonarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceSonarrDetail(server.id).getOrNull()
                        }
                        _sonarrServers.value = details
                    }
                }
            } finally {
                _isLoadingSeerrServices.value = false
            }
        }
    }

    fun loadTvSeasons(tmdbId: Int) {
        viewModelScope.launch {
            _tvSeasons.value = emptyList()
            seerrRepository.getTvDetails(tmdbId).onSuccess { details ->
                _tvSeasons.value = details.seasons.filter { it.seasonNumber > 0 }
            }
        }
    }

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (mediaType == "movie") {
                    seerrRepository.getMovieDetails(tmdbId)
                } else {
                    seerrRepository.getTvDetails(tmdbId)
                }
                val type = if (mediaType == "movie") com.raulshma.jellyplay.core.model.MediaType.MOVIE else com.raulshma.jellyplay.core.model.MediaType.SERIES
                launch { seerrRepository.getRatings(tmdbId, mediaType) }
                launch { seerrRepository.getRecommendations(tmdbId, type) }
                launch { seerrRepository.getSimilar(tmdbId, type) }
            } catch (_: Exception) {
                // Detail screen will retry on failure
            }
            onDone()
        }
    }
}

data class HomeScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

data class HomeFocusPosition(
    val sectionIndex: Int = 0,
    val itemIndex: Int = 0,
)

data class DiscoverRequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)
