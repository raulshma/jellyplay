package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.library.BaseItemDtoWire
import com.raulshma.jellyplay.core.network.library.BaseItemQueryResultDtoWire
import com.raulshma.jellyplay.core.network.library.CreatePlaylistRequestDtoWire
import com.raulshma.jellyplay.core.network.library.HomeSectionSources
import com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher
import com.raulshma.jellyplay.core.network.library.IdResultDtoWire
import com.raulshma.jellyplay.core.network.library.LyricsDtoWire
import com.raulshma.jellyplay.core.network.library.ThemeMediaResultDtoWire
import com.raulshma.jellyplay.core.network.library.UpdatePlaylistRequestDtoWire
import com.raulshma.jellyplay.core.network.library.WasmClock
import com.raulshma.jellyplay.core.network.library.buildItemImageUrl
import com.raulshma.jellyplay.core.network.library.filterByParentalRating
import com.raulshma.jellyplay.core.network.library.parseItemSortList
import com.raulshma.jellyplay.core.network.library.toCollectionSummary
import com.raulshma.jellyplay.core.network.library.toGenre
import com.raulshma.jellyplay.core.network.library.toLibraryFolder
import com.raulshma.jellyplay.core.network.library.toMediaDetail
import com.raulshma.jellyplay.core.network.library.toMediaItem
import com.raulshma.jellyplay.core.network.library.toMediaType
import com.raulshma.jellyplay.core.network.library.toPlaylist
import com.raulshma.jellyplay.core.network.library.toPlaylistItem
import com.raulshma.jellyplay.core.network.library.toStudio
import com.raulshma.jellyplay.core.network.library.toWireItemKind
import io.ktor.client.HttpClient

/**
 * Fields the detail mapper reads from the item DTO — mirrors the jvmShared
 * DETAIL_ITEM_FIELDS projection: userLibraryApi-style GET /Items/{id} returns
 * several of these (notably TRICKPLAY) null without an explicit request.
 */
private val DETAIL_FIELDS = listOf(
    "People", "Chapters", "MediaSources", "Trickplay", "ExternalUrls",
    "OriginalTitle", "ProductionLocations", "Studios", "Genres", "Overview",
    "ProviderIds", "PrimaryImageAspectRatio",
)

private val LIST_FIELDS = listOf("Overview", "PrimaryImageAspectRatio")

/** LRU bound of the favorite-flag cache (the old `LruCache(200)` size). */
private const val FAVORITE_CACHE_MAX_ENTRIES = 200

/**
 * TTL of the favorite-flag cache — generous (the flags only seed a toggle's
 * "current" value until the first real read refreshes them), and the
 * identity-keyed composite key already guarantees a switched user never sees
 * the previous user's flags within any window.
 */
private const val FAVORITE_CACHE_TTL_MS = 15 * 60_000L

/**
 * Phase W chunk 2: the wasmJs [LibraryApiClient] — a hand-rolled Ktor
 * replacement for the jvmShared `LibraryApiClientImpl` (Jellyfin SDK +
 * OkHttp). Endpoint paths, query parameters (including the SDK's non-null
 * `enableTotalRecordCount=true`/`enableImages=true` defaults on `/Items`)
 * and DTO mapping semantics mirror the JVM implementation request-for-
 * request, field-for-field; the home-sections fetch choreography (fan-out,
 * semaphore bounds, TTL sub-caches, recommendations chain) is shared with it
 * via the commonMain `HomeSectionsFetcher` — this class only satisfies
 * [HomeSectionSources] (for free, via the common [LibraryApiClient]
 * supertype) and supplies the atomic-session cache identity.
 *
 * wasm v1 deltas vs the JVM impl (documented, none affect JVM):
 *  - No failover router: every request derives its base URL from the shared
 *    atomic session's current server address (chunk 1 probes alternates only
 *    at `selectReachableAddress` time).
 *  - The home sub-call caches live in the shared fetcher (commonMain
 *    `TtlCache`, `CacheIdentity`-keyed — the same class the JVM client
 *    already used, now compiled for wasm too). The favorite-flag cache uses
 *    that shared `TtlCache` directly as well; its eviction is the shared
 *    class's access-order LRU rather than the former local twin's
 *    insertion-order eviction. Only the empty-fallback LRU
 *    (see [emptyFallbackLibraries]) remains a local wasm equivalent.
 *  - Date-typed fields (premiere/created/lastPlayed, nextUpDateCutoff input
 *    excluded) keep the raw wire strings instead of the SDK's zone-shifted
 *    re-formatting; `nextUpDateCutoff` is computed from the JS clock +
 *    local offset (`WasmClock`) instead of `java.time`.
 */
