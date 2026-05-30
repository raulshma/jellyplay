package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.model.DeviceCapabilities
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.ImageBlurHashes
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SessionPlayState
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.model.TaskTriggerInfo
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfin: Jellyfin,
    private val okHttpClient: OkHttpClient,
) : JellyfinApiClient {

    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    override val currentServer: Flow<ServerInfo?> = _currentServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    override val currentUser: Flow<UserInfo?> = _currentUser.asStateFlow()

    @Volatile
    private var api: ApiClient? = null

    private fun requireApi(): ApiClient =
        api ?: throw IllegalStateException("Not connected to server")

    private suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        runCatching { withContext(Dispatchers.IO) { block() } }

    private companion object {
        val sharedJson = Json { ignoreUnknownKeys = true }
        private val CACHED_CAPABILITIES by lazy {
            org.jellyfin.sdk.model.api.ClientCapabilitiesDto(
                playableMediaTypes = listOf(
                    org.jellyfin.sdk.model.api.MediaType.VIDEO,
                    org.jellyfin.sdk.model.api.MediaType.AUDIO,
                ),
                supportedCommands = org.jellyfin.sdk.model.api.GeneralCommandType.entries,
                supportsMediaControl = true,
                supportsPersistentIdentifier = true,
                deviceProfile = org.jellyfin.sdk.model.api.DeviceProfile(
                    directPlayProfiles = emptyList(),
                    transcodingProfiles = emptyList(),
                    containerProfiles = emptyList(),
                    codecProfiles = emptyList(),
                    subtitleProfiles = listOf(
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "srt",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "ass",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "ssa",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "subrip",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "vtt",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                        org.jellyfin.sdk.model.api.SubtitleProfile(
                            format = "webvtt",
                            method = org.jellyfin.sdk.model.api.SubtitleDeliveryMethod.EXTERNAL,
                        ),
                    ),
                ),
            )
        }
    }

    private val currentMaxParentalRating: Int?
        get() = _currentUser.value?.maxParentalAgeRating

    private fun ratingToAge(rating: String): Int? = when (rating.uppercase()) {
        "G", "TV-Y", "TV-G" -> 0
        "PG", "TV-Y7", "TV-PG" -> 7
        "PG-13", "TV-14" -> 13
        "R", "TV-MA" -> 17
        "NC-17" -> 18
        else -> null
    }

    private fun List<MediaItem>.filterByParentalRating(): List<MediaItem> {
        val max = currentMaxParentalRating ?: return this
        return mapNotNull { item ->
            if (item.officialRating?.let { rating ->
                ratingToAge(rating)?.let { age -> age <= max }
            } != false) item else null
        }
    }

    override suspend fun connectToServer(address: String): Result<ServerInfo> = runCatching {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(normalizedAddress)
                val systemInfo = client.systemApi.getPublicSystemInfo().content
                val info = ServerInfo(
                    id = systemInfo.id?.toString() ?: java.util.UUID.randomUUID().toString(),
                    name = systemInfo.serverName ?: "Jellyfin Server",
                    address = normalizedAddress,
                )
                _currentServer.value = info
                info
            } catch (e: Exception) {
                Log.e("JellyfinApi", "connectToServer failed for $normalizedAddress", e)
                throw e
            }
        }
    }

    override suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> = authenticateUser(
        serverInfo = _currentServer.value ?: connectToServer(serverAddress).getOrThrow(),
        username = username,
        password = password,
    )

    override suspend fun authenticateUser(
        serverInfo: ServerInfo,
        username: String,
        password: String,
    ): Result<UserInfo> = apiResult {
        _currentServer.value = serverInfo
        val client = jellyfin.createApi(serverInfo.address)
        val authResult = client.userApi.authenticateUserByName(
            org.jellyfin.sdk.model.api.AuthenticateUserByName(
                username = username,
                pw = password,
            )
        ).content
        val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
        val authenticatedClient = jellyfin.createApi(
            baseUrl = serverInfo.address,
            accessToken = accessTokenValue,
        )
        api = authenticatedClient
        val userDto = authResult.user ?: throw Exception("Authentication failed")
        val policy = userDto.policy
        val userInfo = UserInfo(
            id = userDto.id.toString(),
            name = userDto.name ?: username,
            serverAddress = serverInfo.address,
            accessToken = accessTokenValue,
            isAdmin = policy?.isAdministrator ?: false,
            maxParentalAgeRating = policy?.maxParentalRating,
            primaryImageTag = userDto.primaryImageTag,
            enabledFolderIds = if (policy?.enableAllFolders == false) {
                policy.enabledFolders?.map { it.toString() } ?: emptyList()
            } else emptyList(),
        )
        _currentUser.value = userInfo
        _currentServer.value = serverInfo.copy(
            userId = userInfo.id,
            accessToken = userInfo.accessToken,
            isConnected = true,
        )
        userInfo
    }

    override suspend fun setServer(serverInfo: ServerInfo) {
        _currentServer.value = serverInfo
    }

    override suspend fun setUser(userInfo: UserInfo) {
        _currentUser.value = userInfo
        val server = _currentServer.value ?: return
        api = jellyfin.createApi(
            baseUrl = server.address,
            accessToken = userInfo.accessToken,
        )
    }

    override suspend fun disconnect() {
        api = null
        _currentUser.value = null
        _currentServer.value = null
    }

    override suspend fun isQuickConnectEnabled(): Result<Boolean> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = api ?: jellyfin.createApi(server.address)
        client.quickConnectApi.getQuickConnectEnabled().content
    }

    override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = api ?: jellyfin.createApi(server.address)
        val result = client.quickConnectApi.initiateQuickConnect().content
        QuickConnectInfo(
            secret = result.secret,
            code = result.code,
        )
    }

    override suspend fun getQuickConnectState(secret: String): Result<QuickConnectState> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("Not connected to server")
        val client = api ?: jellyfin.createApi(server.address)
        val result = client.quickConnectApi.getQuickConnectState(secret).content
        QuickConnectState(
            authenticated = result.authenticated,
            secret = result.secret,
        )
    }

    override suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo> = apiResult {
        _currentServer.value = serverInfo
        val client = jellyfin.createApi(serverInfo.address)
        val authResult = client.userApi.authenticateWithQuickConnect(
            org.jellyfin.sdk.model.api.QuickConnectDto(secret = secret)
        ).content
        val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
        val authenticatedClient = jellyfin.createApi(
            baseUrl = serverInfo.address,
            accessToken = accessTokenValue,
        )
        api = authenticatedClient
        val userDto = authResult.user ?: throw Exception("Quick Connect authentication failed")
        val policy = userDto.policy
        val userInfo = UserInfo(
            id = userDto.id.toString(),
            name = userDto.name ?: "",
            serverAddress = serverInfo.address,
            accessToken = accessTokenValue,
            isAdmin = policy?.isAdministrator ?: false,
            maxParentalAgeRating = policy?.maxParentalRating,
            primaryImageTag = userDto.primaryImageTag,
            enabledFolderIds = if (policy?.enableAllFolders == false) {
                policy.enabledFolders?.map { it.toString() } ?: emptyList()
            } else emptyList(),
        )
        _currentUser.value = userInfo
        _currentServer.value = serverInfo.copy(
            userId = userInfo.id,
            accessToken = userInfo.accessToken,
            isConnected = true,
        )
        userInfo
    }

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
    ): Result<List<HomeSection>> = apiResult {
        coroutineScope {
            val sections = mutableListOf<HomeSection>()
            var firstError: Throwable? = null

            val continueWatchingDeferred = async {
                if (HomeSectionType.CONTINUE_WATCHING in enabledSections) getContinueWatching()
                else Result.success(emptyList())
            }
            val nextUpDeferred = async {
                if (HomeSectionType.NEXT_UP in enabledSections) getNextUp()
                else Result.success(emptyList())
            }
            val foldersDeferred = async {
                if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
                    getLibraryFolders()
                } else {
                    Result.success(emptyList())
                }
            }

            val continueWatchingResult = continueWatchingDeferred.await()
            val nextUpResult = nextUpDeferred.await()
            val foldersResult = foldersDeferred.await()

            var continueWatchingIds = emptySet<String>()

            if (HomeSectionType.CONTINUE_WATCHING in enabledSections) {
                continueWatchingResult
                    .onSuccess { list ->
                        if (list.isNotEmpty()) {
                            continueWatchingIds = list.map { it.id }.toSet()
                            sections.add(HomeSection("continue_watching", "Continue Watching", HomeSectionType.CONTINUE_WATCHING, list))
                        }
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }

            if (HomeSectionType.NEXT_UP in enabledSections) {
                nextUpResult
                    .onSuccess { list ->
                        val filtered = list.filter { it.id !in continueWatchingIds }
                        if (filtered.isNotEmpty()) {
                            sections.add(HomeSection("next_up", "Next Up", HomeSectionType.NEXT_UP, filtered))
                        }
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }

            val allLatestItems = mutableListOf<MediaItem>()

            if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
                foldersResult
                    .onSuccess { folders ->
                        val filteredFolders = folders
                            .filter { it.collectionType != "music" }
                            .filter { it.id !in hiddenLibraryIds }
                        val latestDeferred = filteredFolders
                            .map { folder ->
                                async { folder to getLatestMedia(folder.id, limit = 16) }
                            }
                        latestDeferred.forEach { deferred ->
                            val (folder, result) = deferred.await()
                            result.onSuccess { latest ->
                                allLatestItems.addAll(latest)
                                if (latest.isNotEmpty() && HomeSectionType.LATEST_MEDIA in enabledSections) {
                                    val sectionId = "latest_${folder.id}"
                                    sections.add(HomeSection(sectionId, "Latest ${folder.name}", HomeSectionType.LATEST_MEDIA, latest))
                                }
                            }
                        }
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }

            if (HomeSectionType.RECENTLY_ADDED in enabledSections) {
                val recentlyAddedItems = allLatestItems
                    .distinctBy { it.id }
                    .filter { it.id !in continueWatchingIds }
                if (recentlyAddedItems.isNotEmpty()) {
                    val recentlyAddedSection = HomeSection(
                        "recently_added",
                        "Recently Added",
                        HomeSectionType.RECENTLY_ADDED,
                        recentlyAddedItems,
                    )
                    val insertIndex = sections.indexOfFirst { it.type == HomeSectionType.LATEST_MEDIA }.coerceAtLeast(0)
                    sections.add(insertIndex, recentlyAddedSection)
                }
            }

            if (sections.isEmpty() && firstError != null) {
                throw firstError!!
            }
            sections
        }
    }

    override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            val response = requireApi().userLibraryApi.getLatestMedia(
                parentId = parentId.toUUID(),
                limit = limit,
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ),
            ).content ?: emptyList()
            response.map { it.toMediaItem() }.filter { item ->
                currentMaxParentalRating?.let { max ->
                    item.officialRating?.let { rating ->
                        ratingToAge(rating)?.let { age -> age <= max }
                    } != false
                } != false
            }
        }

    override suspend fun getNextUp(limit: Int): Result<List<MediaItem>> = apiResult {
        val response = requireApi().tvShowsApi.getNextUp(
            limit = limit,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> = apiResult {
        val response = requireApi().itemsApi.getResumeItems(
            limit = limit,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
            .distinctBy { it.id }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = apiResult {
        val response = requireApi().libraryApi.getMediaFolders().content
            ?: throw IllegalStateException("Server returned empty response")
        val enabledFolders = _currentUser.value?.enabledFolderIds
        (response.items ?: emptyList()).map { item ->
            LibraryFolder(
                id = item.id.toString(),
                name = item.name ?: "",
                collectionType = item.collectionType?.serialName,
                type = item.type?.serialName,
            )
        }.filter { folder ->
            enabledFolders.isNullOrEmpty() || folder.id in enabledFolders
        }
    }

    override suspend fun getMediaItems(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val sortByEnum = org.jellyfin.sdk.model.api.ItemSortBy.entries
            .find { it.serialName.equals(sortBy, ignoreCase = true) }
        val sortOrderEnum = org.jellyfin.sdk.model.api.SortOrder.entries
            .find { it.serialName.equals(sortOrder, ignoreCase = true) }
            ?: org.jellyfin.sdk.model.api.SortOrder.ASCENDING
        val response = requireApi().itemsApi.getItems(
            parentId = parentId?.let { it.toUUID() },
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            excludeItemTypes = listOf(
                org.jellyfin.sdk.model.api.BaseItemKind.SEASON,
                org.jellyfin.sdk.model.api.BaseItemKind.EPISODE,
            ),
            genres = genres,
            years = years,
            sortBy = listOfNotNull(sortByEnum),
            sortOrder = listOf(sortOrderEnum),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                org.jellyfin.sdk.model.api.ItemFields.GENRES,
            ),
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = apiResult {
        coroutineScope {
            val client = requireApi()
            val uuid = itemId.toUUID()
            val item = client.userLibraryApi.getItem(itemId = uuid).content
        val people = (item.people?.map { person ->
            PersonInfo(
                id = person.id.toString(),
                name = person.name ?: "",
                role = person.role,
                type = person.type?.serialName ?: "",
                primaryImageTag = person.primaryImageTag,
            )
        } ?: emptyList()).distinctBy { it.id }
        val relatedItemsDeferred = async {
            try {
                client.libraryApi.getSimilarItems(
                    itemId = uuid,
                    limit = 12,
                ).content.items.map { it.toMediaItem() }
                    .distinctBy { it.id }
                    .filter { it.id != itemId }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val relatedItems = relatedItemsDeferred.await()
        val chapters = item.chapters?.map { chapter ->
            ChapterInfo(
                name = chapter.name ?: "",
                startPositionTicks = chapter.startPositionTicks ?: 0L,
                imageDateModified = chapter.imageDateModified?.toString(),
            )
        } ?: emptyList()
        val mediaSources = item.mediaSources?.map { source ->
            com.raulshma.jellyplay.core.model.MediaSource(
                id = source.id.toString(),
                name = source.name ?: "",
                container = source.container,
                size = source.size,
                bitrate = source.bitrate?.toLong(),
                runTimeTicks = source.runTimeTicks,
                supportsTranscoding = source.supportsTranscoding,
                supportsDirectStream = source.supportsDirectStream,
                supportsDirectPlay = source.supportsDirectPlay,
                transcodeUrl = source.transcodingUrl,
                path = source.path,
                mediaStreams = source.mediaStreams?.map { stream ->
                    com.raulshma.jellyplay.core.model.MediaStream(
                        index = stream.index,
                        type = when (stream.type) {
                            org.jellyfin.sdk.model.api.MediaStreamType.VIDEO -> com.raulshma.jellyplay.core.model.StreamType.VIDEO
                            org.jellyfin.sdk.model.api.MediaStreamType.AUDIO -> com.raulshma.jellyplay.core.model.StreamType.AUDIO
                            org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE -> com.raulshma.jellyplay.core.model.StreamType.SUBTITLE
                            else -> com.raulshma.jellyplay.core.model.StreamType.EMBEDDED_IMAGE
                        },
                        codec = stream.codec,
                        language = stream.language,
                        title = stream.title,
                        displayTitle = stream.displayTitle,
                        isDefault = stream.isDefault,
                        isForced = stream.isForced,
                        isExternal = stream.isExternal,
                        width = stream.width,
                        height = stream.height,
                        bitRate = stream.bitRate?.toLong(),
                        sampleRate = stream.sampleRate,
                        channels = stream.channels,
                        deliveryUrl = stream.deliveryUrl,
                        videoRange = stream.videoRange?.serialName,
                        videoRangeType = stream.videoRangeType?.serialName,
                        realFrameRate = stream.realFrameRate,
                        videoDoViTitle = stream.videoDoViTitle,
                    )
                } ?: emptyList(),
                trickplayInfo = item.trickplay
                    ?.get(source.id.toString())
                    ?.values
                    ?.maxByOrNull { it.width ?: 0 }
                    ?.toTrickplayInfo(),
            )
        } ?: emptyList()
        val externalUrls = item.externalUrls?.map { url ->
            com.raulshma.jellyplay.core.model.ExternalUrl(
                name = url.name ?: "",
                url = url.url ?: "",
            )
        } ?: emptyList()
        val providerIds = item.providerIds?.mapNotNull { (k, v) -> v?.let { k.lowercase() to it } }?.toMap() ?: emptyMap()
        MediaDetail(
            item = item.toMediaItem(),
            sortName = item.forcedSortName,
            customRating = item.customRating,
            criticRating = item.criticRating?.toFloat(),
            taglines = item.taglines ?: emptyList(),
            productionLocations = item.productionLocations ?: emptyList(),
            lockData = item.lockData ?: false,
            lockedFields = item.lockedFields?.map { it.toString() } ?: emptyList(),
            status = item.status?.toString(),
            airDays = item.airDays?.map { it.toString() } ?: emptyList(),
            airTime = item.airTime,
            displayOrder = item.displayOrder,
            preferredMetadataLanguage = item.preferredMetadataLanguage,
            preferredMetadataCountryCode = item.preferredMetadataCountryCode,
            dateCreated = item.dateCreated?.toString(),
            people = people,
            relatedItems = relatedItems,
            chapters = chapters,
            mediaSources = mediaSources,
            externalUrls = externalUrls,
            providerIds = providerIds,
        )
        }
    }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            searchTerm = query,
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            limit = limit,
            startIndex = startIndex,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> =
        runCatching {
            val response = requireApi().genresApi.getGenres(
                parentId = parentId?.let { it.toUUID() },
                startIndex = startIndex,
                limit = limit,
            ).content
            response.items.map { item ->
                Genre(id = item.id.toString(), name = item.name ?: "")
            }
        }

    override suspend fun getItemsByGenre(
        genreId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            genreIds = listOf(genreId.toUUID()),
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            albumArtistIds = listOf(artistId.toUUID()),
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM),
            limit = limit,
            recursive = true,
            sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME),
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toMediaItem() }.filterByParentalRating()
    }

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = albumId.toUUID(),
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.PARENT_INDEX_NUMBER, org.jellyfin.sdk.model.api.ItemSortBy.INDEX_NUMBER),
            sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.ASCENDING),
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toMediaItem() }.filterByParentalRating()
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            requireApi().libraryApi.getSimilarItems(
                itemId = itemId.toUUID(),
                limit = limit,
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            val response = requireApi().itemsApi.getItems(
                personIds = listOf(personId.toUUID()),
                limit = limit,
                recursive = true,
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ),
            ).content
            response.items.map { it.toMediaItem() }.filterByParentalRating()
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = apiResult {
        requireApi().tvShowsApi.getSeasons(
            seriesId = seriesId.toUUID(),
        ).content.items.map { it.toMediaItem() }.filterByParentalRating()
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        apiResult {
            requireApi().tvShowsApi.getEpisodes(
                seriesId = seriesId.toUUID(),
                seasonId = seasonId.toUUID(),
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = collectionId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = parentId?.let { it.toUUID() },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(org.jellyfin.sdk.model.api.ItemFields.TAGS),
        ).content
        response.items.flatMap { it.tags ?: emptyList() }.distinct().sorted()
    }

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
            limit = limit,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(),
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun getLyrics(itemId: String): Result<com.raulshma.jellyplay.core.model.LyricsResult> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        LyricsApi.fetchLyrics(okHttpClient, server.address, itemId, user.accessToken)
    }

    override suspend fun getPlaylists(limit: Int): Result<List<com.raulshma.jellyplay.core.model.Playlist>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.PLAYLIST),
            limit = limit,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { item ->
            com.raulshma.jellyplay.core.model.Playlist(
                id = item.id.toString(),
                name = item.name ?: "",
                overview = item.overview,
                itemCount = item.childCount ?: 0,
                imageTag = item.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.toString(),
            )
        }
    }

    override suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int,
        limit: Int,
    ): Result<List<com.raulshma.jellyplay.core.model.PlaylistItem>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = playlistId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { item ->
            com.raulshma.jellyplay.core.model.PlaylistItem(
                id = item.id.toString(),
                name = item.name ?: "",
                artist = item.albumArtist ?: item.artistItems?.firstOrNull()?.name,
                album = item.album,
                mediaType = item.type?.toMediaType() ?: com.raulshma.jellyplay.core.model.MediaType.UNKNOWN,
                runTimeTicks = item.runTimeTicks,
            )
        }
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> = apiResult {
        requireApi().playStateApi.markPlayedItem(
            userId = _currentUser.value?.id!!.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = apiResult {
        requireApi().playStateApi.markUnplayedItem(
            userId = _currentUser.value?.id!!.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> = apiResult {
        val uuid = itemId.toUUID()
        val item = requireApi().userLibraryApi.getItem(itemId = uuid).content
        if (item.userData?.isFavorite == true) {
            requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = _currentUser.value?.id!!.toUUID(),
                itemId = uuid,
            )
            false
        } else {
            requireApi().userLibraryApi.markFavoriteItem(
                userId = _currentUser.value?.id!!.toUUID(),
                itemId = uuid,
            )
            true
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod,
    ): Result<Unit> =
        runCatching {
            val uuid = itemId.toUUID()
            val sdkPlayMethod = when (playMethod) {
                com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY
                com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_STREAM -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_STREAM
                com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE -> org.jellyfin.sdk.model.api.PlayMethod.TRANSCODE
            }
            requireApi().playStateApi.reportPlaybackStart(
                org.jellyfin.sdk.model.api.PlaybackStartInfo(
                    canSeek = true,
                    itemId = uuid,
                    sessionId = sessionId,
                    isPaused = false,
                    isMuted = false,
                    playMethod = sdkPlayMethod,
                    repeatMode = org.jellyfin.sdk.model.api.RepeatMode.REPEAT_NONE,
                    playbackOrder = org.jellyfin.sdk.model.api.PlaybackOrder.DEFAULT,
                )
            )
        }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod,
    ): Result<Unit> = apiResult {
        val uuid = itemId.toUUID()
        val sdkPlayMethod = when (playMethod) {
            com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY
            com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_STREAM -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_STREAM
            com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE -> org.jellyfin.sdk.model.api.PlayMethod.TRANSCODE
        }
        requireApi().playStateApi.reportPlaybackProgress(
            org.jellyfin.sdk.model.api.PlaybackProgressInfo(
                canSeek = true,
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = false,
                playMethod = sdkPlayMethod,
                repeatMode = org.jellyfin.sdk.model.api.RepeatMode.REPEAT_NONE,
                playbackOrder = org.jellyfin.sdk.model.api.PlaybackOrder.DEFAULT,
            )
        )
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> = apiResult {
        val uuid = itemId.toUUID()
        requireApi().playStateApi.reportPlaybackStopped(
            org.jellyfin.sdk.model.api.PlaybackStopInfo(
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                failed = false,
            )
        )
    }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String {
        val server = _currentServer.value ?: return ""
        val indexPart = imageIndex?.let { "/$it" } ?: ""
        val widthPart = maxWidth?.let { "?maxWidth=$it" } ?: ""
        return "${server.address}/Items/$itemId/Images/$imageType$indexPart$widthPart"
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String {
        val server = _currentServer.value ?: return ""
        val user = _currentUser.value ?: return ""
        return "${server.address}/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId&startTimeTicks=$startTimeTicks&api_key=${user.accessToken}"
    }

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val server = _currentServer.value ?: return ""
        val user = _currentUser.value ?: return ""
        val baseUrl = if (deliveryUrl.startsWith("http")) deliveryUrl else "${server.address}$deliveryUrl"
        val separator = if ("?" in baseUrl) "&" else "?"
        return "$baseUrl${separator}api_key=${user.accessToken}"
    }

    override fun getServerUrl(): String? = _currentServer.value?.address

    override fun getAccessToken(): String? = _currentUser.value?.accessToken

    override fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String {
        val server = _currentServer.value ?: return ""
        val user = _currentUser.value ?: return ""
        val format = when ((codec ?: "srt").lowercase()) {
            "subrip" -> "srt"
            "ass", "ssa" -> codec!!.lowercase()
            else -> (codec ?: "srt").lowercase()
        }
        return "${server.address}/Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream.$format?api_key=${user.accessToken}"
    }

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
    ): Result<List<LiveTvChannel>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_CHANNEL),
            startIndex = startIndex,
            limit = limit,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toLiveTvChannel() }
    }

    override suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String?,
        endDateUtc: String?,
    ): Result<List<LiveTvProgram>> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = channelId.toUUID(),
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_PROGRAM),
            fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
        ).content
        response.items.map { it.toLiveTvProgram() }
    }

    override suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int,
        limit: Int,
    ): Result<EpgGuide> = apiResult {
        coroutineScope {
            val client = requireApi()
            val channelsDeferred = async {
                client.itemsApi.getItems(
                    includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_CHANNEL),
                    startIndex = startIndex,
                    limit = limit,
                    fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
                ).content.items.map { it.toLiveTvChannel() }
            }
            val programsDeferred = async {
                client.itemsApi.getItems(
                    includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_PROGRAM),
                    limit = 500,
                    fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
                ).content.items.map { it.toLiveTvProgram() }
            }
            EpgGuide(channels = channelsDeferred.await(), programs = programsDeferred.await())
        }
    }

    override suspend fun getTimers(): Result<List<DvrTimer>> = apiResult {
        requireApi().liveTvApi.getTimers().content.items.map { it.toDvrTimer() }
    }

    override suspend fun getSeriesTimers(): Result<List<DvrSeriesTimer>> = apiResult {
        requireApi().liveTvApi.getSeriesTimers().content.items.map { it.toDvrSeriesTimer() }
    }

    override suspend fun createTimer(
        programId: String,
        channelId: String,
        startDate: String?,
        endDate: String?,
    ): Result<Unit> = apiResult {
        requireApi().liveTvApi.createTimer(
            org.jellyfin.sdk.model.api.TimerInfoDto(
                programId = programId,
                channelId = channelId.toUUID(),
                startDate = startDate?.let { java.time.LocalDateTime.parse(it.replace("Z", "").replace("T", " ").substringBefore('+').replace(" ", "T")) },
                endDate = endDate?.let { java.time.LocalDateTime.parse(it.replace("Z", "").replace("T", " ").substringBefore('+').replace(" ", "T")) },
            )
        )
    }

    override suspend fun cancelTimer(timerId: String): Result<Unit> = apiResult {
        requireApi().liveTvApi.cancelTimer(timerId = timerId)
    }

    override suspend fun getSyncPlayGroups(): Result<List<com.raulshma.jellyplay.core.model.SyncPlayGroup>> = apiResult {
        val response = requireApi().syncPlayApi.syncPlayGetGroups().content
        response.map { groupInfo ->
            com.raulshma.jellyplay.core.model.SyncPlayGroup(
                groupId = groupInfo.groupId.toString(),
                groupName = groupInfo.groupName ?: "",
                participantCount = groupInfo.participants?.size ?: 0,
                participants = groupInfo.participants ?: emptyList(),
                isPlaying = groupInfo.state == org.jellyfin.sdk.model.api.GroupStateType.PLAYING,
            )
        }
    }

    override suspend fun joinSyncPlayGroup(groupId: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayJoinGroup(
            org.jellyfin.sdk.model.api.JoinGroupRequestDto(
                groupId = groupId.toUUID(),
            )
        )
    }

    override suspend fun leaveSyncPlayGroup(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayLeaveGroup()
    }

    override suspend fun createSyncPlayGroup(groupName: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayCreateGroup(
            org.jellyfin.sdk.model.api.NewGroupRequestDto(
                groupName = groupName,
            )
        )
    }

    override suspend fun syncPlayReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
        whenMs: Long?,
    ): Result<Unit> = apiResult {
        val whenDate = whenMs?.let {
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneOffset.UTC)
        } ?: java.time.LocalDateTime.now(java.time.Clock.systemUTC())
        
        requireApi().syncPlayApi.syncPlayReady(
            org.jellyfin.sdk.model.api.ReadyRequestDto(
                `when` = whenDate,
                positionTicks = positionTicks,
                isPlaying = isPlaying,
                playlistItemId = (playlistItemId ?: "00000000-0000-0000-0000-000000000000").toUUID(),
            )
        )
    }

    override suspend fun syncPlayBuffering(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
        whenMs: Long?,
    ): Result<Unit> = apiResult {
        val whenDate = whenMs?.let {
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneOffset.UTC)
        } ?: java.time.LocalDateTime.now(java.time.Clock.systemUTC())

        requireApi().syncPlayApi.syncPlayBuffering(
            org.jellyfin.sdk.model.api.BufferRequestDto(
                `when` = whenDate,
                positionTicks = positionTicks,
                isPlaying = isPlaying,
                playlistItemId = (playlistItemId ?: "00000000-0000-0000-0000-000000000000").toUUID(),
            )
        )
    }

    override suspend fun syncPlayPause(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayPause()
    }

    override suspend fun syncPlayUnpause(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayUnpause()
    }

    override suspend fun syncPlaySeek(positionTicks: Long): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlaySeek(
            org.jellyfin.sdk.model.api.SeekRequestDto(
                positionTicks = positionTicks,
            )
        )
    }

    override suspend fun getSyncPlayInfo(groupId: String?): Result<com.raulshma.jellyplay.core.model.SyncPlayGroupInfo> = apiResult {
        val activeId = groupId ?: throw IllegalArgumentException("groupId is required for getSyncPlayInfo")
        val groups = requireApi().syncPlayApi.syncPlayGetGroups().content
        val groupInfo = groups.find { it.groupId.toString() == activeId }
            ?: throw IllegalStateException("SyncPlay group $activeId not found")
        com.raulshma.jellyplay.core.model.SyncPlayGroupInfo(
            groupId = groupInfo.groupId.toString(),
            groupName = groupInfo.groupName ?: "",
            participants = (groupInfo.participants ?: emptyList()).map { name ->
                com.raulshma.jellyplay.core.model.SyncPlayParticipant(
                    userId = name,
                    userName = name,
                    isConnected = true,
                )
            },
            isPlaying = groupInfo.state == org.jellyfin.sdk.model.api.GroupStateType.PLAYING,
        )
    }

    override suspend fun syncPlayStop(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayStop()
    }

    override suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayNextItem(
            org.jellyfin.sdk.model.api.NextItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayPreviousItem(
            org.jellyfin.sdk.model.api.PreviousItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlaySetRepeatMode(mode: com.raulshma.jellyplay.core.model.SyncPlayRepeatMode): Result<Unit> = apiResult {
        val sdkMode = when (mode) {
            com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_NONE -> org.jellyfin.sdk.model.api.GroupRepeatMode.REPEAT_NONE
            com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ALL -> org.jellyfin.sdk.model.api.GroupRepeatMode.REPEAT_ALL
            com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ONE -> org.jellyfin.sdk.model.api.GroupRepeatMode.REPEAT_ONE
        }
        requireApi().syncPlayApi.syncPlaySetRepeatMode(
            org.jellyfin.sdk.model.api.SetRepeatModeRequestDto(mode = sdkMode)
        )
    }

    override suspend fun syncPlaySetShuffleMode(mode: com.raulshma.jellyplay.core.model.SyncPlayShuffleMode): Result<Unit> = apiResult {
        val sdkMode = when (mode) {
            com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SORTED -> org.jellyfin.sdk.model.api.GroupShuffleMode.SORTED
            com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SHUFFLE -> org.jellyfin.sdk.model.api.GroupShuffleMode.SHUFFLE
        }
        requireApi().syncPlayApi.syncPlaySetShuffleMode(
            org.jellyfin.sdk.model.api.SetShuffleModeRequestDto(mode = sdkMode)
        )
    }

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlaySetNewQueue(
            org.jellyfin.sdk.model.api.PlayRequestDto(
                playingQueue = itemIds.map { it.toUUID() },
                playingItemPosition = itemIds.indexOf(playingItemId).takeIf { it >= 0 } ?: 0,
                startPositionTicks = startPositionTicks,
            )
        )
    }

    override suspend fun syncPlayQueue(
        itemIds: List<String>,
        mode: String,
    ): Result<Unit> = apiResult {
        val sdkMode = org.jellyfin.sdk.model.api.GroupQueueMode.fromNameOrNull(mode)
            ?: org.jellyfin.sdk.model.api.GroupQueueMode.QUEUE
        requireApi().syncPlayApi.syncPlayQueue(
            org.jellyfin.sdk.model.api.QueueRequestDto(
                itemIds = itemIds.map { it.toUUID() },
                mode = sdkMode,
            )
        )
    }

    override suspend fun syncPlaySetPlaylistItem(playlistItemId: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlaySetPlaylistItem(
            org.jellyfin.sdk.model.api.SetPlaylistItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
            )
        )
    }

    override suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlaySetIgnoreWait(
            org.jellyfin.sdk.model.api.IgnoreWaitRequestDto(ignoreWait = ignore)
        )
    }

    override suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayRemoveFromPlaylist(
            org.jellyfin.sdk.model.api.RemoveFromPlaylistRequestDto(
                playlistItemIds = listOf(playlistItemId.toUUID()),
                clearPlayingItem = true,
                clearPlaylist = false,
            )
        )
    }

    override suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayMovePlaylistItem(
            org.jellyfin.sdk.model.api.MovePlaylistItemRequestDto(
                playlistItemId = playlistItemId.toUUID(),
                newIndex = newIndex,
            )
        )
    }

    override suspend fun syncPlayPing(pingMs: Long): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayPing(
            org.jellyfin.sdk.model.api.PingRequestDto(ping = pingMs)
        )
    }

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/IntroSkipTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                IntroTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResult IntroTimestamps(itemId)
                sharedJson.decodeFromString<IntroTimestamps>(body)
            }
        }
    }

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/CreditTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                CreditTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResult CreditTimestamps(itemId)
                sharedJson.decodeFromString<CreditTimestamps>(body)
            }
        }
    }

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/MediaSegments/$itemId"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emptyList()
            } else {
                val body = response.body?.string() ?: return@apiResult emptyList<MediaSegment>()
                val response = sharedJson.decodeFromString<MediaSegmentsResponse>(body)
                response.Items.map { dto ->
                    MediaSegment(
                        id = dto.Id,
                        itemId = dto.ItemId,
                        type = MediaSegmentType.fromApiName(dto.Type),
                        startTicks = dto.StartTicks,
                        endTicks = dto.EndTicks,
                    )
                }
            }
        }
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@apiResult emptyList<RemoteSubtitleInfo>()
            val body = response.body?.string() ?: return@apiResult emptyList<RemoteSubtitleInfo>()
            sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }
    }

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles/$subtitleId"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download subtitle: ${response.code}")
            }
        }
    }

    private fun org.jellyfin.sdk.model.api.TimerInfoDto.toDvrTimer() = DvrTimer(
        id = id?.toString() ?: java.util.UUID.randomUUID().toString(),
        programId = programId?.toString() ?: "",
        programName = name ?: "",
        channelId = channelId?.toString() ?: "",
        channelName = channelName ?: "",
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        status = when (status) {
            org.jellyfin.sdk.model.api.RecordingStatus.NEW -> DvrTimerStatus.NEW
            org.jellyfin.sdk.model.api.RecordingStatus.IN_PROGRESS -> DvrTimerStatus.RECORDING
            org.jellyfin.sdk.model.api.RecordingStatus.COMPLETED -> DvrTimerStatus.COMPLETED
            org.jellyfin.sdk.model.api.RecordingStatus.CANCELLED -> DvrTimerStatus.CANCELLED
            else -> {
                // SDK version may not have SCHEDULED/CONFLICT_OK/CONFLICT_NOT_OK
                val name = status?.serialName
                when (name) {
                    "SCHEDULED" -> DvrTimerStatus.SCHEDULED
                    "RECORDING" -> DvrTimerStatus.RECORDING
                    "CONFLICT_OK" -> DvrTimerStatus.CONFLICT_OK
                    "CONFLICT_NOT_OK" -> DvrTimerStatus.CONFLICT_NOT_OK
                    else -> DvrTimerStatus.NEW
                }
            }
        },
        isPrePaddingRequired = isPrePaddingRequired ?: false,
        isPostPaddingRequired = isPostPaddingRequired ?: false,
        prePaddingSeconds = prePaddingSeconds ?: 0,
        postPaddingSeconds = postPaddingSeconds ?: 0,
        priority = priority ?: 0,
        seriesTimerId = seriesTimerId?.toString(),
    )

    private fun org.jellyfin.sdk.model.api.SeriesTimerInfoDto.toDvrSeriesTimer() = DvrSeriesTimer(
        id = id?.toString() ?: java.util.UUID.randomUUID().toString(),
        name = name ?: "",
        channelId = channelId?.toString(),
        channelName = channelName,
        days = days?.map { it.serialName } ?: emptyList(),
        priority = priority ?: 0,
        recordAnyTime = recordAnyTime ?: true,
        recordAnyChannel = recordAnyChannel ?: true,
        keepUpTo = keepUpTo ?: 0,
    )

    private fun org.jellyfin.sdk.model.api.BaseItemDto.toLiveTvChannel() = LiveTvChannel(
        id = id.toString(),
        name = name ?: "",
        number = channelNumber,
        imageTag = imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.toString(),
        currentProgram = currentProgram?.toLiveTvProgram(),
        mediaType = MediaType.CHANNEL,
        primaryBlurHash = imageBlurHashes
            ?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
            ?.values?.firstOrNull(),
    )

    private fun org.jellyfin.sdk.model.api.BaseItemDto.toLiveTvProgram() = LiveTvProgram(
        id = id.toString(),
        name = name ?: "",
        overview = overview,
        channelId = channelId?.toString() ?: "",
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        durationTicks = runTimeTicks,
        episodeTitle = episodeTitle,
        officialRating = officialRating,
        isMovie = isMovie ?: false,
        isNews = isNews ?: false,
        isSports = isSports ?: false,
        isKids = isKids ?: false,
        isLive = isLive ?: false,
        isPremiere = isPremiere ?: false,
        isSeries = isSeries ?: false,
    )

    private fun org.jellyfin.sdk.model.api.BaseItemDto.toMediaItem() = MediaItem(
        id = id.toString(),
        name = name ?: "",
        originalTitle = originalTitle,
        overview = overview,
        mediaType = type?.toMediaType() ?: MediaType.UNKNOWN,
        year = productionYear,
        communityRating = communityRating?.toFloat(),
        officialRating = officialRating,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = userData?.playbackPositionTicks,
        isPlayed = userData?.played == true,
        isFavorite = userData?.isFavorite == true,
        premiereDate = premiereDate?.toString(),
        genres = genres ?: emptyList(),
        studios = studios?.mapNotNull { it.name } ?: emptyList(),
        tags = tags ?: emptyList(),
        parentId = parentId?.toString(),
        seriesId = seriesId?.toString(),
        seasonId = seasonId?.toString(),
        seriesName = seriesName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        indexNumber = indexNumber,
        childCount = childCount,
        albumArtist = albumArtist,
        album = album,
        blurHashes = ImageBlurHashes(
            primary = imageBlurHashes
                ?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
                ?.values?.firstOrNull(),
            backdrop = imageBlurHashes
                ?.get(org.jellyfin.sdk.model.api.ImageType.BACKDROP)
                ?.values?.firstOrNull(),
        ),
        normalizationGain = normalizationGain,
    )

    private fun org.jellyfin.sdk.model.api.BaseItemKind.toMediaType(): MediaType = when (this) {
        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> MediaType.MOVIE
        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> MediaType.SERIES
        org.jellyfin.sdk.model.api.BaseItemKind.SEASON -> MediaType.SEASON
        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> MediaType.EPISODE
        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM -> MediaType.ALBUM
        org.jellyfin.sdk.model.api.BaseItemKind.AUDIO -> MediaType.AUDIO
        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST -> MediaType.ARTIST
        org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET -> MediaType.COLLECTION
        org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_CHANNEL -> MediaType.CHANNEL
        org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_PROGRAM -> MediaType.LIVE_TV
        else -> MediaType.UNKNOWN
    }

    private fun MediaType.toBaseItemKind(): org.jellyfin.sdk.model.api.BaseItemKind? = when (this) {
        MediaType.MOVIE -> org.jellyfin.sdk.model.api.BaseItemKind.MOVIE
        MediaType.SERIES -> org.jellyfin.sdk.model.api.BaseItemKind.SERIES
        MediaType.SEASON -> org.jellyfin.sdk.model.api.BaseItemKind.SEASON
        MediaType.EPISODE -> org.jellyfin.sdk.model.api.BaseItemKind.EPISODE
        MediaType.ALBUM -> org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM
        MediaType.AUDIO -> org.jellyfin.sdk.model.api.BaseItemKind.AUDIO
        MediaType.ARTIST -> org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST
        MediaType.COLLECTION -> org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET
        MediaType.CHANNEL -> org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_CHANNEL
        MediaType.LIVE_TV -> org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_PROGRAM
        MediaType.MUSIC -> org.jellyfin.sdk.model.api.BaseItemKind.AUDIO
        MediaType.UNKNOWN -> null
    }

    private fun org.jellyfin.sdk.model.api.TrickplayInfoDto.toTrickplayInfo() = TrickplayInfo(
        width = width ?: 320,
        height = height ?: 180,
        tileWidth = tileWidth ?: 10,
        tileHeight = tileHeight ?: 1,
        thumbnailCount = thumbnailCount ?: 0,
        interval = interval ?: 10_000,
        bandwidth = bandwidth ?: 0,
    )

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        try {
            withContext(Dispatchers.IO) {
                requireApi().trickplayApi.getTrickplayTileImage(
                    itemId = itemId.toUUID(),
                    width = width,
                    index = index,
                ).content
            }
        } catch (_: Exception) {
            null
        }

    override suspend fun getServerTime(): Result<com.raulshma.jellyplay.core.model.UtcTimeResponse> = apiResult {
        val response = requireApi().timeSyncApi.getUtcTime().content
        com.raulshma.jellyplay.core.model.UtcTimeResponse(
            requestReceptionTime = response.requestReceptionTime?.toString() ?: "",
            responseTransmissionTime = response.responseTransmissionTime?.toString() ?: "",
        )
    }

    override suspend fun postCapabilities(): Result<Unit> = apiResult {
        requireApi().sessionApi.postFullCapabilities(data = CACHED_CAPABILITIES)
    }

    override suspend fun updateItem(
        itemId: String,
        name: String,
        originalTitle: String?,
        sortName: String?,
        overview: String?,
        tagline: String?,
        genres: List<String>,
        tags: List<String>,
        studios: List<String>,
        communityRating: Float?,
        criticRating: Float?,
        officialRating: String?,
        customRating: String?,
        productionYear: Int?,
        premiereDate: String?,
        endDate: String?,
        runtimeTicks: Long?,
        indexNumber: Int?,
        parentIndexNumber: Int?,
        displayOrder: String?,
        status: String?,
        airDays: List<String>,
        airTime: String?,
        people: List<com.raulshma.jellyplay.core.model.EditorPerson>,
        providerIds: Map<String, String>,
        lockData: Boolean,
        lockedFields: List<String>,
        preferredMetadataLanguage: String?,
        preferredMetadataCountryCode: String?,
        taglines: List<String>,
        productionLocations: List<String>,
        dateCreated: String?,
    ): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId"
        val body = buildJsonObject {
            put("Id", JsonPrimitive(itemId))
            put("Name", JsonPrimitive(name))
            originalTitle?.let { put("OriginalTitle", JsonPrimitive(it)) }
            sortName?.let { put("ForcedSortName", JsonPrimitive(it)) }
            overview?.let { put("Overview", JsonPrimitive(it)) }
            if (taglines.isNotEmpty()) {
                put("Taglines", buildJsonArray { taglines.forEach { add(JsonPrimitive(it)) } })
            }
            put("Genres", buildJsonArray { genres.forEach { add(JsonPrimitive(it)) } })
            put("Tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
            put("Studios", buildJsonArray {
                studios.forEach { studio ->
                    add(buildJsonObject { put("Name", JsonPrimitive(studio)) })
                }
            })
            communityRating?.let { put("CommunityRating", JsonPrimitive(it.toDouble())) }
            criticRating?.let { put("CriticRating", JsonPrimitive(it.toDouble())) }
            officialRating?.let { put("OfficialRating", JsonPrimitive(it)) }
            customRating?.let { put("CustomRating", JsonPrimitive(it)) }
            productionYear?.let { put("ProductionYear", JsonPrimitive(it)) }
            premiereDate?.let { put("PremiereDate", JsonPrimitive(it)) }
            endDate?.let { put("EndDate", JsonPrimitive(it)) }
            runtimeTicks?.let { put("RunTimeTicks", JsonPrimitive(it)) }
            indexNumber?.let { put("IndexNumber", JsonPrimitive(it)) }
            parentIndexNumber?.let { put("ParentIndexNumber", JsonPrimitive(it)) }
            displayOrder?.let { put("DisplayOrder", JsonPrimitive(it)) }
            status?.let { put("Status", JsonPrimitive(it)) }
            if (airDays.isNotEmpty()) {
                put("AirDays", buildJsonArray { airDays.forEach { add(JsonPrimitive(it)) } })
            }
            airTime?.let { put("AirTime", JsonPrimitive(it)) }
            if (people.isNotEmpty()) {
                put("People", buildJsonArray {
                    people.forEach { person ->
                        add(buildJsonObject {
                            put("Id", JsonPrimitive(person.id))
                            put("Name", JsonPrimitive(person.name))
                            person.role?.let { put("Role", JsonPrimitive(it)) }
                            put("Type", JsonPrimitive(person.type))
                            person.primaryImageTag?.let { put("PrimaryImageTag", JsonPrimitive(it)) }
                            person.sortOrder?.let { put("SortOrder", JsonPrimitive(it)) }
                        })
                    }
                })
            }
            if (providerIds.isNotEmpty()) {
                put("ProviderIds", buildJsonObject {
                    providerIds.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            put("LockData", JsonPrimitive(lockData))
            if (lockedFields.isNotEmpty()) {
                put("LockedFields", buildJsonArray { lockedFields.forEach { add(JsonPrimitive(it)) } })
            }
            preferredMetadataLanguage?.let { put("PreferredMetadataLanguage", JsonPrimitive(it)) }
            preferredMetadataCountryCode?.let { put("PreferredMetadataCountryCode", JsonPrimitive(it)) }
            if (productionLocations.isNotEmpty()) {
                put("ProductionLocations", buildJsonArray { productionLocations.forEach { add(JsonPrimitive(it)) } })
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to update item: ${response.code}")
            }
        }
    }

    override suspend fun getMetadataEditorInfo(itemId: String): Result<com.raulshma.jellyplay.core.model.MetadataEditorInfo> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")

        val editorUrl = "${server.address}/Items/$itemId/MetadataEditorInfo"
        val editorRequest = Request.Builder()
            .url(editorUrl)
            .header("X-Emby-Token", user.accessToken)
            .build()
        val editorJson = okHttpClient.newCall(editorRequest).execute().use { response ->
            response.body?.string() ?: throw Exception("Empty response")
        }

        val jsonElement = sharedJson.parseToJsonElement(editorJson).jsonObject

        val externalIds = jsonElement["ExternalIdInfos"]?.jsonArray?.map { elem ->
            val obj = elem.jsonObject
            com.raulshma.jellyplay.core.model.ExternalIdInfo(
                name = obj["Name"]?.jsonPrimitive?.content ?: "",
                key = obj["Key"]?.jsonPrimitive?.content ?: "",
                urlFormatString = obj["UrlFormatString"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

        val parentalRatings = jsonElement["ParentalRatingOptions"]?.jsonArray?.map { elem ->
            val obj = elem.jsonObject
            com.raulshma.jellyplay.core.model.ParentalRating(
                name = obj["Name"]?.jsonPrimitive?.content ?: "",
                value = obj["Value"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } ?: emptyList()

        val cultures = try {
            val culturesUrl = "${server.address}/Localization/Cultures"
            val culturesRequest = Request.Builder()
                .url(culturesUrl)
                .header("X-Emby-Token", user.accessToken)
                .build()
            okHttpClient.newCall(culturesRequest).execute().use { response ->
                response.body?.string()?.let { body ->
                    sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                        val obj = elem.jsonObject
                        com.raulshma.jellyplay.core.model.CultureInfo(
                            name = obj["Name"]?.jsonPrimitive?.content ?: "",
                            displayName = obj["DisplayName"]?.jsonPrimitive?.content ?: "",
                            twoLetterISOLanguageName = obj["TwoLetterISOLanguageName"]?.jsonPrimitive?.contentOrNull,
                            threeLetterISOLanguageName = obj["ThreeLetterISOLanguageName"]?.jsonPrimitive?.contentOrNull,
                        )
                    }
                } ?: emptyList()
            }
        } catch (_: Exception) { emptyList() }

        val countries = try {
            val countriesUrl = "${server.address}/Localization/Countries"
            val countriesRequest = Request.Builder()
                .url(countriesUrl)
                .header("X-Emby-Token", user.accessToken)
                .build()
            okHttpClient.newCall(countriesRequest).execute().use { response ->
                response.body?.string()?.let { body ->
                    sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                        val obj = elem.jsonObject
                        com.raulshma.jellyplay.core.model.CountryInfo(
                            name = obj["Name"]?.jsonPrimitive?.content ?: "",
                            displayName = obj["DisplayName"]?.jsonPrimitive?.content ?: "",
                            twoLetterISORegionName = obj["TwoLetterISORegionName"]?.jsonPrimitive?.contentOrNull,
                            threeLetterISORegionName = obj["ThreeLetterISORegionName"]?.jsonPrimitive?.contentOrNull,
                        )
                    }
                } ?: emptyList()
            }
        } catch (_: Exception) { emptyList() }

        com.raulshma.jellyplay.core.model.MetadataEditorInfo(
            parentalRatingOptions = parentalRatings,
            contentTypeOptions = emptyList(),
            externalIdInfos = externalIds,
            cultures = cultures,
            countries = countries,
        )
    }

    override suspend fun refreshItemMetadata(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
        regenerateTrickplay: Boolean,
    ): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Refresh" +
            "?MetadataRefreshMode=$metadataRefreshMode" +
            "&ImageRefreshMode=$imageRefreshMode" +
            "&ReplaceAllMetadata=$replaceAllMetadata" +
            "&ReplaceAllImages=$replaceAllImages" +
            "&RegenerateTrickplay=$regenerateTrickplay"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(ByteArray(0).toRequestBody())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to refresh metadata: ${response.code}")
            }
        }
    }

    override suspend fun getItemImageInfo(itemId: String): Result<List<com.raulshma.jellyplay.core.model.ImageInfo>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Images"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                val obj = elem.jsonObject
                com.raulshma.jellyplay.core.model.ImageInfo(
                    imageType = obj["ImageType"]?.jsonPrimitive?.content ?: "",
                    imageIndex = obj["ImageIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    width = obj["Width"]?.jsonPrimitive?.intOrNull ?: 0,
                    height = obj["Height"]?.jsonPrimitive?.intOrNull ?: 0,
                    blurHash = obj["BlurHash"]?.jsonPrimitive?.contentOrNull,
                    imageTag = obj["ImageTag"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }
    }

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/Images/$imageType"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "image/*")
            .post(imageBytes.toRequestBody("image/*".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to upload image: ${response.code}")
            }
        }
    }

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val indexPart = imageIndex?.let { "/$it" } ?: ""
        val url = "${server.address}/Items/$itemId/Images/$imageType$indexPart"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .delete()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to delete image: ${response.code}")
            }
        }
    }

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String?,
        provider: String?,
        startIndex: Int?,
        limit: Int?,
    ): Result<com.raulshma.jellyplay.core.model.RemoteImageResult> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = buildString {
            append("${server.address}/Items/$itemId/RemoteImages")
            append("?Limit=${limit ?: 50}")
            imageType?.let { append("&Type=$it") }
            provider?.let { append("&ProviderName=$it") }
            startIndex?.let { append("&StartIndex=$it") }
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = sharedJson.parseToJsonElement(body).jsonObject
            val images = json["Images"]?.jsonArray?.map { elem ->
                val obj = elem.jsonObject
                com.raulshma.jellyplay.core.model.RemoteImageInfo(
                    providerName = obj["ProviderName"]?.jsonPrimitive?.content ?: "",
                    url = obj["Url"]?.jsonPrimitive?.content ?: "",
                    thumbnailUrl = obj["ThumbnailUrl"]?.jsonPrimitive?.content ?: "",
                    height = obj["Height"]?.jsonPrimitive?.intOrNull ?: 0,
                    width = obj["Width"]?.jsonPrimitive?.intOrNull ?: 0,
                    language = obj["Language"]?.jsonPrimitive?.contentOrNull,
                    communityRating = obj["CommunityRating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                    voteCount = obj["VoteCount"]?.jsonPrimitive?.intOrNull,
                    ratingType = obj["RatingType"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            } ?: emptyList()
            val providers = json["Providers"]?.jsonArray?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull ?: it.jsonObject["Name"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            com.raulshma.jellyplay.core.model.RemoteImageResult(
                images = images,
                totalRecordCount = json["TotalRecordCount"]?.jsonPrimitive?.intOrNull ?: 0,
                providers = providers,
            )
        }
    }

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<com.raulshma.jellyplay.core.model.ImageProviderInfo>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteImages/Providers"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            sharedJson.parseToJsonElement(body).jsonArray.map { elem ->
                val obj = elem.jsonObject
                com.raulshma.jellyplay.core.model.ImageProviderInfo(
                    name = obj["Name"]?.jsonPrimitive?.content ?: "",
                    supportedImages = obj["SupportedImages"]?.jsonArray?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: emptyList(),
                )
            }
        }
    }

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val encodedUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8")
        val url = "${server.address}/Items/$itemId/RemoteImages/Download" +
            "?Type=$imageType&ImageUrl=$encodedUrl"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(ByteArray(0).toRequestBody())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download remote image: ${response.code}")
            }
        }
    }

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Videos/$itemId/Subtitles"
        val body = buildJsonObject {
            put("Data", JsonPrimitive(data))
            put("FileName", JsonPrimitive(fileName))
            put("Language", JsonPrimitive(language ?: ""))
            put("IsForced", JsonPrimitive(isForced))
            put("IsHearingImpaired", JsonPrimitive(isHearingImpaired))
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to upload subtitle: ${response.code}")
            }
        }
    }

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Videos/$itemId/Subtitles/$index"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .delete()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to delete subtitle: ${response.code}")
            }
        }
    }

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles/$language"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@use emptyList()
            if (!response.isSuccessful) return@use emptyList()
            sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }
    }

    override suspend fun getSystemInfo(): Result<SystemInfo> = apiResult {
        val dto = requireApi().systemApi.getSystemInfo().content
        SystemInfo(
            serverName = dto.serverName ?: "",
            version = dto.version ?: "",
            productName = dto.productName ?: "",
            id = dto.id?.toString() ?: "",
            localAddress = dto.localAddress ?: "",
            wanAddress = "",
            operatingSystem = dto.operatingSystem ?: "",
            operatingSystemDisplayName = dto.operatingSystemDisplayName ?: "",
            hasPendingRestart = dto.hasPendingRestart,
            isShuttingDown = dto.isShuttingDown,
            startupWizardCompleted = dto.startupWizardCompleted ?: true,
            webSocketPortNumber = dto.webSocketPortNumber,
            packageName = dto.packageName ?: "",
            canSelfRestart = dto.canSelfRestart ?: false,
            canLaunchWebBrowser = dto.canLaunchWebBrowser ?: false,
            transcodingTempPath = dto.transcodingTempPath ?: "",
            cachePath = dto.cachePath ?: "",
            logPath = dto.logPath ?: "",
            internalMetadataPath = dto.internalMetadataPath ?: "",
        )
    }

    override suspend fun getItemCounts(): Result<ItemCounts> = apiResult {
        val dto = requireApi().libraryApi.getItemCounts().content
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
    }

    override suspend fun restartServer(): Result<Unit> = apiResult {
        requireApi().systemApi.restartApplication()
    }

    override suspend fun shutdownServer(): Result<Unit> = apiResult {
        requireApi().systemApi.shutdownApplication()
    }

    override suspend fun getScheduledTasks(isHidden: Boolean?, isEnabled: Boolean?): Result<List<ScheduledTaskInfo>> = apiResult {
        val response = requireApi().scheduledTasksApi.getTasks(
            isHidden = isHidden,
            isEnabled = isEnabled,
        ).content ?: emptyList()
        response.mapNotNull { dto ->
            try { dto.toTaskModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getScheduledTask(taskId: String): Result<ScheduledTaskInfo> = apiResult {
        requireApi().scheduledTasksApi.getTask(taskId = taskId).content.toTaskModel()
    }

    override suspend fun startTask(taskId: String): Result<Unit> = apiResult {
        requireApi().scheduledTasksApi.startTask(taskId = taskId)
    }

    override suspend fun cancelTask(taskId: String): Result<Unit> = apiResult {
        requireApi().scheduledTasksApi.stopTask(taskId = taskId)
    }

    override suspend fun updateTaskTriggers(taskId: String, triggers: List<TaskTriggerInfo>): Result<Unit> = apiResult {
        val sdkTriggers = triggers.map { trigger ->
            org.jellyfin.sdk.model.api.TaskTriggerInfo(
                type = org.jellyfin.sdk.model.api.TaskTriggerInfoType.entries.find { it.serialName.equals(trigger.type, ignoreCase = true) }
                    ?: org.jellyfin.sdk.model.api.TaskTriggerInfoType.INTERVAL_TRIGGER,
                timeOfDayTicks = trigger.timeOfDayTicks,
                intervalTicks = trigger.intervalTicks,
                dayOfWeek = trigger.dayOfWeek?.let { dow ->
                    org.jellyfin.sdk.model.api.DayOfWeek.entries.find { it.serialName.equals(dow, ignoreCase = true) }
                },
                maxRuntimeTicks = trigger.maxRuntimeTicks,
            )
        }
        requireApi().scheduledTasksApi.updateTask(taskId = taskId, data = sdkTriggers)
    }

    override suspend fun getDevices(userId: String?): Result<List<DeviceInfo>> = apiResult {
        val response = requireApi().devicesApi.getDevices(
            userId = userId?.toUUID(),
        ).content
        response.items.mapNotNull { dto ->
            try { dto.toDeviceModel() } catch (_: Exception) { null }
        }
    }

    override suspend fun getDeviceInfo(deviceId: String): Result<DeviceInfo> = apiResult {
        requireApi().devicesApi.getDeviceInfo(id = deviceId).content.toDeviceModel()
    }

    override suspend fun updateDeviceOptions(deviceId: String, customName: String?): Result<Unit> = apiResult {
        requireApi().devicesApi.updateDeviceOptions(
            id = deviceId,
            data = org.jellyfin.sdk.model.api.DeviceOptionsDto(
                id = 0,
                deviceId = deviceId,
                customName = customName,
            ),
        )
    }

    override suspend fun deleteDevice(deviceId: String): Result<Unit> = apiResult {
        requireApi().devicesApi.deleteDevice(
            id = deviceId,
        )
    }

    override suspend fun getLogFiles(): Result<List<LogFile>> = apiResult {
        val logs = requireApi().systemApi.getServerLogs().content
        logs.map { it.toLogFileModel() }
    }

    override suspend fun getLogFileContent(fileName: String): Result<String> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/System/Logs/Log?name=${java.net.URLEncoder.encode(fileName, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to get log file: ${response.code}")
            response.body?.string() ?: ""
        }
    }

    override suspend fun getActivityLogEntries(
        startIndex: Int?,
        limit: Int?,
        minDate: String?,
        hasUserId: Boolean?,
    ): Result<List<ActivityLogEntry>> = apiResult {
        val result = requireApi().activityLogApi.getLogEntries(
            startIndex = startIndex,
            limit = limit,
            minDate = minDate?.let { java.time.LocalDateTime.parse(it) },
            hasUserId = hasUserId,
        ).content
        result.items.map { it.toActivityModel() }
    }

    override suspend fun getSessions(): Result<List<SessionInfo>> = apiResult {
        val sessions = requireApi().sessionApi.getSessions().content
        sessions.map { it.toSessionModel() }
    }

    private fun okHttp3MultipartBody(bytes: ByteArray): okhttp3.RequestBody =
        bytes.toRequestBody("image/*".toMediaType())

    private fun org.jellyfin.sdk.model.api.TaskInfo.toTaskModel() = ScheduledTaskInfo(
        id = id?.toString() ?: "",
        name = name ?: "",
        state = when (state) {
            org.jellyfin.sdk.model.api.TaskState.RUNNING -> TaskState.RUNNING
            org.jellyfin.sdk.model.api.TaskState.CANCELLING -> TaskState.CANCELLING
            else -> TaskState.IDLE
        },
        isHidden = isHidden,
        isEnabled = true,
        triggers = triggers?.map { it.toTriggerModel() } ?: emptyList(),
        lastExecutionResult = lastExecutionResult?.toExecutionModel(),
        currentProgressPercentage = currentProgressPercentage,
        description = description,
        category = category,
    )

    private fun org.jellyfin.sdk.model.api.TaskTriggerInfo.toTriggerModel() = TaskTriggerInfo(
        type = type.serialName,
        timeOfDayTicks = timeOfDayTicks,
        intervalTicks = intervalTicks,
        dayOfWeek = dayOfWeek?.serialName,
        maxRuntimeTicks = maxRuntimeTicks,
    )

    private fun org.jellyfin.sdk.model.api.TaskResult.toExecutionModel() = TaskExecutionInfo(
        name = name ?: "",
        key = key ?: "",
        startTimeUtc = startTimeUtc.toString(),
        endTimeUtc = endTimeUtc.toString(),
        status = status.serialName,
        errorMessage = errorMessage,
    )

    private fun org.jellyfin.sdk.model.api.DeviceInfoDto.toDeviceModel() = DeviceInfo(
        id = id?.toString() ?: "",
        name = name ?: "",
        customName = customName,
        appName = appName ?: "",
        appVersion = appVersion ?: "",
        lastUserName = lastUserName ?: "",
        lastUserId = lastUserId?.toString() ?: "",
        dateLastActivity = dateLastActivity?.toString() ?: "",
        iconUrl = iconUrl,
        capabilities = capabilities.let { it.toCapabilitiesModel() },
    )

    private fun org.jellyfin.sdk.model.api.ClientCapabilitiesDto.toCapabilitiesModel() = DeviceCapabilities(
        playableMediaTypes = playableMediaTypes.map { it.serialName },
        supportedCommands = supportedCommands.map { it.serialName },
        supportsMediaControl = supportsMediaControl,
        supportsContentUploading = false,
    )

    private fun org.jellyfin.sdk.model.api.LogFile.toLogFileModel() = LogFile(
        name = name,
        dateModified = dateModified.toString(),
        size = size,
        contentType = "text/plain",
    )

    private fun org.jellyfin.sdk.model.api.ActivityLogEntry.toActivityModel() = ActivityLogEntry(
        id = id,
        name = name,
        type = type,
        userId = userId.toString(),
        overview = overview,
        shortOverview = shortOverview,
        itemId = itemId,
        date = date.toString(),
        severity = when (severity) {
            org.jellyfin.sdk.model.api.LogLevel.TRACE -> ActivityLogSeverity.TRACE
            org.jellyfin.sdk.model.api.LogLevel.DEBUG -> ActivityLogSeverity.DEBUG
            org.jellyfin.sdk.model.api.LogLevel.WARNING -> ActivityLogSeverity.WARNING
            org.jellyfin.sdk.model.api.LogLevel.ERROR -> ActivityLogSeverity.ERROR
            org.jellyfin.sdk.model.api.LogLevel.CRITICAL -> ActivityLogSeverity.FATAL
            else -> ActivityLogSeverity.INFORMATION
        },
    )

    private fun org.jellyfin.sdk.model.api.SessionInfoDto.toSessionModel() = SessionInfo(
        id = id?.toString() ?: "",
        userId = userId.toString(),
        userName = userName ?: "",
        client = client ?: "",
        lastActivityDate = lastActivityDate.toString(),
        lastPlaybackCheckIn = lastPlaybackCheckIn?.toString(),
        deviceName = deviceName ?: "",
        deviceType = deviceType ?: "",
        nowPlayingItem = nowPlayingItem?.toSessionItemModel(),
        playState = playState?.toSessionPlayStateModel(),
        isActive = isActive,
        supportsRemoteControl = supportsRemoteControl,
    )

    private fun org.jellyfin.sdk.model.api.BaseItemDto.toSessionItemModel() = SessionNowPlayingItem(
        id = id?.toString() ?: "",
        name = name ?: "",
        type = type.serialName,
        mediaType = mediaType?.serialName,
        runTimeTicks = runTimeTicks,
        primaryImageTag = imageTags?.entries?.firstOrNull()?.value,
    )

    private fun org.jellyfin.sdk.model.api.PlayerStateInfo.toSessionPlayStateModel() = SessionPlayState(
        positionTicks = positionTicks,
        isPaused = isPaused,
        isMuted = isMuted,
        volumeLevel = volumeLevel,
        repeatMode = repeatMode.serialName,
        playMethod = playMethod?.serialName,
    )
}

@kotlinx.serialization.Serializable
private data class MediaSegmentDto(
    val Id: String,
    val ItemId: String,
    val Type: String,
    val StartTicks: Long,
    val EndTicks: Long,
)

@kotlinx.serialization.Serializable
private data class MediaSegmentsResponse(
    val Items: List<MediaSegmentDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)
