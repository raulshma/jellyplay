package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingActivity
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaInfoApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : MediaInfoApiClient {

    private val rawRequester = JellyfinRawRequester(engine)

    // The server name is effectively session-static but lives below the
    // repository layer (which already caches library folders), so every
    // newsletter render previously bypassed the in-memory cache. Short TTL
    // keeps it fresh across server renames without per-render network calls.
    private val serverNameCache = TtlCache<String>(maxSize = 4, ttlMs = 30 * 60 * 1000L)

    private suspend fun getCachedServerName(): String =
        runCatchingRethrowingCancellation {
            serverNameCache.getOrPut(KEY_SERVER_NAME) {
                engine.requireApi().systemApi.getSystemInfo().content.serverName ?: ""
            }
        }.getOrDefault("")

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> = engine.apiResultWithRetry {
        coroutineScope {
            val serverName = async { getCachedServerName() }
            val recentlyAdded = async {
                runCatchingRethrowingCancellation {
                    val folders = engine.requireApi().userViewsApi.getUserViews().content?.items ?: emptyList()
                    val candidateFolders = folders.filter { folder ->
                        folder.collectionType?.serialName != "music"
                    }
                    // Bound the per-folder getLatestMedia concurrency with a
                    // Semaphore(4), mirroring LibraryApiClientImpl.getHomeSections.
                    // Without this the previous flatMap fired all
                    // getLatestMedia calls concurrently with no upper bound —
                    // on a server with 30+ libraries that was 30 simultaneous
                    // HTTP requests against a single Jellyfin instance, which
                    // often has a per-connection thread cap and degrades
                    // everyone's experience.
                    Semaphore(4).mapConcurrent(candidateFolders) { folder ->
                        engine.requireApi().userLibraryApi.getLatestMedia(
                            parentId = folder.id,
                            limit = limit,
                            fields = LIST_ITEM_FIELDS,
                        ).content ?: emptyList()
                    }
                        .flatten()
                        .map { it.toMediaItem() }.distinctBy { it.id }.take(limit)
                }.getOrDefault(emptyList())
            }
            val activityDigest = async {
                runCatchingRethrowingCancellation {
                    val result = engine.requireApi().activityLogApi.getLogEntries(
                        limit = limit,
                        minDate = java.time.LocalDateTime.parse(sinceDate),
                    ).content
                    result.items.map { it.toActivityModel() }
                }.getOrDefault(emptyList())
            }
            val libraryStats = async {
                runCatchingRethrowingCancellation {
                    engine.requireApi().libraryApi.getItemCounts().content.toItemCounts()
                }.getOrNull()
            }
            val continueWatching = async {
                runCatchingRethrowingCancellation {
                    val response = engine.requireApi().itemsApi.getResumeItems(
                        limit = 10,
                        fields = LIST_ITEM_FIELDS,
                    ).content
                    (response?.items ?: emptyList()).map { it.toMediaItem() }
                }.getOrDefault(emptyList())
            }
            val nextUp = async {
                runCatchingRethrowingCancellation {
                    val response = engine.requireApi().tvShowsApi.getNextUp(
                        limit = 10,
                        fields = LIST_ITEM_FIELDS,
                    ).content
                    (response?.items ?: emptyList()).map { it.toMediaItem() }
                }.getOrDefault(emptyList())
            }
            val curatedPicks = async {
                runCatchingRethrowingCancellation {
                    val response = engine.requireApi().itemsApi.getItems(
                        includeItemTypes = listOf(
                            org.jellyfin.sdk.model.api.BaseItemKind.MOVIE,
                            org.jellyfin.sdk.model.api.BaseItemKind.SERIES,
                        ),
                        excludeItemTypes = listOf(
                            org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET,
                        ),
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                        recursive = true,
                        fields = LIST_ITEM_FIELDS,
                    ).content
                    (response?.items ?: emptyList())
                        .map { it.toMediaItem() }
                        .filter { it.mediaType != MediaType.COLLECTION }
                }.getOrDefault(emptyList())
            }
            NewsletterData(
                serverName = serverName.await(),
                recentlyAdded = recentlyAdded.await(),
                activityDigest = activityDigest.await(),
                libraryStats = libraryStats.await(),
                continueWatching = continueWatching.await(),
                nextUp = nextUp.await(),
                curatedPicks = curatedPicks.await(),
            )
        }
    }

    override suspend fun sendNewsletter(): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.postStatusOnly("/newsletter/send", "Failed to send newsletter")
    }

    override suspend fun sendTestNewsletter(): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.postStatusOnly("/newsletter/test", "Failed to send test newsletter")
    }

    override suspend fun getUsers(): Result<List<JellyfinUser>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val response = api.userApi.getUsers().content ?: emptyList()
        response.map(::toJellyfinUser)
    }

    override suspend fun getUserById(userId: String): Result<JellyfinUser> = engine.apiResultWithRetry {
        engine.requireApi().userApi.getUserById(java.util.UUID.fromString(userId)).content.let(::toJellyfinUser)
    }

    private fun toJellyfinUser(dto: org.jellyfin.sdk.model.api.UserDto): JellyfinUser = JellyfinUser(
        id = dto.id.toString(),
        name = dto.name ?: "",
        primaryImageTag = dto.primaryImageTag,
        lastLoginDate = dto.lastLoginDate?.toString(),
        lastActivityDate = dto.lastActivityDate?.toString(),
        isAdmin = dto.policy?.isAdministrator ?: false,
        isDisabled = dto.policy?.isDisabled ?: false,
        isHidden = false,
        hasPassword = dto.hasPassword,
    )

    override suspend fun getUserPlayedItemCount(userId: String, includeItemTypes: List<String>?): Result<Int> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val types = includeItemTypes?.mapNotNull { parseItemKind(it) } ?: emptyList()
        val response = api.itemsApi.getItems(
            userId = java.util.UUID.fromString(userId),
            isPlayed = true,
            includeItemTypes = types,
            limit = 0,
            recursive = true,
            enableTotalRecordCount = true,
        ).content
        response?.totalRecordCount ?: 0
    }

    override suspend fun getUserUnplayedItemCount(userId: String, includeItemTypes: List<String>?): Result<Int> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val types = includeItemTypes?.mapNotNull { parseItemKind(it) } ?: emptyList()
        val response = api.itemsApi.getItems(
            userId = java.util.UUID.fromString(userId),
            isPlayed = false,
            includeItemTypes = types,
            limit = 0,
            recursive = true,
            enableTotalRecordCount = true,
        ).content
        response?.totalRecordCount ?: 0
    }

    override suspend fun getItemsWithUserData(
        userId: String,
        includeItemTypes: List<String>?,
        isPlayed: Boolean?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
    ): Result<Pair<Int, List<MediaItem>>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val types = includeItemTypes?.mapNotNull { parseItemKind(it) } ?: emptyList()
        val sortList = parseItemSortList(sortBy)
        val order = if (sortOrder == "Descending") SortOrder.DESCENDING else SortOrder.ASCENDING
        val response = api.itemsApi.getItems(
            userId = java.util.UUID.fromString(userId),
            isPlayed = isPlayed,
            includeItemTypes = types,
            sortBy = sortList.takeIf { it.isNotEmpty() },
            sortOrder = listOf(order),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            enableTotalRecordCount = true,
            fields = LIST_ITEM_FIELDS,
        ).content
        val total = response?.totalRecordCount ?: 0
        val items = (response?.items ?: emptyList()).map { it.toMediaItem() }
        Pair(total, items)
    }

    override suspend fun getStaleItems(
        daysThreshold: Int,
        includeNeverPlayed: Boolean,
        includeItemTypes: List<String>,
        parentId: String?,
        startIndex: Int,
        limit: Int,
        useDateAdded: Boolean,
    ): Result<Pair<Int, List<StaleMediaItem>>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val types = includeItemTypes.mapNotNull { parseItemKind(it) }
        val allItems = mutableListOf<StaleMediaItem>()
        var totalEstimate = 0

        val (playedResponse, unplayedResponse) = coroutineScope {
            val parentIdUuid = parentId?.let { java.util.UUID.fromString(it) }

            suspend fun fetchPage(isPlayed: Boolean, pageStartIndex: Int, sortBy: ItemSortBy) =
                api.itemsApi.getItems(
                    isPlayed = isPlayed,
                    includeItemTypes = types,
                    parentId = parentIdUuid,
                    startIndex = pageStartIndex,
                    limit = limit,
                    recursive = true,
                    enableTotalRecordCount = true,
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    fields = listOf(
                        ItemFields.OVERVIEW,
                        ItemFields.DATE_CREATED,
                    ),
                ).content

            val playedDeferred = async { fetchPage(isPlayed = true, pageStartIndex = startIndex, sortBy = ItemSortBy.DATE_PLAYED) }
            val unplayedDeferred = if (includeNeverPlayed) async {
                fetchPage(isPlayed = false, pageStartIndex = 0, sortBy = ItemSortBy.DATE_CREATED)
            } else null
            playedDeferred.await() to unplayedDeferred?.await()
        }

        val playedItems = playedResponse?.items ?: emptyList()
        totalEstimate = playedResponse?.totalRecordCount ?: 0

        val now = java.time.LocalDateTime.now()
        for (dto in playedItems) {
            val userData = dto.userData
            val lastPlayedStr = userData?.lastPlayedDate?.toString()
            val lastPlayed = userData?.lastPlayedDate
            val dateCreated = dto.dateCreated
            val referenceDate = if (useDateAdded) dateCreated else lastPlayed
            val daysSince = if (referenceDate != null) {
                java.time.Duration.between(referenceDate, now).toDays().toInt()
            } else {
                Int.MAX_VALUE
            }
            if (daysSince >= daysThreshold) {
                allItems.add(
                    dto.toStaleMediaItem(
                        lastPlayedDate = lastPlayedStr,
                        daysSincePlay = daysSince,
                        playCount = userData?.playCount ?: 0,
                    )
                )
            }
        }

        if (unplayedResponse != null) {
            val unplayedItems = unplayedResponse?.items ?: emptyList()
            totalEstimate += unplayedResponse?.totalRecordCount ?: 0

            for (dto in unplayedItems) {
                val userData = dto.userData
                if ((userData?.playCount ?: 0) == 0) {
                    val created = dto.dateCreated
                    val daysSinceCreation = if (created != null) {
                        java.time.Duration.between(created, now).toDays().toInt()
                    } else Int.MAX_VALUE
                    if (daysSinceCreation >= daysThreshold) {
                        allItems.add(
                            dto.toStaleMediaItem(
                                lastPlayedDate = null,
                                daysSincePlay = daysSinceCreation,
                                playCount = 0,
                            )
                        )
                    }
                }
            }
        }

        Pair(totalEstimate, allItems)
    }

    override suspend fun getWatchedItems(
        userId: String,
        includeItemTypes: List<String>,
        minDaysSincePlayed: Int,
        keepFavorites: Boolean,
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<Pair<Int, List<WatchedMediaItem>>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val types = includeItemTypes.mapNotNull { parseItemKind(it) }
        val response = api.itemsApi.getItems(
            userId = java.util.UUID.fromString(userId),
            isPlayed = true,
            includeItemTypes = types,
            parentId = parentId?.let { java.util.UUID.fromString(it) },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            enableTotalRecordCount = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.DATE_CREATED,
            ),
        ).content

        val items = (response?.items ?: emptyList())
        val now = java.time.LocalDateTime.now()
        val filtered = items.filter { dto ->
            if (keepFavorites && dto.userData?.isFavorite == true) return@filter false
            val lastPlayed = dto.userData?.lastPlayedDate
            if (minDaysSincePlayed > 0 && lastPlayed != null) {
                val daysSince = java.time.Duration.between(lastPlayed, now).toDays().toInt()
                daysSince >= minDaysSincePlayed
            } else true
        }

        val total = response?.totalRecordCount ?: 0
        val watchedItems = filtered.map { dto ->
            val userData = dto.userData
            val runtime = dto.runTimeTicks ?: 0L
            val position = userData?.playbackPositionTicks ?: 0L
            val completionPct = if (runtime > 0) ((runtime - position).toFloat() / runtime).coerceIn(0f, 1f) else if (userData?.played == true) 1f else 0f
            WatchedMediaItem(
                itemId = dto.id?.toString() ?: "",
                name = dto.name ?: "",
                type = dto.type?.serialName ?: "",
                mediaType = dto.mediaType?.serialName,
                playCount = userData?.playCount ?: 0,
                lastPlayedDate = userData?.lastPlayedDate?.toString(),
                completionPct = completionPct,
                runtimeTicks = runtime,
                isFavorite = userData?.isFavorite ?: false,
                parentId = dto.parentId?.toString(),
                seriesName = dto.seriesName,
                seasonName = dto.seasonName,
                seasonNumber = dto.parentIndexNumber,
                episodeNumber = dto.indexNumber,
                posterBlurHash = dto.imageBlurHashes
                    ?.get(ImageType.PRIMARY)
                    ?.values?.firstOrNull(),
                overview = dto.overview,
                year = dto.productionYear,
                sizeBytes = 0,
            )
        }
        Pair(total, watchedItems)
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().libraryApi.deleteItem(itemId = java.util.UUID.fromString(itemId))
    }

    override suspend fun deleteItems(itemIds: List<String>): Result<Int> = engine.apiResultWithRetry {
        engine.requireApi().libraryApi.deleteItems(
            ids = itemIds.map { java.util.UUID.fromString(it) },
        )
        itemIds.size
    }

    override suspend fun checkPlaybackReportingPlugin(): Result<PlaybackReportingStatus> = engine.apiResultWithRetry {
        if (rawRequester.getBodyText("/user_usage_stats/type_filter_list") != null) {
            PlaybackReportingStatus.AVAILABLE
        } else {
            PlaybackReportingStatus.UNAVAILABLE
        }
    }

    override suspend fun getPlaybackReportingUserActivity(days: Int): Result<List<PlaybackReportingActivity>> = engine.apiResultWithRetry {
        rawRequester.getJson("/user_usage_stats/user_activity?days=$days", "Plugin request failed") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "")
            json.mapNotNull { element ->
                val obj = element.jsonObject
                PlaybackReportingActivity(
                    userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                    userName = obj["user_name"]?.jsonPrimitive?.content ?: "",
                    totalTime = obj["total_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    latestDate = obj["latest_date"]?.jsonPrimitive?.content ?: "",
                    totalPlayTime = obj["total_play_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    hasImage = obj["has_image"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                )
            }
        }
    }

    private suspend fun getPlaybackReportingTypeFilterList(): List<String> =
        runCatchingRethrowingCancellation {
            rawRequester.getBodyText("/user_usage_stats/type_filter_list")?.let { body ->
                JellyfinApiEngine.sharedJson.decodeFromString<List<String>>(body)
            } ?: emptyList()
        }.getOrDefault(emptyList())

    override suspend fun getPlaybackReportingPlayActivity(days: Int, dataType: String, filter: String?): Result<List<PlaybackActivityPoint>> = engine.apiResultWithRetry {
        val currentUserId = engine.currentUserId()
        var targetUserId: String? = null
        val mediaTypes = mutableListOf<String>()

        if (filter != null) {
            val tokens = filter.split(",")
            for (tokenStr in tokens) {
                val trimmed = tokenStr.trim()
                if (isPlaybackReportingUserIdToken(trimmed)) {
                    targetUserId = trimmed
                } else if (trimmed.isNotEmpty()) {
                    mediaTypes.add(trimmed)
                }
            }
        }

        if (targetUserId == null) {
            targetUserId = currentUserId
        }

        val serverFilter = if (mediaTypes.isNotEmpty()) {
            mediaTypes.joinToString(",")
        } else {
            val fetchedTypes = getPlaybackReportingTypeFilterList()
            if (fetchedTypes.isNotEmpty()) {
                fetchedTypes.joinToString(",")
            } else {
                "Movie,Episode,Audio,Video,MusicVideo,TvChannel,Recording"
            }
        }

        rawRequester.getJson("/user_usage_stats/PlayActivity?days=$days&dataType=$dataType&filter=$serverFilter", "Plugin request failed") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "")
            val points = mutableListOf<PlaybackActivityPoint>()

            val targetClean = targetUserId?.replace("-", "")?.lowercase()

            for (element in json) {
                val obj = element.jsonObject
                val entryId = obj["user_id"]?.jsonPrimitive?.content ?: ""
                val entryClean = entryId.replace("-", "").lowercase()

                if (targetClean == null || entryClean == targetClean) {
                    val usage = obj["user_usage"]?.jsonObject
                    if (usage != null) {
                        for ((date, value) in usage) {
                            points.add(
                                PlaybackActivityPoint(
                                    date = date,
                                    value = (value as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                                )
                            )
                        }
                    }
                }
            }
            points.sortedBy { it.date }
        }
    }

    override suspend fun getPlaybackReportingUserItems(userId: String, date: String, filter: String?): Result<List<PlaybackReportingDetail>> = engine.apiResultWithRetry {
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        rawRequester.getJson("/user_usage_stats/$userId/$date/GetItems?$filterParam", "Plugin request failed") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "")
            json.mapNotNull { element ->
                val obj = element.jsonObject
                PlaybackReportingDetail(
                    time = obj["Time"]?.jsonPrimitive?.content ?: "",
                    itemId = obj["Id"]?.jsonPrimitive?.content ?: "",
                    name = obj["Name"]?.jsonPrimitive?.content ?: "",
                    type = obj["Type"]?.jsonPrimitive?.content ?: "",
                    client = obj["Client"]?.jsonPrimitive?.content ?: "",
                    method = obj["Method"]?.jsonPrimitive?.content ?: "",
                    device = obj["Device"]?.jsonPrimitive?.content ?: "",
                    duration = obj["Duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
        }
    }

    override suspend fun getPlaybackReportingBreakdown(breakdownType: String, days: Int, filter: String?): Result<List<ContentBreakdown>> {
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        return fetchBreakdownReport("/user_usage_stats/$breakdownType/BreakdownReport?days=$days$filterParam")
    }

    override suspend fun getPlaybackReportingArtistBreakdown(days: Int, filter: String?): Result<List<ContentBreakdown>> {
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        return fetchBreakdownReport("/user_usage_stats/Parent/BreakdownReport?days=$days$filterParam")
    }

    /**
     * Shared BreakdownReport fetch: both breakdown endpoints return the same
     * array shape, so only the request path differs between them (decode in
     * [parseBreakdownReport]).
     */
    private suspend fun fetchBreakdownReport(path: String): Result<List<ContentBreakdown>> = engine.apiResultWithRetry {
        rawRequester.getJson(path, "Plugin request failed") { body ->
            parseBreakdownReport(body?.string() ?: "")
        }
    }

    private companion object {
        const val KEY_SERVER_NAME = "serverName"
    }
}

