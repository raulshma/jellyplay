package com.raulshma.jellyplay.core.network.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Pins the wasm auth header builder's byte-compatibility with the Jellyfin
 * SDK's AuthorizationHeaderBuilder (SDK 1.8.12 — format extracted from the
 * shipped jar): parameter order, `", "` joins, `Token` omission when null,
 * value encoding (trim, line-feed strip, unreserved/space/percent rules).
 */
class JellyfinAuthorizationHeaderTest {

    @Test
    fun `builds the full header with token`() {
        val header = JellyfinAuthorizationHeader.build(
            clientName = "JellyPlay",
            clientVersion = "1.0",
            deviceId = "device-1",
            deviceName = "JellyPlay Web",
            accessToken = "token-1",
        )
        // The space in the device name encodes as '+' (SDK encodeURLPart rule).
        assertEquals(
            "MediaBrowser Client=\"JellyPlay\", Version=\"1.0\", " +
                "DeviceId=\"device-1\", Device=\"JellyPlay+Web\", Token=\"token-1\"",
            header,
        )
    }

    @Test
    fun `token parameter is omitted when null`() {
        val header = JellyfinAuthorizationHeader.build(
            clientName = "JellyPlay",
            clientVersion = "1.0",
            deviceId = "device-1",
            deviceName = "JellyPlay Web",
            accessToken = null,
        )
        assertEquals(
            "MediaBrowser Client=\"JellyPlay\", Version=\"1.0\", " +
                "DeviceId=\"device-1\", Device=\"JellyPlay+Web\"",
            header,
        )
        assertFalse(header.contains("Token"), "no Token parameter may appear")
    }

    @Test
    fun `values are trimmed before encoding`() {
        assertEquals(
            "Device=\"JellyPlay+Web\"",
            JellyfinAuthorizationHeader.buildParameter("Device", "  JellyPlay Web  "),
        )
    }

    @Test
    fun `spaces encode as plus and line feeds are stripped`() {
        // Trim first (leading space gone), then the interior line feeds are
        // removed before encoding — nothing left to encode.
        assertEquals(
            "Device=\"JellyPlayWeb\"",
            JellyfinAuthorizationHeader.buildParameter("Device", " JellyPlay\nWeb\n"),
        )
        // A space that survives trimming still encodes as '+'.
        assertEquals(
            "Device=\"JellyPlay+Web\"",
            JellyfinAuthorizationHeader.buildParameter("Device", "JellyPlay Web"),
        )
    }

    @Test
    fun `non-unreserved characters percent-encode uppercase hexadecimal`() {
        // 'ü' is 0xC3 0xBC in UTF-8; the quote is not unreserved either.
        assertEquals(
            "Device=\"Gr%C3%BCn\"",
            JellyfinAuthorizationHeader.buildParameter("Device", "Grün"),
        )
        assertEquals(
            "Client=\"%22quoted%22\"",
            JellyfinAuthorizationHeader.buildParameter("Client", "\"quoted\""),
        )
    }

    @Test
    fun `unreserved characters pass through unencoded`() {
        assertEquals(
            "DeviceId=\"aB09-._~\"",
            JellyfinAuthorizationHeader.buildParameter("DeviceId", "aB09-._~"),
        )
    }

    @Test
    fun `keys with header-corrupting characters are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            JellyfinAuthorizationHeader.buildParameter("Ba=d", "x")
        }
        assertFailsWith<IllegalArgumentException> {
            JellyfinAuthorizationHeader.buildParameter("Ba,d", "x")
        }
        assertFailsWith<IllegalArgumentException> {
            JellyfinAuthorizationHeader.buildParameter("\"Bad", "x")
        }
        assertFailsWith<IllegalArgumentException> {
            JellyfinAuthorizationHeader.buildParameter("Bad\"", "x")
        }
    }
}
