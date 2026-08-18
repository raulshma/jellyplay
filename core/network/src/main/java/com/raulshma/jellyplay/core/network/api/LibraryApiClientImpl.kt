package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.lruMapOf
import com.raulshma.jellyplay.core.model.isAudioType
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

/**
 * Fields the detail mapper ([getMediaDetail]) reads from the BaseItemDto.
 * Projected explicitly because [org.jellyfin.sdk.api.operations.UserLibraryApi.getItem]
 * accepts no `fields` parameter and several of these (notably TRICKPLAY, used
 * for scrub preview and download) come back null without an explicit request.
 */
private val DETAIL_ITEM_FIELDS = listOf(
    ItemFields.PEOPLE,
    ItemFields.CHAPTERS,
    ItemFields.MEDIA_SOURCES,
    ItemFields.TRICKPLAY,
    ItemFields.EXTERNAL_URLS,
    ItemFields.ORIGINAL_TITLE,
    ItemFields.PRODUCTION_LOCATIONS,
    ItemFields.STUDIOS,
    ItemFields.GENRES,
    ItemFields.OVERVIEW,
    ItemFields.PROVIDER_IDS,
    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
)

@Singleton
class LibraryApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val lyricsApi: LyricsApi,
) : LibraryApiClient {

    /**
     * Parent ids of libraries already known to return nothing from both the
     * primary items query and the getLatestMedia fallback — see getMediaItems.
     * Access-order LRU, capped small; safe across library refreshes because a
     * library that gains content short-circuits before the fallback is reached.
     */
    private val emptyFallbackLibraries = lruMapOf<String, Unit>(32)

    private fun rememberEmptyFallback(parentId: String) {
        // Main-dispatcher-safe: confined to the apiResultWithRetry IO block.
        synchronized(emptyFallbackLibraries) { emptyFallbackLibraries[parentId] = Unit }
    }

    // ── Home hot-path sub-call caches ──────────────────────────────────────
    // MediaRepositoryImpl.getHomeSections caches the whole HomeSectionsResult
    // for 60s and the HomeViewModel's periodic refresh also runs every 60s, so
    // without these each refresh re-fans-out one getLatestMedia per library
    // folder + up to 5 getSimilarItems calls. Latest/recommendations change far
    // less often than Continue Watching / Next Up, so a short TTL here skips
    // those round-trips on back-to-back refreshes while CW/NextUp stay live.
    // Mirrors the 2-minute TTL the repo already uses for the same concepts.
    private val homeLatestMediaCache = TtlCache<List<MediaItem>>(ttlMs = HOME_SUBCALL_CACHE_TTL_MS)
    private val homeSimilarCache = TtlCache<List<MediaItem>>(ttlMs = HOME_SUBCALL_CACHE_TTL_MS)

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>>,
        nextUpRewatching: Boolean,
        nextUpMaxDays: Int,
        nextUpExcludedSeriesIds: Set<String>,
        hiddenCwItemIds: Set<String>,
        pinnedSections: List<PinnedHomeSection>,
    ): Result<HomeSectionsResult> = engine.apiResultWithRetry {
        coroutineScope {
            val sections = mutableListOf<HomeSection>()
            val failedTypes = mutableSetOf<HomeSectionType>()
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

            // Launch the recommendations chain now: it depends only on the
            // Continue Watching / Next Up seeds above (already resolved), not
            // on the per-folder latest-media fan-out below — overlapping the
            // two chains turns home-load wall clock from
            // latestChain + recommendationsChain into max(...) while keeping
            // section emission order unchanged (awaited at its original spot).
            val recommendationsDeferred: kotlinx.coroutines.Deferred<Result<RecommendationResult>>? =
                if (HomeSectionType.RECOMMENDATIONS in enabledSections) {
                    // Reuse the Continue Watching + Next Up lists already fetched
                    // above as recommendation seeds instead of re-hitting the
                    // /Items/Resume and /Shows/NextUp endpoints a second time.
                    val recommendationSeeds =
                        continueWatchingResult.getOrDefault(emptyList()) +
                            nextUpResult.getOrDefault(emptyList())
                    async { getRecommendations(limit = 20, seeds = recommendationSeeds) }
                } else null

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
                    .onFailure {
                        if (firstError == null) firstError = it
                        failedTypes.add(HomeSectionType.CONTINUE_WATCHING)
                    }
            }

            if (HomeSectionType.NEXT_UP in enabledSections) {
                nextUpResult
                    .onSuccess { list ->
                        // Drop items whose series is in the user's "remove from Next Up" blocklist.
                        val filtered = list.filter { it.id !in continueWatchingIds }
                            .filter { it.seriesId == null || it.seriesId !in nextUpExcludedSeriesIds }
                        if (filtered.isNotEmpty()) {
                            sections.add(HomeSection("next_up", "NextUp", HomeSectionType.NEXT_UP, filtered))
                        }
                    }
                    .onFailure {
                        if (firstError == null) firstError = it
                        failedTypes.add(HomeSectionType.NEXT_UP)
                    }
            }

            val allLatestItems = mutableListOf<MediaItem>()

            if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
                foldersResult
                    .onSuccess { folders ->
                        val filteredFolders = folders
                            .filter { it.collectionType != "music" }
                        val semaphore = Semaphore(4)
                        val latestDeferred = filteredFolders
                            .map { folder ->
                                async {
                                    semaphore.acquire()
                                    try { folder to getLatestMediaForHome(folder.id, limit = 16) }
                                    finally { semaphore.release() }
                                }
                            }
                        latestDeferred.forEach { deferred ->
                            val (folder, result) = deferred.await()
                            val disabledForFolder = libraryHomeSectionOverrides[folder.id].orEmpty()
                            result.onSuccess { latest ->
                                // Only feed the aggregated Recently Added row from
                                // libraries the user hasn't disabled it for.
                                if (HomeSectionType.RECENTLY_ADDED !in disabledForFolder) {
                                    allLatestItems.addAll(latest)
                                }
                                val latestEnabledForFolder = HomeSectionType.LATEST_MEDIA in enabledSections &&
                                    HomeSectionType.LATEST_MEDIA !in disabledForFolder
                                if (latest.isNotEmpty() && latestEnabledForFolder) {
                                    val sectionId = "latest_${folder.id}"
                                    sections.add(HomeSection(sectionId, "Latest ${folder.name}", HomeSectionType.LATEST_MEDIA, latest, libraryId = folder.id, collectionType = folder.collectionType))
                                }
                            }.onFailure {
                                // A per-folder Latest Media 403 (e.g. a stale
                                // cached folder list racing with a permission
                                // change) should surface as a partial-load
                                // banner, not vanish silently.
                                if (firstError == null) firstError = it
                                if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                                    failedTypes.add(HomeSectionType.LATEST_MEDIA)
                                }
                            }
                        }
                    }
                    .onFailure {
                        if (firstError == null) firstError = it
                        // The shared folders fetch backs both Latest Media and
                        // Recently Added rows; a failure starves both sections.
                        if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                            failedTypes.add(HomeSectionType.LATEST_MEDIA)
                        }
                        if (HomeSectionType.RECENTLY_ADDED in enabledSections) {
                            failedTypes.add(HomeSectionType.RECENTLY_ADDED)
                        }
                    }
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
                // Launched right after the seed lists resolved (above), so the
                // /Items/Similar fan-out overlapped the per-folder latest-media
                // chain instead of starting after it.
                recommendationsDeferred!!.await()
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
                            //. Mirrors the search "Suggestions" data source.
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
                    .onFailure {
                        if (firstError == null) firstError = it
                        failedTypes.add(HomeSectionType.RECOMMENDATIONS)
                    }
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
            HomeSectionsResult(sections, failedTypes.toSet())
        }
    }

    /**
     * Home-path wrapper around [getLatestMedia] that consults [homeLatestMediaCache]
     * first. The home screen refreshes every 60s (foreground) and re-issues one
     * `/Items/Latest` call per library folder on each refresh; latest-in-library
     * changes less often than that, so a short TTL skips the redundant fan-out.
     * Only the home path uses this — browse/library screens still go straight to
     * [getLatestMedia] for fresh data.
     */
    private suspend fun getLatestMediaForHome(parentId: String, limit: Int): Result<List<MediaItem>> {
        val identity = currentHomeCacheIdentity()
        val cacheKey = "${parentId}_$limit"
        homeLatestMediaCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return getLatestMedia(parentId, limit).also { result ->
            result.getOrNull()?.let { homeLatestMediaCache.put(identity, cacheKey, it) }
        }
    }

    /**
     * Home-path wrapper around [getSimilarItems] (via [getRecommendations]) that
     * memoises each seed's similar-items list in [homeSimilarCache]. The
     * recommendations fan-out (up to 5 concurrent `getSimilarItems` calls) is
     * the single most expensive part of a home refresh; seeds rarely change
     * within the TTL window, so back-to-back refreshes skip it entirely.
     */
    private suspend fun getSimilarItemsForHome(seedId: String, limit: Int): Result<List<MediaItem>> {
        val identity = currentHomeCacheIdentity()
        val cacheKey = "${seedId}_$limit"
        homeSimilarCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return getSimilarItems(seedId, limit).also { result ->
            result.getOrNull()?.let { homeSimilarCache.put(identity, cacheKey, it) }
        }
    }

    /**
     * The current `(serverId, userId)` as a [CacheIdentity], read from the
     * engine's [StateFlow]s. The home sub-call caches are identity-keyed so a
     * user/server switch can't serve the previous identity's latest-media /
     * recommendations — wrong identity misses by construction, so no parallel
     * cross-boundary clearer is needed. Falls back to [CacheIdentity.UNKNOWN]
     * before login / after logout; nothing cached under that key can leak.
     */
    private fun currentHomeCacheIdentity(): CacheIdentity =
        CacheIdentity.ofOrNull(engine.currentServer.value?.id, engine.currentUser.value?.id)
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
        // explicitly included. Jellyfin would otherwise receive contradictory
        // include+exclude for the same kind (e.g. section mode for a TV library
        // includes EPISODE to match /Items/Latest) and return an empty result.
        val mediaTypes = filters.mediaTypes.takeIf { it.isNotEmpty() }
        val includeKinds = mediaTypes?.mapNotNull { it.toBaseItemKind() }.orEmpty()
        val excludeKinds = buildList {
            if (BaseItemKind.SEASON !in includeKinds) add(BaseItemKind.SEASON)
            if (!kindFilter.includeEpisodes && BaseItemKind.EPISODE !in includeKinds) add(BaseItemKind.EPISODE)
        }
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
        val rawItems = if (response.items.isEmpty() && parentId != null && searchTerm.isNullOrBlank()) {
            // Skip the doubled request for libraries already known to be
            // genuinely empty (primary query empty AND fallback empty) — a
            // visit to an empty library previously paid both requests every
            // time. Bounded LRU: once the library gains content the primary
            // query returns items and the fallback is never reached, so the
            // memo cannot serve a stale non-empty state.
            if (synchronized(emptyFallbackLibraries) { emptyFallbackLibraries.containsKey(parentId) }) {
                emptyList()
            } else {
                val fallback = runCatching {
                    engine.requireApi().userLibraryApi.getLatestMedia(
                        parentId = parentId.toUUID(),
                        limit = if (limit > 0) limit else 50,
                        fields = listOf(
                            ItemFields.OVERVIEW,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ItemFields.GENRES,
                        ),
                    ).content
                }.getOrNull() ?: emptyList()
                if (fallback.isEmpty()) rememberEmptyFallback(parentId)
                fallback
            }
        } else {
            response.items
        }
        val totalCount = if (response.items.isEmpty() && rawItems.isNotEmpty()) rawItems.size else response.totalRecordCount
        SearchResult(
            items = engine.run { rawItems.map { it.toMediaItem() }.filterByParentalRating() },
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
        // filterByParentalRating() is intentionally not applied here: the server
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
            (response?.items ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
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
            (response ?: emptyList()).map { it.toMediaItem() }.filterByParentalRating()
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
            // filterByParentalRating() is intentionally not applied: the result
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

    override suspend fun getRecommendations(
        limit: Int,
        seeds: List<MediaItem>,
    ): Result<RecommendationResult> = runCatching {
        // Reuse caller-supplied seeds when available (e.g. the home screen has
        // already fetched Continue Watching + Next Up) to avoid duplicate
        // /Items/Resume and /Shows/NextUp round-trips within the same load.
        val seedItems = if (seeds.isNotEmpty()) {
            seeds.distinctBy { it.id }.take(5)
        } else {
            val continueWatching = getContinueWatching(limit = 5).getOrDefault(emptyList())
            val nextUp = getNextUp(limit = 5).getOrDefault(emptyList())
            (continueWatching + nextUp).distinctBy { it.id }.take(5)
        }

        if (seedItems.isEmpty()) return@runCatching RecommendationResult(emptyList(), null)

        val seedIds = seedItems.map { it.id }.toSet()
        val semaphore = Semaphore(3)
        val allSimilar = coroutineScope {
            seedItems.map { seed ->
                async {
                    semaphore.acquire()
                    try {
                        // Routed through homeSimilarCache: recommendations are the
                        // most expensive part of a home refresh (up to 5 concurrent
                        // /Items/Similar calls) and seeds rarely change within the
                        // TTL window, so back-to-back refreshes (60s cadence) skip
                        // the fan-out. Also benefits the detail screen's re-entry.
                        val perSeedLimit = limit / seedItems.size + 2
                        getSimilarItemsForHome(seed.id, perSeedLimit).getOrDefault(emptyList())
                    }
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

    override suspend fun getAllEpisodes(seriesId: String): Result<List<MediaItem>> =
        engine.apiResultWithRetry {
            engine.run {
                engine.requireApi().tvShowsApi.getEpisodes(
                    seriesId = seriesId.toUUID(),
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
        // filterByParentalRating() is intentionally not applied: tags are
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
        // filterByParentalRating() is intentionally not applied: PlaylistItem
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
        val cacheKey = "$userId:$uuid"
        val cached = favoriteCache[cacheKey]
        val isFavorite = currentIsFavorite ?: cached ?: run {
            val fetched = engine.requireApi().userLibraryApi.getItem(itemId = uuid).content.userData?.isFavorite == true
            favoriteCache.put(cacheKey, fetched)
            fetched
        }
        if (isFavorite) {
            engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
            favoriteCache.put(cacheKey, false)
            false
        } else {
            engine.requireApi().userLibraryApi.markFavoriteItem(
                userId = userId.toUUID(),
                itemId = uuid,
            )
            favoriteCache.put(cacheKey, true)
            true
        }
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
        favoriteCache.put("$userId:$uuid", isFavorite)
        Unit
    }

    private val favoriteCache = androidx.collection.LruCache<String, Boolean>(200)

    /**
     * Clears the in-memory favorite cache. Called on disconnect / server switch
     * so stale favorite flags from a previous server don't linger until evicted
     * by access count. Defensive — entries are also scoped per-user so a stale
     * entry can only ever resolve for the user that wrote it.
     */
    override fun clearFavoriteCache() {
        favoriteCache.evictAll()
    }

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

    private companion object {
        // Matches MediaRepositoryImpl's LATEST_CACHE_TTL_MS so the home path's
        // sub-call caches and the repo's same-concept caches expire in lockstep.
        private const val HOME_SUBCALL_CACHE_TTL_MS = 2 * 60 * 1000L
    }
}
