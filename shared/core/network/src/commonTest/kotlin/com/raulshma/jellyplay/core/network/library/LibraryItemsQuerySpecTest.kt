package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the commonMain query-spec builders ([buildMediaItemsQuerySpec] and
 * siblings): the per-endpoint request assembly BOTH library clients now
 * derive their `/Items` queries from. These are the decisions that were
 * previously hand-mirrored in `LibraryApiClientImpl` (SDK enums) and
 * `KtorWasmLibraryApiClient` (raw strings) — played-status/resumable filter
 * mapping, sort field+order derivation, kind include/exclude resolution,
 * empty-collection omission, the rating floor, paging and field projections —
 * so a value pinned here is a value BOTH adapters put on the wire.
 */
class LibraryItemsQuerySpecTest {

    // ── buildMediaItemsQuerySpec ──────────────────────────────────────────

    @Test
    fun `default filters parse the default sort and exclude season and episode`() {
        val spec = buildMediaItemsQuerySpec(
            parentId = "lib-1",
            filters = LibraryFilters(),
            studioIds = null,
            startIndex = 0,
            limit = 40,
            searchTerm = null,
            kindFilter = ItemKindFilter(),
        )
        // SortOption.YEAR_DESC default: "ProductionYear,SortName", Descending.
        assertEquals(listOf("ProductionYear", "SortName"), spec.sortBy)
        assertEquals(true, spec.sortOrderDescending)
        assertEquals("lib-1", spec.parentId)
        assertEquals(0, spec.startIndex)
        assertEquals(40, spec.limit)
        assertEquals(true, spec.recursive)
        // No media types requested → kinds unconstrained, both excludes on
        // (includeEpisodes defaults false).
        assertNull(spec.includeKinds)
        assertEquals(listOf("Season", "Episode"), spec.excludeKinds)
        // No played-status/resumable/rating/term dimension active.
        assertNull(spec.itemFilters)
        assertNull(spec.minCommunityRating)
        assertNull(spec.searchTerm)
        assertNull(spec.genres)
        assertNull(spec.years)
        assertNull(spec.tags)
        assertNull(spec.studioIds)
        // The library grid renders genres → the genres-extended projection.
        assertEquals(listOf("Overview", "PrimaryImageAspectRatio", "Genres"), spec.fields)
    }

    @Test
    fun `played status maps onto IsPlayed and IsUnplayed and composes with IsResumable`() {
        fun filters(status: PlayedStatus, resumable: Boolean? = null) =
            LibraryFilters(playedStatus = status, isResumable = resumable)

        assertEquals(
            listOf("IsPlayed"),
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.PLAYED), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
        assertEquals(
            listOf("IsUnplayed"),
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.UNPLAYED), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
        assertNull(
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.ALL), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
        // IsResumable appends after the status token and stands alone when
        // the status is ALL; an explicit `false` is a stored "off" (no filter).
        assertEquals(
            listOf("IsPlayed", "IsResumable"),
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.PLAYED, resumable = true), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
        assertEquals(
            listOf("IsResumable"),
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.ALL, resumable = true), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
        assertNull(
            buildMediaItemsQuerySpec("p", filters(PlayedStatus.ALL, resumable = false), null, 0, 10, null, ItemKindFilter())
                .itemFilters,
        )
    }

    @Test
    fun `sort order normalizes case-insensitively with an ascending fallback`() {
        fun orderOf(option: SortOption) = buildMediaItemsQuerySpec(
            parentId = null,
            filters = LibraryFilters(sortBy = option),
            studioIds = null,
            startIndex = 0,
            limit = 10,
            searchTerm = null,
            kindFilter = ItemKindFilter(),
        ).sortOrderDescending

        assertEquals(true, orderOf(SortOption.YEAR_DESC))
        assertEquals(false, orderOf(SortOption.SORT_NAME))
        assertEquals(false, orderOf(SortOption.ALBUM))
    }

