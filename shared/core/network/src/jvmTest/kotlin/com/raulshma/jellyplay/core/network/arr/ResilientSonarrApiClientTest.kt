package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the retry contract of [ResilientSonarrApiClient] over a stubbed
 * [SonarrApiClientImpl] delegate (mirror of [ResilientRadarrApiClientTest]):
 *  1. a retryable failure is retried until the first success;
 *  2. a non-retryable failure (HTTP 401) surfaces after exactly one call;
 *  3. retry exhaustion stops at [ResilientSonarrApiClient.MAX_RETRIES] extra
 *     attempts;
 *  4. every [SonarrApiClient] method stays explicitly overridden — no interface
 *     method may bypass retry through delegated defaults.
 */
class ResilientSonarrApiClientTest {

    private val delegate = mockk<SonarrApiClientImpl>()
    private val resilient = ResilientSonarrApiClient(delegate)

    @Test
    fun `retries a retryable failure and returns the first success`() = runTest {
        coEvery { delegate.testConnection(any(), any()) } returnsMany listOf(
            Result.failure(ApiException(isRetryable = true, message = "HTTP 503: Service Unavailable")),
            Result.success(Unit),
        )

        val result = resilient.testConnection("http://sonarr", "key")

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { delegate.testConnection(any(), any()) }
    }

    @Test
    fun `does not retry a non-retryable failure`() = runTest {
        coEvery { delegate.getQueue(any(), any()) } returns
            Result.failure(ApiException(isRetryable = false, message = "HTTP 401: Unauthorized"))

        val result = resilient.getQueue("http://sonarr", "badkey")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { delegate.getQueue(any(), any()) }
    }

    @Test
    fun `exhausts MAX_RETRIES extra attempts before failing`() = runTest {
        coEvery { delegate.getWanted(any(), any(), any(), any()) } returns
            Result.failure(ApiException(isRetryable = true, message = "HTTP 500: Internal Server Error"))

        val result = resilient.getWanted("http://sonarr", "key", page = 1, pageSize = 50)

        assertTrue(result.isFailure)
        coVerify(exactly = ResilientSonarrApiClient.MAX_RETRIES + 1) {
            delegate.getWanted(any(), any(), any(), any())
        }
    }

    @Test
    fun `arguments are forwarded to the delegate untouched`() = runTest {
        coEvery { delegate.findSeriesByTvdb(any(), any(), any()) } returns Result.success(7)

        val result = resilient.findSeriesByTvdb("http://sonarr", "key", tvdbId = 12345)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { delegate.findSeriesByTvdb("http://sonarr", "key", 12345) }
    }

    @Test
    fun `every SonarrApiClient method is overridden by the resilient wrapper`() {
        val interfaceMethods = SonarrApiClient::class.java.declaredMethods
            .filter { !it.isSynthetic && !it.name.contains('$') }
        val overridden = ResilientSonarrApiClient::class.java.declaredMethods
            .map { it.name }
            .toSet()

        val missing = interfaceMethods.map { it.name }.filter { it !in overridden }

        assertTrue(
            missing.isEmpty(),
            "Missing retrying overrides for: ${missing.joinToString(", ")}",
        )
    }
}
