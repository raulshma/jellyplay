package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.util.Log
import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.model.AuditItemDetail
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingActivity
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminStatisticsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: JellyfinApiClient,
    private val auditLogDao: AuditLogDao,
    private val scanStateDao: ScanStateDao,
    private val json: Json,
    /**
     * Shared application scope for the fire-and-forget background scans below
     * (the `@ApplicationScope` binding — never cancelled for this singleton,
     * matching the scan jobs' previous dedicated-scope lifetime).
     */
    @ApplicationScope private val scope: CoroutineScope,
) : AdminStatisticsRepository {

    /**
     * Bounds concurrency of the per-user statistics fan-out so a server with
     * many users does not fire N×4 simultaneous requests. Mirrors the
     * [kotlinx.coroutines.sync.Semaphore] pattern in `ArrRepositoryImpl`.
     */
    private val statsSemaphore = Semaphore(4)

    private val _pluginStatus = MutableStateFlow(PlaybackReportingStatus.UNKNOWN)
    override fun getPlaybackReportingStatus() = _pluginStatus.asStateFlow()

    override suspend fun refreshPlaybackReportingStatus() {
        _pluginStatus.value = apiClient.checkPlaybackReportingPlugin().getOrDefault(PlaybackReportingStatus.UNAVAILABLE)
        cleanupOldAuditLogs()
    }

    private suspend fun cleanupOldAuditLogs() {
        try {
            val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            auditLogDao.deleteOlderThan(ninetyDaysAgo)
        } catch (e: Exception) {
            Log.d("AdminStats", "Failed to cleanup old audit logs", e)
        }
    }

    override suspend fun getAllUsersWithStatistics(): Result<List<UserStatistics>> = runCatching {
        val pluginAvailable = _pluginStatus.value == PlaybackReportingStatus.AVAILABLE

        coroutineScope {
            val usersDeferred = async { apiClient.getUsers().getOrThrow() }
            val sessionsDeferred = async { apiClient.getSessions().getOrDefault(emptyList()) }
            val pluginDeferred = async {
                if (pluginAvailable) {
                    apiClient.getPlaybackReportingUserActivity(days = 30).getOrDefault(emptyList())
                } else emptyList()
            }

            val users = usersDeferred.await()
            val activeUserIds = sessionsDeferred.await().map { it.userId }.toSet()
            val pluginMap = pluginDeferred.await().associateBy { it.userId }

            users.map { user ->
                async {
                    statsSemaphore.withPermit {
                        buildUserStatistics(
                            user = user,
                            isActive = activeUserIds.contains(user.id),
                            pluginData = pluginMap[user.id],
                        )
                    }
                }
            }.awaitAll()
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

    private suspend fun buildUserStatistics(
        user: JellyfinUser,
        isActive: Boolean,
        pluginData: com.raulshma.jellyplay.core.model.PlaybackReportingActivity?,
    ): UserStatistics {
        val userId = user.id

        val stats = fetchUserPlayCounts(userId)
        val moviePlayed = stats.moviePlayed
        val episodePlayed = stats.episodePlayed
        val songPlayed = stats.songPlayed
        val movieUnplayed = stats.movieUnplayed
        val movieTotal = movieUnplayed + moviePlayed
        val completionRate = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

        return UserStatistics(
            userId = userId,
            userName = user.name,
            userAvatarTag = user.primaryImageTag,
            isAdmin = user.isAdmin,
            totalPlayCount = moviePlayed + episodePlayed + songPlayed,
            moviePlayCount = moviePlayed,
            episodePlayCount = episodePlayed,
            songPlayCount = songPlayed,
            totalWatchTimeSec = pluginData?.totalTime ?: 0L,
            lastSeen = user.lastActivityDate,
            completionRate = completionRate,
            isCurrentlyActive = isActive,
        )
    }

    override suspend fun getUserDetailStatistics(userId: String, page: Int, pageSize: Int): Result<UserDetailPage> = runCatching {
        val pluginAvailable = _pluginStatus.value == PlaybackReportingStatus.AVAILABLE

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
                if (pluginAvailable) {
                    apiClient.getPlaybackReportingPlayActivity(days = 30, dataType = "count", filter = userId)
                        .getOrDefault(emptyList())
                } else emptyList()
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
        val fallbackChart = if (fallbackItems.isNotEmpty()) buildFallbackActivityChart(fallbackItems) else pluginChart
        val fallbackTrendData = if (fallbackItems.isNotEmpty()) buildFallbackActivityChart(fallbackItems) else emptyList()
        val moviePlayedCount = counts.moviePlayed
        val episodePlayedCount = counts.episodePlayed
        val songPlayedCount = counts.songPlayed
        val movieUnplayed = counts.movieUnplayed

        val typeBreakdown = listOf(
            ContentBreakdown(
                label = context.getString(R.string.data_label_movies),
                value = moviePlayedCount.toLong(),
                colorIndex = 0,
            ),
            ContentBreakdown(
                label = context.getString(R.string.data_label_episodes),
                value = episodePlayedCount.toLong(),
                colorIndex = 1,
            ),
            ContentBreakdown(
                label = context.getString(R.string.data_label_songs),
                value = songPlayedCount.toLong(),
                colorIndex = 2,
            ),
        ).filter { it.value > 0 }

        // Breakdowns and the enhanced wave launch together: none of the
        // enhanced calls depends on breakdown results (only the local math
        // after the awaits does), so overlapping removes a full sequential
        // round-trip wave from the detail-page load.
        val breakdowns: BreakdownResults
        val enhancedDeferreds: EnhancedDeferreds?
        coroutineScope {
            val genreDeferred = async {
                if (pluginAvailable) apiClient.getPlaybackReportingBreakdown("Genre", days = 30, filter = userId).getOrDefault(emptyList()) else emptyList()
            }
            val methodDeferred = async {
                if (pluginAvailable) apiClient.getPlaybackReportingBreakdown("PlaybackMethod", days = 30, filter = userId).getOrDefault(emptyList()) else emptyList()
            }
            val deviceDeferred = async {
                if (pluginAvailable) apiClient.getPlaybackReportingBreakdown("ClientName", days = 30, filter = userId).getOrDefault(emptyList()) else emptyList()
            }
            val activityDeferred = async {
                if (pluginAvailable) apiClient.getPlaybackReportingUserActivity(days = 30).getOrDefault(emptyList()) else emptyList()
            }
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

        val moviePlayed = moviePlayedCount
        val episodePlayed = episodePlayedCount
        val songPlayed = songPlayedCount
        val movieTotal = movieUnplayed + moviePlayed
        val completionRate = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

        val userPluginActivity = pluginActivity.firstOrNull { it.userId == userId }
        var totalWatchTimeSec = userPluginActivity?.totalTime ?: 0L

        if (totalWatchTimeSec == 0L) {
            totalWatchTimeSec = watchTimeBreakdown.totalSeconds
        }

        // enhancedDeferreds is non-null exactly when the plugin wave launched
        // (same pluginAvailable gate), so the null check is the plugin gate.
        val enhancedData = enhancedDeferreds?.let { deferreds ->
            val weeklyUserData = deferreds.weeklyActivity.await().firstOrNull { it.userId == userId }
            var weeklyWatchTimeSec = weeklyUserData?.totalTime ?: 0L
            var monthlyWatchTimeSec = userPluginActivity?.totalTime ?: 0L

            if (weeklyWatchTimeSec == 0L) weeklyWatchTimeSec = watchTimeBreakdown.last7DaysSeconds
            if (monthlyWatchTimeSec == 0L) monthlyWatchTimeSec = watchTimeBreakdown.last30DaysSeconds

            val streakData = deferreds.sixMonthCount.await()
            val viewingStreak = calculateViewingStreak(streakData)

            // pluginChart already holds the identical 30-day/count/userId
            // series — reusing it drops a duplicate round-trip per load.
            val pluginTrend = pluginChart.sortedBy { it.date }
            val trendData = if (pluginTrend.isNotEmpty() && pluginTrend.any { it.value > 0 }) pluginTrend else fallbackTrendData
            val activeDays = trendData.count { it.value > 0 }.coerceAtLeast(1)
            val averageDailyMinutes = if (monthlyWatchTimeSec > 0) {
                (monthlyWatchTimeSec / 60 / activeDays).toInt()
            } else 0

            val currentMonthMinutes = monthlyWatchTimeSec / 60
            val previousMonthMinutes = watchTimeBreakdown.previous30DaysSeconds / 60
            val percentageChange = if (previousMonthMinutes > 0) {
                ((currentMonthMinutes - previousMonthMinutes).toFloat() / previousMonthMinutes.toFloat()) * 100f
            } else if (currentMonthMinutes > 0) 100f else 0f

            val musicGenres = deferreds.musicGenreBreakdown.await()
            val musicArtists = deferreds.musicArtistBreakdown.await()
            val musicItems = deferreds.musicTopItems.await()
            val audioCount = deferreds.audioPlayCount.await()

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

                val genrePieData = genreBreakdown.take(8)

                EnhancedStatistics(
                    weeklyWatchTimeSec = weeklyWatchTimeSec,
                    monthlyWatchTimeSec = monthlyWatchTimeSec,
                    viewingStreak = viewingStreak,
                    trendData = trendData,
                    averageDailyMinutes = averageDailyMinutes,
                    monthlyComparison = com.raulshma.jellyplay.core.model.MonthlyComparison(
                        currentMonthMinutes = currentMonthMinutes,
                        previousMonthMinutes = previousMonthMinutes,
                        percentageChange = percentageChange,
                    ),
                    musicStats = com.raulshma.jellyplay.core.model.MusicStatistics(
                        totalListeningHours = musicItems.second.sumOf { (it.runTimeTicks ?: 0L) / 10_000_000L * it.playCount.coerceAtLeast(1) } / 3600f,
                        topArtists = musicArtists.take(5),
                        topGenres = musicGenres.take(5),
                        topTracks = musicTopTracks.take(5),
                    ),
                    genrePieData = genrePieData,
                )
        } ?: run {
            EnhancedStatistics(
                weeklyWatchTimeSec = watchTimeBreakdown.last7DaysSeconds,
                monthlyWatchTimeSec = watchTimeBreakdown.last30DaysSeconds,
                viewingStreak = com.raulshma.jellyplay.core.model.ViewingStreak(),
                trendData = fallbackTrendData,
                averageDailyMinutes = if (watchTimeBreakdown.last30DaysSeconds > 0 && fallbackTrendData.isNotEmpty()) {
                    (watchTimeBreakdown.last30DaysSeconds / 60 / fallbackTrendData.count { it.value > 0 }.coerceAtLeast(1)).toInt()
                } else 0,
                monthlyComparison = com.raulshma.jellyplay.core.model.MonthlyComparison(
                    currentMonthMinutes = watchTimeBreakdown.last30DaysSeconds / 60,
                    previousMonthMinutes = watchTimeBreakdown.previous30DaysSeconds / 60,
                    percentageChange = if (watchTimeBreakdown.previous30DaysSeconds > 0) {
                        ((watchTimeBreakdown.last30DaysSeconds - watchTimeBreakdown.previous30DaysSeconds).toFloat() / watchTimeBreakdown.previous30DaysSeconds.toFloat()) * 100f
                    } else if (watchTimeBreakdown.last30DaysSeconds > 0) 100f else 0f,
                ),
                musicStats = com.raulshma.jellyplay.core.model.MusicStatistics(),
                genrePieData = emptyList(),
            )
        }

        UserDetailPage(
            user = user,
            statistics = UserStatistics(
                userId = userId,
                userName = user.name,
                userAvatarTag = user.primaryImageTag,
                isAdmin = user.isAdmin,
                totalPlayCount = moviePlayed + episodePlayed + songPlayed,
                moviePlayCount = moviePlayed,
                episodePlayCount = episodePlayed,
                songPlayCount = songPlayed,
                totalWatchTimeSec = totalWatchTimeSec,
                completionRate = completionRate,
                lastSeen = user.lastActivityDate,
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

    override suspend fun detectStaleMedia(config: MediaCleanupConfig): Result<String> = runCatching {
        val scanId = java.util.UUID.randomUUID().toString()
        scanStateDao.insert(
            ScanStateEntity(
                scanId = scanId,
                type = "STALE",
                configJson = json.encodeToString(MediaCleanupConfig.serializer(), config),
                status = ScanPhase.SCANNING.name,
            )
        )
        scope.launch { runStaleMediaScan(scanId, config) }
        scanId
    }

    override suspend fun detectWatchedMedia(config: MediaCleanupConfig): Result<String> = runCatching {
        val scanId = java.util.UUID.randomUUID().toString()
        scanStateDao.insert(
            ScanStateEntity(
                scanId = scanId,
                type = "WATCHED",
                configJson = json.encodeToString(MediaCleanupConfig.serializer(), config),
                status = ScanPhase.SCANNING.name,
            )
        )
        scope.launch { runWatchedMediaScan(scanId, config) }
        scanId
    }

    private suspend fun runStaleMediaScan(scanId: String, config: MediaCleanupConfig) {
        try {
            val allResults = mutableListOf<MediaItemStub>()
            var startIndex = 0
            val pageSize = 200
            var hasMore = true

            while (hasMore) {
                val result = apiClient.getStaleItems(
                    daysThreshold = config.daysThreshold,
                    includeNeverPlayed = config.includeNeverPlayed,
                    includeItemTypes = config.includeItemTypes.toList(),
                    startIndex = startIndex,
                    limit = pageSize,
                    useDateAdded = config.useDateAdded,
                ).getOrDefault(Pair(0, emptyList()))

                val items = result.second.map { staleItem ->
                    val dateStr = if (config.useDateAdded) staleItem.dateAdded else staleItem.lastPlayedDate
                    val formattedDate = dateStr?.take(10)
                    val neverPlayed = staleItem.daysSincePlay <= 0 && staleItem.playCount == 0
                    val addedAgoText = staleItem.dateAdded?.let { added ->
                        try {
                            val addedDate = java.time.LocalDate.parse(added.take(10))
                            val days = java.time.temporal.ChronoUnit.DAYS.between(addedDate, java.time.LocalDate.now())
                            when {
                                days < 1 -> context.getString(R.string.data_added_today)
                                days == 1L -> context.getString(R.string.data_added_one_day_ago)
                                days < 30 -> context.getString(R.string.data_added_days_ago, days.toInt())
                                days < 365 -> context.getString(R.string.data_added_months_ago, (days / 30).toInt())
                                else -> context.getString(R.string.data_added_years_ago, (days / 365).toInt())
                            }
                        } catch (_: Exception) { null }
                    }
                    MediaItemStub(
                        itemId = staleItem.itemId,
                        name = staleItem.name,
                        type = staleItem.type,
                        sizeText = staleItem.sizeText,
                        detail = buildString {
                            if (staleItem.daysSincePlay > 0) append(context.getString(R.string.data_days_since_play, staleItem.daysSincePlay))
                            else append(context.getString(R.string.data_never_played))
                            if (staleItem.playCount > 0) {
                                append(" · " + context.getString(R.string.data_plays_count, staleItem.playCount))
                            }
                        },
                        seriesName = staleItem.seriesName,
                        seasonName = staleItem.seasonName,
                        seasonNumber = staleItem.seasonNumber,
                        episodeNumber = staleItem.episodeNumber,
                        dateText = if (config.useDateAdded) {
                            formattedDate?.let { context.getString(R.string.data_added_date, it) } ?: context.getString(R.string.data_added_unknown)
                        } else if (neverPlayed) {
                            addedAgoText ?: context.getString(R.string.data_never_played)
                        } else {
                            formattedDate?.let { context.getString(R.string.data_played_date, it) }
                        },
                    )
                }
                allResults.addAll(items)

                // Targeted progress write; 0 affected rows = scan row deleted
                // (cancelled) — stop without the full-row read.
                if (scanStateDao.updateProgress(
                        scanId = scanId,
                        progress = minOf(startIndex + result.second.size, result.first),
                        total = result.first,
                        itemsFound = allResults.size,
                    ) == 0
                ) {
                    return
                }

                startIndex += pageSize
                hasMore = result.second.size >= pageSize && startIndex < result.first
            }

            val entity = scanStateDao.getById(scanId) ?: return
            scanStateDao.update(
                entity.copy(
                    status = ScanPhase.COMPLETED.name,
                    progress = entity.total,
                    itemsFound = allResults.size,
                    resultJson = json.encodeToString(ListSerializer(MediaItemStub.serializer()), allResults),
                )
            )
        } catch (e: Exception) {
            val entity = scanStateDao.getById(scanId)
            if (entity != null) {
                scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
            }
        }
    }

    private suspend fun runWatchedMediaScan(scanId: String, config: MediaCleanupConfig) {
        try {
            val allResults = mutableListOf<MediaItemStub>()
            // Seen-id set alongside allResults so dedup is O(1) per item instead
            // of O(n) via allResults.any{}. Without this the .mapNotNull below
            // is O(total_watched_items²) because allResults grows every
            // iteration. First-occurrence wins, identical to the previous
            // allResults.any{} semantics — output ordering is irrelevant here
            // (results are persisted as JSON and the UI doesn't depend on
            // insertion order).
            val seenItemIds = HashSet<String>()
            val users = apiClient.getUsers().getOrDefault(emptyList())
            val pageSize = 200

            for (user in users) {
                var startIndex = 0
                var hasMore = true

                while (hasMore) {
                    val result = apiClient.getWatchedItems(
                        userId = user.id,
                        includeItemTypes = config.includeItemTypes.toList(),
                        minDaysSincePlayed = config.minDaysSinceWatched,
                        keepFavorites = config.keepFavorites,
                        startIndex = startIndex,
                        limit = pageSize,
                    ).getOrDefault(Pair(0, emptyList()))

                    val items = result.second
                        .filter { if (!config.includePartiallyWatched) it.completionPct >= 0.9f else true }
                        .mapNotNull { watched ->
                            if (!seenItemIds.add(watched.itemId)) return@mapNotNull null
                            val lastPlayedStr = watched.lastPlayedDate?.take(10)
                            MediaItemStub(
                                itemId = watched.itemId,
                                name = watched.name,
                                type = watched.type,
                                sizeText = formatSize(watched.sizeBytes),
                                detail = buildString {
                                    append("${watched.playCount} plays")
                                    if (watched.completionPct < 1f) {
                                        append(" · ${(watched.completionPct * 100).toInt()}%")
                                    }
                                },
                                seriesName = watched.seriesName,
                                seasonName = watched.seasonName,
                                seasonNumber = watched.seasonNumber,
                                episodeNumber = watched.episodeNumber,
                                dateText = lastPlayedStr?.let { "Played $it" },
                            )
                        }

                    allResults.addAll(items)

                    // Targeted progress write; 0 affected rows = scan row
                    // deleted (cancelled) — stop without the full-row read.
                    if (scanStateDao.updateProgress(
                            scanId = scanId,
                            progress = allResults.size,
                            total = allResults.size + (result.first.coerceAtLeast(0) - items.size),
                            itemsFound = allResults.size,
                        ) == 0
                    ) {
                        return
                    }

                    startIndex += pageSize
                    hasMore = result.second.size >= pageSize
                }
            }

            val entity = scanStateDao.getById(scanId) ?: return
            scanStateDao.update(
                entity.copy(
                    status = ScanPhase.COMPLETED.name,
                    progress = entity.total,
                    itemsFound = allResults.size,
                    resultJson = json.encodeToString(ListSerializer(MediaItemStub.serializer()), allResults),
                )
            )
        } catch (e: Exception) {
            val entity = scanStateDao.getById(scanId)
            if (entity != null) {
                scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
            }
        }
    }

    override fun getScanProgress(scanId: String): Flow<ScanProgress> =
        scanStateDao.observeProgress(scanId).map { row ->
            if (row == null) ScanProgress()
            else ScanProgress(
                phase = runCatching { ScanPhase.valueOf(row.status) }.getOrDefault(ScanPhase.IDLE),
                scanned = row.progress,
                total = row.total,
                itemsFound = row.itemsFound,
            )
        }

    override suspend fun getScanResultJson(scanId: String): String? {
        return scanStateDao.getById(scanId)?.resultJson
    }

    override suspend fun removeMediaItems(
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        actionType: CleanupActionType,
        config: MediaCleanupConfig,
    ): Result<AuditLogEntry> = runCatching {
        val currentUser = apiClient.currentUser.first()
        val adminId = currentUser?.id ?: ""
        val adminName = currentUser?.name ?: ""

        val deleted = apiClient.deleteItems(itemIds).getOrThrow()

        val itemDetails = itemIds.map { id ->
            AuditItemDetail(
                itemId = id,
                name = itemNameMap[id] ?: "",
                type = "",
                sizeText = "",
                detail = "",
            )
        }

        val entry = AuditLogEntry(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            adminUserId = adminId,
            adminUserName = adminName,
            actionType = actionType,
            configSnapshot = json.encodeToString(MediaCleanupConfig.serializer(), config),
            itemCount = itemDetails.size,
            itemDetails = itemDetails,
        )

        auditLogDao.insert(
            MediaAuditLogEntity(
                id = entry.id,
                timestamp = entry.timestamp,
                adminUserId = entry.adminUserId,
                adminUserName = entry.adminUserName,
                actionType = entry.actionType.name,
                configJson = entry.configSnapshot,
                itemCount = entry.itemCount,
                itemDetailsJson = json.encodeToString(
                    kotlinx.serialization.serializer<List<AuditItemDetail>>(),
                    entry.itemDetails,
                ),
            )
        )

        entry
    }

    override fun getAuditHistory(actionType: CleanupActionType?): Flow<List<AuditLogEntry>> =
        if (actionType != null) {
            auditLogDao.getByActionType(actionType.name)
        } else {
            auditLogDao.getAll()
        }.map { entities ->
            entities.map { entity ->
                AuditLogEntry(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    adminUserId = entity.adminUserId,
                    adminUserName = entity.adminUserName,
                    actionType = runCatching { CleanupActionType.valueOf(entity.actionType) }.getOrDefault(CleanupActionType.STALE_REMOVAL),
                    configSnapshot = entity.configJson,
                    itemCount = entity.itemCount,
                    itemDetails = runCatching {
                        json.decodeFromString<List<AuditItemDetail>>(entity.itemDetailsJson)
                    }.getOrDefault(emptyList()),
                )
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    private fun buildFallbackActivityChart(items: List<com.raulshma.jellyplay.core.model.MediaItem>): List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint> {
        val now = java.time.LocalDate.now()
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

    private data class WatchTimeBreakdown(
        val totalSeconds: Long,
        val last30DaysSeconds: Long,
        val last7DaysSeconds: Long,
        val previous30DaysSeconds: Long,
    )

    private suspend fun computeWatchTimeBreakdown(userId: String): WatchTimeBreakdown {
        return try {
            val items = apiClient.getItemsWithUserData(
                userId = userId,
                isPlayed = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                startIndex = 0,
                limit = 500,
            ).getOrDefault(Pair(0, emptyList()))

            val now = java.time.LocalDate.now()
            var totalSec = 0L
            var last30Sec = 0L
            var last7Sec = 0L
            var prev30Sec = 0L

            for (item in items.second) {
                val runtimeSec = (item.runTimeTicks ?: 0L) / 10_000_000L
                if (runtimeSec == 0L) continue
                val plays = item.playCount.coerceAtLeast(1)
                totalSec += runtimeSec * plays

                val lastPlayed = item.lastPlayedDate?.take(10) ?: continue
                val playedDate = try { java.time.LocalDate.parse(lastPlayed) } catch (_: Exception) { continue }
                val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(playedDate, now)

                if (daysAgo <= 30) {
                    val recentPlays = if (plays == 1) 1 else maxOf(1, plays * 30 / (daysAgo.toInt() + 30))
                    last30Sec += runtimeSec * recentPlays
                }
                if (daysAgo <= 7) {
                    val recentPlays = if (plays == 1) 1 else maxOf(1, plays * 7 / (daysAgo.toInt() + 7))
                    last7Sec += runtimeSec * recentPlays
                }
                if (daysAgo in 31..60) {
                    prev30Sec += runtimeSec * plays
                }
            }

            WatchTimeBreakdown(
                totalSeconds = totalSec,
                last30DaysSeconds = last30Sec.coerceAtMost(totalSec),
                last7DaysSeconds = last7Sec.coerceAtMost(totalSec),
                previous30DaysSeconds = prev30Sec.coerceAtMost(totalSec - last30Sec).coerceAtLeast(0L),
            )
        } catch (_: Exception) {
            WatchTimeBreakdown(0L, 0L, 0L, 0L)
        }
    }

    private fun formatDate(date: java.time.LocalDate): String = date.toString()

    private data class BreakdownResults(
        val genre: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val method: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val device: List<com.raulshma.jellyplay.core.model.ContentBreakdown>,
        val pluginActivity: List<com.raulshma.jellyplay.core.model.PlaybackReportingActivity>,
        val watchTime: WatchTimeBreakdown,
    )

    /**
     * Enhanced-wave deferreds, launched alongside the breakdowns wave so both
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

    private fun calculateViewingStreak(activityData: List<com.raulshma.jellyplay.core.model.PlaybackActivityPoint>): com.raulshma.jellyplay.core.model.ViewingStreak {
        val activeDates = activityData
            .filter { it.value > 0 }
            .map { it.date }
            .toSet()

        if (activeDates.isEmpty()) {
            return com.raulshma.jellyplay.core.model.ViewingStreak()
        }

        val today = java.time.LocalDate.now()
        var currentStreak = 0
        var streakStartDate: String? = null

        var checkDate = today
        while (activeDates.contains(formatDate(checkDate))) {
            currentStreak++
            streakStartDate = formatDate(checkDate)
            checkDate = checkDate.minusDays(1)
        }

        val sortedDates = activeDates.sorted()
        var longestStreak = 0
        var tempStreak = 1
        for (i in 1 until sortedDates.size) {
            try {
                val prev = java.time.LocalDate.parse(sortedDates[i - 1])
                val curr = java.time.LocalDate.parse(sortedDates[i])
                if (java.time.temporal.ChronoUnit.DAYS.between(prev, curr) == 1L) {
                    tempStreak++
                } else {
                    longestStreak = maxOf(longestStreak, tempStreak)
                    tempStreak = 1
                }
            } catch (_: Exception) {
                longestStreak = maxOf(longestStreak, tempStreak)
                tempStreak = 1
            }
        }
        longestStreak = maxOf(longestStreak, tempStreak)

        return com.raulshma.jellyplay.core.model.ViewingStreak(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            streakStartDate = streakStartDate,
        )
    }
}
