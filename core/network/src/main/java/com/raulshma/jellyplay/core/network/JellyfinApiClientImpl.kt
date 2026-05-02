package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiClientImpl @Inject constructor() : JellyfinApiClient {

    private val jellyfin: Jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "JellyPlay", version = "1.0.0")
    }

    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    override val currentServer: Flow<ServerInfo?> = _currentServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    override val currentUser: Flow<UserInfo?> = _currentUser.asStateFlow()

    private var api: ApiClient? = null

    private fun requireApi(): ApiClient =
        api ?: throw IllegalStateException("Not connected to server")

    override suspend fun connectToServer(address: String): Result<ServerInfo> = runCatching {
        val client = jellyfin.createApi(address)
        val systemInfo = client.systemApi.getPublicSystemInfo().content
        val info = ServerInfo(
            id = systemInfo.id?.toString() ?: java.util.UUID.randomUUID().toString(),
            name = systemInfo.serverName ?: "Jellyfin Server",
            address = address,
        )
        _currentServer.value = info
        info
    }

    override suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo> = runCatching {
        val server = _currentServer.value
            ?: connectToServer(serverAddress).getOrThrow()
        val client = jellyfin.createApi(server.address)
        val authResult = client.userApi.authenticateUserByName(
            org.jellyfin.sdk.model.api.AuthenticateUserByName(
                username = username,
                pw = password,
            )
        ).content
        val accessTokenValue = authResult.accessToken ?: throw Exception("No access token")
        val authenticatedClient = jellyfin.createApi(
            baseUrl = server.address,
            accessToken = accessTokenValue,
        )
        api = authenticatedClient
        val userDto = authResult.user ?: throw Exception("Authentication failed")
        val userInfo = UserInfo(
            id = userDto.id.toString(),
            name = userDto.name ?: username,
            serverAddress = server.address,
            accessToken = accessTokenValue,
            isAdmin = userDto.policy?.isAdministrator ?: false,
        )
        _currentUser.value = userInfo
        _currentServer.value = server.copy(
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

    override suspend fun getHomeSections(): Result<List<HomeSection>> = runCatching {
        val sections = mutableListOf<HomeSection>()
        val continueWatching = getContinueWatching().getOrDefault(emptyList())
        if (continueWatching.isNotEmpty()) {
            sections.add(HomeSection("Continue Watching", HomeSectionType.CONTINUE_WATCHING, continueWatching))
        }
        val nextUp = getNextUp().getOrDefault(emptyList())
        if (nextUp.isNotEmpty()) {
            sections.add(HomeSection("Next Up", HomeSectionType.NEXT_UP, nextUp))
        }
        val folders = getLibraryFolders().getOrDefault(emptyList())
        for (folder in folders) {
            val latest = getLatestMedia(folder.id, limit = 16).getOrDefault(emptyList())
            if (latest.isNotEmpty()) {
                sections.add(HomeSection("Latest ${folder.name}", HomeSectionType.LATEST_MEDIA, latest))
            }
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
            ).content
            response.map { it.toMediaItem() }
        }

    override suspend fun getNextUp(limit: Int): Result<List<MediaItem>> = runCatching {
        val response = requireApi().tvShowsApi.getNextUp(
            limit = limit,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toMediaItem() }
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> = runCatching {
        val response = requireApi().itemsApi.getResumeItems(
            limit = limit,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toMediaItem() }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = runCatching {
        val response = requireApi().libraryApi.getMediaFolders().content
        response.items.map { item ->
            LibraryFolder(
                id = item.id.toString(),
                name = item.name ?: "",
                collectionType = item.collectionType?.serialName,
                type = item.type?.serialName,
            )
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
    ): Result<SearchResult> = runCatching {
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
            items = response.items.map { it.toMediaItem() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = runCatching {
        val client = requireApi()
        val uuid = java.util.UUID.fromString(itemId)
        val item = client.userLibraryApi.getItem(itemId = uuid).content
        val people = item.people?.map { person ->
            PersonInfo(
                id = person.id.toString(),
                name = person.name ?: "",
                role = person.role,
                type = person.type?.serialName ?: "",
                primaryImageTag = person.primaryImageTag,
            )
        } ?: emptyList()
        val relatedItems = client.libraryApi.getSimilarItems(
            itemId = uuid,
            limit = 12,
        ).content.items.map { it.toMediaItem() }
        MediaDetail(
            item = item.toMediaItem(),
            people = people,
            relatedItems = relatedItems,
        )
    }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = runCatching {
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
            items = response.items.map { it.toMediaItem() },
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
    ): Result<SearchResult> = runCatching {
        val response = requireApi().itemsApi.getItems(
            genreIds = listOf(java.util.UUID.fromString(genreId)),
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
        ).content
        SearchResult(
            items = response.items.map { it.toMediaItem() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            requireApi().libraryApi.getSimilarItems(
                itemId = java.util.UUID.fromString(itemId),
                limit = limit,
            ).content.items.map { it.toMediaItem() }
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = runCatching {
        requireApi().tvShowsApi.getSeasons(
            seriesId = java.util.UUID.fromString(seriesId),
        ).content.items.map { it.toMediaItem() }
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        runCatching {
            requireApi().tvShowsApi.getEpisodes(
                seriesId = java.util.UUID.fromString(seriesId),
                seasonId = java.util.UUID.fromString(seasonId),
            ).content.items.map { it.toMediaItem() }
        }

    override suspend fun markPlayed(itemId: String): Result<Unit> = runCatching {
        requireApi().playStateApi.markPlayedItem(
            userId = java.util.UUID.fromString(_currentUser.value?.id),
            itemId = java.util.UUID.fromString(itemId),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = runCatching {
        requireApi().playStateApi.markUnplayedItem(
            userId = java.util.UUID.fromString(_currentUser.value?.id),
            itemId = java.util.UUID.fromString(itemId),
        )
    }

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> = runCatching {
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
        runCatching { /* TODO */ }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
    ): Result<Unit> = runCatching { /* TODO */ }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> = runCatching { /* TODO */ }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int, tag: String?): String {
        val server = _currentServer.value ?: return ""
        return "${server.address}/Items/$itemId/Images/$imageType?maxWidth=$maxWidth"
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, tag)

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String {
        val server = _currentServer.value ?: return ""
        val user = _currentUser.value ?: return ""
        return "${server.address}/Videos/$itemId/stream?mediaSourceId=$mediaSourceId&startTimeTicks=$startTimeTicks&api_key=${user.accessToken}"
    }

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
