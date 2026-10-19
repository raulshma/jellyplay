package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.lruMapOf
import com.raulshma.jellyplay.core.model.buildItemImageUrl
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.network.LyricsApi
import com.raulshma.jellyplay.core.network.library.ChildItemImageRow
import com.raulshma.jellyplay.core.network.library.ClassicLatestCard
import com.raulshma.jellyplay.core.network.library.CLASSIC_TV_LATEST_MEDIA_TYPE
import com.raulshma.jellyplay.core.network.library.DETAIL_PROJECTION_FIELDS
import com.raulshma.jellyplay.core.network.library.EmptyLibraryFallback
import com.raulshma.jellyplay.core.network.library.FavoriteFlagCache
import com.raulshma.jellyplay.core.network.library.HomeSectionSources
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePortAdapter
import com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_FIELDS
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_ITEM_TYPES
import com.raulshma.jellyplay.core.network.library.SEARCH_SUGGESTIONS_SORT_BY
import com.raulshma.jellyplay.core.network.library.JellyPlayHomeSectionSources
import com.raulshma.jellyplay.core.network.library.SeerrHomeSectionSources
import com.raulshma.jellyplay.core.network.library.buildChildItemImagesQuerySpec
import com.raulshma.jellyplay.core.network.library.buildDiscoverRowQuerySpec
import com.raulshma.jellyplay.core.network.library.buildFavoritesQuerySpec
import com.raulshma.jellyplay.core.network.library.buildItemsByGenreQuerySpec
import com.raulshma.jellyplay.core.network.library.buildItemsByStudioQuerySpec
import com.raulshma.jellyplay.core.network.library.buildMediaItemsQuerySpec
import com.raulshma.jellyplay.core.network.library.buildResumeQuerySpec
import com.raulshma.jellyplay.core.network.library.buildSearchHintsQuerySpec
import com.raulshma.jellyplay.core.network.library.emptyFallbackTotalCount
import com.raulshma.jellyplay.core.network.library.synthesizedSeries
import com.raulshma.jellyplay.core.network.library.toChildItemImageUrls
import com.raulshma.jellyplay.core.network.library.toClassicLatestCards
import com.raulshma.jellyplay.core.network.library.toFilteredLatestRows
import com.raulshma.jellyplay.core.network.library.toFilteredResumeRows
import com.raulshma.jellyplay.core.network.library.toWireItemKind
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ScrollDirection
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import kotlinx.coroutines.sync.Semaphore
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
    ItemSortBy.entries.firstOrNull { it.serialName == token }
        ?: error("ItemSortBy has no serial name '$token' — SDK drift vs the search-suggestions projection")
}

/**
 * Cap on the BoxSets enumeration feeding [LibraryApiClient.getBoxSetsContaining]:
 * the old enumerate was unbounded, and the concurrent probes
 * multiply it. 100 covers every realistic library (server default SortName
 * order); a server past the cap gets its first-100 probed — the accepted,
 * documented truncation.
 */
private const val BOX_SET_ENUMERATE_CAP = 100

/**
 * Cap on the total library items the tag scan behind
 * [LibraryApiClient.getTags] pages through: the filter sheets need every tag
 * in the library, and the former first-page-only read silently dropped any
 * tag not carried by the first `limit` items. The scan pages with a
 * TAGS-only projection until the server's totalRecordCount is exhausted or
 * 1000 items (10 pages at the default page size) have been scanned — the
 * accepted, documented truncation for a pathological library.
 */
private const val TAGS_ITEM_SCAN_CAP = 1000

private val SEARCH_SUGGESTIONS_KINDS = SEARCH_SUGGESTIONS_ITEM_TYPES.map { token ->
    BaseItemKind.entries.firstOrNull { it.serialName == token }
        ?: error("BaseItemKind has no serial name '$token' — SDK drift vs the search-suggestions projection")
}

private val SEARCH_SUGGESTIONS_PROJECTION = SEARCH_SUGGESTIONS_FIELDS.map { token ->
    ItemFields.entries.firstOrNull { it.serialName == token }
        ?: error("ItemFields has no serial name '$token' — SDK drift vs the search-suggestions projection")
}

/** Discover-row window bound (epoch millis) → the SDK getItems date param type (java.time.LocalDateTime on the JVM). */
private fun Long?.toSdkLocalDateTime(): java.time.LocalDateTime? = this?.let {
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())
}

/** The default-zone wall clock through the seam — the ONE "now" derivation every seam read in this file shares (mirrors [toSdkLocalDateTime]). */
private fun TimeSource.nowLocalDateTime(): java.time.LocalDateTime =
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowEpochMillis()), java.time.ZoneId.systemDefault())

/** ImageType resolved by serial name once — [ImageType.fromNameOrNull] linear-scans per call and Coil binds run per item. Keys lowercased: [fromNameOrNull] matches serial names case-insensitively, so callers passing server-JSON casing ("primary") must resolve too. */
private val IMAGE_TYPES_BY_SERIAL_NAME = ImageType.entries.associateBy { it.serialName.lowercase() }

