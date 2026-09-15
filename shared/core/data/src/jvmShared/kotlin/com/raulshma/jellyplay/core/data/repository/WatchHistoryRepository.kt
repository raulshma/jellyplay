package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

data class DailyWatchActivity(
    val date: String,
    val value: Long,
)

@Immutable
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
    suspend fun getMinimumActivityDate(): String?
}

class WatchHistoryRepositoryImpl constructor(
    private val apiClient: JellyfinApiClient,
) : WatchHistoryRepository {

    private val _playbackReportingStatus = MutableStateFlow(PlaybackReportingStatus.UNKNOWN)
    override val playbackReportingStatus: StateFlow<PlaybackReportingStatus> = _playbackReportingStatus.asStateFlow()

    /**
     * Memoizes the played-items scan per `(year, filter)`: the paged network
     * scan behind [getPlayedItems] serves both the heatmap grid (through
     * [getDailyActivity]'s fallback) and every day-tap detail sheet (through
     * [getItemsForDay]'s fallback), so each tap on a day re-scanned the whole
     * year page by page. Concurrent callers for one key share a single
     * in-flight fetch (Mutex-guarded in-flight Deferred map — the hand-rolled
     * SingleFlightFetcher core, minus its identity/epoch machinery). Entries
     * live until the next [refreshPlaybackReportingStatus], so a day tap is
     * exactly as stale as the grid load it belongs to.
     */
    private data class PlayedItemsKey(val year: Int, val filter: HeatmapFilter)

    private val playedItemsMutex = Mutex()
    private val playedItemsCache = mutableMapOf<PlayedItemsKey, List<MediaItem>>()
    private val playedItemsInFlight = mutableMapOf<PlayedItemsKey, Deferred<List<MediaItem>>>()
    /** Bumped by [refreshPlaybackReportingStatus]; a flight only stores under its own generation. */
    private var playedItemsGeneration = 0L

    override suspend fun getMinimumActivityDate(): String? {
        val user = apiClient.currentUser.first() ?: return null
        return try {
            val result = apiClient.getItemsWithUserData(
                userId = user.id,
                isPlayed = true,
                sortBy = "DatePlayed",
                sortOrder = "Ascending",
                startIndex = 0,
                limit = 1,
            ).getOrDefault(Pair(0, emptyList()))
            result.second.firstOrNull()?.lastPlayedDate
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun refreshPlaybackReportingStatus() {
        // A new refresh window begins: drop the played-items memo so the grid
        // and day taps re-scan. A flight that started before this point still
        // returns its result to its callers but stores nothing — its
        // generation no longer matches.
        playedItemsMutex.withLock {
            playedItemsGeneration++
            playedItemsCache.clear()
        }
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

        val isPluginAvailable = playbackReportingStatus.value == PlaybackReportingStatus.AVAILABLE
        val points = if (isPluginAvailable) {
            apiClient.getPlaybackReportingPlayActivity(
                days = days,
                dataType = "count",
                filter = filterParam,
            ).getOrDefault(emptyList())
        } else emptyList()

        if (!isPluginAvailable || points.isEmpty()) {
            // Fallback to basic watch history
            val items = getPlayedItems(year, filter)
            val countsByDate = mutableMapOf<String, Long>()
            for (item in items) {
                val lastPlayed = item.lastPlayedDate ?: continue
                val dateStr = lastPlayed.take(10)
                countsByDate[dateStr] = countsByDate.getOrDefault(dateStr, 0L) + item.playCount.coerceAtLeast(1)
            }
            return countsByDate.map { (date, value) ->
                DailyWatchActivity(date = date, value = value)
            }.sortedBy { it.date }
        }

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

        val isPluginAvailable = playbackReportingStatus.value == PlaybackReportingStatus.AVAILABLE
        val details = if (isPluginAvailable) {
            apiClient.getPlaybackReportingUserItems(
                userId = user.id,
                date = date,
                filter = filterParam,
            ).getOrDefault(emptyList())
        } else emptyList()

        if (!isPluginAvailable || details.isEmpty()) {
            // Fallback to basic watch history
            val year = date.take(4).toIntOrNull() ?: java.time.LocalDate.now().year
            val items = getPlayedItems(year, filter)
            val filteredItems = items.filter { item ->
                item.lastPlayedDate?.startsWith(date) == true
            }
            return filteredItems.map { item ->
                val timeStr = item.lastPlayedDate?.let { dateStr ->
                    runCatchingRethrowingCancellation {
                        val parsed = java.time.ZonedDateTime.parse(dateStr)
                        parsed.format(TIME_OF_DAY_FORMATTER)
                    }.getOrElse {
                        runCatchingRethrowingCancellation {
                            val parsed = java.time.LocalDateTime.parse(dateStr)
                            parsed.format(TIME_OF_DAY_FORMATTER)
                        }.getOrDefault("")
                    }
                } ?: ""

                PlaybackReportingDetail(
                    time = timeStr,
                    itemId = item.id,
                    name = item.name,
                    type = item.mediaType.name,
                    // This is a fallback when the Playback Reporting plugin is
                    // unavailable, so the real client/device/method/watch-duration
                    // are genuinely unknown. Reporting fabricated values (a fixed
                    // client/device, DirectPlay, and the content runtime as the
                    // watch duration) is misleading — surface "Unknown" and 0 so
                    // the heatmap/stats don't pretend to a fidelity they lack.
                    client = "Unknown",
                    method = "Unknown",
                    device = "Unknown",
                    duration = 0L,
                )
            }
        }

        return details
    }

    override suspend fun getPlayedItems(year: Int, filter: HeatmapFilter): List<MediaItem> =
        getOrFetchPlayedItems(PlayedItemsKey(year, filter))

    private suspend fun getOrFetchPlayedItems(key: PlayedItemsKey): List<MediaItem> = coroutineScope {
        val deferred: Deferred<List<MediaItem>> = playedItemsMutex.withLock {
            playedItemsCache[key]?.let { return@coroutineScope it }
            val generationAtStart = playedItemsGeneration
            playedItemsInFlight.getOrPut(key) {
                async {
                    try {
                        fetchAndStorePlayedItems(key, generationAtStart)
                    } finally {
                        playedItemsMutex.withLock { playedItemsInFlight.remove(key) }
                    }
                }
            }
        }
        try {
            deferred.await()
        } catch (ce: CancellationException) {
            // The flight is a child of its originator's scope: an awaiter that
            // is not itself cancelled saw the originator die — re-enter the
            // single-flight machinery on this still-alive caller instead of
            // failing the tap (the SingleFlightFetcher cancellation ladder).
            // Re-entering (rather than fetching directly) matters when several
            // awaiters survive: the first through the mutex becomes the new
            // originator and the rest join its flight, instead of every
            // orphaned awaiter firing its own full-year scan concurrently.
            if (coroutineContext[Job]?.isCancelled == true) throw ce
            getOrFetchPlayedItems(key)
        }
    }

    private suspend fun fetchAndStorePlayedItems(key: PlayedItemsKey, generationAtStart: Long): List<MediaItem> {
        val items = fetchPlayedItems(key.year, key.filter)
        if (items.isNotEmpty()) {
            playedItemsMutex.withLock {
                // A failed page surfaces as an empty result (never cached, so a
                // failed scan can't pin an empty day — a genuinely empty year
                // just re-scans per tap, the pre-memo behavior), and a refresh
                // that landed mid-flight vetoes this write via the generation.
                if (playedItemsGeneration == generationAtStart) {
                    playedItemsCache[key] = items
                }
            }
        }
        return items
    }

    private suspend fun fetchPlayedItems(year: Int, filter: HeatmapFilter): List<MediaItem> {
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

            if (result.second.isEmpty()) break

            var reachedEarlierYear = false
            for (item in result.second) {
                val lastPlayed = item.lastPlayedDate ?: continue
                val itemYear = lastPlayed.take(4).toIntOrNull() ?: continue
                if (itemYear == year) {
                    allItems.add(item)
                } else if (itemYear < year) {
                    reachedEarlierYear = true
                }
            }

            if (reachedEarlierYear) break
            startIndex += batchSize
        } while (result.second.size == batchSize && result.first > startIndex)

        return allItems
    }

    companion object {
        private val TIME_OF_DAY_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
