package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L
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

    private var lastContinueWatchingIds: Set<String> = emptySet()

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var homeScrollPosition = HomeScrollPosition()

    init {
        // First, listen for user changes 
        viewModelScope.launch {
            var previousUserId: String? = null
            preferencesStore.activeUserId.collect { userId ->
                if (previousUserId != null && previousUserId != userId) {
                    // User switched, force a full refresh with cleared state
                    refreshJob?.cancel()
                    resetHomeScrollPosition()
                    sections = emptyList()
                    favorites = emptyList()
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
            sections = emptyList()
            favorites = emptyList()
            error = null
            fetchAndUpdateSections()
            isLoading = false
            startPeriodicRefresh()
        }
    }

    private suspend fun fetchAndUpdateSections() {
        if (!refreshMutex.tryLock()) return
        try {
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
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                fetchAndUpdateSections()
            }
        }
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
}

data class HomeScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