class LibraryApiClientImpl(
    private val engine: JellyfinApiEngine,
    private val lyricsApi: LyricsApi,
    /**
     * Clock seam (D3) for the discover-row window bound and the Next Up
     * date cutoff — same reads as the bare System.currentTimeMillis() /
     * LocalDateTime.now() before, through the injected clock.
     */
    private val timeSource: TimeSource,
    /**
     * The Seerr-side leaf source for the home fetcher's SEERR discover rows
     * (satisfied by the Koin construction owner with the session-aware
     * adapter; this client cannot supply it for free the way it supplies
     * [HomeSectionSources] — Seerr session state lives in the datastore
     * layer). Default null = this wiring fetches no Seerr rows (unit fakes).
     */
    private val seerrHomeSectionSources: SeerrHomeSectionSources? = null,
    /**
     * The plugin-side leaf source for the home fetcher's PLUGIN_ROW rows
     * (seasonal today; satisfied by the data layer's adapter beside the
     * `JellyPlayPluginApiClient` (the family) —
     * the capability probe lives in the data layer's status store). Default
     * null = this wiring fetches no plugin rows (unit fakes, graphs without
     * the plugin cluster).
     */
    private val jellyPlayHomeSectionSources: JellyPlayHomeSectionSources? = null,
    /**
     * The collection family adapter ([CollectionApiClientImpl] — the four
     * BoxSet reads/writes moved off this class) over the SAME engine single.
     * Koin passes its [CollectionApiClient] single so the collection-seam
     * consumers and this client's one remaining collection obligation (the
     * [HomeSectionSources.getCollectionItems] the home fetcher's pinned
     * collection sections ride) share ONE instance. The test default builds
     * the adapter over this client's engine.
     */
    private val collectionApiClient: CollectionApiClient = CollectionApiClientImpl(engine),
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
                fields = LIST_ITEM_FIELDS_WITH_GENRES,
            ).content ?: emptyList()
        },
    )

    /**
     * The home feed's fetch choreography (sub-call fan-out, semaphore bounds,
     * TTL sub-caches, recommendations chain, both discover-row sources) lives
     * in the commonMain [HomeSectionsFetcher]; this client merely supplies the
     * transport via [HomeSectionSources] (satisfied for free — the same
     * overrides serve [LibraryApiClient]), its atomic-session identity, and —
     * for the SEERR-sourced rows — the separately-wired
     * [seerrHomeSectionSources] adapter plus the today-string the row params
     * need (derived from the same clock seam as every other wall-time read
     * here: LocalDate.now() IS ofEpochMilli(nowEpochMillis()) in the default
     * zone, so this is the exact value the feature layer's HomeClock
     * produced).
     */
    private val homeSectionsFetcher = HomeSectionsFetcher(
        sources = this,
        seerrSources = seerrHomeSectionSources,
        cacheIdentity = { currentHomeCacheIdentity() },
        today = {
            // ofInstant-then-toLocalDate (not LocalDate.ofInstant — a JDK 9 API
            // this Android floor must not assume): [nowLocalDateTime].
            timeSource.nowLocalDateTime().toLocalDate().toString()
        },
        jellyPlaySources = jellyPlayHomeSectionSources,
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

    // The home cache-maintenance verbs are NOT on [LibraryApiClient] anymore:
    // the data layer's write/roll paths reach them through
    // [HomeSectionsCachePort] — whose production adapter now lives beside the
    // fetcher ([HomeSectionsCachePortAdapter]). This impl constructs that
    // adapter over its own fetcher and re-exposes it (the Koin construction
    // owner binds the port to this property), so the port, this client, and
    // the fetcher stay one home stack. The discover-row verbs'
    // HomeWriteGeneration token rides along untouched: it is the data layer's
    // post-bump cache-write token, and this client neither derives nor alters
    // it — pure pass-through to the fetcher's mirror.
    internal val homeSectionsCachePort: HomeSectionsCachePort =
        HomeSectionsCachePortAdapter(engine, homeSectionsFetcher)

    override suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>> = engine.withApi { api ->
        // Query assembly (all shared + discover-only dimensions) lives in the
        // commonMain [buildDiscoverRowQuerySpec]; this adapter resolves the
        // spec against the SDK enums and the epoch-millis window bounds
        // against java.time — same split as getMediaItems/getNextUp.
        val nowEpochMs = timeSource.nowEpochMillis()
        // Empty scope = ONE catalog-wide query (null parentId); otherwise one
        // query per library, merged in library order. mapConcurrentCatching
        // drops a failing/deleted library instead of failing the whole row —
        // the pinned-section degrade policy.
        val parentIds: List<String?> = row.libraryIds.ifEmpty { listOf(null) }
        val perLibrary = Semaphore(3).mapConcurrentCatching(parentIds) { parentId ->
            val spec = buildDiscoverRowQuerySpec(
                row = row,
                parentId = parentId,
                startIndex = 0,
                limit = row.limit,
                nowEpochMs = nowEpochMs,
            )
            val response = api.itemsApi.getItems(
                parentId = spec.parentId?.toUUID(),
                includeItemTypes = spec.includeKinds.toBaseItemKinds(),
                excludeItemTypes = spec.excludeKinds.toBaseItemKinds(),
                genres = spec.genres,
                years = spec.years,
                studioIds = spec.studioIds?.map { it.toUUID() },
                tags = spec.tags,
                sortBy = spec.sortBy.toItemSortBys(),
                sortOrder = spec.sortOrderDescending.toSortOrderList(),
                startIndex = spec.startIndex,
                limit = spec.limit,
                recursive = spec.recursive,
                filters = spec.itemFilters.toItemFilters(),
                minCommunityRating = spec.minCommunityRating,
                personIds = spec.personIds?.map { it.toUUID() },
                minDateLastSaved = spec.minDateLastSavedMs.toSdkLocalDateTime(),
                minPremiereDate = spec.minPremiereDateMs.toSdkLocalDateTime(),
                fields = spec.fields.toItemFieldsList(),
            ).content
            (response.items ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
        }
        perLibrary.flatten().distinctBy { it.id }.take(row.limit)
    }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
        classicEpisodePool: Int?,
    ): Result<List<MediaItem>> =
        engine.withApi { api ->
            if (classicEpisodePool == null) {
                // Modern (default): the server decides — on 12.x the smart
                // Series/Season/Episode container selection IS the feature.
                // groupItems=true is the SDK/server default; passed explicitly
                // so the two branches stay self-describing on the wire.
                // Home-row projection (not the two-field minimum): the latest
                // cards render the quality badges / NEW dot / trickplay
                // preview from this same read (H11/H12/H13).
                val response = api.userLibraryApi.getLatestMedia(
                    parentId = parentId.toUUID(),
                    limit = limit,
                    groupItems = true,
                    fields = HOME_ROW_ITEM_FIELDS,
                ).content ?: emptyList()
                response.map { it.toMediaItem() }
                    .toFilteredLatestRows(engine.currentMaxParentalRating, allowedKinds = null)
            } else {
                fetchClassicLatestRow(api, parentId, limit, classicEpisodePool)
            }
        }

    /**
     * The classic-rows TV-latest pipeline (#168), true 1:1 with the pre-12
     * wire result: fetch the raw-Episode pool the 10.x server fed its
     * own grouping (IncludeItemTypes=Episode + groupItems=false — the
     * shape every server generation answers with plain episode rows),
     * re-run the 10.x grouping client-side (toClassicLatestCards), then
     * resolve each grouped card against the REAL Series items — one batched
     * ids query — so grouped cards carry true Series DTO data (year,
     * unplayed count), matching what the 10.x controller returned. The
     * Episode-kind fold re-applies the pin for servers that ignore
     * includeItemTypes.
     */
    private suspend fun fetchClassicLatestRow(
        api: ApiClient,
        parentId: String,
        limit: Int,
        classicEpisodePool: Int,
    ): List<MediaItem> {
        val pool = api.userLibraryApi.getLatestMedia(
            parentId = parentId.toUUID(),
            limit = classicEpisodePool,
            includeItemTypes = listOfNotNull(CLASSIC_TV_LATEST_MEDIA_TYPE.toWireItemKind()).toBaseItemKinds(),
            groupItems = false,
            fields = HOME_ROW_ITEM_FIELDS,
        ).content ?: emptyList()
        val cards = pool
            .map { it.toMediaItem() }
            .toFilteredLatestRows(engine.currentMaxParentalRating, allowedKinds = setOf(CLASSIC_TV_LATEST_MEDIA_TYPE))
            .toClassicLatestCards(limit)
        val groupedSeriesIds = cards.filterIsInstance<ClassicLatestCard.Grouped>().map { it.seriesId }
        val seriesById: Map<String, MediaItem> =
            if (groupedSeriesIds.isEmpty()) {
                emptyMap()
            } else {
                // Degrade, not fail: a lost ids query must not blank the
                // row — grouped cards fall back to synthesis. (Cancellation
                // rethrown, per the concurrency rule.)
                runCatchingRethrowingCancellation {
                    api.itemsApi.getItems(
                        ids = groupedSeriesIds.map { it.toUUID() },
                        fields = HOME_ROW_ITEM_FIELDS,
                    ).content?.items.orEmpty()
                }.getOrDefault(emptyList())
                    .map { it.toMediaItem() }
                    .associateBy { it.id }
            }
        return cards.map { card ->
            when (card) {
                is ClassicLatestCard.Single -> card.mostRecentEpisode
                is ClassicLatestCard.Grouped ->
                    seriesById[card.seriesId]
                        ?.copy(childCount = card.episodes.size)
                        ?: card.synthesizedSeries()
            }
        }
    }

    /**
     * The [com.raulshma.jellyplay.core.network.library.HomeSectionSources]
     * view of Next Up (the home fetcher's user-scoped 3-param shape) — a
     * delegating overload of the full client member below, null-scoped (the
     * server-default read the home rows always ran).
     */
    override suspend fun getNextUp(
        limit: Int,
        enableRewatching: Boolean,
        maxDays: Int,
    ): Result<List<MediaItem>> =
        getNextUp(limit = limit, enableRewatching = enableRewatching, maxDays = maxDays, seriesId = null, enableResumable = null)

    override suspend fun getNextUp(
        limit: Int,
        enableRewatching: Boolean,
        maxDays: Int,
        seriesId: String?,
        enableResumable: Boolean?,
    ): Result<List<MediaItem>> = engine.withApi { api ->
        // The limit/projection shape is the shared resume spec (NextUp rides
        // it with no kind narrowing); the cutoff CLOCK stays here (JVM-side
        // java.time).
        val spec = buildResumeQuerySpec(limit, kinds = null)
        // Same value LocalDateTime.now() produced, through the epoch seam —
        // [nowLocalDateTime] (toSdkLocalDateTime uses the same derivation
        // for the inbound bounds).
        val now = timeSource.nowLocalDateTime()
        val cutoff = if (maxDays > 0) {
            now.minusDays(maxDays.toLong())
        } else {
            null
        }
        val response = api.tvShowsApi.getNextUp(
            limit = spec.limit,
            enableRewatching = enableRewatching,
            nextUpDateCutoff = cutoff,
            // Null keeps the server default (the home row's user-scoped
            // read); the D12 series-scoped diff supplies both.
            seriesId = seriesId?.let(java.util.UUID::fromString),
            enableResumable = enableResumable,
            fields = spec.fields.toItemFieldsList(),
        ).content
        (response?.items ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getUpcoming(
        userId: String?,
        parentId: String?,
        limit: Int,
    ): Result<List<MediaItem>> = engine.withApi { api ->
        // Same limit/projection shape as getNextUp (the shared resume spec —
        // Upcoming rides it with no kind narrowing and no date cutoff; the
        // home-row projection carries the card-overlay fields, H4/H12).
        val spec = buildResumeQuerySpec(limit, kinds = null)
        val response = api.tvShowsApi.getUpcomingEpisodes(
            userId = userId?.toUUID(),
            limit = spec.limit,
            parentId = parentId?.toUUID(),
            fields = spec.fields.toItemFieldsList(),
        ).content
        (response?.items ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getContinueWatching(
        limit: Int,
        classicRows: Boolean,
        excludeActiveSessions: Boolean,
    ): Result<List<MediaItem>> = engine.withApi { api ->
        // The wire request is the exact pre-12 shape in BOTH modes (no
        // IncludeItemTypes — the 10.x video resume row never narrowed).
        // excludeActiveSessions (H6) threads through the shared spec so the
        // batch and refresh arms send the identical flag.
        val spec = buildResumeQuerySpec(limit, kinds = null, excludeActiveSessions = excludeActiveSessions)
        val response = api.itemsApi.getResumeItems(
            limit = spec.limit,
            fields = spec.fields.toItemFieldsList(),
            excludeActiveSessions = spec.excludeActiveSessions,
        ).content
        // #157: the fold drops played rows the resume endpoint still reports —
        // see resumableOnly() for the full rationale. Classic rows (#168) add
        // the rollup fold: a 12.x server also reports Series/Season containers
        // as resumable themselves, which the pre-12 server never did — dropped
        // client-side so the wire stays byte-identical across generations.
        (response?.items ?: emptyList())
            .map { it.toMediaItem() }
            .toFilteredResumeRows(
                engine.currentMaxParentalRating,
                isBooks = false,
                dropContainerRollups = classicRows,
            )
    }

    override suspend fun getContinueReading(limit: Int): Result<List<MediaItem>> = engine.withApi { api ->
        // Server-side narrowing to books (spec.includeKinds); the fold's
        // books half stays as belt-and-braces (old servers may ignore
        // includeItemTypes).
        val spec = buildResumeQuerySpec(limit, kinds = listOf("Book"))
        val response = api.itemsApi.getResumeItems(
            limit = spec.limit,
            fields = spec.fields.toItemFieldsList(),
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
        ).content
        (response?.items ?: emptyList())
            .map { it.toMediaItem() }
            .toFilteredResumeRows(engine.currentMaxParentalRating, isBooks = true)
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = engine.withApi { api ->
        // Use the server-filtered user-views endpoint (/Users/{userId}/Views)
        // instead of /Library/MediaFolders. MediaFolders is admin-only and
        // returns ALL physical folders with no per-user access filtering,
        // which forced a fragile client-side filter on a login-time snapshot
        // of enabledFolderIds — a snapshot that goes stale the moment an
        // admin changes the user's library access. getUserViews returns only
        // the libraries the current user can access, live.
        val response = api.userViewsApi.getUserViews().content
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
    ): Result<SearchResult> = engine.withApi { api ->
        // The filter/sort/kind/projection decisions live in the shared
        // commonMain builder ([buildMediaItemsQuerySpec]); this adapter only
        // resolves the spec's wire serial names against the SDK enums
        // ([LibraryItemsQueryResolvers]).
        val spec = buildMediaItemsQuerySpec(
            parentId = parentId,
            filters = filters,
            studioIds = studioIds,
            startIndex = startIndex,
            limit = limit,
            searchTerm = searchTerm,
            kindFilter = kindFilter,
        )
        val response = api.itemsApi.getItems(
            parentId = spec.parentId?.toUUID(),
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
            excludeItemTypes = spec.excludeKinds.toBaseItemKinds(),
            genres = spec.genres,
            years = spec.years,
            studioIds = spec.studioIds?.map { it.toUUID() },
            tags = spec.tags,
            sortBy = spec.sortBy.toItemSortBys(),
            sortOrder = spec.sortOrderDescending.toSortOrderList(),
            startIndex = spec.startIndex,
            limit = spec.limit,
            recursive = spec.recursive,
            searchTerm = spec.searchTerm,
            filters = spec.itemFilters.toItemFilters(),
            hasSubtitles = spec.hasSubtitles,
            hasTrailer = spec.hasTrailer,
            minCommunityRating = spec.minCommunityRating,
            fields = spec.fields.toItemFieldsList(),
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
            items = rawItems.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = totalCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getItemsByIds(ids: List<String>): Result<List<MediaItem>> = engine.withApi { api ->
        if (ids.isEmpty()) {
            emptyList()
        } else {
            // Same batched-ids read the classic-latest pipeline uses to
            // resolve grouped Series cards (degrade-not-fail there); here the
            // caller — the home fetcher's plugin-row mapping — degrades per
            // entry, so a failed read surfaces as a failed Result and every
            // entry falls back to its title+year tile. Parental-rating
            // filtered like every list read: the row renders on home, so it
            // obeys the home rules.
            api.itemsApi.getItems(
                ids = ids.map { it.toUUID() },
                fields = LIST_ITEM_FIELDS,
            ).content?.items.orEmpty().toFilteredMediaItems(engine.currentMaxParentalRating)
        }
    }

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = engine.withApi { api ->
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
        val client = api
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
        // The DTO → MediaDetail mapping lives in JellyfinDtoMappers (beside
        // toMediaItem); the former commonMain wire twin went with the
        // removed wasmJs target.
        item.toMediaDetail()
    }

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> = engine.withApi { api ->
        val userId = engine.requireUserId().toUUID()
        val response = api.userLibraryApi.getIntros(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        (response?.items ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> = engine.withApi { api ->
        val userId = engine.requireUserId().toUUID()
        // Unlike getIntros (a BaseItemDtoQueryResult with a paginated `.items`
        // wrapper), getSpecialFeatures returns a bare List<BaseItemDto> directly
        // — the /Items/{id}/SpecialFeatures endpoint emits a JSON array, so the
        // SDK surfaces it as a list rather than a query result.
        val response = api.userLibraryApi.getSpecialFeatures(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        (response ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getLocalTrailers(itemId: String): Result<List<MediaItem>> = engine.withApi { api ->
        // Same bare-List shape as getSpecialFeatures (the /Items/{id}/…
        // companion endpoint emits a JSON array, not a query envelope).
        val userId = engine.requireUserId().toUUID()
        val response = api.userLibraryApi.getLocalTrailers(
            itemId = itemId.toUUID(),
            userId = userId,
        ).content
        (response ?: emptyList()).toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = engine.withApi { api ->
        val spec = buildSearchHintsQuerySpec(query, mediaTypes, limit, startIndex)
        val response = api.itemsApi.getItems(
            searchTerm = spec.searchTerm,
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
            limit = spec.limit,
            startIndex = spec.startIndex,
            recursive = spec.recursive,
            fields = spec.fields.toItemFieldsList(),
        ).content
        SearchResult(
            items = response.items.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = engine.withApi { api ->
        // The jellyfin-web useSearchSuggestions shape, held once in commonMain
        // (SEARCH_SUGGESTIONS_* and resolved above against the SDK enums).
        val response = api.itemsApi.getItems(
            sortBy = SEARCH_SUGGESTIONS_SORT,
            includeItemTypes = SEARCH_SUGGESTIONS_KINDS,
            limit = limit,
            recursive = true,
            fields = SEARCH_SUGGESTIONS_PROJECTION,
        ).content
        SearchResult(
            items = response.items.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = 0,
        )
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        engine.withApi { api ->
            // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: the result
            // is a bare item id used for matching, not display, and the server
            // scopes the query to the authenticated user's libraries.
            //
            // The Jellyfin SDK's typed getItems() doesn't expose the AnyProviderId
            // filter, so use the raw GET with a custom query parameter. Jellyfin's
            // /Items endpoint accepts "AnyProviderId" in the "tmdb:123" format.
            val response = api.get<org.jellyfin.sdk.model.api.BaseItemDtoQueryResult>(
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
        engine.withApi { api ->
            val userId = engine.currentUserId()?.toUUID()
            val response = api.genresApi.getGenres(
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
    ): Result<SearchResult> = engine.withApi { api ->
        val spec = buildItemsByGenreQuerySpec(genreId, mediaTypes, startIndex, limit)
        val response = api.itemsApi.getItems(
            genreIds = spec.genreIds?.map { it.toUUID() },
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
            startIndex = spec.startIndex,
            limit = spec.limit,
            recursive = spec.recursive,
        ).content
        SearchResult(
            items = response.items.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getStudios(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<Studio>> = engine.withApi { api ->
        val userId = engine.currentUserId()?.toUUID()
        val response = api.studiosApi.getStudios(
            parentId = parentId?.let { it.toUUID() },
            userId = userId,
            startIndex = startIndex,
            limit = limit,
        ).content
        response.items.map { item ->
            Studio(id = item.id.toString(), name = item.name ?: "")
        }
    }

    override suspend fun getPeople(
        searchTerm: String?,
        limit: Int,
        appearsInItemId: String?,
    ): Result<List<PersonRef>> = engine.withApi { api ->
        val userId = engine.currentUserId()?.toUUID()
        val response = api.personsApi.getPersons(
            limit = limit,
            searchTerm = searchTerm?.takeIf { it.isNotBlank() },
            userId = userId,
            appearsInItemId = appearsInItemId?.toUUID(),
        ).content
        response.items.map { item ->
            PersonRef(id = item.id.toString(), name = item.name ?: "")
        }
    }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = engine.withApi { api ->
        val spec = buildItemsByStudioQuerySpec(studioId, mediaTypes, startIndex, limit)
        val response = api.itemsApi.getItems(
            studioIds = spec.studioIds?.map { it.toUUID() },
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
            startIndex = spec.startIndex,
            limit = spec.limit,
            recursive = spec.recursive,
            fields = spec.fields.toItemFieldsList(),
        ).content
        SearchResult(
            items = response.items.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = engine.withApi { api ->
        val response = api.itemsApi.getItems(
            albumArtistIds = listOf(artistId.toUUID()),
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = limit,
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = LIST_ITEM_FIELDS,
        ).content
        response.items.toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = engine.withApi { api ->
        val response = api.itemsApi.getItems(
            parentId = albumId.toUUID(),
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = LIST_ITEM_FIELDS,
        ).content
        response.items.toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        engine.withApi { api ->
            api.libraryApi.getSimilarItems(
                itemId = itemId.toUUID(),
                limit = limit,
            ).content.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        engine.withApi { api ->
            val userId = engine.currentUserId()?.toUUID()
                ?: return@withApi emptyList()
            api.instantMixApi.getInstantMixFromItem(
                userId = userId,
                itemId = itemId.toUUID(),
                limit = limit,
                fields = LIST_ITEM_FIELDS,
            ).content.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        engine.withApi { api ->
            // + People (D18): the filmography rows extract the per-item role
            // from the person entry matching this id. Bounded by the
            // call-site limit (50) — one extra field on an existing query.
            val response = api.itemsApi.getItems(
                personIds = listOf(personId.toUUID()),
                limit = limit,
                recursive = true,
                fields = LIST_ITEM_FIELDS + ItemFields.PEOPLE,
            ).content
            response.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getBoxSetsContaining(itemId: String): Result<List<CollectionSummary>> =
        engine.withApi { api ->
            // D6 membership, resolved explicitly: Jellyfin never lists BoxSets
            // among /Items/{id}/Ancestors (verified against 12.2 — an item's
            // ancestors stop at the library folder), so enumerate the user's
            // boxsets (same query shape as getCollections) and probe each one
            // for the item. The probe is an id-filtered query under the
            // boxset parent — 0 or 1 stub items per boxset, so cost scales
            // with boxset count, not library size. No parental filter (mirrors
            // findItemByProviderId): ids/names/poster tags feed a navigation
            // row, and the server scopes the read to the authenticated user.
            // Members resolve through getCollectionItems — not duplicated here.
            //
            // Probes run CONCURRENTLY: the old serial `filter`
            // paid one HTTP round-trip per collection in sequence — 30
            // collections at 50-100 ms RTT was 1.5-3 s of wall time on every
            // movie/series detail open. [mapConcurrent] preserves the
            // enumeration order AND the old failure semantics (a failed probe
            // fails the whole call, like the serial filter did). The
            // enumerate itself is capped at [BOX_SET_ENUMERATE_CAP] rows
            // (server default sort) so a pathological collection count can't
            // fan out unbounded.
            val itemUuid = itemId.toUUID()
            val boxSets = api.itemsApi.getItems(
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
                fields = listOf(ItemFields.CHILD_COUNT),
                limit = BOX_SET_ENUMERATE_CAP,
            ).content.items
            Semaphore(DEFAULT_FANOUT_PARALLELISM).mapConcurrent(boxSets) { boxSet ->
                boxSet to api.itemsApi.getItems(
                    parentId = boxSet.id,
                    ids = listOf(itemUuid),
                    limit = 1,
                ).content.items.isNotEmpty()
            }
                .filter { it.second }
                .map { (item, _) ->
                    CollectionSummary(
                        id = item.id.toString(),
                        name = item.name ?: "",
                        itemCount = item.childCount ?: 0,
                        imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
                    )
                }
        }

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        engine.withApi { api ->
            val response = api.libraryApi.getThemeSongs(
                itemId = itemId.toUUID(),
            ).content
            response.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getThemeVideos(itemId: String, inheritFromParent: Boolean): Result<List<MediaItem>> =
        engine.withApi { api ->
            // Same ThemeMediaResult envelope as getThemeSongs — the identical
            // mapping and parental filter apply (H7 hero clip source).
            val response = api.libraryApi.getThemeVideos(
                itemId = itemId.toUUID(),
                inheritFromParent = inheritFromParent,
            ).content
            response.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = engine.withApi { api ->
        api.tvShowsApi.getSeasons(
            seriesId = seriesId.toUUID(),
        ).content.items.toFilteredMediaItems(engine.currentMaxParentalRating)
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String, isMissing: Boolean?): Result<List<MediaItem>> =
        engine.withApi { api ->
            // MediaStreams projection (D11): the episode rows derive the
            // subtitle/audio badges from it. Per-season only — the
            // all-episodes path below carries the same projection so the
            // series-detail snapshot renders the badges too. MEDIA_SOURCES is
            // the container the mapper reads (see getAllEpisodes).
            api.tvShowsApi.getEpisodes(
                seriesId = seriesId.toUUID(),
                seasonId = seasonId.toUUID(),
                isMissing = isMissing,
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS),
            ).content.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getAllEpisodes(seriesId: String, isMissing: Boolean?): Result<List<MediaItem>> =
        engine.withApi { api ->
            // MediaStreams projection (D11): the series-detail catalogue serves
            // its episode rows from this single round-trip, so the stream
            // badges must ride along. MEDIA_SOURCES is the field the mapper
            // actually reads (mediaSources.firstOrNull().mediaStreams) — on a
            // real server MEDIA_STREAMS alone populates only the item-level
            // flattened stream list, which the mapper ignores, and the badges
            // stayed dead on-device until MEDIA_SOURCES joined the query.
            api.tvShowsApi.getEpisodes(
                seriesId = seriesId.toUUID(),
                isMissing = isMissing,
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS),
            ).content.items.toFilteredMediaItems(engine.currentMaxParentalRating)
        }

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> =
        // The four collection members moved to [CollectionApiClientImpl] (the
        // family adapter over the same engine, ctor-injected above); this
        // class keeps only the [HomeSectionSources] obligation — the home
        // fetcher's pinned collection sections read through it — discharged
        // by this one-line forward.
        collectionApiClient.getCollectionItems(collectionId, startIndex, limit)

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = engine.withApi { api ->
        // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: tags are
        // plain strings with no rating attribute to filter on, and the server
        // already enforces library-access scoping on the underlying item query.
        //
        // The scan pages the whole library (not just the first `limit` items —
        // a tag not carried by the first page never surfaced before): each page
        // requests the TAGS-only projection, advancing until the server's
        // totalRecordCount is exhausted (a short page also ends it — the last
        // page's shape), capped at [TAGS_ITEM_SCAN_CAP] scanned items. [limit]
        // is the page size; the returned list stays the sorted distinct tag
        // fold it has always been (never truncated to [limit]).
        val tags = sortedSetOf<String>()
        var cursor = startIndex
        var total = Int.MAX_VALUE
        while (cursor < total && cursor - startIndex < TAGS_ITEM_SCAN_CAP) {
            val pageSize = minOf(limit, TAGS_ITEM_SCAN_CAP - (cursor - startIndex))
            val response = api.itemsApi.getItems(
                parentId = parentId?.let { it.toUUID() },
                startIndex = cursor,
                limit = pageSize,
                recursive = true,
                fields = listOf(ItemFields.TAGS),
            ).content
            val items = response.items ?: emptyList()
            if (items.isEmpty()) break
            items.forEach { item -> tags.addAll(item.tags ?: emptyList()) }
            cursor += items.size
            total = response.totalRecordCount
            if (items.size < pageSize) break
        }
        tags.toList()
    }

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = engine.withApi { api ->
        val spec = buildFavoritesQuerySpec(mediaTypes, limit, startIndex)
        val response = api.itemsApi.getItems(
            includeItemTypes = spec.includeKinds.toBaseItemKinds(),
            filters = spec.itemFilters.toItemFilters(),
            limit = spec.limit,
            startIndex = spec.startIndex,
            recursive = spec.recursive,
            fields = spec.fields.toItemFieldsList(),
        ).content
        SearchResult(
            items = response.items.toFilteredMediaItems(engine.currentMaxParentalRating),
            totalRecordCount = response.totalRecordCount,
            startIndex = startIndex,
        )
    }

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = engine.apiResultWithRetry {
        lyricsApi.fetchLyrics(itemId)
    }

    // The playlist family (the eight /Playlists reads/writes) and the rest of
    // the collection family (getCollections / createCollection /
    // addItemsToCollection) moved to [PlaylistApiClientImpl] /
    // [CollectionApiClientImpl] — their own adapter singles over this class's
    // SAME engine (the union and the Koin seams delegate there). Nothing else
    // on this class closed over them: no caches, no fetcher, no favorite-flag
    // store — pure relocation, byte-identical wire.

    /**
     * The ONE user-data write funnel (the four former per-verb members
     * folded): each op keeps its exact former transport — mark/unmark ride
     * [JellyfinApiEngine.withApi], the toggle rides the retry chassis +
     * [FavoriteFlagCache] read-flip cache. The [UserDataWriteOutcome] carries
     * the post-toggle favorite state; state writes answer [UserDataWriteOutcome.Done].
     */
    override suspend fun writeUserData(write: UserDataWrite): Result<UserDataWriteOutcome> = when (write) {
        is UserDataWrite.MarkPlayed -> engine.withApi { api ->
            val userId = engine.requireUserId()
            api.playStateApi.markPlayedItem(
                userId = userId.toUUID(),
                itemId = write.itemId.toUUID(),
            )
            UserDataWriteOutcome.Done
        }

        is UserDataWrite.MarkUnplayed -> engine.withApi { api ->
            val userId = engine.requireUserId()
            api.playStateApi.markUnplayedItem(
                userId = userId.toUUID(),
                itemId = write.itemId.toUUID(),
            )
            UserDataWriteOutcome.Done
        }

        is UserDataWrite.ToggleFavorite -> engine.apiResultWithRetry {
            val userId = engine.requireUserId()
            val uuid = write.itemId.toUUID()
            val toggled = favoriteFlags.toggle(
                cacheKey = uuid.toString(),
                currentIsFavorite = write.currentIsFavorite,
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
            UserDataWriteOutcome.FavoriteNow(toggled)
        }

        is UserDataWrite.SetFavorite -> engine.withApi { api ->
            val userId = engine.requireUserId()
            val uuid = write.itemId.toUUID()
            if (write.isFavorite) {
                api.userLibraryApi.markFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            } else {
                api.userLibraryApi.unmarkFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            }
            favoriteFlags.put(uuid.toString(), write.isFavorite)
            UserDataWriteOutcome.Done
        }
    }

    /**
     * The home-layout DisplayPreferences slice (H20). Transport: the SDK's
     * DisplayPreferencesApi keyed by (displayPreferencesId, user, client) —
     * the client value ("jellyplay") namespaces our store away from
     * jellyfin-web's; the payload rides the DTO's CustomPrefs string map
     * under the HomeLayoutSync keys. The write is a single POST with a fresh
     * DTO: this wrapper owns the whole namespace, so replacing the custom
     * prefs is safe — no foreign keys to preserve, and no stale key can
     * outlive a layout delete.
     */
    override suspend fun readHomeLayoutDisplayPreferences(userId: String): Result<HomeLayoutServerState> =
        engine.withApi { api ->
            val dto = api.displayPreferencesApi.getDisplayPreferences(
                displayPreferencesId = HOME_LAYOUT_DISPLAY_PREFERENCES_ID,
                userId = userId.toUUID(),
                client = HOME_LAYOUT_CLIENT,
            ).content
            // Missing keys decode to null state (never synced / partial
            // store) — a value, not a failure; the caller's lenient decode
            // treats absent as "nothing on server".
            HomeLayoutServerState(
                json = dto.customPrefs[HOME_LAYOUT_JSON_KEY],
                schemaVersion = dto.customPrefs[HOME_LAYOUT_SCHEMA_VERSION_KEY]?.toIntOrNull(),
                savedAtEpochMillis = dto.customPrefs[HOME_LAYOUT_SAVED_AT_KEY]?.toLongOrNull(),
            )
        }

    override suspend fun writeHomeLayoutDisplayPreferences(userId: String, json: String, schemaVersion: Int): Result<Unit> =
        engine.withApi { api ->
            // savedAt stamps the write-time client clock (the DisplayPreferences
            // DTO carries no server timestamp) — the manual sync dialog's
            // newer-copy signal, not a clock-sync guarantee.
            api.displayPreferencesApi.updateDisplayPreferences(
                displayPreferencesId = HOME_LAYOUT_DISPLAY_PREFERENCES_ID,
                userId = userId.toUUID(),
                client = HOME_LAYOUT_CLIENT,
                data = DisplayPreferencesDto(
                    id = userId,
                    viewType = null,
                    sortBy = null,
                    indexBy = null,
                    rememberIndexing = false,
                    primaryImageHeight = 0,
                    primaryImageWidth = 0,
                    customPrefs = mapOf(
                        HOME_LAYOUT_JSON_KEY to json,
                        HOME_LAYOUT_SCHEMA_VERSION_KEY to schemaVersion.toString(),
                        HOME_LAYOUT_SAVED_AT_KEY to timeSource.nowEpochMillis().toString(),
                    ),
                    scrollDirection = ScrollDirection.HORIZONTAL,
                    showBackdrop = false,
                    rememberSorting = false,
                    sortOrder = SortOrder.ASCENDING,
                    showSidebar = false,
                    client = HOME_LAYOUT_CLIENT,
                ),
            ).content
            Unit
        }

    /**
     * The shared commonMain favorite-flag cache-aside choreography
     * ([FavoriteFlagCache]); this client supplies the atomic-session identity
     * (see [currentHomeCacheIdentity]) and the SDK transport calls.
     */
    private val favoriteFlags = FavoriteFlagCache { currentHomeCacheIdentity() }

    /**
     * Delegates to core:model's [buildItemImageUrl] — the single owner of the
     * item-image URL math (a byte-parity port of the SDK `imageApi` builder,
     * pinned by ImageUrlBuilderTest); the SDK transport stays for the actual
     * requests. One deliberate divergence from the former direct SDK call:
     * the builder's [normalizeItemIdGuid] accepts the compact 32-hex item ids
     * Jellyfin 10.11 emits (the `toUUID()` parse refused them), normalizing
     * to the dashed form the server serves either way.
     */
    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String {
        val api = engine.api ?: return ""
        val imageTypeEnum = IMAGE_TYPES_BY_SERIAL_NAME[imageType.lowercase()]
            ?: return ""
        return buildItemImageUrl(
            baseUrl = api.baseUrl,
            itemId = itemId,
            imageType = imageTypeEnum.serialName,
            maxWidth = maxWidth,
            tag = tag,
            imageIndex = imageIndex,
        )
    }

    override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String =
        getImageUrl(itemId, "Backdrop", maxWidth, null, tag)

    override suspend fun getChildItemImageUrls(parentId: String, limit: Int): List<String> {
        return try {
            // Request shape + Primary-tag fold live in commonMain
            // (ChildItemImageUrls.kt); this side keeps only the SDK transport
            // and the image-URL seam.
            val spec = buildChildItemImagesQuerySpec(parentId, limit)
            val api = engine.requireApi()
            val response = api.itemsApi.getItems(
                parentId = spec.parentId?.toUUID(),
                includeItemTypes = spec.includeKinds.toBaseItemKinds(),
                limit = spec.limit,
                sortBy = spec.sortBy.toItemSortBys(),
                sortOrder = spec.sortOrderDescending.toSortOrderList(),
                fields = spec.fields.toItemFieldsList(),
            ).content
            response.items.map { item ->
                ChildItemImageRow(
                    id = item.id.toString(),
                    hasPrimaryImage = item.imageTags?.containsKey(ImageType.PRIMARY) == true,
                )
            }.toChildItemImageUrls { itemId -> getImageUrl(itemId, "Primary", 200) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