    @Test
    fun `media types map to include kinds and prune the excludes they cover`() {
        fun spec(filters: LibraryFilters, kindFilter: ItemKindFilter = ItemKindFilter()) =
            buildMediaItemsQuerySpec(null, filters, null, 0, 10, null, kindFilter)

        // Season explicitly included → only Episode excluded; with episodes
        // included too the exclude list empties and the param is omitted
        // (wire-identical on both adapters: the SDK's UrlBuilder emits zero
        // params for an empty collection, wasm drops nulls).
        val withSeason = spec(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE, MediaType.SEASON)))
        assertEquals(listOf("Movie", "Season"), withSeason.includeKinds)
        assertEquals(listOf("Episode"), withSeason.excludeKinds)
        val withEpisodes = spec(
            LibraryFilters(mediaTypes = listOf(MediaType.SEASON, MediaType.EPISODE)),
            kindFilter = ItemKindFilter(includeEpisodes = true),
        )
        assertEquals(listOf("Season", "Episode"), withEpisodes.includeKinds)
        assertNull(withEpisodes.excludeKinds)

        // includeEpisodes keeps Episode out of the excludes without an
        // explicit include (the section-mode shape).
        val episodesAllowed = spec(LibraryFilters(mediaTypes = listOf(MediaType.SERIES)), ItemKindFilter(includeEpisodes = true))
        assertEquals(listOf("Season"), episodesAllowed.excludeKinds)

        // UNKNOWN maps to null ("do not constrain") and an all-UNKNOWN input
        // normalizes to an omitted param.
        assertNull(spec(LibraryFilters(mediaTypes = listOf(MediaType.UNKNOWN))).includeKinds)
        // MUSIC folds to "Audio" (toWireItemKind's contract).
        assertEquals(
            listOf("Audio"),
            spec(LibraryFilters(mediaTypes = listOf(MediaType.MUSIC, MediaType.UNKNOWN))).includeKinds,
        )
    }

    @Test
    fun `name dimensions pass through non-empty and the rating floor drops zero`() {
        val spec = buildMediaItemsQuerySpec(
            parentId = "lib-1",
            filters = LibraryFilters(
                genres = listOf("Sci-Fi", "Drama"),
                years = listOf(1982, 2017),
                tags = listOf("sync"),
                minRating = 7.5f,
            ),
            studioIds = listOf("studio-a", "studio-b"),
            startIndex = 24,
            limit = 24,
            searchTerm = "blade",
            kindFilter = ItemKindFilter(),
        )
        assertEquals(listOf("Sci-Fi", "Drama"), spec.genres)
        assertEquals(listOf(1982, 2017), spec.years)
        assertEquals(listOf("sync"), spec.tags)
        assertEquals(listOf("studio-a", "studio-b"), spec.studioIds)
        assertEquals(7.5, spec.minCommunityRating)
        assertEquals(24, spec.startIndex)
        assertEquals(24, spec.limit)
        assertEquals("blade", spec.searchTerm)

        // The empty/off corners: every dimension omits its parameter.
        val off = buildMediaItemsQuerySpec(
            parentId = null,
            filters = LibraryFilters(genres = emptyList(), years = emptyList(), minRating = 0f),
            studioIds = emptyList(),
            startIndex = 0,
            limit = 10,
            searchTerm = "   ",
            kindFilter = ItemKindFilter(),
        )
        assertNull(off.genres)
        assertNull(off.years)
        assertNull(off.minCommunityRating)
        assertNull(off.studioIds)
        assertNull(off.searchTerm)
    }

    @Test
    fun `an unparseable sort key omits sortBy rather than sending junk`() {
        val spec = buildMediaItemsQuerySpec(
            parentId = null,
            filters = LibraryFilters(sortBy = SortOption.RANDOM), // "Random" parses
            studioIds = null,
            startIndex = 0,
            limit = 10,
            searchTerm = null,
            kindFilter = ItemKindFilter(),
        )
        assertEquals(listOf("Random"), spec.sortBy)

        // parseItemSortList drops unknown tokens; an all-unknown compound key
        // therefore produces no sortBy param at all (both adapters omit it).
        assertEquals(
            emptyList(),
            parseItemSortList("NotARealSortKey,AlsoFake"),
        )
    }

    // ── the list-shaped drill-downs ───────────────────────────────────────

    @Test
    fun `search hints pass the term through verbatim and normalize kinds`() {
        val spec = buildSearchHintsQuerySpec(
            query = "blade",
            mediaTypes = listOf(MediaType.MOVIE, MediaType.UNKNOWN),
            limit = 12,
            startIndex = 0,
        )
        assertEquals("blade", spec.searchTerm)
        assertEquals(listOf("Movie"), spec.includeKinds)
        assertEquals(12, spec.limit)
        assertEquals(0, spec.startIndex)
        assertEquals(true, spec.recursive)
        assertEquals(listOf("Overview", "PrimaryImageAspectRatio"), spec.fields)
        assertNull(spec.parentId)
        assertNull(spec.itemFilters)

        // null and empty media types both omit the param; a blank term is
        // still sent verbatim (the caller decides blank-gating — getMediaItems
        // blanks, search does not).
        assertNull(buildSearchHintsQuerySpec("x", null, 5, 0).includeKinds)
        assertNull(buildSearchHintsQuerySpec("x", emptyList(), 5, 0).includeKinds)
        assertEquals("", buildSearchHintsQuerySpec("", null, 5, 0).searchTerm)
    }

    @Test
    fun `favorites pin the IsFavorite filter over optionally narrowed kinds`() {
        val spec = buildFavoritesQuerySpec(
            mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES),
            limit = 30,
            startIndex = 60,
        )
        assertEquals(listOf("IsFavorite"), spec.itemFilters)
        assertEquals(listOf("Movie", "Series"), spec.includeKinds)
        assertEquals(30, spec.limit)
        assertEquals(60, spec.startIndex)
        assertEquals(true, spec.recursive)
        assertEquals(listOf("Overview", "PrimaryImageAspectRatio"), spec.fields)
        assertNull(buildFavoritesQuerySpec(null, 30, 0).includeKinds)
    }

    @Test
    fun `the genre drill-down targets genreIds and projects no fields`() {
        val spec = buildItemsByGenreQuerySpec(
            genreId = "genre-7",
            mediaTypes = listOf(MediaType.MOVIE),
            startIndex = 3,
            limit = 9,
        )
        assertEquals(listOf("genre-7"), spec.genreIds)
        assertEquals(listOf("Movie"), spec.includeKinds)
        assertEquals(3, spec.startIndex)
        assertEquals(9, spec.limit)
        assertEquals(true, spec.recursive)
        // Pre-spec shape kept: the genre drill-down sends NO fields param.
        assertNull(spec.fields)
    }

    @Test
    fun `the studio drill-down targets studioIds with the list projection`() {
        val spec = buildItemsByStudioQuerySpec(
            studioId = "studio-9",
            mediaTypes = null,
            startIndex = 1,
            limit = 2,
        )
        assertEquals(listOf("studio-9"), spec.studioIds)
        assertNull(spec.includeKinds)
        assertEquals(1, spec.startIndex)
        assertEquals(2, spec.limit)
        assertEquals(listOf("Overview", "PrimaryImageAspectRatio"), spec.fields)
    }
}
