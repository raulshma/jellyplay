package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.lruMapOf
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.network.LyricsApi
import com.raulshma.jellyplay.core.network.library.DETAIL_PROJECTION_FIELDS
import com.raulshma.jellyplay.core.network.library.EmptyLibraryFallback
import com.raulshma.jellyplay.core.network.library.FavoriteFlagCache
import com.raulshma.jellyplay.core.network.library.HomeSectionSources
import com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_FIELDS
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_ITEM_TYPES
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_SORT_BY
import com.raulshma.jellyplay.core.network.library.emptyFallbackTotalCount
import com.raulshma.jellyplay.core.network.library.libraryExcludeKinds
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

/**
 * Fields the detail mapper ([getMediaDetail]) reads from the BaseItemDto,
 * resolved from the shared commonMain wire projection
 * ([DETAIL_PROJECTION_FIELDS] — see it for why the projection is explicit).
 */
private val DETAIL_ITEM_FIELDS = DETAIL_PROJECTION_FIELDS.map { name ->
    requireNotNull(ItemFields.entries.firstOrNull { it.serialName == name }) {
        "ItemFields has no serial name '$name' — SDK drift vs the shared projection"
    }
}

/** The jellyfin-web useSearchSuggestions shape, resolved against the SDK enums. */
private val SEARCH_SUGGESTIONS_SORT = SEARCH_SUGGESTIONS_SORT_BY.map { token ->
    ItemSortBy.entries.first { it.serialName == token }
}

private val SEARCH_SUGGESTIONS_KINDS = SEARCH_SUGGESTIONS_ITEM_TYPES.map { token ->
    BaseItemKind.entries.first { it.serialName == token }
}

private val SEARCH_SUGGESTIONS_PROJECTION = SEARCH_SUGGESTIONS_FIELDS.map { token ->
    ItemFields.entries.first { it.serialName == token }
}

