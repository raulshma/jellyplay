package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus

/**
 * The request-SHAPE half of the library twins' shared policy — the previously
 * un-extracted counterpart of [LibraryRequestPolicy]'s projections and
 * [HomeSectionsFetcher]'s choreography. Each builder below decides, ONCE, what
 * one read endpoint's `/Items` query contains (filters, sort field+order, kind
 * include/exclude, paging, field projection); the two hand-mirrored clients
 * ([com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl] on the JVM,
 * [com.raulshma.jellyplay.core.network.api.KtorWasmLibraryApiClient] on wasm)
 * become thin adapters that only translate a [LibraryItemsQuerySpec] into
 * their transport vocabulary — SDK typed setters vs raw query strings.
 *
 * Everything here is wire-level (string serial names) and pure: no Ktor, no
 * Jellyfin SDK, only shared/core/model inputs. The JVM adapter resolves the
 * wire names against the SDK enums it sends (the same serialName-lookup
 * regime [LibraryRequestPolicy] established); the wasm adapter sends them
 * as-is.
 *
 * Collection encoding stays adapter-side BY DESIGN: the SDK's UrlBuilder
 * repeats a collection param per element (`fields=A&fields=B`) while the wasm
 * client comma-joins (`fields=A,B`) — server-side model binding treats both
 * identically, and [WasmMirrorContractTest] pins the param-NAME parity rather
 * than the encoding. The same reasoning lets builders normalize empty
 * collections to null: the SDK's UrlBuilder skips null values AND adds zero
 * params for an empty collection, so "null" and "empty" are wire-identical on
 * the JVM side, and the wasm adapter's null-filtering `q()` drops both.
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
    /** Minimum community rating; null = no floor. */
    val minCommunityRating: Double? = null,
    /** ItemFields serial names; null = server default projection. */
    val fields: List<String>? = null,
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
        // insensitively with an Ascending default — the fallback both twins
        // applied (JVM: SortOrder enum lookup, wasm: string re-derivation).
        sortOrderDescending = filters.sortBy.sortOrder.equals("Descending", ignoreCase = true),
        startIndex = startIndex,
        limit = limit,
        searchTerm = searchTerm?.takeIf { it.isNotBlank() },
        itemFilters = itemFilters.takeIf { it.isNotEmpty() },
        minCommunityRating = filters.minRating.takeIf { it > 0f }?.toDouble(),
        fields = LIST_PROJECTION_FIELDS + "Genres",
    )
}

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
 * [MediaType]s → includeItemTypes serial names, shared by every spec'd
 * endpoint that narrows by kind: UNKNOWN maps to null ("do not constrain by
 * type" — [MediaType.toWireItemKind]'s contract) and an all-null or empty
 * input normalizes to null so the parameter is omitted entirely.
 */
private fun includeItemKindsOrNull(mediaTypes: List<MediaType>?): List<String>? =
    mediaTypes
        ?.mapNotNull { it.toWireItemKind() }
        ?.takeIf { it.isNotEmpty() }
