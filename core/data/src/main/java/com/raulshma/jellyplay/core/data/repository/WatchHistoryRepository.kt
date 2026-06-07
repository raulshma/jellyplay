package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class DailyWatchActivity(
    val date: String,
    val value: Long,
)

data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalActiveDays: Int,
)

enum class HeatmapFilter(val label: String, val itemTypes: List<String>?) {
    ALL("All", null),
    VIDEO("Video", listOf("Movie", "Episode", "Series")),
    MUSIC("Music", listOf("Audio")),
}

interface WatchHistoryRepository {
    val playbackReportingStatus: StateFlow<PlaybackReportingStatus>
    suspend fun refreshPlaybackReportingStatus()
    suspend fun getDailyActivity(year: Int, filter: HeatmapFilter): List<DailyWatchActivity>
    suspend fun getItemsForDay(date: String, filter: HeatmapFilter): List<PlaybackReportingDetail>
    suspend fun getPlayedItems(year: Int, filter: HeatmapFilter): List<MediaItem>
}

@Singleton
class WatchHistoryRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : WatchHistoryRepository {

    private val _playbackReportingStatus = MutableStateFlow(PlaybackReportingStatus.UNKNOWN)
    override val playbackReportingStatus: StateFlow<PlaybackReportingStatus> = _playbackReportingStatus.asStateFlow()

    override suspend fun refreshPlaybackReportingStatus() {
        _playbackReportingStatus.value = apiClient.checkPlaybackReportingPlugin()
            .getOrDefault(PlaybackReportingStatus.UNAVAILABLE)
    }

    override suspend fun getDailyActivity(year: Int, filter: HeatmapFilter): List<DailyWatchActivity> {
        val days = if (year == java.time.LocalDate.now().year) {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.of(year, 1, 1),
                java.time.LocalDate.now(),
            ).toInt() + 1
        } else 365

        val filterParam = when (filter) {
            HeatmapFilter.VIDEO -> "Movie,Episode"
            HeatmapFilter.MUSIC -> "Audio"
            HeatmapFilter.ALL -> null
        }

        val points = apiClient.getPlaybackReportingPlayActivity(
            days = days,
            dataType = "count",
            filter = filterParam,
        ).getOrDefault(emptyList())

        return points.map { point ->
            DailyWatchActivity(date = point.date, value = point.value)
        }
    }

    override suspend fun getItemsForDay(date: String, filter: HeatmapFilter): List<PlaybackReportingDetail> {
        val user = apiClient.currentUser.first() ?: return emptyList()
        val filterParam = when (filter) {
            HeatmapFilter.VIDEO -> "Movie,Episode"
            HeatmapFilter.MUSIC -> "Audio"
            HeatmapFilter.ALL -> null
        }
        return apiClient.getPlaybackReportingUserItems(
            userId = user.id,
            date = date,
            filter = filterParam,
        ).getOrDefault(emptyList())
    }

    override suspend fun getPlayedItems(year: Int, filter: HeatmapFilter): List<MediaItem> {
        val user = apiClient.currentUser.first() ?: return emptyList()
        val types = filter.itemTypes
        val allItems = mutableListOf<MediaItem>()
        var startIndex = 0
        val batchSize = 200

        do {
            val result = apiClient.getItemsWithUserData(
                userId = user.id,
                includeItemTypes = types,
                isPlayed = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                startIndex = startIndex,
                limit = batchSize,
            ).getOrDefault(Pair(0, emptyList()))

            allItems.addAll(result.second)
            startIndex += batchSize
        } while (result.second.size == batchSize && result.first > startIndex)

        return allItems
    }
}
