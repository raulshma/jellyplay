package com.raulshma.jellyplay.core.network

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the jvmShared actual of the Phase W [isRetryableNetworkError] seam
 * (android + desktop share it through jvmShared). The generic-IOException
 * branch is deliberately listed last — `ConnectException` etc. are its
 * subtypes, so reordering the `when` would change nothing semantically but
 * the explicit sub-cases document intent. Non-IO throwables (decode bugs,
 * argument bugs) must never be retried.
 */
class NetworkErrorClassifierJvmTest {

    @Test
    fun `SocketTimeoutException is retryable`() {
        assertTrue(isRetryableNetworkError(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `ConnectException is retryable`() {
        assertTrue(isRetryableNetworkError(ConnectException("connection refused")))
    }

    @Test
    fun `UnknownHostException is retryable`() {
        assertTrue(isRetryableNetworkError(UnknownHostException("no DNS")))
    }

    @Test
    fun `generic IOException is retryable`() {
        // Covers reset / broken pipe / SSL handshake IO failures.
        assertTrue(isRetryableNetworkError(IOException("connection reset")))
    }

    @Test
    fun `SerializationException is not retryable`() {
        // A decode failure is deterministic — retrying the identical request
        // would fail identically.
        assertFalse(isRetryableNetworkError(SerializationException("unexpected JSON token")))
    }

    @Test
    fun `IllegalArgumentException is not retryable`() {
        assertFalse(isRetryableNetworkError(IllegalArgumentException("bad argument")))
    }
}
