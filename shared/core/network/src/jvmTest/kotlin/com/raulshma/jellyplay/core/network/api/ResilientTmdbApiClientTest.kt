package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the retry contract of [ResilientTmdbApiClient] over a stubbed
 * [TmdbApiClientImpl] delegate:
 *  1. a retryable failure (ApiException.isRetryable, e.g. TMDB 5xx) is retried
 *     and the first success wins;
 *  2. a non-retryable failure (parse error / 4xx) surfaces after exactly one
 *     delegate call;
 *  3. retry exhaustion stops at [ResilientTmdbApiClient.MAX_RETRIES] extra
 *     attempts;
 *  4. both [TmdbApiClient] methods stay explicitly overridden so a new
 *     interface addition cannot silently bypass retry.
 */
class ResilientTmdbApiClientTest {

    private val delegate = mockk<TmdbApiClientImpl>()
    private val resilient = ResilientTmdbApiClient(delegate)

    @Test
    fun `retries a retryable failure and returns the first success`() = runTest {
        val videos = listOf(SeerrRelatedVideoFixture.youtube())
        coEvery { delegate.getVideos(any(), any()) } returnsMany listOf(
            Result.failure(ApiException(isRetryable = true, message = "HTTP 503: Service Unavailable")),
            Result.success(videos),
        )

        val result = resilient.getVideos(tmdbId = 550, mediaType = MediaType.MOVIE)

        assertTrue(result.isSuccess)
        assertEquals(videos, result.getOrThrow())
        coVerify(exactly = 2) { delegate.getVideos(any(), any()) }
    }

    @Test
    fun `does not retry a non-retryable failure`() = runTest {
        coEvery { delegate.getReviews(any(), any()) } returns
            Result.failure(ApiException(isRetryable = false, message = "TMDB parse error: bad payload"))

        val result = resilient.getReviews(tmdbId = 550, mediaType = MediaType.MOVIE)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { delegate.getReviews(any(), any()) }
    }

    @Test
    fun `exhausts MAX_RETRIES extra attempts before failing`() = runTest {
        coEvery { delegate.getVideos(any(), any()) } returns
            Result.failure(ApiException(isRetryable = true, message = "HTTP 500: Internal Server Error"))

        val result = resilient.getVideos(tmdbId = 42, mediaType = MediaType.SERIES)

        assertTrue(result.isFailure)
        coVerify(exactly = ResilientTmdbApiClient.MAX_RETRIES + 1) { delegate.getVideos(any(), any()) }
    }

    @Test
    fun `every TmdbApiClient method is overridden by the resilient wrapper`() {
        val interfaceMethods = TmdbApiClient::class.java.declaredMethods
            .filter { !it.isSynthetic && !it.name.contains('$') }
        val overridden = ResilientTmdbApiClient::class.java.declaredMethods
            .map { it.name }
            .toSet()

        val missing = interfaceMethods.map { it.name }.filter { it !in overridden }

        assertTrue(
            missing.isEmpty(),
            "Missing retrying overrides for: ${missing.joinToString(", ")}",
        )
    }
}

private object SeerrRelatedVideoFixture {
    fun youtube() = com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo(
        key = "abc123",
        name = "Trailer",
        size = 1080,
        type = "Trailer",
        site = "YouTube",
        url = "https://www.youtube.com/watch?v=abc123",
    )
}
