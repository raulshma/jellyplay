package com.raulshma.jellyplay.core.data.repository

/**
 * Narrow port for the wholesale cache drop
 * ([MediaRepositoryImpl.invalidateCaches] — plan 08 demoted it off the public
 * [MediaRepository] interface). The background sync workers (legacy
 * :core:data — `UserDataSyncWorker` / `PlaybackSyncWorker`) used to
 * ctor-inject the concrete [MediaRepositoryImpl] just to reach that one
 * method; they now inject [MediaRepository] for their real reads plus this
 * seam for the drop, so nothing outside the shared module names the impl.
 *
 * Bound in [com.raulshma.jellyplay.core.data.di.DataKoinModule] to the same
 * singleton as [MediaRepository] — one repo, one set of caches behind both
 * types (the [MediaRepositoryCacheInvalidation] precedent).
 */
fun interface MediaCacheInvalidator {

    /** Drops every in-memory cache the media repository owns (wholesale). */
    suspend fun invalidateCaches()
}
