package com.raulshma.jellyplay.core.network.api

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Typed wrapper for all API failures (Jellyfin + Seerr) carrying a pre-classified
 * [isRetryable] flag so [com.raulshma.jellyplay.core.network.RetryPolicy] can decide whether
 * to retry without relying on brittle message-string matching.
 *
 * The original throwable is preserved as [cause] and a friendly, user-facing text is exposed
 * via [message] so existing `result.exceptionOrNull()?.message` call sites continue to display
 * the same strings they did before this class was introduced.
 *
 * Classification happens **before** the friendly mapping (see [JellyfinErrorMapper]) so the
 * retry decision sees the real exception type / HTTP status rather than a localized string.
 */
class ApiException(
    val isRetryable: Boolean,
    val httpCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        /**
         * Classify a throwable thrown from the **Jellyfin SDK** path into an [ApiException].
         *
         * Retryability rules:
         *  - `CancellationException` must never be classified — callers must rethrow it.
         *  - Transient network errors (`SocketTimeoutException`, `ConnectException`,
         *    `UnknownHostException`, Jellyfin `TimeoutException`, generic `IOException`)
         *    → retryable.
         *  - HTTP 429 and any 5xx from Jellyfin `InvalidStatusException` → retryable.
         *  - Anything else (4xx, programming errors) → not retryable.
         */
        fun fromJellyfin(throwable: Throwable): ApiException {
            val (retryable, code) = classifyJellyfin(throwable)
            return ApiException(
                isRetryable = retryable,
                httpCode = code,
                message = JellyfinErrorMapper.map(throwable),
                cause = throwable,
            )
        }

        /**
         * Build an [ApiException] for an HTTP-status failure (response received but
         * status code indicates an error). Retryable for 429 and any 5xx.
         *
         * Service-agnostic; the `fromSeerrHttp` alias below delegates here.
         */
        fun fromHttp(httpCode: Int, message: String): ApiException = ApiException(
            isRetryable = httpCode in com.raulshma.jellyplay.core.network.RetryPolicy.RETRYABLE_STATUS_CODES,
            httpCode = httpCode,
            message = message,
        )

        /**
         * Classify a raw network throwable thrown by OkHttp into an [ApiException].
         * HTTP-status failures should use [fromHttp] instead.
         *
         * Service-agnostic; the `fromSeerrNetwork` alias below delegates here.
         */
        fun fromNetwork(throwable: Throwable, friendlyMessage: String): ApiException {
            val retryable = classifyNetwork(throwable)
            return ApiException(
                isRetryable = retryable,
                message = friendlyMessage,
                cause = throwable,
            )
        }

        /**
         * Build an [ApiException] for a Seerr HTTP-status failure (response received but
         * status code indicates an error). Delegates to [fromHttp].
         */
        fun fromSeerrHttp(httpCode: Int, message: String): ApiException = fromHttp(httpCode, message)

        /**
         * Classify a raw network throwable thrown by OkHttp on the **Seerr** path.
         * HTTP-status failures should use [fromSeerrHttp] instead. Delegates to [fromNetwork].
         */
        fun fromSeerrNetwork(throwable: Throwable, friendlyMessage: String): ApiException =
            fromNetwork(throwable, friendlyMessage)

        internal fun classifyJellyfin(throwable: Throwable): Pair<Boolean, Int?> {
            // SDK InvalidStatusException carries the HTTP code on the exception itself.
            if (throwable is InvalidStatusException) {
                val code = throwable.status
                return (code in com.raulshma.jellyplay.core.network.RetryPolicy.RETRYABLE_STATUS_CODES) to code
            }
            // Jellyfin SDK TimeoutException is its own type (distinct from java.util.concurrent).
            if (throwable is TimeoutException) return true to null
            return classifyNetwork(throwable) to null
        }

        internal fun classifyNetwork(throwable: Throwable): Boolean {
            return when (throwable) {
                is SocketTimeoutException -> true
                is ConnectException -> true
                is UnknownHostException -> true
                // NOTE: java.io.IOException is a supertype of the above; deliberately listed
                // last so the more specific cases above take precedence. Treats generic IO
                // failures (reset, broken pipe, SSL handshake) as retryable.
                is IOException -> true
                else -> false
            }
        }
    }
}
