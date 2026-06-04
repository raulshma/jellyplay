package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : JellyPlayViewModel() {

    private val _state = stateFlow(UserStatisticsState())
    val state = _state.flow

    init {
        loadStatistics()
        observePluginStatus()
    }

    private fun observePluginStatus() {
        launch {
            repository.getPlaybackReportingStatus().collect { status ->
                _state.update { it.copy(pluginStatus = status) }
            }
        }
    }

    fun loadStatistics() {
        launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.refreshPlaybackReportingStatus()
            repository.getAllUsersWithStatistics()
                .onSuccess { users ->
                    val activeCount = users.count { it.isCurrentlyActive }
                    val totalPlays = users.sumOf { it.totalPlayCount }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            users = users,
                            totalUsers = users.size,
                            activeThisWeek = activeCount,
                            totalPlays = totalPlays,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message,
                        )
                    }
                }
        }
    }

    fun refresh() {
        launch {
            _state.update { it.copy(isRefreshing = true) }
            repository.refreshPlaybackReportingStatus()
            repository.getAllUsersWithStatistics()
                .onSuccess { users ->
                    val activeCount = users.count { it.isCurrentlyActive }
                    val totalPlays = users.sumOf { it.totalPlayCount }
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            users = users,
                            totalUsers = users.size,
                            activeThisWeek = activeCount,
                            totalPlays = totalPlays,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isRefreshing = false) }
                }
        }
    }
}
