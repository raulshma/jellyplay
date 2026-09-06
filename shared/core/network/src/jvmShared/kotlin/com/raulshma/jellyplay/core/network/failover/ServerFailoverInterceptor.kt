package com.raulshma.jellyplay.core.network.failover

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Transparent primary/alternate address failover for every request that
 * targets a known endpoint of the active Jellyfin server.
 *
 * All app traffic — SDK REST calls, Coil images, ExoPlayer streams, downloads
 * and the realtime WebSocket — flows through clients derived from the shared
 * [okhttp3.OkHttpClient], so installing this interceptor there covers every
 * path, including absolute URLs string-built against the primary address.
 *
 * Behavior for a request targeting a known endpoint:
 *  1. rewrite it onto the router's active endpoint (usually a no-op);
 *  2. on a connection-establishment failure ([ConnectException] /
 *     [UnknownHostException] anywhere in the cause chain), try the remaining
 *     endpoints in preference order (primary first);
 *  3. when a candidate answers, mark it active so subsequent requests go
 *     straight there (the sticky switch is also what [ServerAddressRouter]
 *     re-selection reverses once the primary is reachable again).
 *
 * Requests to anything else (GitHub, TMDB, Seerr, lyric providers, …) pass
 * through untouched.
 */
class ServerFailoverInterceptor(
    private val router: ServerAddressRouter,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val candidates = router.failoverCandidates(request.url)
        if (candidates.isEmpty()) {
            return chain.proceed(request)
        }

        var lastError: IOException? = null
        for (candidate in candidates) {
            val candidateRequest = if (candidate == request.url) {
                request
            } else {
                request.newBuilder().url(candidate).build()
            }
            try {
                val response = chain.proceed(candidateRequest)
                router.markActive(candidate)
                return response
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                if (!isConnectFailure(e)) throw e
                lastError = e
            }
        }
        throw lastError ?: IOException("All server endpoints unreachable")
    }

    /**
     * Only failures that mean "could not establish a connection to this
     * endpoint" justify trying another address. A read timeout in the middle
     * of a valid transfer (slow network, large stream) must NOT churn the
     * endpoint, so [java.net.SocketTimeoutException] is deliberately excluded;
     * router re-selection covers black-holed addresses with its short probe
     * timeouts.
     */
    private fun isConnectFailure(e: IOException): Boolean =
        generateSequence(e as Throwable) { it.cause }.take(8).any {
            it is ConnectException || it is UnknownHostException
        }
}