/**
 * True when a comma token from the playback-reporting `filter` string looks
 * like a user id (32–36 chars — dashed or bare-hex UUID) rather than a media
 * type name. The plugin accepts either form in the same param, so
 * [MediaInfoApiClientImpl.getPlaybackReportingPlayActivity] routes tokens by
 * this shape test. Internal for tests.
 */
internal fun isPlaybackReportingUserIdToken(token: String): Boolean =
    token.length in 32..36 && (token.contains("-") || token.all { it.isLetterOrDigit() })

/**
 * Decodes a playback-reporting BreakdownReport body — the fold shared by
 * [MediaInfoApiClientImpl.getPlaybackReportingBreakdown] and
 * [MediaInfoApiClientImpl.getPlaybackReportingArtistBreakdown]. The plugin's
 * field naming varies by report type (`label` vs `name`, `total` vs `count`
 * vs `value`), so each fold falls through in plugin-recorded order; row order
 * becomes the colorIndex. Internal for tests.
 */
internal fun parseBreakdownReport(bodyText: String): List<ContentBreakdown> {
    val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(bodyText)
    return json.mapIndexed { index, element ->
        val obj = element.jsonObject
        ContentBreakdown(
            label = obj["label"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
                ?: "",
            value = obj["total"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: obj["count"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: obj["value"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: 0,
            colorIndex = index,
        )
    }
}

/**
 * Shared [StaleMediaItem] projection for getStaleItems' played + unplayed
 * branches: the field mapping is verbatim-identical, only the watched-state
 * inputs ([lastPlayedDate], [daysSincePlay], [playCount]) differ per branch.
 * Internal for tests.
 */
internal fun BaseItemDto.toStaleMediaItem(
    lastPlayedDate: String?,
    daysSincePlay: Int,
    playCount: Int,
): StaleMediaItem = StaleMediaItem(
    itemId = id?.toString() ?: "",
    name = name ?: "",
    type = type?.serialName ?: "",
    mediaType = mediaType?.serialName,
    lastPlayedDate = lastPlayedDate,
    daysSincePlay = daysSincePlay,
    playCount = playCount,
    sizeBytes = 0,
    sizeText = "",
    parentId = parentId?.toString(),
    seriesName = seriesName,
    seasonName = seasonName,
    seasonNumber = parentIndexNumber,
    episodeNumber = indexNumber,
    posterBlurHash = imageBlurHashes
        ?.get(ImageType.PRIMARY)
        ?.values?.firstOrNull(),
    premiereDate = premiereDate?.toString(),
    overview = overview,
    year = productionYear,
    dateAdded = dateCreated?.toString(),
)
