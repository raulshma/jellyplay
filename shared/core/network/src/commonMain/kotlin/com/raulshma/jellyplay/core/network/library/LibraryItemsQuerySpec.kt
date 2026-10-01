package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus

/**
 * The request-SHAPE half of the library twins' shared policy — the previously
 * un-extracted counterpart of [LibraryRequestPolicy]'s projections and
 * [HomeSectionsFetcher]'s choreography. Each builder below decides, ONCE, what
 * one read endpoint's `/Items` query contains (filters, sort field+order, kind
 * include/exclude, paging, field projection); the hand-mirrored client
 * ([com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl] on the JVM)
 * becomes a thin adapter that only translates a [LibraryItemsQuerySpec] into
 * its transport vocabulary — SDK typed setters.
 *
 * Everything here is wire-level (string serial names) and pure: no
 * Jellyfin SDK, only shared/core/model inputs. The JVM adapter resolves the
 * wire names against the SDK enums it sends (the same serialName-lookup
 * regime [LibraryRequestPolicy] established).
 *
 * Collection encoding stays adapter-side BY DESIGN: the SDK's UrlBuilder
 * repeats a collection param per element (`fields=A&fields=B`). The same
 * reasoning lets builders normalize empty
 * collections to null: the SDK's UrlBuilder skips null values AND adds zero
 * params for an empty collection, so "null" and "empty" are wire-identical on
 * the JVM side.
 *
 * Endpoints whose assembly is a single fixed path (no per-call decisions —
 * e.g. getLatestMedia, getAlbumTracks, getCollections, the detail projection)
 * stay hand-assembled at their call sites; the builders here exist exactly
 * where a mapping decision used to be encoded twice.
 */

/**
 * One `/Items` read query in wire vocabulary, covering the item-list endpoints
 * the two library clients share. `null` always means "omit the parameter";
 * list fields hold wire serial names ([BaseItemKind]/[ItemFilter]/
 * ItemSortBy/ItemFields equivalents — see the Jellyfin SDK enums' serialName).
 */
internal data class LibraryItemsQuerySpec(
    val parentId: String? = null,
    /** includeItemTypes serial names ("Movie", "Series", …); null = unconstrained. */
    val includeKinds: List<String>? = null,
    /** excludeItemTypes serial names; null = no excludes. */
    val excludeKinds: List<String>? = null,
    /** genreIds / studioIds as raw server ids; single-element lists at their call sites. */
    val genreIds: List<String>? = null,
    val studioIds: List<String>? = null,
    /** Name-matched genres / tags / production years. */
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val years: List<Int>? = null,
    /** sortBy serial names, already split into tokens ("ProductionYear", "SortName"). */
    val sortBy: List<String>? = null,
    /** true = "Descending", false = "Ascending"; null = omit sortOrder entirely. */
    val sortOrderDescending: Boolean? = null,
    val startIndex: Int? = null,
    val limit: Int? = null,
    /** Every spec'd endpoint queries recursively. */
    val recursive: Boolean = true,
    /** Passed through verbatim (callers decide blank-gating). */
    val searchTerm: String? = null,
    /** ItemFilter serial names ("IsPlayed", "IsUnplayed", "IsResumable", "IsFavorite"). */
    val itemFilters: List<String>? = null,
    /**
     * Standalone /Items presence params (NOT ItemFilter values — the resolver
     * fails fast on unknown tokens): true = only items with subtitles / a
     * trailer. Tri-state like [LibraryFilters.isResumable]: null and a stored
     * false are both "off" and omit the parameter.
     */
    val hasSubtitles: Boolean? = null,
    val hasTrailer: Boolean? = null,
    /** Minimum community rating; null = no floor. */
    val minCommunityRating: Double? = null,
    /** ItemFields serial names; null = server default projection. */
    val fields: List<String>? = null,
    /** Discover rows only: personIds as raw server ids (cast/crew filter); null = unconstrained. */
    val personIds: List<String>? = null,
    /**
     * Discover rows only: window lower bounds as epoch millis, resolved to the
     * SDK's LocalDateTime date params by the JVM adapter (minDateLastSaved /
     * minPremiereDate). null = no window.
     */
    val minDateLastSavedMs: Long? = null,
    val minPremiereDateMs: Long? = null,
)

/**
 * The library-grid query (getMediaItems): the heaviest duplicated assembly —
 * played-status → ItemFilter mapping, the resumable position filter, compound
 * sortBy parsing, sort-order normalization, kind include/exclude resolution,
 * the rating floor, and the blank-term/empty-collection gating, previously
 * encoded twice (~35 lines per twin).
 */
