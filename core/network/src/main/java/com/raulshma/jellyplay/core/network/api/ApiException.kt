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
 *
 * [isAccessDenied] is set for HTTP 401/403 so callers can branch into a dedicated
 * "you don't have access" UX instead of showing a generic network-error string.
 */
class ApiException(
    val isRetryable: Boolean,
    val httpCode: Int? = null,
    val isAccessDenied: Boolean = false,
    /**
     * Server-advised minimum backoff in milliseconds, parsed from a `Retry-After`
     * header (HTTP 429/503) when present. When non-null and the policy's
     * computed exponential backoff is shorter, [RetryPolicy] floors the delay
     * at this value so we honor the server's guidance rather than hammering it.
     * Null when the server did not advise (most failures).
     */
    val retryAfterMs: Long? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        private val ACCESS_DENIED_CODES = setOf(401, 403)

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
                isAccessDenied = code in ACCESS_DENIED_CODES,
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
            isAccessDenied = httpCode in ACCESS_DENIED_CODES,
            message = message,
        )

        /**
         * Build an [ApiException] for an HTTP-status failure, parsing a
         * `Retry-After` header (seconds) into [retryAfterMs] when present so
         * [RetryPolicy] can honor it. Used by subtitle providers (and any other
         * rate-limited service) that expose the raw OkHttp [okhttp3.Response].
         */
        fun fromHttpResponse(httpCode: Int, message: String, retryAfterHeader: String?): ApiException =
            ApiException(
                isRetryable = httpCode in com.raulshma.jellyplay.core.network.RetryPolicy.RETRYABLE_STATUS_CODES,
                httpCode = httpCode,
                isAccessDenied = httpCode in ACCESS_DENIED_CODES,
                retryAfterMs = parseRetryAfterMs(retryAfterHeader),
                message = message,
            )

        /**
         * Parses a `Retry-After` header value into milliseconds. Supports both
         * delta-seconds (e.g. `30`) and the HTTP-date form (rare for API
         * gateways); the latter falls back to null rather than risking a parse
         * error. Returns null for blank/invalid input.
         */
        internal fun parseRetryAfterMs(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val seconds = header.trim().toLongOrNull() ?: return null
            return if (seconds > 0) seconds * 1000 else null
        }

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
