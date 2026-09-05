package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache

/**
 * Request-level policies shared by BOTH [com.raulshma.jellyplay.core.network.api.LibraryApiClient]
 * implementations (the jvmShared SDK client and the wasmJs Ktor client),
 * extracted from the hand-copied twins the same way [HomeSectionsFetcher]
 * extracted the home-feed choreography. Everything here is wire-level (string
 * serial names, raw item lists) so commonMain can hold it; the JVM client
 * resolves the wire names against the SDK enums it sends, and each client
 * keeps only its transport calls and its platform thread-safety regime.
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

/**
 * The empty-library fallback ladder shared by both library clients: when the
 * primary /Items query returns nothing for an unfiltered browse (no search
 * term), try /Items/Latest before declaring the library empty, and memoise
 * libraries where BOTH queries returned nothing so a repeat visit pays a
 * single request instead of the doubled round trip.
 *
 * The memo itself stays a per-client port (the two constructor lambdas)
 * because the thread-safety regimes genuinely differ: the JVM client runs
 * inside the engine's Dispatchers.IO block and synchronizes its access-order
 * LRU; the single-threaded wasm event loop needs no lock but emulates
 * access-order by remove+reinsert. The DECISION ladder — skip the doubled
 * request for known-empty libraries, coerce the fallback limit, remember only
 * genuinely-empty libraries — lives here, once.
 */
internal class EmptyLibraryFallback<Item>(
    /** Memo probe: true when the library is known to fall back to nothing. */
    private val isKnownEmpty: (String) -> Boolean,
    /** Memo write: the primary query AND the fallback both returned nothing. */
    private val rememberEmpty: (String) -> Unit,
    /** The /Items/Latest transport call each client performs. */
    private val fetchLatest: suspend (parentId: String, limit: Int) -> List<Item>,
) {
    /**
     * Raw items for a getMediaItems response: [primaryItems] when non-empty
     * (or when the query is not an unfiltered browse of one folder), else the
     * fallback ladder. Bounded-LRU note: once the library gains content the
     * primary query returns items and the fallback is never reached, so the
     * memo cannot serve a stale non-empty state.
     */
    suspend fun resolve(
        primaryItems: List<Item>,
        parentId: String?,
        searchTerm: String?,
        limit: Int,
    ): List<Item> {
        if (primaryItems.isNotEmpty() || parentId == null || !searchTerm.isNullOrBlank()) return primaryItems
        if (isKnownEmpty(parentId)) return emptyList()
        val fallback = runCatching { fetchLatest(parentId, if (limit > 0) limit else 50) }
            .getOrNull() ?: emptyList()
        if (fallback.isEmpty()) rememberEmpty(parentId)
        return fallback
    }
}

/**
 * totalRecordCount derivation when the empty-library fallback served the
 * items: the server's count describes the (empty) primary query, so the
 * fallback batch size is the honest total; otherwise the server count stands.
 */
internal fun emptyFallbackTotalCount(primaryCount: Int, resolvedCount: Int, serverTotal: Int): Int =
    if (primaryCount == 0 && resolvedCount > 0) resolvedCount else serverTotal

/** LRU bound of the favorite-flag cache (the old `LruCache(200)` size). */
internal const val FAVORITE_CACHE_MAX_ENTRIES = 200

/**
 * TTL of the favorite-flag cache — generous (the flags only seed a toggle's
 * "current" value until the first real read refreshes them), and the
 * identity-keyed composite key already guarantees a switched user never sees
 * the previous user's flags within any window.
 */
internal const val FAVORITE_CACHE_TTL_MS = 15 * 60_000L

/**
 * The favorite-flag cache-aside choreography shared by both library clients:
 * seed the "current" flag from the identity-keyed [TtlCache] (a user/server
 * switch misses by construction), fall back to ONE item read when the caller
 * supplies no guess and nothing is cached, then write the flipped value back
 * after the transport mutation succeeds. Parameterized by the transport calls
 * each platform performs (SDK typed calls on the JVM; raw POST/DELETE
 * /UserFavoriteItems on wasm) — the read→maybe-fetch→mutate→write-through
 * POLICY lives here once. The cache key derivation stays with each client
 * (the JVM normalises via `UUID.toString()`, wasm keeps the raw item id) so
 * each platform's keys stay byte-identical to its historical ones.
 */
internal class FavoriteFlagCache(
    private val currentIdentity: () -> CacheIdentity,
) {
    private val cache = TtlCache<Boolean>(
        maxSize = FAVORITE_CACHE_MAX_ENTRIES,
        ttlMs = FAVORITE_CACHE_TTL_MS,
    )

    /**
     * The toggle ladder: returns the NEW flag. [fetchCurrent] runs only on a
     * total cache miss with no caller guess; [markOnServer] /
     * [unmarkOnServer] run exactly once, and the resolved value is written
     * through only after the mutation returns.
     */
    suspend fun toggle(
        cacheKey: String,
        currentIsFavorite: Boolean?,
        fetchCurrent: suspend () -> Boolean,
        markOnServer: suspend () -> Unit,
        unmarkOnServer: suspend () -> Unit,
    ): Boolean {
        val identity = currentIdentity()
        val cached = cache.get(identity, cacheKey)
        val isFavorite = currentIsFavorite ?: cached ?: run {
            val fetched = fetchCurrent()
            cache.put(identity, cacheKey, fetched)
            fetched
        }
        return if (isFavorite) {
            unmarkOnServer()
            cache.put(identity, cacheKey, false)
            false
        } else {
            markOnServer()
            cache.put(identity, cacheKey, true)
            true
        }
    }

    /** Write-through for the non-toggle [setFavorite] path (identity read at call time). */
    fun put(cacheKey: String, isFavorite: Boolean) {
        cache.put(currentIdentity(), cacheKey, isFavorite)
    }
}
