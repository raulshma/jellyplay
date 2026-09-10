package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache

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