@Singleton
class LibraryApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val lyricsApi: LyricsApi,
) : LibraryApiClient, HomeSectionSources {

    /**
     * Parent ids of libraries already known to return nothing from both the
     * primary items query and the getLatestMedia fallback — see getMediaItems.
     * Access-order LRU, capped small; safe across library refreshes because a
     * library that gains content short-circuits before the fallback is reached.
     */
    private val emptyFallbackLibraries = lruMapOf<String, Unit>(32)

    /**
     * The shared commonMain empty-library fallback ladder
     * ([EmptyLibraryFallback]); this client supplies its synchronized LRU
     * memo (runs inside the apiResultWithRetry IO block, so every access
     * holds the map's monitor — the `containsKey` probe deliberately does not
     * refresh access order, the historical shape) and the SDK
     * getLatestMedia transport.
     */
    private val emptyLibraryFallback = EmptyLibraryFallback(
        isKnownEmpty = { parentId ->
            synchronized(emptyFallbackLibraries) { emptyFallbackLibraries.containsKey(parentId) }
        },
        rememberEmpty = { parentId ->
            // Main-dispatcher-safe: confined to the apiResultWithRetry IO block.
            synchronized(emptyFallbackLibraries) { emptyFallbackLibraries[parentId] = Unit }
        },
        fetchLatest = { parentId, limit ->
            engine.requireApi().userLibraryApi.getLatestMedia(
                parentId = parentId.toUUID(),
                limit = limit,
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    ItemFields.GENRES,
                ),
            ).content ?: emptyList()
        },
    )

    /**
     * The home feed's fetch choreography (sub-call fan-out, semaphore bounds,
     * TTL sub-caches, recommendations chain) lives in the commonMain
     * [HomeSectionsFetcher]; this client merely supplies the transport via
     * [HomeSectionSources] (satisfied for free — the same overrides serve
     * [LibraryApiClient]) and its atomic-session identity.
     */
    private val homeSectionsFetcher = HomeSectionsFetcher(
        sources = this,
        cacheIdentity = { currentHomeCacheIdentity() },
    )

    /**
     * The current cache identity from ONE atomic session snapshot — never two
     * separate StateFlow reads, which could observe a synthetic
     * (newServer, oldUser) pair mid-switch. [CacheIdentity.UNKNOWN] before
     * login never leaks into another user's entry: a wrong identity is a
     * guaranteed miss.
     */
    private fun currentHomeCacheIdentity(): CacheIdentity {
        val session = engine.session.value
        return CacheIdentity.ofOrNull(session?.server?.id, session?.user?.id)
    }

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> = engine.apiResultWithRetry {
        homeSectionsFetcher.fetch(query, force)
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
            engine.run { response.toFilteredMediaItems() }
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
        engine.run { (response?.items ?: emptyList()).toFilteredMediaItems() }
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
            (response?.items ?: emptyList()).toFilteredMediaItems()
                .distinctBy { it.id }
        }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = engine.apiResultWithRetry {
        // Use the server-filtered user-views endpoint (/Users/{userId}/Views)
        // instead of /Library/MediaFolders. MediaFolders is admin-only and
        // returns ALL physical folders with no per-user access filtering,
        // which forced a fragile client-side filter on a login-time snapshot
        // of enabledFolderIds — a snapshot that goes stale the moment an
        // admin changes the user's library access. getUserViews returns only
        // the libraries the current user can access, live.
        val response = engine.requireApi().userViewsApi.getUserViews().content
            ?: throw IllegalStateException("Server returned empty response")
        (response.items ?: emptyList()).map { item ->
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
        filters: LibraryFilters,
        studioIds: List<String>?,
        startIndex: Int,
        limit: Int,
        searchTerm: String?,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Result<SearchResult> = engine.apiResultWithRetry {
        val sortByEnums = parseItemSortList(filters.sortBy.apiValue)
        val sortOrderEnum = SortOrder.entries
            .find { it.serialName.equals(filters.sortBy.sortOrder, ignoreCase = true) }
            ?: SortOrder.ASCENDING
        // Played-status maps onto Jellyfin's ItemFilter (IsPlayed/IsUnplayed).
        // Previously these chips toggled + persisted but never reached the query,
        // so the grid silently ignored them (analysis F1).
        val itemFilters = buildList {
            when (filters.playedStatus.takeIf { it != com.raulshma.jellyplay.core.model.PlayedStatus.ALL }) {
                com.raulshma.jellyplay.core.model.PlayedStatus.PLAYED -> add(ItemFilter.IS_PLAYED)
                com.raulshma.jellyplay.core.model.PlayedStatus.UNPLAYED -> add(ItemFilter.IS_UNPLAYED)
                else -> {}
            }
            // IsResumable restricts to items with a playback position
            // (UserData.PlaybackPositionTicks > 0). Composes with played-status
            // and powers the "In Progress" library filter / sort.
            if (filters.isResumable == true) add(ItemFilter.IS_RESUMABLE)
        }
        // includeItemTypes / excludeItemTypes: resolve the requested kinds once,
        // then drop SEASON/EPISODE from the exclude list when they were
        // explicitly included (the shared [libraryExcludeKinds] policy —
        // Jellyfin would otherwise receive contradictory include+exclude for
        // the same kind and return an empty result).
        val mediaTypes = filters.mediaTypes.takeIf { it.isNotEmpty() }
        val includeKinds = mediaTypes?.mapNotNull { it.toBaseItemKind() }.orEmpty()
        val excludeKinds = libraryExcludeKinds(
            seasonKind = BaseItemKind.SEASON,
            episodeKind = BaseItemKind.EPISODE,
            includeKinds = includeKinds,
            includeEpisodes = kindFilter.includeEpisodes,
        )
        val response = engine.requireApi().itemsApi.getItems(
            parentId = parentId?.let { it.toUUID() },
            includeItemTypes = includeKinds.takeIf { it.isNotEmpty() },
            excludeItemTypes = excludeKinds,
            genres = filters.genres.takeIf { it.isNotEmpty() },
            years = filters.years.takeIf { it.isNotEmpty() },
            studioIds = studioIds?.mapNotNull { it.toUUID() },
            tags = filters.tags.takeIf { it.isNotEmpty() },
            sortBy = sortByEnums.takeIf { it.isNotEmpty() },
            sortOrder = listOf(sortOrderEnum),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            searchTerm = searchTerm?.takeIf { it.isNotBlank() },
            filters = itemFilters.takeIf { it.isNotEmpty() },
            minCommunityRating = filters.minRating.takeIf { it > 0f }?.toDouble(),
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.GENRES,
            ),
        ).content
        val rawItems = emptyLibraryFallback.resolve(
            primaryItems = response.items,
            parentId = parentId,
            searchTerm = searchTerm,
            limit = limit,
        )
        val totalCount = emptyFallbackTotalCount(
            primaryCount = response.items.size,
            resolvedCount = rawItems.size,
            serverTotal = response.totalRecordCount,
        )
        SearchResult(
            items = engine.run { rawItems.toFilteredMediaItems() },
            totalRecordCount = totalCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = engine.apiResultWithRetry {
        // NOTE: similar/related items are fetched separately via [getSimilarItems]
        // rather than nested in this call. Previously a nested async+await
        // blocked the entire detail (title, poster, streams, cast) from
        // returning until similar items resolved — the dominant source of
        // perceived cold-open lag on the detail screen. Now the core detail
        // returns as soon as the item fetch completes; the VM fetches similar
        // concurrently and merges it into state so the screen renders
        // incrementally.
        //
        // The parental-rating filter (toFilteredMediaItems) is intentionally not applied here: the server
        // applies its own parental controls to userLibraryApi.getItem, and the
        // detail screen is reached only after the item already surfaced in a
        // filtered list — double-filtering a single detail adds no protection.
        val client = engine.requireApi()
        val uuid = itemId.toUUID()
        // Project the non-default ItemFields the mapper reads. userLibraryApi.
        // getItem accepts no `fields`, so without projection some fields
        // (notably TRICKPLAY, used for scrub preview and download) come back
        // null depending on server defaults, and source.trickplayInfo silently
        // vanishes. Querying via itemsApi.getItems with an id filter + fields
        // set both bounds the payload and guarantees the mapper's reads are
        // populated. Fall back to getItem if the projected query returns empty
        // (older servers occasionally omit trickplay-less items).
        val item = run {
            val projected = client.itemsApi.getItems(
                ids = listOf(uuid),
                fields = DETAIL_ITEM_FIELDS,
            ).content.items?.firstOrNull()
            projected ?: client.userLibraryApi.getItem(itemId = uuid).content
        }
        val people = (item.people?.map { person ->
            PersonInfo(
                id = person.id.toString(),
                name = person.name ?: "",
                role = person.role,
                type = person.type?.serialName ?: "",
                primaryImageTag = person.primaryImageTag,
            )
        } ?: emptyList()).distinctBy { it.id }
        val chapters = item.chapters?.map { chapter ->
            ChapterInfo(
                name = chapter.name ?: "",
                startPositionTicks = chapter.startPositionTicks ?: 0L,
                imageDateModified = chapter.imageDateModified?.toString(),
                imageTag = chapter.imageTag,
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
            relatedItems = emptyList(),
            chapters = chapters,
            mediaSources = mediaSources,
            externalUrls = externalUrls,
            providerIds = providerIds,
        )
    }

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
            ?: throw IllegalStateException("Not authenticated")
        val response = engine.requireApi().userLibraryApi.getIntros(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        engine.run {
            (response?.items ?: emptyList()).toFilteredMediaItems()
        }
    }

    override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
            ?: throw IllegalStateException("Not authenticated")
        // Unlike getIntros (a BaseItemDtoQueryResult with a paginated `.items`
        // wrapper), getSpecialFeatures returns a bare List<BaseItemDto> directly
        // — the /Items/{id}/SpecialFeatures endpoint emits a JSON array, so the
        // SDK surfaces it as a list rather than a query result.
        val response = engine.requireApi().userLibraryApi.getSpecialFeatures(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        engine.run {
            (response ?: emptyList()).toFilteredMediaItems()
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
            items = engine.run { response.items.toFilteredMediaItems() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = engine.apiResultWithRetry {
        // The jellyfin-web useSearchSuggestions shape, held once in commonMain
        // (SEARCH_SUGGESTIONS_* and resolved above against the SDK enums).
        val response = engine.requireApi().itemsApi.getItems(
            sortBy = SEARCH_SUGGESTIONS_SORT,
            includeItemTypes = SEARCH_SUGGESTIONS_KINDS,
            limit = limit,
            recursive = true,
            fields = SEARCH_SUGGESTIONS_PROJECTION,
        ).content
        SearchResult(
            items = engine.run { response.items.toFilteredMediaItems() },
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        engine.apiResultWithRetry {
            // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: the result
            // is a bare item id used for matching, not display, and the server
            // scopes the query to the authenticated user's libraries.
            //
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
            items = engine.run { response.items.toFilteredMediaItems() },
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
            items = engine.run { response.items.toFilteredMediaItems() },
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
        engine.run { response.items.toFilteredMediaItems() }
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
        engine.run { response.items.toFilteredMediaItems() }
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            engine.run {
                engine.requireApi().libraryApi.getSimilarItems(
                    itemId = itemId.toUUID(),
                    limit = limit,
                ).content.items.toFilteredMediaItems()
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
                ).content.items.toFilteredMediaItems()
            }
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
            engine.run { response.items.toFilteredMediaItems() }
        }

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            val response = engine.requireApi().libraryApi.getThemeSongs(
                itemId = itemId.toUUID(),
            ).content
            engine.run { response.items.toFilteredMediaItems() }
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = engine.apiResultWithRetry {
        engine.run {
            engine.requireApi().tvShowsApi.getSeasons(
                seriesId = seriesId.toUUID(),
            ).content.items.toFilteredMediaItems()
        }
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            engine.run {
                engine.requireApi().tvShowsApi.getEpisodes(
                    seriesId = seriesId.toUUID(),
                    seasonId = seasonId.toUUID(),
                ).content.items.toFilteredMediaItems()
            }
        }

    override suspend fun getAllEpisodes(seriesId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            engine.run {
                engine.requireApi().tvShowsApi.getEpisodes(
                    seriesId = seriesId.toUUID(),
                ).content.items.toFilteredMediaItems()
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
            items = engine.run { response.items.toFilteredMediaItems() },
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> =
        engine.apiResultWithRetry {
            // Collections are BoxSet items. Mirrors the getPlaylists query
            // (includeItemTypes + recursive) but targets BOX_SET. CHILD_COUNT is
            // requested so the picker can show "N items" per collection.
            val response = engine.requireApi().itemsApi.getItems(
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                limit = limit,
                recursive = true,
                fields = listOf(ItemFields.CHILD_COUNT),
            ).content
            response.items.map { item ->
                CollectionSummary(
                    id = item.id.toString(),
                    name = item.name ?: "",
                    itemCount = item.childCount ?: 0,
                    imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
                )
            }
        }

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        engine.apiResultWithRetry {
            val result = engine.requireApi().collectionApi.createCollection(
                name = name,
                ids = itemIds,
            ).content
            result.id.toString()
        }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        engine.apiResultWithRetry {
            engine.requireApi().collectionApi.addToCollection(
                collectionId = collectionId.toUUID(),
                ids = itemIds.map { it.toUUID() },
            ).content
            Unit
        }

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = engine.apiResultWithRetry {
        // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: tags are
        // plain strings with no rating attribute to filter on, and the server
        // already enforces library-access scoping on the underlying item query.
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
            items = engine.run { response.items.toFilteredMediaItems() },
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
        // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: PlaylistItem
        // does not carry an officialRating, and the server enforces playlist
        // ACLs plus parental controls on the underlying item query.
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
        mediaType: MediaType,
    ): Result<String> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id?.toUUID()
        // Jellyfin tags a playlist with a single media type so the server can
        // sort/limit it correctly. Music callers (the default) keep AUDIO;
        // video detail screens pass VIDEO. Without this, a playlist created
        // from a movie would be mislabelled AUDIO.
        val sdkMediaType = if (mediaType.isAudioType) SdkMediaType.AUDIO else SdkMediaType.VIDEO
        val dto = CreatePlaylistDto(
            name = name,
            ids = itemIds.map { it.toUUID() },
            userId = userId,
            mediaType = sdkMediaType,
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
        favoriteFlags.toggle(
            cacheKey = uuid.toString(),
            currentIsFavorite = currentIsFavorite,
            fetchCurrent = {
                engine.requireApi().userLibraryApi.getItem(itemId = uuid).content.userData?.isFavorite == true
            },
            markOnServer = {
                engine.requireApi().userLibraryApi.markFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            },
            unmarkOnServer = {
                engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            },
        )
    }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = engine.apiResultWithRetry {
        val userId = engine.currentUser.value?.id
            ?: throw IllegalStateException("Not authenticated")
        val uuid = itemId.toUUID()
        if (isFavorite) {
            engine.requireApi().userLibraryApi.markFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
        } else {
            engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
        }
        favoriteFlags.put(uuid.toString(), isFavorite)
        Unit
    }

    /**
     * The shared commonMain favorite-flag cache-aside choreography
     * ([FavoriteFlagCache]); this client supplies the atomic-session identity
     * (see [currentHomeCacheIdentity]) and the SDK transport calls.
     */
    private val favoriteFlags = FavoriteFlagCache { currentHomeCacheIdentity() }

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
