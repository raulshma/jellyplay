package com.raulshma.jellyplay.core.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException

/**
 * wasmJs actual of the Phase W retry-classifier seam (see RetryPolicy.kt in
 * commonMain). On wasm the HTTP stack is Ktor on the fetch-backed Js engine,
 * so the transient-network taxonomy is:
 *  - [HttpRequestTimeoutException]: the HttpTimeout plugin fired (request or
 *    connect window elapsed).
 *  - [IOException]: Ktor 3's common `io.ktor.utils.io.errors.IOException` —
 *    `kotlin.io.IOException` has no wasmJs actual, and the fetch engine
 *    wraps transport failures (DNS, refused connection, TLS, network change)
 *    in its subclasses. Mirrors the "generic IO failure is retryable" rule
 *    of the JVM actual.
 * Everything else (serialization errors, programming errors) is not
 * retryable, matching the JVM behavior for non-IO throwables.
 */
internal actual fun isRetryableNetworkError(exception: Throwable): Boolean = when (exception) {
    is HttpRequestTimeoutException -> true
    is IOException -> true
    else -> false
}
