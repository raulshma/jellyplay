package com.raulshma.jellyplay.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Forces the network for RANDOM-sorted `/Items` catalog queries (the custom
 * Discover rows' dice re-roll, the library screen's Random sort).
 *
 * The base client carries an [okhttp3.Cache], and Jellyfin's `/Items`
 * responses advertise caching headers. A RANDOM query is byte-identical every
 * time — the shuffle happens server-side, with no nonce in the URL — so
 * within the response's freshness window a re-roll would be served the
 * cached body: same items, same order, a dice tap that "does nothing" until
 * the entry went stale (or the periodic home refresh re-queried first).
 * `Cache-Control: no-cache` on the request forces OkHttp to skip fresh-cache
 * serving; Jellyfin's /Items has no conditional validators, so that is a full
 * refetch — a genuinely new shuffle. Non-Random queries are untouched (their
 * responses are deterministic, and caching them is the point of the cache).
 */
class RandomSortCacheBusterInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val isRandomSortedItemsQuery =
            request.url.encodedPath.endsWith("/Items") &&
                request.url.queryParameter("sortBy")?.contains(RANDOM, ignoreCase = true) == true
        if (!isRandomSortedItemsQuery) return chain.proceed(request)
        val busting = request.newBuilder()
            .header("Cache-Control", "no-cache")
            .build()
        return chain.proceed(busting)
    }

    private companion object {
        /** Jellyfin's serial name for [org.jellyfin.sdk.model.api.ItemSortBy.Random]. */
        private const val RANDOM = "Random"
    }
}
