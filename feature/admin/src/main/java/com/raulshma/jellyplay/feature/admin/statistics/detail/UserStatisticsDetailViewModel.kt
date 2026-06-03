package com.raulshma.jellyplay.feature.admin.statistics.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserDetailPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class UserDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: UserDetailPage = UserDetailPage(),
    val pluginStatus: PlaybackReportingStatus = PlaybackReportingStatus.UNKNOWN,
    val currentPage: Int = 0,
    val isLoadingMore: Boolean = false,
)

@HiltViewModel
class UserStatisticsDetailViewModel @Inject constructor(
    private val repository: AdminStatisticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserDetailState())
    val state = _state.asStateFlow()
    private var userId: String = ""

    fun loadUser(userId: String) {
        if (this.userId == userId && _state.value.detail.topItems.isNotEmpty()) return
        this.userId = userId
        _state.value = UserDetailState()
        loadPage(0)
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            if (page == 0) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            } else {
                _state.value = _state.value.copy(isLoadingMore = true)
            }

            repository.refreshPlaybackReportingStatus()
            _state.value = _state.value.copy(pluginStatus = repository.getPlaybackReportingStatus().first())

            repository.getUserDetailStatistics(userId, page, pageSize = 50)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        detail = if (page == 0) detail else _state.value.detail.copy(
                            topItems = _state.value.detail.topItems + detail.topItems,
                            hasMoreItems = detail.hasMoreItems,
                        ),
                        currentPage = page,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message,
                    )
                }
        }
    }

    fun loadMore() {
        if (!_state.value.isLoadingMore && _state.value.detail.hasMoreItems) {
            loadPage(_state.value.currentPage + 1)
        }
    }
}