internal fun buildMediaItemsQuerySpec(
    parentId: String?,
    filters: LibraryFilters,
    studioIds: List<String>?,
    startIndex: Int,
    limit: Int,
    searchTerm: String?,
    kindFilter: ItemKindFilter,
): LibraryItemsQuerySpec {
    // Played-status maps onto Jellyfin's ItemFilter (IsPlayed/IsUnplayed) and
    // composes with the IsResumable position filter (UserData
    // .PlaybackPositionTicks > 0), which powers the "In Progress" filter/sort.
    // Previously the status chips toggled + persisted but never reached the
    // query (analysis F1) — the mapping is now pinned once, here.
    val itemFilters = buildList {
        when (filters.playedStatus.takeIf { it != PlayedStatus.ALL }) {
            PlayedStatus.PLAYED -> add("IsPlayed")
            PlayedStatus.UNPLAYED -> add("IsUnplayed")
            else -> {}
        }
        if (filters.isResumable == true) add("IsResumable")
    }
    // includeItemTypes / excludeItemTypes: resolve the requested kinds once,
    // then drop SEASON/EPISODE from the exclude list when they were
    // explicitly included (the shared [libraryExcludeKinds] policy —
    // Jellyfin would otherwise receive contradictory include+exclude for the
    // same kind and return an empty result).
    val includeKinds = includeItemKindsOrNull(filters.mediaTypes)
    val excludeKinds = libraryExcludeKinds(
        seasonKind = "Season",
        episodeKind = "Episode",
        includeKinds = includeKinds.orEmpty(),
        includeEpisodes = kindFilter.includeEpisodes,
    )
    return LibraryItemsQuerySpec(
        parentId = parentId,
        includeKinds = includeKinds,
        excludeKinds = excludeKinds.takeIf { it.isNotEmpty() },
        genres = filters.genres.takeIf { it.isNotEmpty() },
        years = filters.years.takeIf { it.isNotEmpty() },
        studioIds = studioIds?.takeIf { it.isNotEmpty() },
        tags = filters.tags.takeIf { it.isNotEmpty() },
        sortBy = parseItemSortList(filters.sortBy.apiValue).takeIf { it.isNotEmpty() },
        // SortOption carries "Ascending"/"Descending"; normalize case-
        // insensitively with an Ascending default (JVM: SortOrder enum lookup).
        sortOrderDescending = filters.sortBy.sortOrder.equals("Descending", ignoreCase = true),
        startIndex = startIndex,
        limit = limit,
        searchTerm = searchTerm?.takeIf { it.isNotBlank() },
        itemFilters = itemFilters.takeIf { it.isNotEmpty() },
        // Presence filters pass only an explicit true: a stored false is the
        // tri-state "off" (same rule as the IsResumable item filter above).
        hasSubtitles = filters.hasSubtitles.takeIf { it == true },
        hasTrailer = filters.hasTrailer.takeIf { it == true },
        minCommunityRating = filters.minRating.takeIf { it > 0f }?.toDouble(),
        fields = LIST_PROJECTION_FIELDS + "Genres",
    )
}

/**
 * The discover-row query: [buildMediaItemsQuerySpec] over the row's shared
 * [LibraryFilters] dimensions, plus the discover-only dimensions (studios,
 * people, relative date windows) and the row's own limit. Pure — [nowEpochMs]
 * param keeps the relative-window arithmetic testable; the JVM adapter
 * resolves the millis to the SDK's date params.
 */
