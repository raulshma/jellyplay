package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.data.repository.StreakInfo
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class WatchProgressHeatmapUiState(
    val isLoading: Boolean = true,
    val year: Int = LocalDate.now().year,
    val filter: HeatmapFilter = HeatmapFilter.ALL,
    val dailyActivities: List<DailyWatchActivity> = emptyList(),
    val streakInfo: StreakInfo = StreakInfo(0, 0, 0),
    val selectedDay: SelectedDayInfo? = null,
    val shareRequested: Boolean = false,
    val isPluginAvailable: Boolean = false,
    val minActivityDate: LocalDate? = null,
    val error: String? = null,
)

@Immutable
data class SelectedDayInfo(
    val date: LocalDate,
    val dateLabel: String,
    val sessions: List<PlaybackReportingDetail>,
    val resolvedItems: Map<String, ResolvedMediaItem>,
)

@Immutable
data class ResolvedMediaItem(
    val name: String,
    val mediaType: MediaType,
    val imageUrl: String?,
)

sealed interface HeatmapEvent {
    data class SetYear(val year: Int) : HeatmapEvent
    data class SetFilter(val filter: HeatmapFilter) : HeatmapEvent
    data class SelectDay(val date: LocalDate?) : HeatmapEvent
    data object DismissDayDetail : HeatmapEvent
    data object RequestShare : HeatmapEvent
    data object ShareConsumed : HeatmapEvent
}

class WatchProgressHeatmapViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private companion object {
        val SELECTED_DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    }

    private val _uiState = stateFlow(WatchProgressHeatmapUiState())
    val uiState: StateFlow<WatchProgressHeatmapUiState> = _uiState.flow

    private var cachedResolvedItems = mutableMapOf<String, ResolvedMediaItem>()

    init {
        launch {
            watchHistoryRepository.refreshPlaybackReportingStatus()
            val minDateStr = watchHistoryRepository.getMinimumActivityDate()
            val minDate = minDateStr?.take(10)?.let {
                runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            }
            _uiState.update { it.copy(minActivityDate = minDate) }
            loadHeatmapData()
        }
    }

    fun onEvent(event: HeatmapEvent) {
        when (event) {
            is HeatmapEvent.SetYear -> {
                _uiState.update { it.copy(year = event.year, isLoading = true) }
                loadHeatmapData()
            }
            is HeatmapEvent.SetFilter -> {
                _uiState.update { it.copy(filter = event.filter, isLoading = true, selectedDay = null) }
                loadHeatmapData()
            }
            is HeatmapEvent.SelectDay -> selectDay(event.date)
            is HeatmapEvent.DismissDayDetail -> _uiState.update { it.copy(selectedDay = null) }
            is HeatmapEvent.RequestShare -> _uiState.update { it.copy(shareRequested = true) }
            is HeatmapEvent.ShareConsumed -> _uiState.update { it.copy(shareRequested = false) }
        }
    }

    /** Force a fresh fetch (e.g. pull-to-refresh or error retry). */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadHeatmapData()
    }

    private fun loadHeatmapData() {
        launch {
            val state = _uiState.value
            val year = state.year
            val filter = state.filter

            try {
                val activities = watchHistoryRepository.getDailyActivity(year, filter)
                val streaks = calculateStreaks(activities)

                val isPluginAvailable = watchHistoryRepository.playbackReportingStatus.value ==
                    com.raulshma.jellyplay.core.model.PlaybackReportingStatus.AVAILABLE

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dailyActivities = activities,
                        streakInfo = streaks,
                        isPluginAvailable = isPluginAvailable,
                        error = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: e.message ?: "",
                    )
                }
            }
        }
    }

    private fun selectDay(date: LocalDate?) {
        if (date == null) {
            _uiState.update { it.copy(selectedDay = null) }
            return
        }
        launch {
            val state = _uiState.value
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val sessions = watchHistoryRepository.getItemsForDay(dateStr, state.filter)

            resolveItems(sessions.map { it.itemId }.distinct())

            _uiState.update {
                it.copy(
                    selectedDay = SelectedDayInfo(
                        date = date,
                        dateLabel = date.format(SELECTED_DAY_LABEL_FORMAT),
                        sessions = sessions,
                        resolvedItems = cachedResolvedItems.toMap(),
                    ),
                )
            }
        }
    }

    private suspend fun resolveItems(itemIds: List<String>) {
        val unresolved = itemIds.filter { it !in cachedResolvedItems }
        for (itemId in unresolved) {
            val detail = mediaRepository.getMediaDetail(itemId).getOrNull() ?: continue
            cachedResolvedItems[itemId] = ResolvedMediaItem(
                name = detail.item.name,
                mediaType = detail.item.mediaType,
                imageUrl = playbackRepository.getImageUrl(itemId, "Primary", 200),
            )
        }
    }

    private fun calculateStreaks(activities: List<DailyWatchActivity>): StreakInfo {
        if (activities.isEmpty()) return StreakInfo(0, 0, 0)

        val activeDates = activities
            .filter { it.value > 0 }
            .mapNotNull {
                runCatching { LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            }
            .distinct()
            .sorted()

        if (activeDates.isEmpty()) return StreakInfo(0, 0, 0)

        var longestStreak = 0
        var tempStreak = 1

        for (i in 1 until activeDates.size) {
            if (activeDates[i] == activeDates[i - 1].plusDays(1)) {
                tempStreak++
            } else {
                if (tempStreak > longestStreak) longestStreak = tempStreak
                tempStreak = 1
            }
        }
        if (tempStreak > longestStreak) longestStreak = tempStreak

        val today = LocalDate.now()
        var currentStreak = 0
        var checkDate = today
        while (checkDate in activeDates) {
            currentStreak++
            checkDate = checkDate.minusDays(1)
        }

        return StreakInfo(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalActiveDays = activeDates.size,
        )
    }
}
