package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.ItemCounts
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    // The server name is effectively session-static but lives below the
    // repository layer (which already caches library folders), so every
    // newsletter render previously bypassed the in-memory cache. Short TTL
    // keeps it fresh across server renames without per-render network calls.
    private val serverNameCache = TtlCache<String>(maxSize = 4, ttlMs = 30 * 60 * 1000L)

    private suspend fun getCachedServerName(): String {
        serverNameCache.get(KEY_SERVER_NAME)?.let { return it }
        return try {
            val name = engine.requireApi().systemApi.getSystemInfo().content.serverName ?: ""
            serverNameCache.put(KEY_SERVER_NAME, name)
            name
        } catch (_: Exception) { "" }
    }

    /**
     * Active endpoint + auth token for the raw-OkHttp endpoints in this client
     * (the SDK calls use `requireApi` instead). Fails like every caller used
     * to: not-connected / not-authenticated as [IllegalStateException].
     */
    private fun requireSession(): Pair<String, String> {
        val server = engine.currentServer.value?.address ?: throw IllegalStateException("Not connected")
        val token = engine.currentUser.value?.accessToken ?: throw IllegalStateException("Not authenticated")
        return server to token
    }

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> = engine.apiResultWithRetry {
        coroutineScope {
            val serverName = async { getCachedServerName() }
            val recentlyAdded = async {
                try {
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
                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                    candidateFolders
                        .map { folder ->
                            async {
                                semaphore.acquire()
                                try {
                                    engine.requireApi().userLibraryApi.getLatestMedia(
                                        parentId = folder.id,
                                        limit = limit,
                                        fields = listOf(
                                            ItemFields.OVERVIEW,
                                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                        ),
                                    ).content ?: emptyList()
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll()
                        .flatMap { it }
                        .map { it.toMediaItem() }.distinctBy { it.id }.take(limit)
                } catch (_: Exception) { emptyList() }
            }
            val activityDigest = async {
                try {
                    val result = engine.requireApi().activityLogApi.getLogEntries(
                        limit = limit,
                        minDate = java.time.LocalDateTime.parse(sinceDate),
                    ).content
                    result.items.map { it.toActivityModel() }
                } catch (_: Exception) { emptyList() }
            }
            val libraryStats = async {
                try {
                    val dto = engine.requireApi().libraryApi.getItemCounts().content
                    ItemCounts(
                        movieCount = dto.movieCount.toLong(),
                        seriesCount = dto.seriesCount.toLong(),
                        episodeCount = dto.episodeCount.toLong(),
                        albumCount = dto.albumCount.toLong(),
                        songCount = dto.songCount.toLong(),
                        musicVideoCount = dto.musicVideoCount.toLong(),
                        bookCount = dto.bookCount.toLong(),
                        totalCount = dto.movieCount.toLong() + dto.seriesCount.toLong() +
                                dto.episodeCount.toLong() + dto.albumCount.toLong() +
                                dto.songCount.toLong() + dto.musicVideoCount.toLong() +
                                dto.bookCount.toLong(),
                    )
                } catch (_: Exception) { null }
            }
            val continueWatching = async {
                try {
                    val response = engine.requireApi().itemsApi.getResumeItems(
                        limit = 10,
                        fields = listOf(
                            ItemFields.OVERVIEW,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ),
                    ).content
                    (response?.items ?: emptyList()).map { it.toMediaItem() }
                } catch (_: Exception) { emptyList() }
            }
            val nextUp = async {
                try {
                    val response = engine.requireApi().tvShowsApi.getNextUp(
                        limit = 10,
                        fields = listOf(
                            ItemFields.OVERVIEW,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ),
                    ).content
                    (response?.items ?: emptyList()).map { it.toMediaItem() }
                } catch (_: Exception) { emptyList() }
            }
            val curatedPicks = async {
                try {
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
                        fields = listOf(
                            ItemFields.OVERVIEW,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ),
                    ).content
                    (response?.items ?: emptyList())
                        .map { it.toMediaItem() }
                        .filter { it.mediaType != MediaType.COLLECTION }
                } catch (_: Exception) { emptyList() }
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
        val (server, token) = requireSession()
        val request = Request.Builder()
            .url("${server}/newsletter/send")
            .header("X-Emby-Token", token)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to send newsletter: ${response.code}")
        }
    }

    override suspend fun sendTestNewsletter(): Result<Unit> = engine.apiResultWithRetry {
        val (server, token) = requireSession()
        val request = Request.Builder()
            .url("${server}/newsletter/test")
            .header("X-Emby-Token", token)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to send test newsletter: ${response.code}")
        }
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
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
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

        val playedResponse = api.itemsApi.getItems(
            isPlayed = true,
            includeItemTypes = types,
            parentId = parentId?.let { java.util.UUID.fromString(it) },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            enableTotalRecordCount = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.DATE_CREATED,
            ),
        ).content

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
                    StaleMediaItem(
                        itemId = dto.id?.toString() ?: "",
                        name = dto.name ?: "",
                        type = dto.type?.serialName ?: "",
                        mediaType = dto.mediaType?.serialName,
                        lastPlayedDate = lastPlayedStr,
                        daysSincePlay = daysSince,
                        playCount = userData?.playCount ?: 0,
                        sizeBytes = 0,
                        sizeText = "",
                        parentId = dto.parentId?.toString(),
                        seriesName = dto.seriesName,
                        seasonName = dto.seasonName,
                        seasonNumber = dto.parentIndexNumber,
                        episodeNumber = dto.indexNumber,
                        posterBlurHash = dto.imageBlurHashes
                            ?.get(ImageType.PRIMARY)
                            ?.values?.firstOrNull(),
                        premiereDate = dto.premiereDate?.toString(),
                        overview = dto.overview,
                        year = dto.productionYear,
                        dateAdded = dto.dateCreated?.toString(),
                    )
                )
            }
        }

        if (includeNeverPlayed) {
            val unplayedResponse = api.itemsApi.getItems(
                isPlayed = false,
                includeItemTypes = types,
                parentId = parentId?.let { java.util.UUID.fromString(it) },
                startIndex = 0,
                limit = limit,
                recursive = true,
                enableTotalRecordCount = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.DATE_CREATED,
                ),
            ).content

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
                            StaleMediaItem(
                                itemId = dto.id?.toString() ?: "",
                                name = dto.name ?: "",
                                type = dto.type?.serialName ?: "",
                                mediaType = dto.mediaType?.serialName,
                                lastPlayedDate = null,
                                daysSincePlay = daysSinceCreation,
                                playCount = 0,
                                sizeBytes = 0,
                                sizeText = "",
                                parentId = dto.parentId?.toString(),
                                seriesName = dto.seriesName,
                                seasonName = dto.seasonName,
                                seasonNumber = dto.parentIndexNumber,
                                episodeNumber = dto.indexNumber,
                                posterBlurHash = dto.imageBlurHashes
                                    ?.get(ImageType.PRIMARY)
                                    ?.values?.firstOrNull(),
                                premiereDate = dto.premiereDate?.toString(),
                                overview = dto.overview,
                                year = dto.productionYear,
                                dateAdded = dto.dateCreated?.toString(),
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
        val (server, token) = requireSession()
        val url = "${server}/user_usage_stats/type_filter_list"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                PlaybackReportingStatus.AVAILABLE
            } else {
                PlaybackReportingStatus.UNAVAILABLE
            }
        }
    }

    override suspend fun getPlaybackReportingUserActivity(days: Int): Result<List<PlaybackReportingActivity>> = engine.apiResultWithRetry {
        val (server, token) = requireSession()
        val url = "${server}/user_usage_stats/user_activity?days=$days"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Plugin request failed: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
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

    private suspend fun getPlaybackReportingTypeFilterList(server: String, token: String): List<String> {
        val url = "${server}/user_usage_stats/type_filter_list"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        return try {
            engine.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    JellyfinApiEngine.sharedJson.decodeFromString<List<String>>(body)
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getPlaybackReportingPlayActivity(days: Int, dataType: String, filter: String?): Result<List<PlaybackActivityPoint>> = engine.apiResultWithRetry {
        val (server, token) = requireSession()

        val currentUserId = engine.currentUser.value?.id
        var targetUserId: String? = null
        val mediaTypes = mutableListOf<String>()

        if (filter != null) {
            val tokens = filter.split(",")
            for (tokenStr in tokens) {
                val trimmed = tokenStr.trim()
                if (trimmed.length in 32..36 && (trimmed.contains("-") || trimmed.all { it.isLetterOrDigit() })) {
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
            val fetchedTypes = getPlaybackReportingTypeFilterList(server, token)
            if (fetchedTypes.isNotEmpty()) {
                fetchedTypes.joinToString(",")
            } else {
                "Movie,Episode,Audio,Video,MusicVideo,TvChannel,Recording"
            }
        }

        val url = "${server}/user_usage_stats/PlayActivity?days=$days&dataType=$dataType&filter=$serverFilter"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Plugin request failed: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
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
        val (server, token) = requireSession()
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        val url = "${server}/user_usage_stats/$userId/$date/GetItems?$filterParam"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Plugin request failed: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
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

    override suspend fun getPlaybackReportingBreakdown(breakdownType: String, days: Int, filter: String?): Result<List<ContentBreakdown>> = engine.apiResultWithRetry {
        val (server, token) = requireSession()
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        val url = "${server}/user_usage_stats/$breakdownType/BreakdownReport?days=$days$filterParam"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Plugin request failed: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
            json.mapIndexed { index, element ->
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
    }

    override suspend fun getPlaybackReportingArtistBreakdown(days: Int, filter: String?): Result<List<ContentBreakdown>> = engine.apiResultWithRetry {
        val (server, token) = requireSession()
        val filterParam = filter?.let { "&filter=$it" } ?: ""
        val url = "${server}/user_usage_stats/Parent/BreakdownReport?days=$days$filterParam"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Plugin request failed: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
            json.mapIndexed { index, element ->
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
    }

    private companion object {
        const val KEY_SERVER_NAME = "serverName"
    }
}
