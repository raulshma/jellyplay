package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio

interface LibraryApiClient {
    suspend fun getHomeSections(
        query: HomeSectionQuery = HomeSectionQuery(),
        /**
         * Bypasses the short-TTL latest-media / similar-items sub-call caches
         * for this read (manual refresh / pull-to-refresh): the underlying
         * `/Items/Latest` and `/Items/Similar` fan-outs re-hit the server, so
         * a forced refresh now refreshes those rows too instead of only the
         * Continue Watching / Next Up sections.
         */
        force: Boolean = false,
    ): Result<HomeSectionsResult>

    // The home hot-path cache-maintenance verbs (invalidateHomeSubcallCaches /
    // invalidateDiscoverRowCache / seedDiscoverRowCache) left this interface:
    // cache-management vocabulary does not belong on a network API surface, and
    // their one consumer (the data layer's write/roll paths) reaches them
    // through the narrow [com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort]
    // instead.

    suspend fun getLatestMedia(
        parentId: String,
        limit: Int = 16,
        /**
         * Server-side `IncludeItemTypes` narrowing for the `/Items/Latest`
         * call (wire serial names, e.g. `["Series"]`). Null = unconstrained —
         * the server decides, which on Jellyfin 12.x means a mix of Series,
         * Season and Episode rows (#168). The home fetcher passes the classic-
         * rows narrowing per folder; other callers stay unconstrained.
         */
        includeKinds: List<String>? = null,
    ): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int = 20, enableRewatching: Boolean = false, maxDays: Int = 0): Result<List<MediaItem>>
    suspend fun getContinueWatching(
        limit: Int = 20,
        /**
         * Server-side `IncludeItemTypes` narrowing for the video resume query
         * (wire serial names, e.g. `["Episode","Movie"]`). Null = unconstrained —
         * the server decides, which on Jellyfin 12.x means Series/Season resume
         * rollups ride the row (#168). The client-side played-row/kind fold in
         * [com.raulshma.jellyplay.core.network.library.resumableOnly] still
         * applies on top.
         */
        includeKinds: List<String>? = null,
    ): Result<List<MediaItem>>

    /**
     * The books half of the resume query (`/UserItems/Resume` narrowed to
     * `IncludeItemTypes=Book`): in-progress books for the home Continue
     * Reading section. The client-side [readingResumableOnly] filter applies
     * the same played-row rule as [getContinueWatching]. Empty when nothing is
     * mid-read — a legitimately empty row, not a failure.
     */
    suspend fun getContinueReading(limit: Int = 20): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

    suspend fun getMediaItems(
        parentId: String? = null,
        /**
         * Bundles the filter/sort dimensions that always travel together
         * (mediaTypes, genres, years, tags, [com.raulshma.jellyplay.core.model.SortOption],
         * [com.raulshma.jellyplay.core.model.PlayedStatus], minRating, isResumable).
         * Replaces the long primitive parameter list so adding a dimension is a
         * single field on [LibraryFilters] instead of a signature edit here.
         * Defaults to the library landing sort (newest first, no filters).
         */
        filters: LibraryFilters = LibraryFilters(),
        studioIds: List<String>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        searchTerm: String? = null,
        /**
         * Controls which nested media kinds the query excludes. Library browsing
         * and section mode ("See All" from a home Latest row) both use the
         * default [com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL]
         * (seasons and episodes excluded), differing only in sort order.
         */
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL,
    ): Result<SearchResult>

    /**
     * Fetches one user-configured Discover row's items (JELLYFIN source): the
     * shared [LibraryFilters] dimensions plus the row's discover-only
     * dimensions (studios, people, relative date windows), scoped to
     * [DiscoverRowConfig.libraryIds] — empty scope runs ONE catalog-wide
     * query, otherwise one query per library merged, de-duplicated by id and
     * capped at the row's limit. A library that errors is skipped, not fatal
     * (same degrade policy as pinned sections). Always hits the server: the
     * fetch-side per-row TTL cache that keeps RANDOM rows stable across the
     * periodic refresh is consulted only by the home-sections path.
     */
    suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>>

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    /**
     * Fetch intros/trailers configured by the Jellyfin Cinema Mode intros plugin
     * for the given item. Returns an empty list when cinema mode is disabled or
     * no intros are configured server-side.
     */
    suspend fun getIntros(itemId: String): Result<List<MediaItem>>

    /**
     * Fetch special features / extras (featurettes, deleted scenes, interviews,
     * etc.) attached to the given item via Jellyfin's `/Items/{id}/SpecialFeatures`
     * endpoint. Returns an empty list when the item has no extras. Remote-only —
     * the endpoint is server-side and the result is filtered by parental rating.
     */
    suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>>

    suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    /**
     * Discovery suggestions for the empty search state — favorited/liked items
     * surfaced in random order. Mirrors the official jellyfin-web behavior
     * (getItems sorted by `IsFavoriteOrLiked, Random`). Returned items are
     * navigable: clicking opens the item's detail page rather than filling
     * the search box.
     */
    suspend fun getSearchSuggestions(limit: Int = 20): Result<SearchResult>

    /**
     * Resolves a library item by a provider (external) id, e.g. `tmdb`, `tvdb`,
     * or `imdb`. Uses Jellyfin's `AnyProviderId` filter ("tmdb:123"). Returns the
     * first matching Jellyfin item id, or null when no match exists. Used to open
     * a Seerr "Available" item directly in the library.
     */
    suspend fun findItemByProviderId(provider: String, id: String): Result<String?>

    suspend fun getGenres(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<Genre>>

    suspend fun getItemsByGenre(
        genreId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getStudios(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<Studio>>

    /**
     * Cast/crew person lookup for the discover-row editor's People picker —
     * the `/Persons` endpoint narrowed by an optional [searchTerm]. Returns
     * the id+name pair only: persons have no MediaItem surface (the item
     * mappers drop them), and the picker needs nothing else.
     */
    suspend fun getPeople(
        searchTerm: String? = null,
        limit: Int = 50,
    ): Result<List<PersonRef>>

    suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>
    suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>>
    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>
    suspend fun getInstantMix(itemId: String, limit: Int = 100): Result<List<MediaItem>>
    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>
    suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>>
    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>

    /**
     * Episodes of one season. [isMissing] is Jellyfin's `isMissing` filter,
     * mapped jellyfin-web style: `false` hides the server's virtual (missing /
     * unaired) episode placeholders; null omits the filter so they come back
     * and render as placeholders. Hide is the sensible default.
     */
    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String,
        isMissing: Boolean? = false,
    ): Result<List<MediaItem>>

    /**
     * Fetches every episode for a series in a single round-trip. The Jellyfin
     * `/Shows/{seriesId}/Episodes` endpoint returns the full set when
     * `seasonId` is omitted, which collapses an N-season fan-out (one request
     * per season) into a single call. Callers that need per-season grouping
     * can `groupBy { it.seasonId }` the result locally. [isMissing] behaves
     * exactly as on [getEpisodes].
     */
    suspend fun getAllEpisodes(seriesId: String, isMissing: Boolean? = false): Result<List<MediaItem>>

    suspend fun getTags(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<String>>

    suspend fun getFavorites(
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    suspend fun getLyrics(itemId: String): Result<LyricsResult>

    // The playlist ×8 and collection ×4 members left for the
    // [PlaylistApiClient] / [CollectionApiClient] family seams (the
    // one-impl-many-seams idiom: [LibraryApiClientImpl] implements all three;
    // the JellyfinApiClient union carries them; consumers narrow to the seam
    // they read).

    /**
     * The ONE user-data write member — the parameterized fold of the four
     * former write verbs over [UserDataWrite]; the outcome carries the
     * post-toggle favorite state ([UserDataWriteOutcome.FavoriteNow]) so the
     * single member serves the flip and the replay funnels alike.
     */
    suspend fun writeUserData(write: UserDataWrite): Result<UserDataWriteOutcome>

    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        maxWidth: Int? = 400,
        imageIndex: Int? = null,
        tag: String? = null,
    ): String

    fun getBackdropImageUrl(
        itemId: String,
        maxWidth: Int = 1280,
        tag: String? = null,
    ): String

    suspend fun getChildItemImageUrls(
        parentId: String,
        limit: Int = 4,
    ): List<String>
}
