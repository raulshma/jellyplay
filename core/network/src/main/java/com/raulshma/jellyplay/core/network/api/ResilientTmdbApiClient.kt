package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient wrapper around [TmdbApiClientImpl] that applies [RetryPolicy] to every
 * [TmdbApiClient] method.
 *
 * **Note:** This class deliberately implements [TmdbApiClient] directly (rather than using
 * Kotlin interface delegation `by delegate`) so that adding a new method to the interface
 * produces a compile error here, forcing the author to wire it through [req]. This mirrors
 * the discipline enforced by [com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient]
 * and [com.raulshma.jellyplay.core.network.arr.ResilientRadarrApiClient].
 */
@Singleton
class ResilientTmdbApiClient @Inject constructor(
    private val delegate: TmdbApiClientImpl,
) : TmdbApiClient {

    private suspend fun <T> req(block: suspend () -> Result<T>): Result<T> =
        RetryPolicy.executeWithRetry(
            maxRetries = MAX_RETRIES,
            block = block,
        )

    override suspend fun getVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> =
        req { delegate.getVideos(tmdbId, mediaType) }

    companion object {
        internal const val MAX_RETRIES = 4
    }
}
