package com.raulshma.jellyplay.core.network.library

/**
 * Request-level policies shared by BOTH [com.raulshma.jellyplay.core.network.api.LibraryApiClient]
 * implementations (the jvmShared SDK client and the wasmJs Ktor client),
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
 * null without an explicit request.
 */
internal val DETAIL_PROJECTION_FIELDS: List<String> = listOf(
    "People", "Chapters", "MediaSources", "Trickplay", "ExternalUrls",
    "OriginalTitle", "ProductionLocations", "Studios", "Genres", "Overview",
    "ProviderIds", "PrimaryImageAspectRatio",
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
 * Generic over the kind token because the JVM client speaks SDK
 * [org.jellyfin.sdk.model.api.BaseItemKind] enums and the wasm client speaks
 * their wire serial names — the POLICY is identical.
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
