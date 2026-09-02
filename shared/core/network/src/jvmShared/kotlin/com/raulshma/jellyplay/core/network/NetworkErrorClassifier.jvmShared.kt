package com.raulshma.jellyplay.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * JVM/Android actual of the Phase W retry-classifier seam (see
 * RetryPolicy.kt in commonMain). The checks are verbatim from the pre-split
 * jvmShared RetryPolicy.isRetryable `when` block — androidMain and jvmMain
 * both see this source set through jvmShared, so one actual serves both
 * targets and neither compiles ktor (wasmJsMain owns the Ktor mapping).
 */
internal actual fun isRetryableNetworkError(exception: Throwable): Boolean = when (exception) {
    is SocketTimeoutException -> true
    is ConnectException -> true
    is UnknownHostException -> true
    // NOTE: java.io.IOException is a supertype of the above; deliberately listed
    // last so the more specific cases above take precedence. Treats generic IO
    // failures (reset, broken pipe, SSL handshake) as retryable.
    is IOException -> true
    else -> false
}
