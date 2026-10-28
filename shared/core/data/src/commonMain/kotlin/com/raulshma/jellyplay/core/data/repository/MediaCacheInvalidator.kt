package com.raulshma.jellyplay.core.data.repository

/**
 * Narrow port for the wholesale cache drop
 * ([MediaRepositoryImpl.invalidateCaches] — no repository family carries
 * it). The background sync workers (legacy
 * core:data — `UserDataSyncWorker` / `PlaybackSyncWorker`) used to
 * ctor-inject the concrete [MediaRepositoryImpl] just to reach that one
 * method; they now inject their family seams for their real reads plus this
 * port for the drop, so nothing outside the shared module names the impl.
 *
 * Bound to the same impl single as every repository family — one repo, one
 * set of caches behind all the seams (the
 * [MediaRepositoryCacheInvalidation] precedent).
 */
fun interface MediaCacheInvalidator {

    /** Drops every in-memory cache the media repository owns (wholesale). */
    suspend fun invalidateCaches()
}
