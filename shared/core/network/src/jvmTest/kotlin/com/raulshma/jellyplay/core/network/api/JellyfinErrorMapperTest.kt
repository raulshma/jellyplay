package com.raulshma.jellyplay.core.network.api

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the user-facing message table of [JellyfinErrorMapper] — one branch per
 * raw throwable the Jellyfin SDK/OkHttp path can raise, plus the
 * [InvalidStatusException] status sub-table. These strings surface verbatim in
 * the UI (error chips, dialogs), so a reword here is a UX change and must be
 * reviewed as one.
 */
class JellyfinErrorMapperTest {

    @Test
    fun `UnknownHostException asks for a URL and network check`() {
        assertEquals(
            "Unable to reach server. Check the URL and your network connection.",
            JellyfinErrorMapper.map(UnknownHostException("no such host")),
        )
    }

    @Test
    fun `ConnectException asks to ensure the server is running`() {
        assertEquals(
            "Could not connect to server. Ensure it is running and reachable.",
            JellyfinErrorMapper.map(ConnectException("connection refused")),
        )
    }

    @Test
    fun `SocketTimeoutException maps to the timeout message`() {
        assertEquals(
            "Connection timed out. The server took too long to respond.",
            JellyfinErrorMapper.map(SocketTimeoutException("read timed out")),
        )
    }

    @Test
    fun `Jellyfin SDK TimeoutException shares the SocketTimeout message`() {
        // The SDK raises its own TimeoutException type (not java.net's) on
        // request timeouts; both must render identically.
        assertEquals(
            "Connection timed out. The server took too long to respond.",
            JellyfinErrorMapper.map(TimeoutException()),
        )
    }

    @Test
    fun `InvalidStatusException 401 asks to sign in again`() {
        assertEquals(
            "Authentication required. Please sign in again.",
            JellyfinErrorMapper.map(InvalidStatusException(401)),
        )
    }

    @Test
    fun `InvalidStatusException 403 maps to the permission message`() {
        assertEquals(
            "You don't have permission to access this item.",
            JellyfinErrorMapper.map(InvalidStatusException(403)),
        )
    }

    @Test
    fun `InvalidStatusException 404 maps to item not found`() {
        assertEquals(
            "Item not found.",
            JellyfinErrorMapper.map(InvalidStatusException(404)),
        )
    }

    @Test
    fun `InvalidStatusException 5xx carries the status in the message`() {
        assertEquals(
            "Server error (503). Please try again later.",
            JellyfinErrorMapper.map(InvalidStatusException(503)),
        )
    }

    @Test
    fun `InvalidStatusException unmapped status falls back to request failed`() {
        assertEquals(
            "Request failed (418).",
            JellyfinErrorMapper.map(InvalidStatusException(418)),
        )
    }

    @Test
    fun `generic IOException maps to the network error message`() {
        // Must stay distinct from its ConnectException subclass: the mapper's
        // when order relies on the specific cases winning over this branch.
        assertEquals(
            "Network error. Check your connection and try again.",
            JellyfinErrorMapper.map(IOException("connection reset")),
        )
    }

    @Test
    fun `non-network throwable falls back to its message`() {
        assertEquals("boom", JellyfinErrorMapper.map(IllegalArgumentException("boom")))
    }

    @Test
    fun `throwable with null message falls back to the simple class name`() {
        val exception = object : RuntimeException(null as String?) {}
        assertEquals(exception.javaClass.simpleName, JellyfinErrorMapper.map(exception))
    }
}
