package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the retry contract of [ResilientRadarrApiClient] over a stubbed
 * [RadarrApiClientImpl] delegate:
 *  1. a retryable failure (ApiException.isRetryable, e.g. HTTP 503) is retried
 *     and the first success is returned;
 *  2. a non-retryable failure (HTTP 401) surfaces after exactly ONE delegate
 *     call — retrying a bad key just burns time;
 *  3. retry exhaustion stops at [ResilientRadarrApiClient.MAX_RETRIES] extra
 *     attempts;
 *  4. every [RadarrApiClient] method is explicitly overridden (the class
 *     deliberately avoids `by delegate` so a new interface method cannot
 *     silently bypass retry — this test guards against a future default body
 *     reintroducing that hole).
 */
class ResilientRadarrApiClientTest {

    private val delegate = mockk<RadarrApiClientImpl>()
    private val resilient = ResilientRadarrApiClient(delegate)

    @Test
    fun `retries a retryable failure and returns the first success`() = runTest {
        coEvery { delegate.testConnection(any(), any()) } returnsMany listOf(
            Result.failure(ApiException(isRetryable = true, message = "HTTP 503: Service Unavailable")),
            Result.success(Unit),
        )

        val result = resilient.testConnection("http://radarr", "key")

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { delegate.testConnection(any(), any()) }
    }

    @Test
    fun `does not retry a non-retryable failure`() = runTest {
        coEvery { delegate.getQueue(any(), any()) } returns
            Result.failure(ApiException(isRetryable = false, message = "HTTP 401: Unauthorized"))

        val result = resilient.getQueue("http://radarr", "badkey")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { delegate.getQueue(any(), any()) }
    }

    @Test
    fun `exhausts MAX_RETRIES extra attempts before failing`() = runTest {
        coEvery { delegate.getBlocklist(any(), any(), any(), any()) } returns
            Result.failure(ApiException(isRetryable = true, message = "HTTP 500: Internal Server Error"))

        val result = resilient.getBlocklist("http://radarr", "key", page = 0, pageSize = 50)

        assertTrue(result.isFailure)
        coVerify(exactly = ResilientRadarrApiClient.MAX_RETRIES + 1) {
            delegate.getBlocklist(any(), any(), any(), any())
        }
    }

    @Test
    fun `arguments are forwarded to the delegate untouched`() = runTest {
        coEvery {
            delegate.postCommand(any(), any(), any(), any(), any())
        } returns Result.success(ArrCommand(id = 1, name = "RefreshMovie", status = "queued"))

        resilient.postCommand(
            baseUrl = "http://radarr",
            apiKey = "key",
            commandName = ArrCommandName.REFRESH_MOVIE,
            movieIds = listOf(1, 2),
            episodeIds = null,
        )

        coVerify(exactly = 1) {
            delegate.postCommand("http://radarr", "key", ArrCommandName.REFRESH_MOVIE, listOf(1, 2), null)
        }
    }

    @Test
    fun `every RadarrApiClient method is overridden by the resilient wrapper`() {
        val interfaceMethods = RadarrApiClient::class.java.declaredMethods
            .filter { !it.isSynthetic && !it.name.contains('$') }
        val overridden = ResilientRadarrApiClient::class.java.declaredMethods
            .map { it.name }
            .toSet()

        val missing = interfaceMethods.map { it.name }.filter { it !in overridden }

        assertTrue(
            missing.isEmpty(),
            "Missing retrying overrides for: ${missing.joinToString(", ")}",
        )
    }
}
