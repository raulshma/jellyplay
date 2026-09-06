package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.sortedWithCachedKey
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_name
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_plays
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_time

enum class UserStatisticsSort(val labelRes: StringResource) {
    PLAYS(Res.string.user_stats_sort_plays),
    TIME(Res.string.user_stats_sort_time),
    NAME(Res.string.user_stats_sort_name),
}

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
    val sort: UserStatisticsSort = UserStatisticsSort.PLAYS,
    val shareRequested: Boolean = false,
)

class UserStatisticsViewModel(
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

    private fun applySort(users: List<UserStatistics>, sort: UserStatisticsSort): List<UserStatistics> {
        val comparator = when (sort) {
            UserStatisticsSort.PLAYS -> compareByDescending<Pair<UserStatistics, String>> { (user, _) -> user.totalPlayCount }
                .thenByDescending { (user, _) -> user.totalWatchTimeSec }
                .thenBy { (_, nameKey) -> nameKey }
            UserStatisticsSort.TIME -> compareByDescending<Pair<UserStatistics, String>> { (user, _) -> user.totalWatchTimeSec }
                .thenByDescending { (user, _) -> user.totalPlayCount }
                .thenBy { (_, nameKey) -> nameKey }
            UserStatisticsSort.NAME -> compareBy<Pair<UserStatistics, String>> { (_, nameKey) -> nameKey }
                .thenByDescending { (user, _) -> user.totalPlayCount }
        }
        return users.sortedWithCachedKey({ it.userName.lowercase() }, comparator)
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
                            users = applySort(users, it.sort),
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
                            users = applySort(users, it.sort),
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

    fun setSort(sort: UserStatisticsSort) {
        _state.update {
            it.copy(
                sort = sort,
                users = applySort(it.users, sort),
            )
        }
    }

    fun requestExport() {
        _state.update { it.copy(shareRequested = true) }
    }

    fun consumeExportRequest() {
        _state.update { it.copy(shareRequested = false) }
    }
}
