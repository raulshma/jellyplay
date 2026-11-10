package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.UserPlayCounts
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.sortedWithCachedKey
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_name
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_plays
import com.raulshma.jellyplay.feature.admin.generated.resources.user_stats_sort_time
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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

    /**
     * The per-user play counts fetched so far this session, keyed by userId.
     * The list read is the repository's aggregate read (see
     * [AdminStatisticsRepository.getAllUsersWithStatistics]); the count
     * fields ride [ensureCountsLoaded] — fetched per visible row, merged into
     * the rows, and REUSED by every later load/refresh (no re-fan-out) for
     * the ViewModel's lifetime.
     */
    private val playCountsCache = mutableMapOf<String, UserPlayCounts>()

    /** UserIds whose count fetch is currently in flight (dedupes re-entries). */
    private val countsInFlight = mutableSetOf<String>()

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

    /**
     * Folds the session's cached counts back into freshly read rows: the
     * aggregate list read carries zero count fields, so without this merge a
     * load/refresh would visually reset the fetched pills to zero and sort
     * the cached totals away. Rows without a cached entry stay zero — their
     * counts are still pending (a visible row's [ensureCountsLoaded] fills
     * them; a failed fetch retries on its next visibility).
     */
    private fun List<UserStatistics>.withCachedCounts(): List<UserStatistics> =
        map { user -> playCountsCache[user.userId]?.mergedInto(user) ?: user }

    private fun publishUsers(users: List<UserStatistics>, settle: (UserStatisticsState) -> UserStatisticsState) {
        val withCounts = users.withCachedCounts()
        _state.update {
            settle(
                it.copy(
                    users = applySort(withCounts, it.sort),
                    totalUsers = withCounts.size,
                    activeThisWeek = withCounts.count { user -> user.isCurrentlyActive },
                    totalPlays = withCounts.sumOf { user -> user.totalPlayCount },
                )
            )
        }
    }

    fun loadStatistics() {
        launch {
            loadInto(
                start = { _state.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    // Status refresh precedes the fetch (its legacy position in
                    // the ladder); its failure modes stay the fetch's own.
                    repository.refreshPlaybackReportingStatus()
                    repository.getAllUsersWithStatistics()
                },
                onSuccess = { users ->
                    publishUsers(users) { state -> state.copy(isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message,
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        launch {
            _state.update { it.copy(isRefreshing = true) }
            repository.refreshPlaybackReportingStatus()
            repository.getAllUsersWithStatistics()
                .onSuccess { users ->
                    // The counts cache is reused as-is (no per-user re-fan-out);
                    // refresh re-reads the aggregate fields only.
                    publishUsers(users) { state -> state.copy(isRefreshing = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isRefreshing = false) }
                }
        }
    }

    /**
     * The visible rows' count trigger: the screen calls this as each row
     * first composes. Cached users return immediately; one fetch per user
     * runs at a time (a repeated call while in flight is a no-op); a failed
     * fetch leaves the row's counts pending and retries on the row's next
     * visibility. Landing counts merge into the row and re-derive the
     * totalPlays summary — the rows do NOT re-sort on the landing (a
     * re-sort mid-scroll would jump rows under the user's finger; the next
     * load/refresh applies the sort over the merged rows).
     */
    fun ensureCountsLoaded(userId: String) {
        if (playCountsCache.containsKey(userId) || !countsInFlight.add(userId)) return
        launch {
            try {
                repository.getUserPlayCounts(userId).onSuccess { counts ->
                    playCountsCache[userId] = counts
                    _state.update { current ->
                        val users = current.users.map { user ->
                            if (user.userId == userId) counts.mergedInto(user) else user
                        }
                        current.copy(
                            users = users,
                            totalPlays = users.sumOf { it.totalPlayCount },
                        )
                    }
                }
            } finally {
                countsInFlight.remove(userId)
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

    /**
     * The CSV export's data pass: exports are rare, explicit user actions, so
     * the exported set's missing counts are fetched here — bounded by the
     * repository's fan-out semaphore (at most four users' reads overlap),
     * cached users ride the session cache. Users whose fetch fails export
     * with zero count columns (the pending render's data); the share request
     * fires either way.
     */
    fun requestExport() {
        launch {
            val missingUserIds = _state.value.users
                .filter { it.userId !in playCountsCache }
                .map { it.userId }
            coroutineScope {
                missingUserIds.map { userId ->
                    async {
                        repository.getUserPlayCounts(userId).onSuccess { counts ->
                            playCountsCache[userId] = counts
                        }
                    }
                }.awaitAll()
            }
            // Re-merge so rows fetched here (and rows fetched since) carry
            // their counts in the exported snapshot.
            val withCounts = _state.value.users.withCachedCounts()
            _state.update {
                it.copy(
                    users = withCounts,
                    totalPlays = withCounts.sumOf { user -> user.totalPlayCount },
                    shareRequested = true,
                )
            }
        }
    }

    fun consumeExportRequest() {
        _state.update { it.copy(shareRequested = false) }
    }
}
