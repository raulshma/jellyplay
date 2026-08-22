package com.raulshma.jellyplay.core.network.api

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
 *
 * C3 split (docs/kmp-migration-plan.md §Phase C3): only the pure, commonMain-safe
 * classification lives in this file so wasm consumers can build `ApiException`s
 * from HTTP statuses. The java.net/Jellyfin-SDK throwable classifiers
 * (`fromJellyfin`, `fromNetwork`) are companion EXTENSION functions in
 * `ApiExceptionJvm.kt` (jvmShared) — every existing `ApiException.fromJellyfin(x)`
 * call site keeps compiling unchanged.
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
    /**
     * Raw response body for an HTTP-status failure, when the caller captured it
     * (subtitle providers read+log it before throwing). Lets upstream code branch
     * on service-specific "empty result" signals — e.g. Wyzie returns HTTP 400 +
     * `{"message":"No subtitles found"}` to mean zero matches, which callers map
     * to an empty success rather than surfacing a (misleading) error. Null when
     * the failure wasn't an HTTP response or the body wasn't read.
     */
    val responseBody: String? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        internal val ACCESS_DENIED_CODES = setOf(401, 403)

        /**
         * HTTP statuses RetryPolicy treats as transient. Canonical home since the
         * C3 split: common code cannot see RetryPolicy (jvmShared), so
         * [RetryPolicy.RETRYABLE_STATUS_CODES] delegates here.
         */
        val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)

        /**
         * Build an [ApiException] for an HTTP-status failure (response received but
         * status code indicates an error). Retryable for 429 and any 5xx.
         *
         * Service-agnostic; the `fromSeerrHttp` alias below delegates here.
         */
        fun fromHttp(httpCode: Int, message: String): ApiException = ApiException(
            isRetryable = httpCode in RETRYABLE_STATUS_CODES,
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
        fun fromHttpResponse(
            httpCode: Int,
            message: String,
            retryAfterHeader: String?,
            responseBody: String? = null,
        ): ApiException =
            ApiException(
                isRetryable = httpCode in RETRYABLE_STATUS_CODES,
                httpCode = httpCode,
                isAccessDenied = httpCode in ACCESS_DENIED_CODES,
                retryAfterMs = parseRetryAfterMs(retryAfterHeader),
                responseBody = responseBody,
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
         * Build an [ApiException] for a Seerr HTTP-status failure (response received but
         * status code indicates an error). Delegates to [fromHttp].
         */
        fun fromSeerrHttp(httpCode: Int, message: String): ApiException = fromHttp(httpCode, message)
    }
}
