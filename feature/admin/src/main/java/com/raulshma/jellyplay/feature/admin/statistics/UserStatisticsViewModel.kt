package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class UserStatisticsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val users: List<UserStatistics> = emptyList(),
    val totalUsers: Int = 0,
    val activeThisWeek: Int = 0,
    val totalPlays: Int = 0,
    val pluginStatus: PlaybackReportingStatus = PlaybackReportingStatus.UNKNOWN,
)

@HiltViewModel
class UserStatisticsViewModel @Inject constructor(
    private val repository: AdminStatisticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserStatisticsState())
    val state = _state.asStateFlow()

    init {
        loadStatistics()
        observePluginStatus()
    }

    private fun observePluginStatus() {
        viewModelScope.launch {
            repository.getPlaybackReportingStatus().collect { status ->
                _state.value = _state.value.copy(pluginStatus = status)
            }
        }
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.refreshPlaybackReportingStatus()
            repository.getAllUsersWithStatistics()
                .onSuccess { users ->
                    val activeCount = users.count { it.isCurrentlyActive }
                    val totalPlays = users.sumOf { it.totalPlayCount }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        users = users,
                        totalUsers = users.size,
                        activeThisWeek = activeCount,
                        totalPlays = totalPlays,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            repository.refreshPlaybackReportingStatus()
            repository.getAllUsersWithStatistics()
                .onSuccess { users ->
                    val activeCount = users.count { it.isCurrentlyActive }
                    val totalPlays = users.sumOf { it.totalPlayCount }
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        users = users,
                        totalUsers = users.size,
                        activeThisWeek = activeCount,
                        totalPlays = totalPlays,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isRefreshing = false)
                }
        }
    }
}
