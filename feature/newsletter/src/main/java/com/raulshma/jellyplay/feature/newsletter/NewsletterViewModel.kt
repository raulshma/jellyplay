package com.raulshma.jellyplay.feature.newsletter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NewsletterViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsletterUiState())
    val uiState: StateFlow<NewsletterUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onEvent(event: NewsletterUiEvent) {
        when (event) {
            NewsletterUiEvent.Refresh -> loadData()
            NewsletterUiEvent.PullToRefresh -> loadData(isPullToRefresh = true)
            NewsletterUiEvent.Dismiss -> markViewed()
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId)

    private fun loadData(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isPullToRefresh,
                    isRefreshing = isPullToRefresh,
                    error = null,
                )
            }

            val sinceDate = LocalDate.now()
                .minusDays(7)
                .atStartOfDay()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            mediaRepository.getNewsletterData(sinceDate)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            serverName = data.serverName,
                            recentlyAdded = data.recentlyAdded,
                            activityDigest = data.activityDigest,
                            libraryStats = data.libraryStats,
                            continueWatching = data.continueWatching,
                            nextUp = data.nextUp,
                            curatedPicks = data.curatedPicks,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                    markViewed()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = e.message ?: "Failed to load newsletter",
                        )
                    }
                }
        }
    }

    private fun markViewed() {
        viewModelScope.launch {
            preferencesStore.setNewsletterLastViewed(System.currentTimeMillis())
        }
    }
}
