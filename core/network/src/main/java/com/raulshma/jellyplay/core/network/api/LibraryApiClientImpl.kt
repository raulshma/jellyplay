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
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.network.LyricsApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class LibraryApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val lyricsApi: LyricsApi,
) : LibraryApiClient {

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
        nextUpRewatching: Boolean,
        nextUpMaxDays: Int,
        nextUpExcludedSeriesIds: Set<String>,
        hiddenCwItemIds: Set<String>,
        pinnedSections: List<PinnedHomeSection>,
    ): Result<List<HomeSection>> = engine.apiResultWithRetry {
        coroutineScope {
            val sections = mutableListOf<HomeSection>()
            var firstError: Throwable? = null

            val continueWatchingDeferred = async {
                if (HomeSectionType.CONTINUE_WATCHING in enabledSections) getContinueWatching()
                else Result.success(emptyList())
            }
            val nextUpDeferred = async {
                if (HomeSectionType.NEXT_UP in enabledSections) getNextUp(
                    enableRewatching = nextUpRewatching,
                    maxDays = nextUpMaxDays,
                )
                else Result.success(emptyList())
            }
            val foldersDeferred = async {
                if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
                    getLibraryFolders()
                } else {
                    Result.success(emptyList())
                }
            }
            // Kick off pinned-section fetches concurrently with the standard
            // sections so they add no extra wall-clock latency to home loading.
            val pinnedDeferred = async { fetchPinnedSections(pinnedSections) }

            val continueWatchingResult = continueWatchingDeferred.await()
            val nextUpResult = nextUpDeferred.await()
            val foldersResult = foldersDeferred.await()

            var continueWatchingIds = emptySet<String>()

            if (HomeSectionType.CONTINUE_WATCHING in enabledSections) {
                continueWatchingResult
                    .onSuccess { list ->
                        val filtered = list.filter { it.id !in hiddenCwItemIds }
                        if (filtered.isNotEmpty()) {
                            continueWatchingIds = filtered.map { it.id }.toSet()
                            sections.add(HomeSection("continue_watching", "Continue Watching", HomeSectionType.CONTINUE_WATCHING, filtered))
                        }
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }

            if (HomeSectionType.NEXT_UP in enabledSections) {
                nextUpResult
                    .onSuccess { list ->
                        // Drop items whose series is in the user's "remove from Next Up" blocklist.
                        val filtered = list.filter { it.id !in continueWatchingIds }
                            .filter { it.seriesId == null || it.seriesId !in nextUpExcludedSeriesIds }
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

            if (HomeSectionType.RECOMMENDATIONS in enabledSections) {
                getRecommendations(limit = 20)
                    .onSuccess { result ->
                        if (result.items.isNotEmpty()) {
                            sections.add(HomeSection(
                                "recommendations",
                                "Recommended For You",
                                HomeSectionType.RECOMMENDATIONS,
                                result.items,
                                seedItem = result.seedItem,
                            ))
                        } else {
                            // Fallback "For You" source when there are no similarity
                            // seeds yet (new user, no watch history): surface favorited
                            // / liked items so the home page still has discovery content
                            // (issue #62-H). Mirrors the search "Suggestions" data source.
                            getSearchSuggestions(limit = 20)
                                .onSuccess { search ->
                                    if (search.items.isNotEmpty()) {
                                        sections.add(HomeSection(
                                            "recommendations",
                                            "Recommended For You",
                                            HomeSectionType.RECOMMENDATIONS,
                                            search.items,
                                        ))
                                    }
                                }
                        }
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }

            // Append user-pinned sections (collections / playlists / favorites /
            // genres / studios). They are always fetched regardless of the
            // enabledSections filter, and appended after the standard sections so
            // the HomeViewModel's ordering logic places them at the end of the
            // home screen in the user-chosen pin order.
            pinnedDeferred.await().forEach { section -> sections.add(section) }

            if (sections.isEmpty() && firstError != null) {
                throw firstError!!
            }
            sections
        }
    }

    /**
     * Fetches the items for each [PinnedHomeSection] concurrently (bounded by a
     * semaphore to avoid flooding the server). Empty results are dropped so the
     * home screen never shows an empty pinned row. Order is preserved so the
     * caller's list order maps directly to home row order.
     */
    private suspend fun fetchPinnedSections(
        pinnedSections: List<PinnedHomeSection>,
    ): List<HomeSection> {
        if (pinnedSections.isEmpty()) return emptyList()
        val semaphore = Semaphore(4)
        return coroutineScope {
            val deferred = pinnedSections.map { pinned ->
                async {
                    semaphore.acquire()
                    try {
                        val items = getPinnedSectionItems(pinned)
                        if (items.isNotEmpty()) {
                            HomeSection(
                                id = "pinned_${pinned.id}",
                                title = pinned.title,
                                type = HomeSectionType.PINNED,
                                items = items,
                            )
                        } else null
                    } catch (_: Exception) {
                        // A single failing pin (e.g. deleted collection) must not
                        // break the whole home screen; just drop that row.
                        null
                    } finally {
                        semaphore.release()
                    }
                }
            }
            deferred.awaitAll().filterNotNull()
        }
    }

    /** Resolves the items for a single pinned section using its source type. */
    private suspend fun getPinnedSectionItems(pinned: PinnedHomeSection): List<MediaItem> = when (pinned.type) {
        PinnedSectionType.COLLECTION -> getCollectionItems(pinned.sourceId, limit = 20)
            .getOrNull()?.items.orEmpty()
        // Playlists and collections are both parent-scoped item queries; reusing
        // getCollectionItems avoids excluding episode items (getMediaItems drops
        // seasons/episodes), which matters for video playlists.
        PinnedSectionType.PLAYLIST -> getCollectionItems(pinned.sourceId, limit = 20)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.FAVORITES -> getFavorites(limit = 20)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.GENRE -> getItemsByGenre(pinned.sourceId, limit = 20)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.STUDIO -> getItemsByStudio(pinned.sourceId, limit = 20)
            .getOrNull()?.items.orEmpty()
    }

    override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
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

    override suspend fun getNextUp(
        limit: Int,
        enableRewatching: Boolean,
        maxDays: Int,
    ): Result<List<MediaItem>> = engine.apiResultWithRetry {
        val cutoff = if (maxDays > 0) {
            java.time.LocalDateTime.now().minusDays(maxDays.toLong())
        } else {
            null
        }
        val response = engine.requireApi().tvShowsApi.getNextUp(
            limit = limit,
            enableRewatching = enableRewatching,
            nextUpDateCutoff = cutoff,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        engine.run { (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating() }
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> = engine.apiResultWithRetry {
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

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = engine.apiResultWithRetry {
        val response = engine.requireApi().libraryApi.getMediaFolders().content
            ?: throw IllegalStateException("Server returned empty response")
        val enabledFolders = engine.currentUser.value?.enabledFolderIds
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
        studioIds: List<String>?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
        searchTerm: String?,
        tags: List<String>?,
    ): Result<SearchResult> = engine.apiResultWithRetry {
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
            studioIds = studioIds?.mapNotNull { it.toUUID() },
            tags = tags,
            sortBy = listOfNotNull(sortByEnum),
            sortOrder = listOf(sortOrderEnum),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            searchTerm = searchTerm?.takeIf { it.isNotBlank() },
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

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = engine.apiResultWithRetry {
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
                source.toMediaSource(
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

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
            ?: throw IllegalStateException("Not authenticated")
        val response = engine.requireApi().userLibraryApi.getIntros(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        engine.run {
            (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
        }
    }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = engine.apiResultWithRetry {
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

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = engine.apiResultWithRetry {
        // Mirrors jellyfin-web's useSearchSuggestions: getItems sorted by
        // [IsFavoriteOrLiked, Random] over Movies, Series and MusicArtists.
        // Unlike the web client (which disables images for the cheap empty
        // state) we keep images on so we can render poster cards that match
        // the rest of the app's design language.
        val response = engine.requireApi().itemsApi.getItems(
            sortBy = listOf(ItemSortBy.IS_FAVORITE_OR_LIKED, ItemSortBy.RANDOM),
            includeItemTypes = listOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.MUSIC_ARTIST,
            ),
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.GENRES,
            ),
        ).content
        SearchResult(
            items = engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() },
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        engine.apiResultWithRetry {
            // The Jellyfin SDK's typed getItems() doesn't expose the AnyProviderId
            // filter, so use the raw GET with a custom query parameter. Jellyfin's
            // /Items endpoint accepts "AnyProviderId" in the "tmdb:123" format.
            val response = engine.requireApi().get<org.jellyfin.sdk.model.api.BaseItemDtoQueryResult>(
                pathTemplate = "/Items",
                queryParameters = mapOf(
                    "Recursive" to true,
                    "Limit" to 1,
                    "AnyProviderId" to "$provider:$id",
                ),
            ).content
            response.items.firstOrNull()?.id?.toString()
        }

    override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> =
        engine.apiResultWithRetry {
            val userId = engine.currentUser.value?.id?.toUUID()
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
    ): Result<SearchResult> = engine.apiResultWithRetry {
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

    override suspend fun getStudios(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<Studio>> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
        val response = engine.requireApi().studiosApi.getStudios(
            parentId = parentId?.let { it.toUUID() },
            userId = userId,
            startIndex = startIndex,
            limit = limit,
        ).content
        response.items.map { item ->
            Studio(id = item.id.toString(), name = item.name ?: "")
        }
    }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = engine.apiResultWithRetry {
        val response = engine.requireApi().itemsApi.getItems(
            studioIds = listOf(studioId.toUUID()),
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
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

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = engine.apiResultWithRetry {
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

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
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
        engine.apiResultWithRetry {
            engine.run {
                engine.requireApi().libraryApi.getSimilarItems(
                    itemId = itemId.toUUID(),
                    limit = limit,
                ).content.items.map { it.toMediaItem() }.filterByParentalRating()
            }
        }

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            val userId = engine.currentUser.value?.id?.toUUID()
                ?: return@apiResultWithRetry emptyList()
            engine.run {
                engine.requireApi().instantMixApi.getInstantMixFromItem(
                    userId = userId,
                    itemId = itemId.toUUID(),
                    limit = limit,
                    fields = listOf(
                        ItemFields.OVERVIEW,
                        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    ),
                ).content.items.map { it.toMediaItem() }.filterByParentalRating()
            }
        }

    override suspend fun getRecommendations(limit: Int): Result<RecommendationResult> = runCatching {
        val continueWatching = getContinueWatching(limit = 5).getOrDefault(emptyList())
        val nextUp = getNextUp(limit = 5).getOrDefault(emptyList())

        val seedItems = (continueWatching + nextUp)
            .distinctBy { it.id }
            .take(5)

        if (seedItems.isEmpty()) return@runCatching RecommendationResult(emptyList(), null)

        val seedIds = seedItems.map { it.id }.toSet()
        val semaphore = Semaphore(3)
        val allSimilar = coroutineScope {
            seedItems.map { seed ->
                async {
                    semaphore.acquire()
                    try { getSimilarItems(seed.id, limit = limit / seedItems.size + 2).getOrDefault(emptyList()) }
                    finally { semaphore.release() }
                }
            }.flatMap { it.await() }
        }

        val recommendations = allSimilar
            .filter { it.id !in seedIds }
            .distinctBy { it.id }
            .take(limit)
        
        RecommendationResult(recommendations, seedItems.firstOrNull())
    }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
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

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            val response = engine.requireApi().libraryApi.getThemeSongs(
                itemId = itemId.toUUID(),
            ).content
            engine.run { response.items.map { it.toMediaItem() }.filterByParentalRating() }
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
        engine.run {
            engine.requireApi().tvShowsApi.getSeasons(
                seriesId = seriesId.toUUID(),
            ).content.items.map { it.toMediaItem() }.filterByParentalRating()
        }
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
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
    ): Result<SearchResult> = engine.apiResultWithRetry {
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
    ): Result<List<String>> = engine.apiResultWithRetry {
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
        startIndex: Int,
    ): Result<SearchResult> = engine.apiResultWithRetry {
        val response = engine.requireApi().itemsApi.getItems(
            includeItemTypes = mediaTypes?.mapNotNull { it.toBaseItemKind() },
            filters = listOf(ItemFilter.IS_FAVORITE),
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

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = engine.apiResultWithRetry {
        lyricsApi.fetchLyrics(itemId)
    }

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = engine.apiResultWithRetry {
        val response = engine.requireApi().itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            limit = limit,
            recursive = true,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.CAN_DELETE,
                ItemFields.DATE_CREATED,
            ),
        ).content
        val currentUserId = engine.currentUser.value?.id
        response.items.map { item ->
            Playlist(
                id = item.id.toString(),
                name = item.name ?: "",
                overview = item.overview,
                itemCount = item.childCount ?: 0,
                imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
                userId = currentUserId,
                isReadOnly = false,
                isPublic = false,
                canEdit = true,
                canDelete = item.canDelete ?: true,
                createdAt = item.dateCreated?.toString(),
            )
        }
    }

    override suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int,
        limit: Int,
    ): Result<List<PlaylistItem>> = engine.apiResultWithRetry {
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
                playlistItemId = item.playlistItemId?.toString(),
                name = item.name ?: "",
                artist = item.albumArtist ?: item.artistItems?.firstOrNull()?.name,
                album = item.album,
                mediaType = item.type?.toMediaType() ?: MediaType.UNKNOWN,
                runTimeTicks = item.runTimeTicks,
            )
        }
    }

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
    ): Result<String> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
        val dto = CreatePlaylistDto(
            name = name,
            ids = itemIds.map { it.toUUID() },
            userId = userId,
            mediaType = SdkMediaType.AUDIO,
            users = emptyList(),
            isPublic = false,
        )
        val response = engine.requireApi().playlistsApi.createPlaylist(dto).content
        response.id?.toString() ?: throw IllegalStateException("Created playlist has no id")
    }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = engine.apiResultWithRetry {
        val dto = UpdatePlaylistDto(
            name = name,
            isPublic = isPublic,
        )
        engine.requireApi().playlistsApi.updatePlaylist(playlistId.toUUID(), dto).content
        Unit
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().request(
            method = HttpMethod.DELETE,
            pathTemplate = "Items/$playlistId",
        )
        Unit
    }

    override suspend fun addItemsToPlaylist(
        playlistId: String,
        itemIds: List<String>,
    ): Result<Unit> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
        engine.requireApi().playlistsApi.addItemToPlaylist(
            playlistId = playlistId.toUUID(),
            ids = itemIds.map { it.toUUID() },
            userId = userId,
        ).content
        Unit
    }

    override suspend fun removeItemsFromPlaylist(
        playlistId: String,
        entryIds: List<String>,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().playlistsApi.removeItemFromPlaylist(
            playlistId = playlistId,
            entryIds = entryIds,
        ).content
        Unit
    }

    override suspend fun movePlaylistItem(
        playlistId: String,
        entryId: String,
        newIndex: Int,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().playlistsApi.moveItem(
            playlistId = playlistId,
            itemId = entryId,
            newIndex = newIndex,
        ).content
        Unit
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id
            ?: throw IllegalStateException("Not authenticated")
        engine.requireApi().playStateApi.markPlayedItem(
            userId = userId.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id
            ?: throw IllegalStateException("Not authenticated")
        engine.requireApi().playStateApi.markUnplayedItem(
            userId = userId.toUUID(),
            itemId = itemId.toUUID(),
        )
    }

    override suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean?): Result<Boolean> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id
            ?: throw IllegalStateException("Not authenticated")
        val uuid = itemId.toUUID()
        val cached = favoriteCache[uuid]
        val isFavorite = currentIsFavorite ?: cached ?: run {
            val fetched = engine.requireApi().userLibraryApi.getItem(itemId = uuid).content.userData?.isFavorite == true
            favoriteCache.put(uuid, fetched)
            fetched
        }
        if (isFavorite) {
            engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
            favoriteCache.put(uuid, false)
            false
        } else {
            engine.requireApi().userLibraryApi.markFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
            favoriteCache.put(uuid, true)
            true
        }
    }

    private val favoriteCache = androidx.collection.LruCache<UUID, Boolean>(200)

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String {
        val api = engine.api ?: return ""
        val imageTypeEnum = org.jellyfin.sdk.model.api.ImageType.fromNameOrNull(imageType)
            ?: return ""
        return api.imageApi.getItemImageUrl(
            itemId = runCatching { itemId.toUUID() }.getOrNull() ?: return "",
            imageType = imageTypeEnum,
            tag = tag,
            maxWidth = maxWidth,
            imageIndex = imageIndex,
        )
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)

    override suspend fun getChildItemImageUrls(parentId: String, limit: Int): List<String> {
        return try {
            val api = engine.requireApi()
            val response = api.itemsApi.getItems(
                parentId = parentId.toUUID(),
                includeItemTypes = listOf(BaseItemKind.PHOTO),
                limit = limit,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
            ).content
            response.items.mapNotNull { item ->
                if (item.imageTags?.containsKey(ImageType.PRIMARY) == true) {
                    getImageUrl(item.id.toString(), "Primary", 200)
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
