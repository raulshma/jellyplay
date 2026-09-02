package com.raulshma.jellyplay.feature.admin.statistics.detail

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.first

@Immutable
data class UserDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: UserDetailPage = UserDetailPage(),
    val pluginStatus: PlaybackReportingStatus = PlaybackReportingStatus.UNKNOWN,
    val currentPage: Int = 0,
    val isLoadingMore: Boolean = false,
)

class UserStatisticsDetailViewModel(
    private val repository: AdminStatisticsRepository,
) : JellyPlayViewModel() {

    private val _state = stateFlow(UserDetailState())
    val state = _state.flow
    private var userId: String = ""

    fun loadUser(userId: String) {
        if (this.userId == userId && _state.value.detail.topItems.isNotEmpty()) return
        this.userId = userId
        _state.set(UserDetailState())
        loadPage(0)
    }

    private fun loadPage(page: Int) {
        launch {
            if (page == 0) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else {
                _state.update { it.copy(isLoadingMore = true) }
            }

            repository.refreshPlaybackReportingStatus()
            val pluginStatus = repository.getPlaybackReportingStatus().first()
            _state.update { it.copy(pluginStatus = pluginStatus) }

            repository.getUserDetailStatistics(userId, page, pageSize = 50)
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            detail = if (page == 0) detail else it.detail.copy(
                                topItems = it.detail.topItems + detail.topItems,
                                hasMoreItems = detail.hasMoreItems,
                            ),
                            currentPage = page,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message,
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.isLoadingMore && current.detail.hasMoreItems) {
            loadPage(current.currentPage + 1)
        }
    }
}
