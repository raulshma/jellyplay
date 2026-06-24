package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object RetryPolicy {
    val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    const val DEFAULT_MAX_RETRIES = 3
    const val DEFAULT_BASE_DELAY_MS = 1_000L
    const val DEFAULT_MAX_DELAY_MS = 8_000L
    const val DEFAULT_JITTER_FLOOR_MS = 200L
    const val BACKOFF_FACTOR = 2.0

    fun isRetryable(exception: Throwable): Boolean {
        if (exception is CancellationException) return false
        // Prefer the typed marker: ApiException carries a pre-classified retryable flag set
        // at the source (Jellyfin SDK or Seerr) before friendly-message mapping.
        if (exception is ApiException) return exception.isRetryable
        when (exception) {
            is SocketTimeoutException -> return true
            is ConnectException -> return true
            is UnknownHostException -> return true
            is IOException -> return true
        }
        val message = exception.message ?: return false
        return RETRYABLE_STATUS_CODES.any { code ->
            message.contains("HTTP $code")
        }
    }

    fun calculateBackoff(
        attempt: Int,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFloorMs: Long = DEFAULT_JITTER_FLOOR_MS,
    ): Long {
        val exponentialDelay = (baseDelayMs * BACKOFF_FACTOR.pow(attempt)).toLong()
        val capped = min(exponentialDelay, maxDelayMs)
        val floor = jitterFloorMs.coerceAtMost(capped)
        return Random.nextLong(floor, capped + 1)
    }

    suspend fun <T> executeWithRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        jitterFloorMs: Long = DEFAULT_JITTER_FLOOR_MS,
        block: suspend () -> Result<T>,
    ): Result<T> {
        var lastResult = block()
        repeat(maxRetries) { attempt ->
            if (lastResult.isSuccess) return lastResult
            val exception = lastResult.exceptionOrNull() ?: return lastResult
            if (!isRetryable(exception)) return lastResult
            val backoffMs = calculateBackoff(attempt, jitterFloorMs = jitterFloorMs)
            delay(backoffMs)
            lastResult = block()
        }
        return lastResult
    }
}
