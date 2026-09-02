package com.raulshma.jellyplay.core.network.websocket

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins the `/socket` URL assembly of [buildSocketUrl]: scheme swap
 * (http(s) → ws(s)), trailing-slash trimming, and the device-identification
 * query params. Both the app-lifetime socket (`api_key` in the URL) and the
 * activity-log channel (token in the `X-Emby-Token` header instead) build
 * their endpoint through this helper, so the no-apiKey variant must never
 * leak a token param.
 */
class SocketUrlTest {

    @Test
    fun `http server address becomes a ws socket url`() {
        assertEquals(
            "ws://example.com/socket?deviceId=dev-1",
            buildSocketUrl("http://example.com", deviceId = "dev-1"),
        )
    }

    @Test
    fun `https server address becomes a wss socket url`() {
        assertEquals(
            "wss://example.com/socket?deviceId=dev-1",
            buildSocketUrl("https://example.com", deviceId = "dev-1"),
        )
    }

    @Test
    fun `trailing slash is trimmed before the path is appended`() {
        // Both trailing forms occur in saved server addresses; without the
        // trim the URL would be "...//socket?" and Jellyfin rejects it.
        assertEquals(
            "wss://example.com/socket?deviceId=dev-1",
            buildSocketUrl("https://example.com/", deviceId = "dev-1"),
        )
        assertEquals(
            "ws://example.com:8096/socket?deviceId=dev-1",
            buildSocketUrl("http://example.com:8096///", deviceId = "dev-1"),
        )
    }

    @Test
    fun `apiKey travels as the first api_key query param`() {
        assertEquals(
            "wss://example.com/socket?api_key=tok-123&deviceId=dev-1",
            buildSocketUrl("https://example.com", deviceId = "dev-1", apiKey = "tok-123"),
        )
    }

    @Test
    fun `deviceId is always present`() {
        val url = buildSocketUrl("https://example.com", deviceId = "dev-1")
        assertFalse(url.contains("api_key"), "no api_key without an explicit token")
        assertEquals("dev-1", url.substringAfter("deviceId="))
    }

    @Test
    fun `deviceName and client are percent-encoded when present`() {
        // URLEncoder's form encoding: space becomes '+', reserved chars are
        // percent-escaped — the server must decode the original strings.
        assertEquals(
            "wss://example.com/socket?deviceId=dev-1&deviceName=Living+Room&client=JellyPlay%2F1.0",
            buildSocketUrl(
                "https://example.com",
                deviceId = "dev-1",
                deviceName = "Living Room",
                client = "JellyPlay/1.0",
            ),
        )
    }

    @Test
    fun `absent optional params are omitted entirely`() {
        val url = buildSocketUrl("https://example.com", deviceId = "dev-1")
        assertFalse(url.contains("deviceName="), "no deviceName param when null")
        assertFalse(url.contains("client="), "no client param when null")
    }

    @Test
    fun `surrounding whitespace on the server address is trimmed`() {
        assertEquals(
            "wss://example.com/socket?deviceId=dev-1",
            buildSocketUrl("  https://example.com  ", deviceId = "dev-1"),
        )
    }

    @Test
    fun `full parameter set arrives in the documented order`() {
        // The shared-socket variant carries every param; the order is pinned
        // because it is what the server logs and what the realtime channels
        // assert against.
        assertEquals(
            "wss://example.com/socket?api_key=tok-123&deviceId=dev-1&deviceName=Desk&client=JellyPlay",
            buildSocketUrl(
                "https://example.com",
                deviceId = "dev-1",
                deviceName = "Desk",
                client = "JellyPlay",
                apiKey = "tok-123",
            ),
        )
    }
}