internal fun buildDiscoverRowQuerySpec(
    row: DiscoverRowConfig,
    parentId: String?,
    startIndex: Int,
    limit: Int,
    nowEpochMs: Long,
): LibraryItemsQuerySpec {
    val base = buildMediaItemsQuerySpec(
        parentId = parentId,
        filters = row.filters,
        studioIds = row.studios.map { it.id }.takeIf { it.isNotEmpty() },
        startIndex = startIndex,
        limit = limit,
        searchTerm = null,
        kindFilter = ItemKindFilter.TOP_LEVEL,
    )
    return base.copy(
        personIds = row.people.map { it.id }.takeIf { it.isNotEmpty() },
        minDateLastSavedMs = row.addedWithinDays?.let { nowEpochMs - it * DAY_MS },
        // Year windows measured in 365-day units — a leap-day skew is
        // immaterial for a discovery filter.
        minPremiereDateMs = row.premieredWithinYears?.let { nowEpochMs - it * YEAR_MS },
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val YEAR_MS = 365L * DAY_MS

/** The search-hints query (getSearchHints): term + optional kind narrowing + paging. */
internal fun buildSearchHintsQuerySpec(
    query: String,
    mediaTypes: List<MediaType>?,
    limit: Int,
    startIndex: Int,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    includeKinds = includeItemKindsOrNull(mediaTypes),
    startIndex = startIndex,
    limit = limit,
    searchTerm = query,
    fields = LIST_PROJECTION_FIELDS,
)

/** The favorites query (getFavorites): the IsFavorite filter over optionally-narrowed kinds. */
internal fun buildFavoritesQuerySpec(
    mediaTypes: List<MediaType>?,
    limit: Int,
    startIndex: Int,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    includeKinds = includeItemKindsOrNull(mediaTypes),
    startIndex = startIndex,
    limit = limit,
    itemFilters = listOf("IsFavorite"),
    fields = LIST_PROJECTION_FIELDS,
)

/**
 * The genre drill-down query (getItemsByGenre). Deliberately projects NO
 * fields — the pre-spec twins both omit the param here (unlike the studio
 * drill-down below); kept as-is.
 */
internal fun buildItemsByGenreQuerySpec(
    genreId: String,
    mediaTypes: List<MediaType>?,
    startIndex: Int,
    limit: Int,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    genreIds = listOf(genreId),
    includeKinds = includeItemKindsOrNull(mediaTypes),
    startIndex = startIndex,
    limit = limit,
)

/** The studio drill-down query (getItemsByStudio): like genre, but with the list projection. */
internal fun buildItemsByStudioQuerySpec(
    studioId: String,
    mediaTypes: List<MediaType>?,
    startIndex: Int,
    limit: Int,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    studioIds = listOf(studioId),
    includeKinds = includeItemKindsOrNull(mediaTypes),
    startIndex = startIndex,
    limit = limit,
    fields = LIST_PROJECTION_FIELDS,
)

/**
 * The resume-row query (getContinueWatching / getContinueReading — and the
 * NextUp row, which rides the same limit + list-projection shape with no kind
 * narrowing): the `/UserItems/Resume` request the twins used to hand-mirror.
 * [kinds] carries the `includeItemTypes` serial names: `["Book"]` for the
 * Continue Reading row, null for the video row — the video row sends the
 * exact pre-12 wire shape (unconstrained) in BOTH modes; classic rows drop
 * the 12.x Series/Season rollups in the client-side fold instead
 * ([toFilteredResumeRows]'s `dropContainerRollups`), so the wire stays
 * byte-identical across server generations.
 *
 * Deliberately NOT here: the `nextUpDateCutoff` CLOCK (`java.time`, JVM-side)
 * and the SDK's non-null enable* defaults
 * (enableTotalRecordCount / enableImages / excludeActiveSessions), which are
 * transport-level — the JVM SDK sends them as non-null defaults.
 */
internal fun buildResumeQuerySpec(
    limit: Int,
    kinds: List<String>?,
): LibraryItemsQuerySpec = LibraryItemsQuerySpec(
    includeKinds = kinds,
    limit = limit,
    fields = LIST_PROJECTION_FIELDS,
)

/**
 * The classic-rows TV-latest leaf kind (#168): the kind the pre-12
 * `/Items/Latest` pipeline fed its Series grouping — the raw-episode pool the
 * client-side twin ([toClassicLatestCards]) re-groups. A [MediaType] so the
 * wire pin ([MediaType.toWireItemKind] serial) and the fold's kind filter
 * ([toFilteredLatestRows] allowed-kinds) resolve through one name.
 */
internal val CLASSIC_TV_LATEST_MEDIA_TYPE: MediaType = MediaType.EPISODE

/**
 * The pre-12 latest-pool overfetch multiplier: Jellyfin 10.x's
 * `GetItemsForLatestItems` fetched `limit * 5` episodes before grouping into
 * `limit` containers. The classic path mirrors the same pool size so the
 * client-side grouping sees exactly the candidate set the 10.x server's
 * grouping saw (UserViewManager.GetLatestItems over a `limit * 5` query).
 */
internal const val CLASSIC_LATEST_POOL_MULTIPLIER: Int = 5

/**
 * The classic-rows episode-pool size for one library folder's `/Items/Latest`
 * call (#168): TV folders fetch a raw-Episode pool of `limit * 5` for the
 * client-side pre-12 grouping ([toClassicLatestCards]); every other collection
 * type stays unconstrained — movies behaved identically before and after 12.x
 * (both generations resolve a movies folder to plain Movie rows), and the
 * mixed-library routing computes movies and shows server-side, so pinning
 * either single kind would silently drop half the folder's additions.
 */
internal fun classicLatestEpisodePool(collectionType: String?, limit: Int): Int? =
    if (collectionType == "tvshows") limit * CLASSIC_LATEST_POOL_MULTIPLIER else null

/**
 * [MediaType]s → includeItemTypes serial names, shared by every spec'd
 * endpoint that narrows by kind: UNKNOWN maps to null ("do not constrain by
 * type" — [MediaType.toWireItemKind]'s contract) and an all-null or empty
 * input normalizes to null so the parameter is omitted entirely.
 */
private fun includeItemKindsOrNull(mediaTypes: List<MediaType>?): List<String>? =
    mediaTypes
        ?.mapNotNull { it.toWireItemKind() }
        ?.takeIf { it.isNotEmpty() }
