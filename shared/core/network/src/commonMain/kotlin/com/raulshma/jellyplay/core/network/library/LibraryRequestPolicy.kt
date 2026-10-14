package com.raulshma.jellyplay.core.network.library

/**
 * Request-level policies for [com.raulshma.jellyplay.core.network.api.LibraryApiClient]
 * (the jvmShared SDK client),
 * extracted from the hand-copied twins the same way [HomeSectionsFetcher]
 * extracted the home-feed choreography and [EmptyLibraryFallback] /
 * [FavoriteFlagCache] (own files) extracted the response-side ladders.
 * Everything here is wire-level (string serial names, raw item lists) so
 * commonMain can hold it; the JVM client resolves the wire names against the
 * SDK enums it sends, and each client keeps only its transport calls and its
 * platform thread-safety regime.
 */

/**
 * Fields the detail mapper reads from the item DTO — the single 12-field
 * projection both clients request for `getMediaDetail` (wire serial names;
 * the JVM client resolves them against the SDK [org.jellyfin.sdk.model.api.ItemFields]
 * enum). Projected explicitly because the plain GET /Items/{id} read returns
 * several of these (notably Trickplay, used for scrub preview and download)
 * null without an explicit request. "Path" is projected for the book detail
 * surface: books have no MediaSources, so Path is the only carrier of their
 * file format.
 */
internal val DETAIL_PROJECTION_FIELDS: List<String> = listOf(
    "People", "Chapters", "MediaSources", "Trickplay", "ExternalUrls",
    "OriginalTitle", "ProductionLocations", "Studios", "Genres", "Overview",
    "ProviderIds", "PrimaryImageAspectRatio", "Path", "Taglines",
)

/**
 * Fields every library LIST projection requests — the two-field minimum both
 * clients attach to their list-shaped queries (latest media, next up, resume,
 * search hints, studio/artist/album/person/collection/favorite/playlist
 * listings; wire serial names; the JVM client resolves them against the SDK
 * [org.jellyfin.sdk.model.api.ItemFields] enum). The genre-rendering queries (the library grid and the empty-library
 * fallback's latest probe) and the playlists listing compose on top of it
 * (`LIST_PROJECTION_FIELDS + "Genres"` /
 * `LIST_PROJECTION_FIELDS + listOf("CanDelete", "DateCreated")`); documented
 * one-offs that project something else (CHILD_COUNT for collection summaries,
 * TAGS for the tag index, the photo grid's aspect-ratio-only projection) stay
 * at their call sites.
 */
internal val LIST_PROJECTION_FIELDS: List<String> = listOf(
    "Overview", "PrimaryImageAspectRatio",
)

/**
 * The home-row composition on top of the list minimum — the projection the
 * home cards' fetches (the `/UserItems/Resume` rows, Next Up, Upcoming and
 * the `/Items/Latest` rows) request so the card overlays can render from the
 * same list read: "MediaStreams" feeds the quality badges (4K/HDR/DV/Atmos
 * derived client-side), "DateCreated" the NEW-arrival dot, "Trickplay" the
 * focus preview strip (a legal item field, same as on
 * [DETAIL_PROJECTION_FIELDS]). Payload stays bounded by the rows' small
 * limits (~20), which is why this extends only the home rows and not every
 * [LIST_PROJECTION_FIELDS] consumer (search hints, collection/playlist
 * listings, … keep the two-field minimum).
 */
internal val HOME_ROW_PROJECTION_FIELDS: List<String> = LIST_PROJECTION_FIELDS + listOf(
    "MediaStreams", "DateCreated", "Trickplay",
)

/**
 * The jellyfin-web `useSearchSuggestions` query shape: getItems sorted by
 * IsFavoriteOrLiked,Random over Movies, Series and MusicArtists, projecting
 * the poster/genre fields (wire serial names; the JVM client resolves them
 * against the SDK enums). Unlike the web client (which disables images for
 * the cheap empty state) both app clients keep images on so the suggestions
 * render as poster cards matching the rest of the app's design language.
 */
internal val SEARCH_SUGGESTIONS_SORT_BY = listOf("IsFavoriteOrLiked", "Random")
internal val SEARCH_SUGGESTIONS_ITEM_TYPES = listOf("Movie", "Series", "MusicArtist")
internal val SEARCH_SUGGESTIONS_FIELDS = listOf("PrimaryImageAspectRatio", "Genres")

/**
 * includeItemTypes / excludeItemTypes policy for [getMediaItems]-style
 * queries: drop [seasonKind] / [episodeKind] from the exclude list when they
 * were explicitly included. Jellyfin would otherwise receive contradictory
 * include+exclude for the same kind (e.g. section mode for a TV library
 * includes EPISODE to match /Items/Latest) and return an empty result.
 * Generic over the kind token — the JVM client speaks SDK
 * [org.jellyfin.sdk.model.api.BaseItemKind] enums.
 */
internal fun <K> libraryExcludeKinds(
    seasonKind: K,
    episodeKind: K,
    includeKinds: Collection<K>,
    includeEpisodes: Boolean,
): List<K> = buildList {
    if (seasonKind !in includeKinds) add(seasonKind)
    if (!includeEpisodes && episodeKind !in includeKinds) add(episodeKind)
}
