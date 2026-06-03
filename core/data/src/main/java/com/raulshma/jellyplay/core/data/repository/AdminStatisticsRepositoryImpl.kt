package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.AuditItemDetail
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminStatisticsRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val auditLogDao: AuditLogDao,
    private val scanStateDao: ScanStateDao,
) : AdminStatisticsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _pluginStatus = MutableStateFlow(PlaybackReportingStatus.UNKNOWN)
    override fun getPlaybackReportingStatus() = _pluginStatus.asStateFlow()

    override suspend fun refreshPlaybackReportingStatus() {
        _pluginStatus.value = apiClient.checkPlaybackReportingPlugin().getOrDefault(PlaybackReportingStatus.UNAVAILABLE)
    }

    override suspend fun getAllUsersWithStatistics(): Result<List<UserStatistics>> = runCatching {
        val users = apiClient.getUsers().getOrThrow()
        val sessions = apiClient.getSessions().getOrDefault(emptyList())
        val activeUserIds = sessions.map { it.userId }.toSet()
        val pluginAvailable = _pluginStatus.value == PlaybackReportingStatus.AVAILABLE

        val pluginActivity = if (pluginAvailable) {
            apiClient.getPlaybackReportingUserActivity(days = 30).getOrDefault(emptyList())
        } else emptyList()
        val pluginMap = pluginActivity.associateBy { it.userId }

        users.map { user ->
            val userId = user.id
            val isActive = activeUserIds.contains(userId)

            val moviePlayed = apiClient.getUserPlayedItemCount(userId, listOf("Movie")).getOrDefault(0)
            val episodePlayed = apiClient.getUserPlayedItemCount(userId, listOf("Episode")).getOrDefault(0)
            val songPlayed = apiClient.getUserPlayedItemCount(userId, listOf("Audio")).getOrDefault(0)
            val movieTotal = apiClient.getUserUnplayedItemCount(userId, listOf("Movie")).getOrDefault(0) + moviePlayed
            val completionRate = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

            val pluginData = pluginMap[userId]
            val watchTimeSec = pluginData?.totalPlayTime ?: 0L

            UserStatistics(
                userId = userId,
                userName = user.name,
                userAvatarTag = user.primaryImageTag,
                isAdmin = user.isAdmin,
                totalPlayCount = moviePlayed + episodePlayed + songPlayed,
                moviePlayCount = moviePlayed,
                episodePlayCount = episodePlayed,
                songPlayCount = songPlayed,
                totalWatchTimeSec = watchTimeSec,
                lastSeen = user.lastActivityDate,
                completionRate = completionRate,
                isCurrentlyActive = isActive,
            )
        }
    }

    override suspend fun getUserDetailStatistics(userId: String, page: Int, pageSize: Int): Result<UserDetailPage> = runCatching {
        val users = apiClient.getUsers().getOrDefault(emptyList())
        val user = users.firstOrNull { it.id == userId } ?: JellyfinUser(id = userId)
        val pluginAvailable = _pluginStatus.value == PlaybackReportingStatus.AVAILABLE

        val playedResult = apiClient.getItemsWithUserData(
            userId = userId,
            isPlayed = true,
            sortBy = "PlayCount",
            sortOrder = "Descending",
            startIndex = page * pageSize,
            limit = pageSize,
        ).getOrDefault(Pair(0, emptyList()))

        val topItems = playedResult.second.map { item ->
            com.raulshma.jellyplay.core.model.UserTopItem(
                itemId = item.id,
                name = item.name,
                type = item.mediaType.name,
                playCount = 0,
                lastPlayedDate = null,
                posterBlurHash = item.blurHashes.primary,
                seriesName = item.seriesName,
                runtimeTicks = item.runTimeTicks ?: 0,
            )
        }

        val activityChart = if (pluginAvailable) {
            apiClient.getPlaybackReportingPlayActivity(days = 30, dataType = "count").getOrDefault(emptyList())
        } else emptyList()

        val typeBreakdown = listOf(
            ContentBreakdown(
                label = "Movies",
                value = apiClient.getUserPlayedItemCount(userId, listOf("Movie")).getOrDefault(0).toLong(),
                colorIndex = 0,
            ),
            ContentBreakdown(
                label = "Episodes",
                value = apiClient.getUserPlayedItemCount(userId, listOf("Episode")).getOrDefault(0).toLong(),
                colorIndex = 1,
            ),
            ContentBreakdown(
                label = "Songs",
                value = apiClient.getUserPlayedItemCount(userId, listOf("Audio")).getOrDefault(0).toLong(),
                colorIndex = 2,
            ),
        ).filter { it.value > 0 }

        val genreBreakdown = if (pluginAvailable) {
            apiClient.getPlaybackReportingBreakdown("ItemType", days = 30).getOrDefault(emptyList())
        } else emptyList()

        val methodBreakdown = if (pluginAvailable) {
            apiClient.getPlaybackReportingBreakdown("PlaybackMethod", days = 30).getOrDefault(emptyList())
        } else emptyList()

        val deviceBreakdown = if (pluginAvailable) {
            apiClient.getPlaybackReportingBreakdown("ClientName", days = 30).getOrDefault(emptyList())
        } else emptyList()

        val moviePlayed = apiClient.getUserPlayedItemCount(userId, listOf("Movie")).getOrDefault(0)
        val episodePlayed = apiClient.getUserPlayedItemCount(userId, listOf("Episode")).getOrDefault(0)
        val songPlayed = apiClient.getUserPlayedItemCount(userId, listOf("Audio")).getOrDefault(0)
        val movieTotal = apiClient.getUserUnplayedItemCount(userId, listOf("Movie")).getOrDefault(0) + moviePlayed
        val completionRate = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

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
                completionRate = completionRate,
                lastSeen = user.lastActivityDate,
            ),
            topItems = topItems,
            topItemsTotalCount = playedResult.first,
            hasMoreItems = playedResult.first > (page + 1) * pageSize,
            activityChart = activityChart,
            typeBreakdown = typeBreakdown,
            genreBreakdown = genreBreakdown,
            methodBreakdown = methodBreakdown,
            deviceBreakdown = deviceBreakdown,
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
                ).getOrDefault(Pair(0, emptyList()))

                val items = result.second.map { staleItem ->
                    MediaItemStub(
                        itemId = staleItem.itemId,
                        name = staleItem.name,
                        type = staleItem.type,
                        sizeText = staleItem.sizeText,
                        detail = buildString {
                            if (staleItem.daysSincePlay > 0) append("${staleItem.daysSincePlay}d ago")
                            if (staleItem.playCount > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("${staleItem.playCount} plays")
                            }
                        },
                    )
                }
                allResults.addAll(items)

                val entity = scanStateDao.getById(scanId) ?: return
                scanStateDao.update(
                    entity.copy(
                        progress = minOf(startIndex + result.second.size, result.first),
                        total = result.first,
                        itemsFound = allResults.size,
                    )
                )

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
            withContext(Dispatchers.Main) {
                val entity = scanStateDao.getById(scanId)
                if (entity != null) {
                    scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
                }
            }
        }
    }

    private suspend fun runWatchedMediaScan(scanId: String, config: MediaCleanupConfig) {
        try {
            val allResults = mutableListOf<MediaItemStub>()
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
                            if (allResults.any { it.itemId == watched.itemId }) return@mapNotNull null
                            MediaItemStub(
                                itemId = watched.itemId,
                                name = watched.name,
                                type = watched.type,
                                sizeText = "",
                                detail = buildString {
                                    append("${watched.playCount} plays")
                                    if (watched.completionPct < 1f) {
                                        append(" · ${(watched.completionPct * 100).toInt()}%")
                                    }
                                },
                            )
                        }

                    allResults.addAll(items)

                    val entity = scanStateDao.getById(scanId) ?: return
                    scanStateDao.update(
                        entity.copy(
                            progress = allResults.size,
                            total = allResults.size + (result.first.coerceAtLeast(0) - items.size),
                            itemsFound = allResults.size,
                        )
                    )

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
            withContext(Dispatchers.Main) {
                val entity = scanStateDao.getById(scanId)
                if (entity != null) {
                    scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
                }
            }
        }
    }

    override fun getScanProgress(scanId: String): Flow<ScanProgress> =
        scanStateDao.observeById(scanId).map { entity ->
            if (entity == null) ScanProgress()
            else ScanProgress(
                phase = runCatching { ScanPhase.valueOf(entity.status) }.getOrDefault(ScanPhase.IDLE),
                scanned = entity.progress,
                total = entity.total,
                itemsFound = entity.itemsFound,
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
        val currentUser = apiClient.currentUser
        var adminId = ""
        var adminName = ""
        currentUser.collect { user ->
            adminId = user?.id ?: ""
            adminName = user?.name ?: ""
        }

        val deleted = apiClient.deleteItems(itemIds).getOrDefault(0)

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
}
