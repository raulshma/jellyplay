package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.session.PlaybackReportingStatusStore
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingActivity
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AdminStatisticsRepositoryImpl constructor(
    private val apiClient: JellyfinApiClient,
    private val auditLogDao: AuditLogDao,
    private val scanStateDao: ScanStateDao,
    private val json: Json,
    /**
     * Shared application scope for the fire-and-forget background scans
     * (handed to [MediaCleanupScanCore]; the application-scope Koin single —
     * never cancelled for this singleton, matching the scan jobs' previous
     * dedicated-scope lifetime).
     */
    private val scope: CoroutineScope,
    /**
     * Locale-aware user-visible labels for the stale/watched-media scan rows
     * and the per-user type breakdown. The repository persists these strings
     * (scan results land in Room as JSON), so the seam keeps formatting at the
     * platform edge instead of hardcoding English in shared code. Android
     * def: the app composition root over legacy core:data R.string (all 9
     * locales); desktop def: base-locale English literals
     * ([DesktopAdminStatisticsLabels] — English-only, the downloads-string
     * precedent).
     */
    private val labels: AdminStatisticsLabelProvider,
    /**
     * Clock seam for the audit-log retention window (the 90-day
     * `deleteOlderThan` cutoff) and the cleanup-entry `timestamp` stamp —
     * injectable so a fake clock pins the retention decision in tests.
     * Genuinely shared with [MediaCleanupScanCore] (one instance, both
     * halves — the scan-side date math and this half's chart/streak windows).
     */
    private val timeSource: TimeSource,
    /**
     * The ONE owner of the Playback Reporting plugin status (a
     * `StateFlow` + refresh, registered with `SessionCacheRegistry` for
     * identity invalidation) — shared with `WatchHistoryRepositoryImpl`;
     * see [PlaybackReportingStatusStore]. Replaces this repository's own
     * `_pluginStatus` MutableStateFlow (one of the two independently stale
     * owners the store folded).
     */
    private val playbackReportingStatusStore: PlaybackReportingStatusStore,
) : AdminStatisticsRepository {

    /**
     * The media-cleanup scan/audit half of this repository (the
     * [DownloadSidecarCore] shape): both cleanup scans, their
     * [MediaCleanupScanCore.runScan] chassis, the progress/result reads, and
     * the audit-log family. The six
     * scan-side interface members below delegate one-to-one — see
     * [MediaCleanupScanCore]'s KDoc for the ownership split and the
     * shared-input decisions.
     */
    private val scanCore = MediaCleanupScanCore(
        apiClient = apiClient,
        auditLogDao = auditLogDao,
        scanStateDao = scanStateDao,
        json = json,
        scope = scope,
        labels = labels,
        timeSource = timeSource,
    )

    /**
     * Bounds concurrency of the per-user statistics fan-out so a server with
     * many users does not fire N×4 simultaneous requests. Mirrors the
     * [kotlinx.coroutines.sync.Semaphore] pattern in `ArrRepositoryImpl`.
     */
    private val statsSemaphore = Semaphore(4)

    /**
     * The plugin status is owned by the shared [PlaybackReportingStatusStore]
     * single — the ONE StateFlow + refresh for "is Playback Reporting
     * installed", cited by this repository AND [WatchHistoryRepositoryImpl]
     * (they used to be two independently stale flows). Delegates below read
     * the store's flow; this repository's `refreshPlaybackReportingStatus`
     * triggers the store's refresh and then keeps ITS OWN side effect (the
     * 90-day audit-log prune), preserving the refresh-then-prune order.
     */
    private val pluginStatus: StateFlow<PlaybackReportingStatus> get() = playbackReportingStatusStore.status

    override fun getPlaybackReportingStatus(): StateFlow<PlaybackReportingStatus> = pluginStatus

    override suspend fun refreshPlaybackReportingStatus() {
        playbackReportingStatusStore.refresh()
        scanCore.cleanupOldAuditLogs()
    }

    /**
     * The plugin-gate fold shared by every plugin-derived list fetch —
     * `if (pluginAvailable) call().getOrDefault(emptyList()) else emptyList()`
     * used to appear inline at each site, hand-syncing the AVAILABLE check
     * against the plugin-status flow. Takes the CALLER-CAPTURED flag, not a live
     * read: a page's gates must stay internally consistent — the detail
     * page's group gate and its member fetches see ONE status even if an
     * admin refresh flips the status mid-load (the captured-local
     * semantics the inline ladders had). [call] is a plain (non-suspend)
     * lambda parameter invoked from the inline body, so suspend api calls
     * are legal at each call site.
     */
    private suspend inline fun <T> whenPlugin(available: Boolean, call: () -> Result<List<T>>): List<T> =
        if (available) {
            call().getOrDefault(emptyList())
        } else {
            emptyList()
        }

    override suspend fun getAllUsersWithStatistics(): Result<List<UserStatistics>> = runCatchingRethrowingCancellation {
        // One capture for the whole page — see [whenPlugin]'s KDoc.
        val pluginAvailable = pluginStatus.value == PlaybackReportingStatus.AVAILABLE
        coroutineScope {
            val usersDeferred = async { apiClient.getUsers().getOrThrow() }
            val sessionsDeferred = async { apiClient.getSessions().getOrDefault(emptyList()) }
            val pluginDeferred = async { whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingUserActivity(days = 30) } }

            val users = usersDeferred.await()
            val activeUserIds = sessionsDeferred.await().map { it.userId }.toSet()
            val pluginMap = pluginDeferred.await().associateBy { it.userId }

            statsSemaphore.mapConcurrent(users) { user ->
                buildUserStatistics(
                    user = user,
                    isActive = activeUserIds.contains(user.id),
                    totalWatchTimeSec = pluginMap[user.id]?.totalTime ?: 0L,
                )
            }
        }
    }

    /** Played/unplayed counters fetched concurrently for one user. */
    private data class UserPlayCounts(
        val moviePlayed: Int,
        val episodePlayed: Int,
        val songPlayed: Int,
        val movieUnplayed: Int,
    )

    private suspend fun fetchUserPlayCounts(userId: String): UserPlayCounts = coroutineScope {
        val movieDeferred = async { apiClient.getUserPlayedItemCount(userId, listOf("Movie")).getOrDefault(0) }
        val episodeDeferred = async { apiClient.getUserPlayedItemCount(userId, listOf("Episode")).getOrDefault(0) }
        val songDeferred = async { apiClient.getUserPlayedItemCount(userId, listOf("Audio")).getOrDefault(0) }
        val movieUnplayedDeferred = async { apiClient.getUserUnplayedItemCount(userId, listOf("Movie")).getOrDefault(0) }
        UserPlayCounts(
            moviePlayed = movieDeferred.await(),
            episodePlayed = episodeDeferred.await(),
            songPlayed = songDeferred.await(),
            movieUnplayed = movieUnplayedDeferred.await(),
        )
    }

    /**
     * The ONE [UserStatistics] builder for both pages — the per-user list and
     * the detail page's `statistics` field (an inline copy of this body that
     * had already drifted once: it omitted `isCurrentlyActive` and had its
     * own verbatim completion-rate math). The completion rate
     * (`movieTotal = unplayed + played; played/total else 0`) lives here
     * exactly once.
     *
     *  - [isActive] is the session-derived "currently playing" flag. Only
     *    the LIST path has a session source; the detail page has never
     *    fetched sessions and nothing on it renders the field (the active
     *    badge and the active-count header are list-screen reads), so the
     *    detail path deliberately takes the `false` default — preserving the
     *    pre-builder detail output exactly.
     *  - [totalWatchTimeSec] is the already-resolved watch time: the list
     *    path passes the plugin's per-user total (`?: 0L`), the detail path
     *    its plugin-then-computed fallback ladder.
     *  - [counts] lets the detail path reuse the [UserPlayCounts] it already
     *    fetched for its type breakdown instead of re-firing the four-call
     *    fan-out; null fetches here.
     */
    private suspend fun buildUserStatistics(
        user: JellyfinUser,
        isActive: Boolean = false,
        totalWatchTimeSec: Long = 0L,
        counts: UserPlayCounts? = null,
    ): UserStatistics {
        val stats = counts ?: fetchUserPlayCounts(user.id)
        val moviePlayed = stats.moviePlayed
        val episodePlayed = stats.episodePlayed
        val songPlayed = stats.songPlayed
        val movieTotal = stats.movieUnplayed + moviePlayed
        val completionRate = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

        return UserStatistics(
            userId = user.id,
            userName = user.name,
            userAvatarTag = user.primaryImageTag,
            isAdmin = user.isAdmin,
            totalPlayCount = moviePlayed + episodePlayed + songPlayed,
            moviePlayCount = moviePlayed,
            episodePlayCount = episodePlayed,
            songPlayCount = songPlayed,
            totalWatchTimeSec = totalWatchTimeSec,
            lastSeen = user.lastActivityDate,
            completionRate = completionRate,
            isCurrentlyActive = isActive,
        )
    }

    override suspend fun getUserDetailStatistics(userId: String, page: Int, pageSize: Int): Result<UserDetailPage> = runCatchingRethrowingCancellation {
        // One capture for the whole page (every gate below reads it — the
        // group gate's non-null deferred bundle IS itself the downstream
        // gate, so the members must see the SAME flag; see [whenPlugin]).
        val pluginAvailable = pluginStatus.value == PlaybackReportingStatus.AVAILABLE

        // User lookup, played page, and plugin chart are independent round-trips
        // — run them concurrently (was: full getUsers() scan + sequential tail
        // paying sum-of-latencies). The per-user endpoint replaces the list scan.
        val user: JellyfinUser
        val playedResult: Pair<Int, List<com.raulshma.jellyplay.core.model.MediaItem>>
        val pluginChart: List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint>
        coroutineScope {
            val userDeferred = async {
                apiClient.getUserById(userId).getOrNull() ?: JellyfinUser(id = userId)
            }
            val playedDeferred = async {
                apiClient.getItemsWithUserData(
                    userId = userId,
                    isPlayed = true,
                    sortBy = "PlayCount",
                    sortOrder = "Descending",
                    startIndex = page * pageSize,
                    limit = pageSize,
                ).getOrDefault(Pair(0, emptyList()))
            }
            val pluginChartDeferred = async {
                whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingPlayActivity(days = 30, dataType = "count", filter = userId) }
            }
            user = userDeferred.await()
            playedResult = playedDeferred.await()
            pluginChart = pluginChartDeferred.await()
        }

        val topItems = playedResult.second.map { item ->
            com.raulshma.jellyplay.core.model.UserTopItem(
                itemId = item.id,
                name = item.name,
                type = item.mediaType.name,
                playCount = item.playCount,
                lastPlayedDate = null,
                posterBlurHash = item.blurHashes.primary,
                seriesName = item.seriesName,
                runtimeTicks = item.runTimeTicks ?: 0,
            )
        }

        // The fallback items list depends on pluginChart's outcome; the four
        // counts don't depend on anything — overlap both groups.
        val fallbackItems: List<com.raulshma.jellyplay.core.model.MediaItem>
        val counts: UserPlayCounts
        coroutineScope {
            val fallbackDeferred = async {
                if (pluginChart.isEmpty() || pluginChart.all { it.value == 0L }) {
                    apiClient.getItemsWithUserData(
                        userId = userId,
                        isPlayed = true,
                        sortBy = "DatePlayed",
                        sortOrder = "Descending",
                        startIndex = 0,
                        limit = 300,
                    ).getOrDefault(Pair(0, emptyList())).second
                } else emptyList()
            }
            val countsDeferred = async { fetchUserPlayCounts(userId) }
            fallbackItems = fallbackDeferred.await()
            counts = countsDeferred.await()
        }
        val fallbackActivityChart = if (fallbackItems.isNotEmpty()) buildFallbackActivityChart(fallbackItems) else null
        val fallbackChart = fallbackActivityChart ?: pluginChart
        val fallbackTrendData = fallbackActivityChart ?: emptyList()
        val moviePlayedCount = counts.moviePlayed
        val episodePlayedCount = counts.episodePlayed
        val songPlayedCount = counts.songPlayed

        val typeBreakdown = listOf(
            ContentBreakdown(
                label = labels.movies(),
                value = moviePlayedCount.toLong(),
                colorIndex = 0,
            ),
            ContentBreakdown(
                label = labels.episodes(),
                value = episodePlayedCount.toLong(),
                colorIndex = 1,
            ),
            ContentBreakdown(
                label = labels.songs(),
                value = songPlayedCount.toLong(),
                colorIndex = 2,
            ),
        ).filter { it.value > 0 }

        // Breakdowns and the enhanced batch launch together: none of the
        // enhanced calls depends on breakdown results (only the local math
        // after the awaits does), so overlapping removes a full sequential
        // round-trip batch from the detail-page load.
        val breakdowns: BreakdownResults
        val enhancedDeferreds: EnhancedDeferreds?
        coroutineScope {
            val genreDeferred = async { whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingBreakdown("Genre", days = 30, filter = userId) } }
            val methodDeferred = async { whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingBreakdown("PlaybackMethod", days = 30, filter = userId) } }
            val deviceDeferred = async { whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingBreakdown("ClientName", days = 30, filter = userId) } }
            val activityDeferred = async { whenPlugin(pluginAvailable) { apiClient.getPlaybackReportingUserActivity(days = 30) } }
            val watchDeferred = async { computeWatchTimeBreakdown(userId) }
            enhancedDeferreds = if (pluginAvailable) {
                EnhancedDeferreds(
                    weeklyActivity = async {
                        apiClient.getPlaybackReportingUserActivity(days = 7).getOrDefault(emptyList())
                    },
                    sixMonthCount = async {
                        apiClient.getPlaybackReportingPlayActivity(days = 180, dataType = "count", filter = userId)
                            .getOrDefault(emptyList())
                    },
                    musicGenreBreakdown = async {
                        apiClient.getPlaybackReportingBreakdown("Genre", days = 30, filter = "$userId,Audio")
                            .getOrDefault(emptyList())
                    },
                    musicArtistBreakdown = async {
                        apiClient.getPlaybackReportingArtistBreakdown(days = 30, filter = "$userId,Audio")
                            .getOrDefault(emptyList())
                    },
                    musicTopItems = async {
                        apiClient.getItemsWithUserData(
                            userId = userId,
                            includeItemTypes = listOf("Audio"),
                            isPlayed = true,
                            sortBy = "PlayCount",
                            sortOrder = "Descending",
                            startIndex = 0,
                            limit = 10,
                        ).getOrDefault(Pair(0, emptyList()))
                    },
                    audioPlayCount = async {
                        apiClient.getUserPlayedItemCount(userId, listOf("Audio")).getOrDefault(0)
                    },
                )
            } else null
            breakdowns = BreakdownResults(
                genre = genreDeferred.await(),
                method = methodDeferred.await(),
                device = deviceDeferred.await(),
                pluginActivity = activityDeferred.await(),
                watchTime = watchDeferred.await(),
            )
        }
        val genreBreakdown = breakdowns.genre
        val methodBreakdown = breakdowns.method
        val deviceBreakdown = breakdowns.device
        val pluginActivity = breakdowns.pluginActivity
        val watchTimeBreakdown = breakdowns.watchTime

        val userPluginActivity = pluginActivity.firstOrNull { it.userId == userId }
        var totalWatchTimeSec = userPluginActivity?.totalTime ?: 0L

        if (totalWatchTimeSec == 0L) {
            totalWatchTimeSec = watchTimeBreakdown.totalSeconds
        }

        // enhancedDeferreds is non-null exactly when the plugin batch launched
        // (same pluginAvailable gate), so the null check is the plugin gate.
        val enhancedData = buildEnhancedStatistics(
            userId = userId,
            deferreds = enhancedDeferreds,
            userPluginActivity = userPluginActivity,
            pluginChart = pluginChart,
            fallbackTrendData = fallbackTrendData,
            watchTimeBreakdown = watchTimeBreakdown,
            genreBreakdown = genreBreakdown,
        )

        UserDetailPage(
            user = user,
            // The shared builder (see its KDoc for the isCurrentlyActive
            // decision): watch time is the plugin-then-computed ladder above,
            // counts are the already-fetched pair used by the type breakdown.
            statistics = buildUserStatistics(
                user = user,
                totalWatchTimeSec = totalWatchTimeSec,
                counts = counts,
            ),
            topItems = topItems,
            topItemsTotalCount = playedResult.first,
            hasMoreItems = playedResult.first > (page + 1) * pageSize,
            activityChart = fallbackChart,
            typeBreakdown = typeBreakdown,
            genreBreakdown = genreBreakdown,
            methodBreakdown = methodBreakdown,
            deviceBreakdown = deviceBreakdown,
            weeklyWatchTimeSec = enhancedData.weeklyWatchTimeSec,
            monthlyWatchTimeSec = enhancedData.monthlyWatchTimeSec,
            viewingStreak = enhancedData.viewingStreak,
            trendData = enhancedData.trendData,
            averageDailyMinutes = enhancedData.averageDailyMinutes,
            monthlyComparison = enhancedData.monthlyComparison,
            musicStats = enhancedData.musicStats,
            genrePieData = enhancedData.genrePieData,
        )
    }

    // ── Media-cleanup scans + audit — delegated to [scanCore] ───────────

    override suspend fun detectStaleMedia(config: MediaCleanupConfig): Result<String> =
        scanCore.detectStaleMedia(config)

    override suspend fun detectWatchedMedia(config: MediaCleanupConfig): Result<String> =
        scanCore.detectWatchedMedia(config)

    override fun getScanProgress(scanId: String): Flow<ScanProgress> =
        scanCore.getScanProgress(scanId)

    override suspend fun getScanResultJson(scanId: String): String? =
        scanCore.getScanResultJson(scanId)

    override suspend fun removeMediaItems(
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        actionType: CleanupActionType,
        config: MediaCleanupConfig,
    ): Result<AuditLogEntry> = scanCore.removeMediaItems(itemIds, itemNameMap, actionType, config)

    override fun getAuditHistory(actionType: CleanupActionType?): Flow<List<AuditLogEntry>> =
        scanCore.getAuditHistory(actionType)

    private fun buildFallbackActivityChart(items: List<com.raulshma.jellyplay.core.model.MediaItem>): List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint> {
        val now = timeSource.today(java.time.ZoneId.systemDefault())
        val dateCounts = mutableMapOf<String, Long>()
        for (i in 0 until 30) {
            val date = now.minusDays(i.toLong())
            dateCounts[formatDate(date)] = 0
        }
        for (item in items) {
            val datePlayed = item.lastPlayedDate ?: continue
            val day = datePlayed.take(10)
            if (day in dateCounts) {
                dateCounts[day] = dateCounts.getOrDefault(day, 0L) + item.playCount.coerceAtLeast(1)
            }
        }
        return dateCounts.entries.sortedBy { it.key }.map { (date, count) ->
            com.raulshma.jellyplay.core.model.PlaybackActivityPoint(date = date, value = count)
        }
    }

    /**
     * The fetched half of the watch-time breakdown (the window math lives in
     * [StatisticsMath.computeWatchTimeBreakdown]): one paged played-items
     * call, `today` resolved through the clock seam, and the whole body
     * guarded by the original catch-to-zeros tail.
     */
    private suspend fun computeWatchTimeBreakdown(userId: String): StatisticsMath.WatchTimeBreakdown {
        return try {
            val items = apiClient.getItemsWithUserData(
                userId = userId,
                isPlayed = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                startIndex = 0,
                limit = 500,
            ).getOrDefault(Pair(0, emptyList())).second

            StatisticsMath.computeWatchTimeBreakdown(
                items = items.map { StatisticsMath.WatchTimeItem(it.runTimeTicks, it.playCount, it.lastPlayedDate) },
                today = timeSource.today(java.time.ZoneId.systemDefault()),
            )
        } catch (_: Exception) {
            StatisticsMath.WatchTimeBreakdown(0L, 0L, 0L, 0L)
        }
    }

    private fun formatDate(date: java.time.LocalDate): String = date.toString()

    private data class BreakdownResults(
        val genre: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val method: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val device: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val pluginActivity: List<com.raulshma.jellyplay.core.model.PlaybackReportingActivity>,
        val watchTime: StatisticsMath.WatchTimeBreakdown,
    )

    /**
     * Enhanced-batch deferreds, launched alongside the breakdowns batch so both
     * fan-outs overlap (none of these calls depends on breakdown results).
     */
    private data class EnhancedDeferreds(
        val weeklyActivity: Deferred<List<PlaybackReportingActivity>>,
        val sixMonthCount: Deferred<List<PlaybackActivityPoint>>,
        val musicGenreBreakdown: Deferred<List<ContentBreakdown>>,
        val musicArtistBreakdown: Deferred<List<ContentBreakdown>>,
        val musicTopItems: Deferred<Pair<Int, List<MediaItem>>>,
        val audioPlayCount: Deferred<Int>,
    )

    private data class EnhancedStatistics(
        val weeklyWatchTimeSec: Long = 0,
        val monthlyWatchTimeSec: Long = 0,
        val viewingStreak: com.raulshma.jellyplay.core.model.ViewingStreak = com.raulshma.jellyplay.core.model.ViewingStreak(),
        val trendData: List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint> = emptyList(),
        val averageDailyMinutes: Int = 0,
        val monthlyComparison: com.raulshma.jellyplay.core.model.MonthlyComparison = com.raulshma.jellyplay.core.model.MonthlyComparison(),
        val musicStats: com.raulshma.jellyplay.core.model.MusicStatistics = com.raulshma.jellyplay.core.model.MusicStatistics(),
        val genrePieData: List<com.raulshma.jellyplay.core.model.ContentBreakdown> = emptyList(),
    )

    /**
     * The enhanced half of [getUserDetailStatistics], built once for both
     * gates: [deferreds] non-null is the plugin batch (its awaits feed the
     * weekly/streak/music figures), null is the no-plugin fallback — the same
     * builder with the plugin inputs absent. The watch-time fallbacks, the
     * average-daily and the month-comparison math were duplicated across the
     * two former branches; they live here exactly once.
     */
    private suspend fun buildEnhancedStatistics(
        userId: String,
        deferreds: EnhancedDeferreds?,
        userPluginActivity: PlaybackReportingActivity?,
        pluginChart: List<PlaybackActivityPoint>,
        fallbackTrendData: List<PlaybackActivityPoint>,
        watchTimeBreakdown: StatisticsMath.WatchTimeBreakdown,
        genreBreakdown: List<ContentBreakdown>,
    ): EnhancedStatistics {
        var weeklyWatchTimeSec = if (deferreds != null) {
            deferreds.weeklyActivity.await().firstOrNull { it.userId == userId }?.totalTime ?: 0L
        } else {
            watchTimeBreakdown.last7DaysSeconds
        }
        var monthlyWatchTimeSec = if (deferreds != null) {
            userPluginActivity?.totalTime ?: 0L
        } else {
            watchTimeBreakdown.last30DaysSeconds
        }

        // Plugin totals of 0 fall back to the computed breakdown (a no-op on
        // the fallback path, whose values already are the breakdown's).
        if (weeklyWatchTimeSec == 0L) weeklyWatchTimeSec = watchTimeBreakdown.last7DaysSeconds
        if (monthlyWatchTimeSec == 0L) monthlyWatchTimeSec = watchTimeBreakdown.last30DaysSeconds

        val viewingStreak = if (deferreds != null) {
            calculateViewingStreak(deferreds.sixMonthCount.await())
        } else {
            com.raulshma.jellyplay.core.model.ViewingStreak()
        }

        val trendData = if (deferreds != null) {
            // pluginChart already holds the identical 30-day/count/userId
            // series — reusing it drops a duplicate round-trip per load.
            val pluginTrend = pluginChart.sortedBy { it.date }
            if (pluginTrend.isNotEmpty() && pluginTrend.any { it.value > 0 }) pluginTrend else fallbackTrendData
        } else {
            fallbackTrendData
        }

        // The fallback path additionally requires a non-empty trend before it
        // reports an average; the plugin path averages over ≥1 active day.
        val averageDailyMinutes = if (deferreds != null || trendData.isNotEmpty()) {
            computeAverageDailyMinutes(monthlyWatchTimeSec, trendData)
        } else {
            0
        }

        val currentMonthMinutes = monthlyWatchTimeSec / 60
        val previousMonthMinutes = watchTimeBreakdown.previous30DaysSeconds / 60

        val musicStats: com.raulshma.jellyplay.core.model.MusicStatistics
        val genrePieData: List<ContentBreakdown>
        if (deferreds != null) {
            val musicGenres = deferreds.musicGenreBreakdown.await()
            val musicArtists = deferreds.musicArtistBreakdown.await()
            val musicItems = deferreds.musicTopItems.await()
            // Joined for parity with the previous sequential await; the count
            // itself is not surfaced.
            deferreds.audioPlayCount.await()

            val musicTopTracks = musicItems.second.map { item ->
                com.raulshma.jellyplay.core.model.UserTopItem(
                    itemId = item.id,
                    name = item.name,
                    type = item.mediaType.name,
                    playCount = item.playCount,
                    posterBlurHash = item.blurHashes.primary,
                    seriesName = item.album,
                    runtimeTicks = item.runTimeTicks ?: 0,
                )
            }

            musicStats = com.raulshma.jellyplay.core.model.MusicStatistics(
                totalListeningHours = musicItems.second.sumOf { (it.runTimeTicks ?: 0L) / 10_000_000L * it.playCount.coerceAtLeast(1) } / 3600f,
                topArtists = musicArtists.take(5),
                topGenres = musicGenres.take(5),
                topTracks = musicTopTracks.take(5),
            )
            genrePieData = genreBreakdown.take(8)
        } else {
            musicStats = com.raulshma.jellyplay.core.model.MusicStatistics()
            genrePieData = emptyList()
        }

        return EnhancedStatistics(
            weeklyWatchTimeSec = weeklyWatchTimeSec,
            monthlyWatchTimeSec = monthlyWatchTimeSec,
            viewingStreak = viewingStreak,
            trendData = trendData,
            averageDailyMinutes = averageDailyMinutes,
            monthlyComparison = com.raulshma.jellyplay.core.model.MonthlyComparison(
                currentMonthMinutes = currentMonthMinutes,
                previousMonthMinutes = previousMonthMinutes,
                percentageChange = computePercentageChange(currentMonthMinutes, previousMonthMinutes),
            ),
            musicStats = musicStats,
            genrePieData = genrePieData,
        )
    }

    /** Minutes per active trend day (at least one day), 0 when nothing was watched. */
    private fun computeAverageDailyMinutes(monthlyWatchTimeSec: Long, trendData: List<PlaybackActivityPoint>): Int =
        if (monthlyWatchTimeSec > 0) {
            (monthlyWatchTimeSec / 60 / trendData.count { it.value > 0 }.coerceAtLeast(1)).toInt()
        } else {
            0
        }

    /** Month-over-month delta in percent; a first non-zero month reads as +100%. */
    private fun computePercentageChange(currentMonthMinutes: Long, previousMonthMinutes: Long): Float =
        if (previousMonthMinutes > 0) {
            ((currentMonthMinutes - previousMonthMinutes).toFloat() / previousMonthMinutes.toFloat()) * 100f
        } else if (currentMonthMinutes > 0) {
            100f
        } else {
            0f
        }

    /** Clock-resolving delegate to the pure [StatisticsMath.calculateViewingStreak]. */
    private fun calculateViewingStreak(activityData: List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint>): com.raulshma.jellyplay.core.model.ViewingStreak =
        StatisticsMath.calculateViewingStreak(activityData, timeSource.today(java.time.ZoneId.systemDefault()))
}
