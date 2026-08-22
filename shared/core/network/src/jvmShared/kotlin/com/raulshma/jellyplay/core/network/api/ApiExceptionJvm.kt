package com.raulshma.jellyplay.core.network.api

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * JVM-side throwable classifiers for [ApiException] (docs/kmp-migration-plan.md
 * §Phase C3 split): these need java.net + Jellyfin-SDK types, so they live in
 * jvmShared as companion EXTENSION functions. Every pre-split call site
 * (`ApiException.fromJellyfin(x)` / `ApiException.fromNetwork(...)`) compiles
 * unchanged — files in other packages just import the extension explicitly.
 */
fun ApiException.Companion.fromJellyfin(throwable: Throwable): ApiException {
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
 * Classify a raw network throwable thrown by OkHttp into an [ApiException].
 * HTTP-status failures should use [ApiException.fromHttp] instead.
 *
 * Service-agnostic; the `fromSeerrNetwork` alias below delegates here.
 */
fun ApiException.Companion.fromNetwork(throwable: Throwable, friendlyMessage: String): ApiException {
    val retryable = classifyNetwork(throwable)
    return ApiException(
        isRetryable = retryable,
        message = friendlyMessage,
        cause = throwable,
    )
}

/**
 * Classify a raw network throwable thrown by OkHttp on the **Seerr** path.
 * HTTP-status failures should use [ApiException.fromSeerrHttp] instead.
 * Delegates to [fromNetwork].
 */
fun ApiException.Companion.fromSeerrNetwork(throwable: Throwable, friendlyMessage: String): ApiException =
    fromNetwork(throwable, friendlyMessage)

/**
 * Classify a throwable thrown from the **Jellyfin SDK** path.
 *
 * Retryability rules:
 *  - `CancellationException` must never be classified — callers must rethrow it.
 *  - Transient network errors (`SocketTimeoutException`, `ConnectException`,
 *    `UnknownHostException`, Jellyfin `TimeoutException`, generic `IOException`)
 *    → retryable.
 *  - HTTP 429 and any 5xx from Jellyfin `InvalidStatusException` → retryable.
 *  - Anything else (4xx, programming errors) → not retryable.
 */
internal fun ApiException.Companion.classifyJellyfin(throwable: Throwable): Pair<Boolean, Int?> {
    // SDK InvalidStatusException carries the HTTP code on the exception itself.
    if (throwable is InvalidStatusException) {
        val code = throwable.status
        return (code in com.raulshma.jellyplay.core.network.RetryPolicy.RETRYABLE_STATUS_CODES) to code
    }
    // Jellyfin SDK TimeoutException is its own type (distinct from java.util.concurrent).
    if (throwable is TimeoutException) return true to null
    return classifyNetwork(throwable) to null
}

internal fun ApiException.Companion.classifyNetwork(throwable: Throwable): Boolean {
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
