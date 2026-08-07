package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NewsletterViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val notificationStore: NotificationStore,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(NewsletterUiState())
    val uiState = _uiState.flow

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
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    private fun loadData(isPullToRefresh: Boolean = false) {
        launch {
            _uiState.update {
                it.copy(
                    isLoading = !isPullToRefresh,
                    isRefreshing = isPullToRefresh,
                    error = null,
                )
            }

            if (isPullToRefresh) {
                mediaRepository.invalidateCaches()
            }

            val sinceDate = LocalDate.now()
                .minusDays(7)
                .atStartOfDay()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            mediaRepository.getNewsletterData(sinceDate)
                .onSuccess { data ->
                    val prefs = notificationStore.notification.value
                    val resolvedOrder = prefs.newsletterSectionOrder
                        .filter { it in prefs.enabledNewsletterSections }
                        .ifEmpty { NewsletterSectionType.DEFAULT_ORDER }
                    _uiState.update {
                        it.copy(
                            serverName = data.serverName,
                            recentlyAdded = data.recentlyAdded,
                            activityDigest = data.activityDigest,
                            libraryStats = data.libraryStats,
                            continueWatching = data.continueWatching,
                            nextUp = data.nextUp,
                            curatedPicks = data.curatedPicks,
                            sectionOrder = resolvedOrder,
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
        launch {
            notificationStore.setNewsletterLastViewed(System.currentTimeMillis())
        }
    }
}
