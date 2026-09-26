package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.concurrency.SingleFlight
import com.raulshma.jellyplay.core.data.session.PlaybackReportingStatusStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.TimeSource
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

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
    /**
     * The ONE owner of the Playback Reporting plugin status (a StateFlow +
     * refresh, registered with `SessionCacheRegistry` for identity
     * invalidation) — shared with `AdminStatisticsRepositoryImpl`; see
     * [com.raulshma.jellyplay.core.data.session.PlaybackReportingStatusStore].
     * Replaces this repository's own `_playbackReportingStatus`
     * MutableStateFlow (one of the two independently stale owners the store
     * folded). This repository's refresh keeps ITS OWN side effect — the
     * played-items memo drop below — preserving the memo-clear-THEN-status
     * order.
     */
    private val statusStore: PlaybackReportingStatusStore,
    /**
     * Clock seam for the heatmap's calendar reads (`LocalDate.now()` before
     * D3): the current-year day count and the malformed-date year fallback
     * below — same reads, through the seam, so jvmTest pins a fixed today.
     */
    private val timeSource: TimeSource,
) : WatchHistoryRepository {

    override val playbackReportingStatus: StateFlow<PlaybackReportingStatus> get() = statusStore.status

    /**
     * Memoizes the played-items scan per `(year, filter)`: the paged network
     * scan behind [getPlayedItems] serves both the heatmap grid (through
     * [getDailyActivity]'s fallback) and every day-tap detail sheet (through
     * [getItemsForDay]'s fallback), so each tap on a day re-scanned the whole
     * year page by page. Concurrent callers for one key share a single
     * in-flight fetch through [playedItemsFlight] — the shared
     * [SingleFlight] core, with this memo's rules expressed as its seams:
     * the generation is a private epoch (no identity key — the memo is
     * single-user by construction), and an empty result votes
     * `mayStore = false` so a failed scan can't pin an empty day. Entries
     * live until the next [refreshPlaybackReportingStatus], so a day tap is
     * exactly as stale as the grid load it belongs to.
     *
     * DECLARED DIVERGENCE from the identity-keyed [TtlCache] house idiom
     * (deliberate, not an oversight — converting would change behavior):
     *  - NO TTL. The memo's freshness policy is "as fresh as the grid load
     *    it belongs to" — invalidated only by a plugin-status refresh. A
     *    TtlCache conversion would need a duration, which this policy
     *    deliberately does not have (see FreshnessCeilings' KDoc: the one
     *    cache whose answer to "how stale?" is "no TTL").
     *  - NO identity key and NO registry registration. The cache is a plain
     *    `(year, filter)` map; a user/server switch would serve the previous
     *    identity's memo until the next refresh. The TtlCache+registry
     *    conversion would FIX that (a behavior change, out of scope for this
     *    naming fold — the heatmap reads run after their own
     *    `refreshPlaybackReportingStatus()`, which drops the memo, so the
     *    practical window is a same-screen identity switch).
     */
    private data class PlayedItemsKey(val year: Int, val filter: HeatmapFilter)

    /**
     * Guarded by [playedItemsFlight]'s mutex: read (locked re-check) and
     * written (generation-vetoed store) only inside [SingleFlight]'s
     * sections, cleared inside its [SingleFlight.invalidateAll].
     */
    private val playedItemsCache = mutableMapOf<PlayedItemsKey, List<MediaItem>>()
    private val playedItemsFlight = SingleFlight<PlayedItemsKey, List<MediaItem>>(epoch = AtomicLong(0L))

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
        // and day taps re-scan. The epoch bump + clear run as one
        // [SingleFlight.invalidateAll] section, so a flight that started
        // before this point still returns its result to its callers but
        // stores nothing — its write is either generation-vetoed or wiped by
        // the clear. Order preserved verbatim from the pre-store owner:
        // memo drop FIRST, then the shared store's status refresh.
        playedItemsFlight.invalidateAll { playedItemsCache.clear() }
        statusStore.refresh()
    }

    override suspend fun getDailyActivity(year: Int, filter: HeatmapFilter): List<DailyWatchActivity> {
        // Same read the bare LocalDate.now() made, through the seam (the
        // system impl is LocalDate.now(zone), so identical at runtime).
        val today = timeSource.today(java.time.ZoneId.systemDefault())
        val days = if (year == today.year) {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.of(year, 1, 1),
                today,
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
            // Fallback to basic watch history; a malformed date string falls
            // back to the seam's current year (the old LocalDate.now().year).
            val year = date.take(4).toIntOrNull() ?: timeSource.today(java.time.ZoneId.systemDefault()).year
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

    override suspend fun getPlayedItems(year: Int, filter: HeatmapFilter): List<MediaItem> {
        val key = PlayedItemsKey(year, filter)
        return playedItemsFlight.getOrFetch(
            key = { key },
            // No fastRead: a plain map has no lock-free read, so the memo's
            // first read is the core's locked re-check.
            readCached = { playedItemsCache[it] },
            fetch = {
                // A failed page surfaces as an empty result, and an empty
                // result votes mayStore = false (never cached, so a failed
                // scan can't pin an empty day — a genuinely empty year just
                // re-scans per tap, the pre-memo behavior). The generation
                // veto on top (a refresh landing mid-flight) is the core's.
                val items = fetchPlayedItems(key.year, key.filter)
                items to items.isNotEmpty()
            },
            store = { k, items -> playedItemsCache[k] = items },
        )
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
