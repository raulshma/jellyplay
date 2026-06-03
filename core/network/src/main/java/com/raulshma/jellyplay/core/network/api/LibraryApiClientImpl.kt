package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.LyricsApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : LibraryApiClient {

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
    ): Result<List<HomeSection>> = engine.apiResult {
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
                        val semaphore = Semaphore(4)
                        val latestDeferred = filteredFolders
                            .map { folder ->
                                async {
                                    semaphore.acquire()
                                    try { folder to getLatestMedia(folder.id, limit = 16) }
                                    finally { semaphore.release() }
                                }
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
                    val latestMediaLastIndex = sections.indexOfLast { it.type == HomeSectionType.LATEST_MEDIA }
                    val insertIndex = if (latestMediaLastIndex >= 0) latestMediaLastIndex + 1 else sections.size
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
            val response = engine.requireApi().userLibraryApi.getLatestMedia(
                parentId = parentId.toUUID(),
                limit = limit,
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ),
            ).content ?: emptyList()
            response.map { it.toMediaItem() }.filter { item ->
                engine.currentMaxParentalRating?.let { max ->
                    item.officialRating?.let { rating ->
                        engine.ratingToAge(rating)?.let { age -> age <= max }
                    } != false
                } != false
            }
        }

    override suspend fun getNextUp(limit: Int): Result<List<MediaItem>> = engine.apiResult {
        val response = engine.requireApi().tvShowsApi.getNextUp(
            limit = limit,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        engine.run { (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating() }
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getResumeItems(
            limit = limit,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        engine.run {
            (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
                .distinctBy { it.id }
        }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = engine.apiResult {
        val response = engine.requireApi().libraryApi.getMediaFolders().content
            ?: throw IllegalStateException("Server returned empty response")
        val enabledFolders = engine._currentUser.value?.enabledFolderIds
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
    ): Result<SearchResult> = engine.apiResult {
        val sortByEnum = ItemSortBy.entries
            .find { it.serialName.equals(sortBy, ignoreCase = true) }
        val sortOrderEnum = SortOrder.entries
            .find { it.serialName.equals(sortOrder, ignoreCase = true) }
            ?: SortOrder.ASCENDING
        val response = engine.requireApi().itemsApi.getItems(
            parentId = parentId?.let { it.toUUID() },
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            excludeItemTypes = listOf(
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
            ),
            genres = genres,
            years = years,
            sortBy = listOfNotNull(sortByEnum),
            sortOrder = listOf(sortOrderEnum),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.GENRES,
            ),
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = engine.apiResult {
        coroutineScope {
            val client = engine.requireApi()
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
    ): Result<SearchResult> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            searchTerm = query,
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            limit = limit,
            startIndex = startIndex,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> =
        engine.apiResult {
            val userId = engine._currentUser.value?.id?.toUUID()
            val response = engine.requireApi().genresApi.getGenres(
                parentId = parentId?.let { it.toUUID() },
                userId = userId,
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
    ): Result<SearchResult> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            genreIds = listOf(genreId.toUUID()),
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            albumArtistIds = listOf(artistId.toUUID()),
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = limit,
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() }
    }

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            parentId = albumId.toUUID(),
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() }
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            engine.run {
                engine.requireApi().libraryApi.getSimilarItems(
                    itemId = itemId.toUUID(),
                    limit = limit,
                ).content.items.map { it.toMediaItem() }.filterByParentalRating()
            }
        }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        runCatching {
            val response = engine.requireApi().itemsApi.getItems(
                personIds = listOf(personId.toUUID()),
                limit = limit,
                recursive = true,
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ),
            ).content
            engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() }
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = engine.apiResult {
        engine.run {
            engine.requireApi().tvShowsApi.getSeasons(
                seriesId = seriesId.toUUID(),
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        engine.apiResult {
            engine.run {
                engine.requireApi().tvShowsApi.getEpisodes(
                    seriesId = seriesId.toUUID(),
                    seasonId = seasonId.toUUID(),
                ).content.items.map { it.toMediaItem() }.filterByParentalRating()
            }
        }

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            parentId = collectionId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            parentId = parentId?.let { it.toUUID() },
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(ItemFields.TAGS),
        ).content
        response.items.flatMap { it.tags ?: emptyList() }.distinct().sorted()
    }

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            filters = listOf(ItemFilter.IS_FAVORITE),
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        LyricsApi.fetchLyrics(engine.okHttpClient, server.address, itemId, user.accessToken)
    }

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { item ->
            Playlist(
                id = item.id.toString(),
                name = item.name ?: "",
                overview = item.overview,
                itemCount = item.childCount ?: 0,
                imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
            )
        }
    }

    override suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int,
        limit: Int,
    ): Result<List<PlaylistItem>> = engine.apiResult {
        val response = engine.requireApi().itemsApi.getItems(
            parentId = playlistId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { item ->
            PlaylistItem(
                id = item.id.toString(),
                name = item.name ?: "",
                artist = item.albumArtist ?: item.artistItems?.firstOrNull()?.name,
                album = item.album,
                mediaType = item.type?.toMediaType() ?: MediaType.UNKNOWN,
                runTimeTicks = item.runTimeTicks,
            )
        }
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> = engine.apiResult {
        engine.requireApi().playStateApi.markPlayedItem(
            userId = engine._currentUser.value?.id!!.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = engine.apiResult {
        engine.requireApi().playStateApi.markUnplayedItem(
            userId = engine._currentUser.value?.id!!.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean?): Result<Boolean> = engine.apiResult {
        val uuid = itemId.toUUID()
        val isFavorite = currentIsFavorite ?: engine.requireApi().userLibraryApi.getItem(itemId = uuid).content.userData?.isFavorite == true
        if (isFavorite) {
            engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = engine._currentUser.value?.id!!.toUUID(),
                itemId = uuid,
            )
            false
        } else {
            engine.requireApi().userLibraryApi.markFavoriteItem(
                userId = engine._currentUser.value?.id!!.toUUID(),
                itemId = uuid,
            )
            true
        }
    }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String {
        val server = engine._currentServer.value ?: return ""
        val indexPart = imageIndex?.let { "/$it" } ?: ""
        val widthPart = maxWidth?.let { "?maxWidth=$it" } ?: ""
        return "${server.address}/Items/$itemId/Images/$imageType$indexPart$widthPart"
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)
}
