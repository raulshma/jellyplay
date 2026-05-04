package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.DvrTimerStatus
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : JellyfinApiClient {

    private val jellyfin: Jellyfin = createJellyfin {
        context = this@JellyfinApiClientImpl.context
        clientInfo = ClientInfo(name = "JellyPlay", version = "1.0.0")
    }

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
        return filter { item ->
            item.officialRating?.let { rating ->
                ratingToAge(rating)?.let { age -> age <= max }
            } != false
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
        withContext(Dispatchers.IO) {
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

    override suspend fun getHomeSections(): Result<List<HomeSection>> = apiResult {
        val sections = mutableListOf<HomeSection>()
        var firstError: Throwable? = null

        getContinueWatching()
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    sections.add(HomeSection("Continue Watching", HomeSectionType.CONTINUE_WATCHING, list))
                }
            }
            .onFailure { if (firstError == null) firstError = it }

        getNextUp()
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    sections.add(HomeSection("Next Up", HomeSectionType.NEXT_UP, list))
                }
            }
            .onFailure { if (firstError == null) firstError = it }

        getLibraryFolders()
            .onSuccess { folders ->
                for (folder in folders) {
                    getLatestMedia(folder.id, limit = 16)
                        .onSuccess { latest ->
                            if (latest.isNotEmpty()) {
                                sections.add(HomeSection("Latest ${folder.name}", HomeSectionType.LATEST_MEDIA, latest))
                            }
                        }
                }
            }
            .onFailure { if (firstError == null) firstError = it }

        if (sections.isEmpty() && firstError != null) {
            throw firstError!!
        }
        sections
    }

    override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            val response = requireApi().userLibraryApi.getLatestMedia(
                parentId = java.util.UUID.fromString(parentId),
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
            parentId = parentId?.let { java.util.UUID.fromString(it) },
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
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
        val client = requireApi()
        val uuid = java.util.UUID.fromString(itemId)
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
        val relatedItems = client.libraryApi.getSimilarItems(
            itemId = uuid,
            limit = 12,
        ).content.items.map { it.toMediaItem() }
            .distinctBy { it.id }
            .filter { it.id != itemId }
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
                    )
                } ?: emptyList(),
            )
        } ?: emptyList()
        MediaDetail(
            item = item.toMediaItem(),
            people = people,
            relatedItems = relatedItems,
            chapters = chapters,
            mediaSources = mediaSources,
        )
    }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            searchTerm = query,
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
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

    override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> =
        runCatching {
            val response = requireApi().genresApi.getGenres(
                parentId = parentId?.let { java.util.UUID.fromString(it) },
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
            genreIds = listOf(java.util.UUID.fromString(genreId)),
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

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            requireApi().libraryApi.getSimilarItems(
                itemId = java.util.UUID.fromString(itemId),
                limit = limit,
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            val response = requireApi().itemsApi.getItems(
                personIds = listOf(java.util.UUID.fromString(personId)),
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
            seriesId = java.util.UUID.fromString(seriesId),
        ).content.items.map { it.toMediaItem() }.filterByParentalRating()
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        runCatching {
            requireApi().tvShowsApi.getEpisodes(
                seriesId = java.util.UUID.fromString(seriesId),
                seasonId = java.util.UUID.fromString(seasonId),
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResult {
        val response = requireApi().itemsApi.getItems(
            parentId = java.util.UUID.fromString(collectionId),
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
            parentId = parentId?.let { java.util.UUID.fromString(it) },
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
        LyricsApi.fetchLyrics(server.address, itemId, user.accessToken)
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
            parentId = java.util.UUID.fromString(playlistId),
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
            userId = java.util.UUID.fromString(_currentUser.value?.id),
            itemId = java.util.UUID.fromString(itemId),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = apiResult {
        requireApi().playStateApi.markUnplayedItem(
            userId = java.util.UUID.fromString(_currentUser.value?.id),
            itemId = java.util.UUID.fromString(itemId),
        )
    }

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> = apiResult {
        val uuid = java.util.UUID.fromString(itemId)
        val item = requireApi().userLibraryApi.getItem(itemId = uuid).content
        if (item.userData?.isFavorite == true) {
            requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = java.util.UUID.fromString(_currentUser.value?.id),
                itemId = uuid,
            )
            false
        } else {
            requireApi().userLibraryApi.markFavoriteItem(
                userId = java.util.UUID.fromString(_currentUser.value?.id),
                itemId = uuid,
            )
            true
        }
    }

    override suspend fun reportPlaybackStart(itemId: String, sessionId: String): Result<Unit> =
        runCatching {
            val uuid = java.util.UUID.fromString(itemId)
            requireApi().playStateApi.reportPlaybackStart(
                org.jellyfin.sdk.model.api.PlaybackStartInfo(
                    canSeek = true,
                    itemId = uuid,
                    sessionId = sessionId,
                    isPaused = false,
                    isMuted = false,
                    playMethod = org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY,
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
    ): Result<Unit> = apiResult {
        val uuid = java.util.UUID.fromString(itemId)
        requireApi().playStateApi.reportPlaybackProgress(
            org.jellyfin.sdk.model.api.PlaybackProgressInfo(
                canSeek = true,
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = false,
                playMethod = org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY,
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
        val uuid = java.util.UUID.fromString(itemId)
        requireApi().playStateApi.reportPlaybackStopped(
            org.jellyfin.sdk.model.api.PlaybackStopInfo(
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                failed = false,
            )
        )
    }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int, tag: String?): String {
        val server = _currentServer.value ?: return ""
        return "${server.address}/Items/$itemId/Images/$imageType?maxWidth=$maxWidth"
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, tag)

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String {
        val server = _currentServer.value ?: return ""
        val user = _currentUser.value ?: return ""
        return "${server.address}/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId&startTimeTicks=$startTimeTicks&api_key=${user.accessToken}"
    }

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val server = _currentServer.value ?: return ""
        return if (deliveryUrl.startsWith("http")) deliveryUrl else "${server.address}$deliveryUrl"
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
            parentId = java.util.UUID.fromString(channelId),
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
        val client = requireApi()
        val channels = client.itemsApi.getItems(
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_CHANNEL),
            startIndex = startIndex,
            limit = limit,
            fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
        ).content.items.map { it.toLiveTvChannel() }

        val programs = client.itemsApi.getItems(
            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.LIVE_TV_PROGRAM),
            limit = 500,
            fields = listOf(org.jellyfin.sdk.model.api.ItemFields.OVERVIEW),
        ).content.items.map { it.toLiveTvProgram() }

        EpgGuide(channels = channels, programs = programs)
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
                channelId = java.util.UUID.fromString(channelId),
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
                groupId = java.util.UUID.fromString(groupId),
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

    override suspend fun syncPlayReady(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayReady(
            org.jellyfin.sdk.model.api.ReadyRequestDto(
                `when` = java.time.LocalDateTime.now(),
                positionTicks = 0L,
                isPlaying = true,
                playlistItemId = java.util.UUID.randomUUID(),
            )
        )
    }

    override suspend fun syncPlayBuffering(): Result<Unit> = apiResult {
        requireApi().syncPlayApi.syncPlayBuffering(
            org.jellyfin.sdk.model.api.BufferRequestDto(
                `when` = java.time.LocalDateTime.now(),
                positionTicks = 0L,
                isPlaying = false,
                playlistItemId = java.util.UUID.randomUUID(),
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

    override suspend fun getSyncPlayInfo(): Result<com.raulshma.jellyplay.core.model.SyncPlayGroupInfo> = apiResult {
        val groups = requireApi().syncPlayApi.syncPlayGetGroups().content
        val groupInfo = groups.firstOrNull()
            ?: throw IllegalStateException("Not in a SyncPlay group")
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

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = java.net.URL("${server.address}/Items/$itemId/IntroSkipTimestamps")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Emby-Token", user.accessToken)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode !in 200..299) {
                IntroTimestamps(itemId)
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                json.decodeFromString<IntroTimestamps>(body)
            }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = java.net.URL("${server.address}/Items/$itemId/RemoteSearch/Subtitles")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Emby-Token", user.accessToken)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode !in 200..299) return@apiResult emptyList<RemoteSubtitleInfo>()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<RemoteSubtitleInfo>>(body)
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> = apiResult {
        val server = _currentServer.value ?: throw IllegalStateException("No server")
        val user = _currentUser.value ?: throw IllegalStateException("No user")
        val url = java.net.URL("${server.address}/Items/$itemId/RemoteSearch/Subtitles/$subtitleId")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-Emby-Token", user.accessToken)
        conn.doOutput = true
        conn.outputStream.use { it.write(byteArrayOf()) }
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode !in 200..299) {
                throw Exception("Failed to download subtitle: ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
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
        indexNumber = indexNumber,
        childCount = childCount,
        albumArtist = albumArtist,
        album = album,
    )

    private fun org.jellyfin.sdk.model.api.BaseItemKind.toMediaType(): MediaType = when (this) {
        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> MediaType.MOVIE
        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> MediaType.SERIES
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
}