class KtorWasmLibraryApiClient(
    httpClient: HttpClient,
    sessionState: AtomicSessionState,
    identity: WasmClientIdentity,
) : WasmApiSupport(httpClient, sessionState, identity), LibraryApiClient, HomeSectionSources {

    private val currentUser get() = sessionState.currentUser.value
    private val currentServer get() = sessionState.currentServer.value
    private val currentMaxParentalRating get() = currentUser?.maxParentalAgeRating

    /**
     * LRU of libraries known empty for the latest fallback (see getMediaItems).
     * wasm note: the JVM sibling uses `lruMapOf(32)` + `synchronized`; wasm
     * has neither — the single-threaded JS event loop makes plain map ops
     * atomic between suspension points, and access-order is emulated by
     * remove+reinsert on read.
     */
    private val emptyFallbackLibraries = LinkedHashMap<String, Unit>()

    private fun rememberEmptyFallback(parentId: String) {
        emptyFallbackLibraries[parentId] = Unit
        while (emptyFallbackLibraries.size > 32) {
            emptyFallbackLibraries.remove(emptyFallbackLibraries.keys.first())
        }
    }

    private fun isKnownEmptyFallback(parentId: String): Boolean {
        if (emptyFallbackLibraries.remove(parentId) == null) return false
        emptyFallbackLibraries[parentId] = Unit
        return true
    }

    // ── Home feed + favorite-flag caches ───────────────────────────────────

    /**
     * The home feed's fetch choreography (sub-call fan-out, semaphore bounds,
     * TTL sub-caches, recommendations chain) lives in the commonMain
     * [HomeSectionsFetcher]; this client merely supplies the transport via
     * [HomeSectionSources] (satisfied for free — the same overrides serve
     * [LibraryApiClient]) and its atomic-session cache identity. Note the
     * unified pre-login semantics: the fetcher memoises under
     * [CacheIdentity.UNKNOWN] when no session exists (this client's twin
     * caches previously skipped caching pre-login) — nothing cached under
     * that sentinel can leak across users.
     */
    private val homeSectionsFetcher = HomeSectionsFetcher(
        sources = this,
        cacheIdentity = {
            val session = sessionState.session.value
            session?.let { CacheIdentity.ofOrNull(it.server.id, it.user.id) }
        },
    )

    private val favoriteCache = TtlCache<Boolean>(
        maxSize = FAVORITE_CACHE_MAX_ENTRIES,
        ttlMs = FAVORITE_CACHE_TTL_MS,
    )

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> = apiResultWithRetry {
        homeSectionsFetcher.fetch(query, force)
    }

    // ── Item list endpoints ────────────────────────────────────────────────

    override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<List<BaseItemDtoWire>>(
                url = apiUrl(server.address, "/Items/Latest"),
                accessToken = currentToken(),
                query = q(
                    "parentId" to parentId,
                    "fields" to LIST_FIELDS.joined(),
                    "limit" to limit.toString(),
                    "groupItems" to "true",
                ),
            )
            response.map { it.toMediaItem() }
                .filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getNextUp(
        limit: Int,
        enableRewatching: Boolean,
        maxDays: Int,
    ): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> = apiResultWithRetry {
        val server = requireConnectedServer()
        val cutoff = if (maxDays > 0) WasmClock.localNowMinusDaysIsoOffset(maxDays.toLong()) else null
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Shows/NextUp"),
            accessToken = currentToken(),
            query = q(
                "limit" to limit.toString(),
                "fields" to LIST_FIELDS.joined(),
                "nextUpDateCutoff" to cutoff,
                "enableTotalRecordCount" to "true",
                "enableResumable" to "true",
                "enableRewatching" to enableRewatching.toString(),
            ),
        )
        response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/UserItems/Resume"),
                accessToken = currentToken(),
                query = q(
                    "limit" to limit.toString(),
                    "fields" to LIST_FIELDS.joined(),
                    "enableTotalRecordCount" to "true",
                    "enableImages" to "true",
                    "excludeActiveSessions" to "false",
                ),
            )
            response.items.map { it.toMediaItem() }
                .filterByParentalRating(currentMaxParentalRating)
                .distinctBy { it.id }
        }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = apiResultWithRetry {
        // Use the server-filtered user-views endpoint (/UserViews) instead of
        // /Library/MediaFolders — MediaFolders is admin-only and unfiltered;
        // this returns only the libraries the current user can access, live.
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire?>(
            url = apiUrl(server.address, "/UserViews"),
            accessToken = currentToken(),
            query = listOf("includeHidden" to "false"),
        ) ?: throw IllegalStateException("Server returned empty response")
        response.items.map { it.toLibraryFolder() }
    }

    override suspend fun getMediaItems(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        startIndex: Int,
        limit: Int,
        searchTerm: String?,
        kindFilter: ItemKindFilter,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val sortByTokens = parseItemSortList(filters.sortBy.apiValue)
        val sortOrder = filters.sortBy.sortOrder.equals("Descending", ignoreCase = true)
            .let { if (it) "Descending" else "Ascending" }
        // Played-status maps onto Jellyfin's ItemFilter (IsPlayed/IsUnplayed)
        // and composes with the IsResumable position filter.
        val itemFilters = buildList {
            when (filters.playedStatus.takeIf { it != PlayedStatus.ALL }) {
                PlayedStatus.PLAYED -> add("IsPlayed")
                PlayedStatus.UNPLAYED -> add("IsUnplayed")
                else -> {}
            }
            if (filters.isResumable == true) add("IsResumable")
        }
        // includeItemTypes / excludeItemTypes: drop SEASON/EPISODE from the
        // exclude list when they were explicitly included — Jellyfin would
        // otherwise receive contradictory include+exclude and return nothing.
        val includeKinds = filters.mediaTypes.mapNotNull { it.toWireItemKind() }
        val excludeKinds = buildList {
            if ("Season" !in includeKinds) add("Season")
            if (!kindFilter.includeEpisodes && "Episode" !in includeKinds) add("Episode")
        }

        val baseQuery = q(
            "parentId" to parentId,
            "includeItemTypes" to includeKinds.takeIf { it.isNotEmpty() }?.joined(),
            "excludeItemTypes" to excludeKinds.takeIf { it.isNotEmpty() }?.joined(),
            "genres" to filters.genres.takeIf { it.isNotEmpty() }?.joined(),
            "years" to filters.years.takeIf { it.isNotEmpty() }?.joinToString(","),
            "studioIds" to studioIds?.takeIf { it.isNotEmpty() }?.joined(),
            "tags" to filters.tags.takeIf { it.isNotEmpty() }?.joined(),
            "sortBy" to sortByTokens.takeIf { it.isNotEmpty() }?.joined(),
            "sortOrder" to sortOrder,
            "startIndex" to startIndex.toString(),
            "limit" to limit.toString(),
            "recursive" to "true",
            "searchTerm" to searchTerm?.takeIf { it.isNotBlank() },
            "filters" to itemFilters.takeIf { it.isNotEmpty() }?.joined(),
            "minCommunityRating" to filters.minRating.takeIf { it > 0f }?.toDouble()?.toString(),
            "fields" to (LIST_FIELDS + "Genres").joined(),
        ) + itemsEndpointDefaults

        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = baseQuery,
        )
        val rawItems = if (response.items.isEmpty() && parentId != null && searchTerm.isNullOrBlank()) {
            // Skip the doubled request for libraries already known to be
            // genuinely empty (primary query empty AND fallback empty).
            if (isKnownEmptyFallback(parentId)) {
                emptyList()
            } else {
                val fallback = runCatching {
                    getJson<List<BaseItemDtoWire>>(
                        url = apiUrl(server.address, "/Items/Latest"),
                        accessToken = currentToken(),
                        query = q(
                            "parentId" to parentId,
                            "limit" to (if (limit > 0) limit else 50).toString(),
                            "fields" to (LIST_FIELDS + "Genres").joined(),
                        ),
                    )
                }.getOrNull() ?: emptyList()
                if (fallback.isEmpty()) rememberEmptyFallback(parentId)
                fallback
            }
        } else {
            response.items
        }
        val totalCount = if (response.items.isEmpty() && rawItems.isNotEmpty()) rawItems.size else response.totalRecordCount
        SearchResult(
            items = rawItems.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = totalCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getMediaDetail(itemId: String): Result<com.raulshma.jellyplay.core.model.MediaDetail> =
        apiResultWithRetry {
            // NOTE: similar/related items are fetched separately via
            // [getSimilarItems] rather than nested here (the JVM comment
            // applies verbatim: nesting blocked the whole detail behind the
            // similar-items fetch). Parental filtering is intentionally not
            // applied — the server scopes getItem already.
            val server = requireConnectedServer()
            // Project the non-default fields the mapper reads: GET /Items with
            // an ids filter bounds the payload and guarantees TRICKPLAY etc.
            // are populated; fall back to the plain item endpoint when the
            // projected query returns empty (older servers).
            val projected = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q("ids" to itemId, "fields" to DETAIL_FIELDS.joined()) + itemsEndpointDefaults,
            ).items.firstOrNull()
            val item = projected ?: getJson<BaseItemDtoWire>(
                url = apiUrl(server.address, "/Items/$itemId"),
                accessToken = currentToken(),
            )
            item.toMediaDetail()
        }

    override suspend fun getIntros(itemId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val userId = requireCurrentUser().id
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items/$itemId/Intros"),
                accessToken = currentToken(),
                query = listOf("userId" to userId),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getSpecialFeatures(itemId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val userId = requireCurrentUser().id
            // Unlike getIntros (a query-result wrapper), SpecialFeatures
            // returns a bare JSON array — same as the SDK's List<BaseItemDto>.
            val response = getJson<List<BaseItemDtoWire>>(
                url = apiUrl(server.address, "/Items/$itemId/SpecialFeatures"),
                accessToken = currentToken(),
                query = listOf("userId" to userId),
            )
            response.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "searchTerm" to query,
                "includeItemTypes" to mediaTypes?.mapNotNull { it.toWireItemKind() }?.takeIf { it.isNotEmpty() }?.joined(),
                "limit" to limit.toString(),
                "startIndex" to startIndex.toString(),
                "recursive" to "true",
                "fields" to LIST_FIELDS.joined(),
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = apiResultWithRetry {
        // Mirrors jellyfin-web's useSearchSuggestions: getItems sorted by
        // [IsFavoriteOrLiked, Random] over Movies, Series and MusicArtists.
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "sortBy" to listOf("IsFavoriteOrLiked", "Random").joined(),
                "includeItemTypes" to listOf("Movie", "Series", "MusicArtist").joined(),
                "limit" to limit.toString(),
                "recursive" to "true",
                "fields" to listOf("PrimaryImageAspectRatio", "Genres").joined(),
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        apiResultWithRetry {
            // Raw query with the SDK-generated capitalised keys (the typed
            // getItems has no AnyProviderId parameter — mirror the raw call,
            // including NOT adding the enable* defaults the typed call adds).
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = listOf(
                    "Recursive" to "true",
                    "Limit" to "1",
                    "AnyProviderId" to "$provider:$id",
                ),
            )
            response.items.firstOrNull()?.id
        }

    override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Genres"),
                accessToken = currentToken(),
                query = q(
                    "startIndex" to startIndex.toString(),
                    "limit" to limit.toString(),
                    "parentId" to parentId,
                    "userId" to currentUser?.id,
                    "enableImages" to "true",
                    "enableTotalRecordCount" to "true",
                ),
            )
            response.items.map { it.toGenre() }
        }

    override suspend fun getItemsByGenre(
        genreId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "genreIds" to genreId,
                "includeItemTypes" to mediaTypes?.mapNotNull { it.toWireItemKind() }?.takeIf { it.isNotEmpty() }?.joined(),
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
                "recursive" to "true",
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getStudios(parentId: String?, startIndex: Int, limit: Int): Result<List<Studio>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Studios"),
                accessToken = currentToken(),
                query = q(
                    "startIndex" to startIndex.toString(),
                    "limit" to limit.toString(),
                    "parentId" to parentId,
                    "userId" to currentUser?.id,
                    "enableImages" to "true",
                    "enableTotalRecordCount" to "true",
                ),
            )
            response.items.map { it.toStudio() }
        }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "studioIds" to studioId,
                "includeItemTypes" to mediaTypes?.mapNotNull { it.toWireItemKind() }?.takeIf { it.isNotEmpty() }?.joined(),
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
                "recursive" to "true",
                "fields" to LIST_FIELDS.joined(),
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "albumArtistIds" to artistId,
                    "includeItemTypes" to "MusicAlbum",
                    "limit" to limit.toString(),
                    "recursive" to "true",
                    "sortBy" to "SortName",
                    "fields" to LIST_FIELDS.joined(),
                ) + itemsEndpointDefaults,
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getAlbumTracks(albumId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "parentId" to albumId,
                    "includeItemTypes" to "Audio",
                    "recursive" to "true",
                    "sortBy" to listOf("ParentIndexNumber", "IndexNumber").joined(),
                    "sortOrder" to "Ascending",
                    "fields" to LIST_FIELDS.joined(),
                ) + itemsEndpointDefaults,
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items/$itemId/Similar"),
                accessToken = currentToken(),
                query = listOf("limit" to limit.toString()),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val user = currentUser ?: return@apiResultWithRetry emptyList()
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items/$itemId/InstantMix"),
                accessToken = currentToken(),
                query = q(
                    "userId" to user.id,
                    "limit" to limit.toString(),
                    "fields" to LIST_FIELDS.joined(),
                ),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "personIds" to personId,
                    "limit" to limit.toString(),
                    "recursive" to "true",
                    "fields" to LIST_FIELDS.joined(),
                ) + itemsEndpointDefaults,
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getThemeSongs(itemId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<ThemeMediaResultDtoWire>(
                url = apiUrl(server.address, "/Items/$itemId/ThemeSongs"),
                accessToken = currentToken(),
                query = listOf("inheritFromParent" to "false"),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getSeasons(seriesId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Shows/$seriesId/Seasons"),
                accessToken = currentToken(),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Shows/$seriesId/Episodes"),
                accessToken = currentToken(),
                query = listOf("seasonId" to seasonId),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    override suspend fun getAllEpisodes(seriesId: String): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        apiResultWithRetry {
            // Omitting seasonId returns the full set — collapses the
            // N-season fan-out into a single call (see interface KDoc).
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Shows/$seriesId/Episodes"),
                accessToken = currentToken(),
            )
            response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating)
        }

    // ── Collections / playlists / tags / favorites ─────────────────────────

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "parentId" to collectionId,
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
                "recursive" to "true",
                "fields" to LIST_FIELDS.joined(),
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> =
        apiResultWithRetry {
            // Collections are BoxSet items; CHILD_COUNT is requested so the
            // picker can show "N items" per collection.
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "includeItemTypes" to "BoxSet",
                    "limit" to limit.toString(),
                    "recursive" to "true",
                    "fields" to "ChildCount",
                ) + itemsEndpointDefaults,
            )
            response.items.map { it.toCollectionSummary() }
        }

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val query = mutableListOf("name" to name, "isLocked" to "false")
            itemIds.forEach { query.add("ids" to it) }
            val result = postForJson<IdResultDtoWire>(
                url = apiUrl(server.address, "/Collections"),
                accessToken = currentToken(),
                query = query,
            )
            result.id ?: throw IllegalStateException("Created collection has no id")
        }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val query = mutableListOf<Pair<String, String>>()
            itemIds.forEach { query.add("ids" to it) }
            postStatusOnly(
                url = apiUrl(server.address, "/Collections/$collectionId/Items"),
                accessToken = currentToken(),
                query = query,
            )
        }

    override suspend fun getTags(parentId: String?, startIndex: Int, limit: Int): Result<List<String>> =
        apiResultWithRetry {
            // Parental filtering intentionally not applied: tags are plain
            // strings with no rating attribute (server scopes the query).
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "parentId" to parentId,
                    "startIndex" to startIndex.toString(),
                    "limit" to limit.toString(),
                    "recursive" to "true",
                    "fields" to "Tags",
                ) + itemsEndpointDefaults,
            )
            response.items.flatMap { it.tags ?: emptyList() }.distinct().sorted()
        }

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "includeItemTypes" to mediaTypes?.mapNotNull { it.toWireItemKind() }?.takeIf { it.isNotEmpty() }?.joined(),
                "filters" to "IsFavorite",
                "limit" to limit.toString(),
                "startIndex" to startIndex.toString(),
                "recursive" to "true",
                "fields" to LIST_FIELDS.joined(),
            ) + itemsEndpointDefaults,
        )
        SearchResult(
            items = response.items.map { it.toMediaItem() }.filterByParentalRating(currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = apiResultWithRetry {
        // Mirrors the jvmShared LyricsApi.fetchLyrics: any failure degrades
        // to an empty UNKNOWN result rather than an error.
        val server = requireConnectedServer()
        val dto = runCatching {
            getJson<LyricsDtoWire>(
                url = apiUrl(server.address, "/Audio/$itemId/Lyrics"),
                accessToken = currentToken(),
            )
        }.getOrNull() ?: LyricsDtoWire()
        dto.toLyricsResult()
    }

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "includeItemTypes" to "Playlist",
                "limit" to limit.toString(),
                "recursive" to "true",
                "fields" to (LIST_FIELDS + listOf("CanDelete", "DateCreated")).joined(),
            ) + itemsEndpointDefaults,
        )
        val currentUserId = currentUser?.id
        response.items.map { it.toPlaylist(currentUserId) }
    }

    override suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int,
        limit: Int,
    ): Result<List<PlaylistItem>> = apiResultWithRetry {
        // Parental filtering intentionally not applied: PlaylistItem carries
        // no officialRating (server enforces playlist ACLs on the query).
        val server = requireConnectedServer()
        val response = getJson<BaseItemQueryResultDtoWire>(
            url = apiUrl(server.address, "/Items"),
            accessToken = currentToken(),
            query = q(
                "parentId" to playlistId,
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
                "recursive" to "true",
                "fields" to LIST_FIELDS.joined(),
            ) + itemsEndpointDefaults,
        )
        response.items.map { it.toPlaylistItem() }
    }

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> = apiResultWithRetry {
        val server = requireConnectedServer()
        // Jellyfin tags a playlist with a single media type so the server can
        // sort/limit it correctly: music callers keep AUDIO; video detail
        // screens pass VIDEO (overview is accepted but — mirroring the JVM
        // impl — not sent; the server keeps its own).
        val wireMediaType = if (mediaType.isAudioType) "Audio" else "Video"
        val result = postForJson<IdResultDtoWire>(
            url = apiUrl(server.address, "/Playlists"),
            accessToken = currentToken(),
            bodyText = encodeBody(
                CreatePlaylistRequestDtoWire(
                    name = name,
                    ids = itemIds,
                    userId = currentUser?.id,
                    mediaType = wireMediaType,
                    users = emptyList(),
                    isPublic = false,
                ),
            ),
        )
        result.id ?: throw IllegalStateException("Created playlist has no id")
    }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        // overview deliberately not sent — the JVM UpdatePlaylistDto carries
        // only name/isPublic; mirroring keeps server-side state identical.
        postStatusOnly(
            url = apiUrl(server.address, "/Playlists/$playlistId"),
            accessToken = currentToken(),
            bodyText = encodeBody(UpdatePlaylistRequestDtoWire(name = name, isPublic = isPublic)),
        )
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        deleteStatusOnly(
            url = apiUrl(server.address, "/Items/$playlistId"),
            accessToken = currentToken(),
        )
    }

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val query = mutableListOf<Pair<String, String>>()
            itemIds.forEach { query.add("ids" to it) }
            currentUser?.id?.let { query.add("userId" to it) }
            postStatusOnly(
                url = apiUrl(server.address, "/Playlists/$playlistId/Items"),
                accessToken = currentToken(),
                query = query,
            )
        }

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val query = mutableListOf<Pair<String, String>>()
            entryIds.forEach { query.add("entryIds" to it) }
            deleteStatusOnly(
                url = apiUrl(server.address, "/Playlists/$playlistId/Items"),
                accessToken = currentToken(),
                query = query,
            )
        }

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            postStatusOnly(
                url = apiUrl(server.address, "/Playlists/$playlistId/Items/$entryId/Move/$newIndex"),
                accessToken = currentToken(),
            )
        }

    // ── Played / favorite state ────────────────────────────────────────────

    override suspend fun markPlayed(itemId: String): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        val userId = requireCurrentUser().id
        postStatusOnly(
            url = apiUrl(server.address, "/UserPlayedItems/$itemId"),
            accessToken = currentToken(),
            query = listOf("userId" to userId),
        )
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        val userId = requireCurrentUser().id
        deleteStatusOnly(
            url = apiUrl(server.address, "/UserPlayedItems/$itemId"),
            accessToken = currentToken(),
            query = listOf("userId" to userId),
        )
    }

    override suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean?): Result<Boolean> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val userId = requireCurrentUser().id
            // Identity from the ATOMIC session flow; UNKNOWN before login never
            // collides with a real identity (mirrors the JVM impl's keying).
            val identity = sessionState.session.value
                ?.let { CacheIdentity.ofOrNull(it.server.id, it.user.id) }
                ?: CacheIdentity.UNKNOWN
            val cacheKey = itemId
            val cached = favoriteCache.get(identity, cacheKey)
            val isFavorite = currentIsFavorite ?: cached ?: run {
                val fetched = getJson<BaseItemDtoWire>(
                    url = apiUrl(server.address, "/Items/$itemId"),
                    accessToken = currentToken(),
                ).userData?.isFavorite == true
                favoriteCache.put(identity, cacheKey, fetched)
                fetched
            }
            if (isFavorite) {
                deleteStatusOnly(
                    url = apiUrl(server.address, "/UserFavoriteItems/$itemId"),
                    accessToken = currentToken(),
                    query = listOf("userId" to userId),
                )
                favoriteCache.put(identity, cacheKey, false)
                false
            } else {
                postStatusOnly(
                    url = apiUrl(server.address, "/UserFavoriteItems/$itemId"),
                    accessToken = currentToken(),
                    query = listOf("userId" to userId),
                )
                favoriteCache.put(identity, cacheKey, true)
                true
            }
        }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        val userId = requireCurrentUser().id
        if (isFavorite) {
            postStatusOnly(
                url = apiUrl(server.address, "/UserFavoriteItems/$itemId"),
                accessToken = currentToken(),
                query = listOf("userId" to userId),
            )
        } else {
            deleteStatusOnly(
                url = apiUrl(server.address, "/UserFavoriteItems/$itemId"),
                accessToken = currentToken(),
                query = listOf("userId" to userId),
            )
        }
        favoriteCache.put(
            sessionState.session.value
                ?.let { CacheIdentity.ofOrNull(it.server.id, it.user.id) }
                ?: CacheIdentity.UNKNOWN,
            itemId,
            isFavorite,
        )
    }

    // ── Image URL builders (pure; ported verbatim) ─────────────────────────

    override fun getImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int?,
        imageIndex: Int?,
        tag: String?,
    ): String = buildItemImageUrl(
        baseUrl = currentServer?.address,
        itemId = itemId,
        imageType = imageType,
        maxWidth = maxWidth,
        tag = tag,
        imageIndex = imageIndex,
    )

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)

    override suspend fun getChildItemImageUrls(parentId: String, limit: Int): List<String> {
        return try {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire>(
                url = apiUrl(server.address, "/Items"),
                accessToken = currentToken(),
                query = q(
                    "parentId" to parentId,
                    "includeItemTypes" to "Photo",
                    "limit" to limit.toString(),
                    "sortBy" to "DateCreated",
                    "sortOrder" to "Descending",
                    "fields" to "PrimaryImageAspectRatio",
                ) + itemsEndpointDefaults,
            )
            response.items.mapNotNull { item ->
                if (item.imageTags?.containsKey("Primary") == true) {
                    getImageUrl(item.id ?: "", "Primary", 200)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * The non-null query defaults the SDK's typed getItems/getResumeItems
     * always send (`enableTotalRecordCount = true`, `enableImages = true`) —
     * replicated so server-side behavior (e.g. totalRecordCount presence)
     * matches the JVM client byte-for-byte.
     */
    private val itemsEndpointDefaults = listOf(
        "enableTotalRecordCount" to "true",
        "enableImages" to "true",
    )

    /** Null-filtering ordered query builder. */
    private fun q(vararg pairs: Pair<String, String?>): List<Pair<String, String>> =
        pairs.mapNotNull { (k, v) -> v?.let { k to it } }

    /** Comma-join for the SDK's repeated-value query keys ("Overview,Genres"). */
    private fun List<String>.joined(): String = joinToString(",")

    /** Maps the lyric DTO like the jvmShared `LyricsApi.toLyricsResult`. */
    private fun LyricsDtoWire.toLyricsResult(): LyricsResult {
        val lines = lyrics.mapIndexedNotNull { idx, line ->
            val startMs = line.start?.let { it / 10_000 } ?: 0L
            val nextStartMs = if (idx + 1 < lyrics.size) {
                lyrics[idx + 1].start?.div(10_000) ?: startMs
            } else startMs
            val text = line.text
            val words = line.cues?.map { cue ->
                LyricsWord(
                    timeMs = cue.start / 10_000,
                    text = text.substring(cue.position, cue.endPosition.coerceAtMost(text.length)),
                    durationMs = ((cue.end ?: cue.start) - cue.start) / 10_000,
                )
            }.orEmpty()
            LyricsLine(
                timeMs = startMs,
                text = text,
                durationMs = (nextStartMs - startMs).coerceAtLeast(0L),
                words = words,
            )
        }
        val source = if (lines.isEmpty()) LyricsSource.UNKNOWN else LyricsSource.EXTERNAL
        return LyricsResult(lines = lines, source = source)
    }
}

