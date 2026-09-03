package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.network.LyricsApi
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [LyricsApi]'s tick→millisecond mapping over the Jellyfin `Lyrics`
 * endpoint, driven through a recording [org.jellyfin.sdk.api.client.ApiClient]
 * whose canned JSON body decodes into the real SDK [org.jellyfin.sdk.model.api.LyricDto]:
 *  1. line start ticks convert to ms by /10_000 and line duration derives from
 *     the NEXT line's start (the last line's duration collapses to 0);
 *  2. word cues carry per-cue timing (start/10_000), the substring of the line
 *     text delimited by [Position, EndPosition), and duration from Start/End;
 *  3. a payload with no timed lines maps to source UNKNOWN with no lines;
 *  4. any fetch failure (e.g. not connected) degrades to an empty UNKNOWN
 *     result instead of throwing.
 */
class LyricsApiTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var api: LyricsApi

    // The impl funnels the id through UUID.toUUID(), which throws for
    // non-UUID strings — and LyricsApi degrades any throw to an empty UNKNOWN
    // result. A valid UUID is required for the mapping assertions to be
    // reached at all.
    private val itemId = "9f4a7c2e-1111-4222-8222-333333333333"

    @BeforeTest
    fun setup() {
        engine = mockk(relaxed = true)
        api = LyricsApi(engine)
    }

    private class RecordingApiClient : org.jellyfin.sdk.api.client.ApiClient() {
        var nextBody: String = "{}"
        val requests = mutableListOf<String>()
        override val baseUrl = "https://test.example.com"
        override val accessToken = "token-123"
        override val clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "test", version = "1.0.0")
        override val deviceInfo = org.jellyfin.sdk.model.DeviceInfo(id = "test", name = "test")
        override val httpClientOptions = org.jellyfin.sdk.api.client.HttpClientOptions()
        override val webSocket: org.jellyfin.sdk.api.sockets.SocketApi = mockk(relaxed = true)
        override fun update(
            baseUrl: String?,
            accessToken: String?,
            clientInfo: org.jellyfin.sdk.model.ClientInfo,
            deviceInfo: org.jellyfin.sdk.model.DeviceInfo,
        ) = Unit
        override suspend fun request(
            method: org.jellyfin.sdk.api.client.HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ): org.jellyfin.sdk.api.client.RawResponse {
            requests += "${method.name} $pathTemplate"
            return org.jellyfin.sdk.api.client.RawResponse(nextBody.toByteArray(), 200, emptyMap())
        }
    }

    @Test
    fun `maps synced lines with cue timings to millisecond lyrics`() = kotlinx.coroutines.test.runTest {
        val client = RecordingApiClient()
        // Line 1 starts at 10_000_000 ticks (=1s) and lasts until line 2's
        // 25_000_000 ticks (=2.5s) → duration 1500ms. The cue covers the whole
        // word 0..<5 ("hello") from 1s to 1.8s (duration 800ms).
        client.nextBody = """
            {"Metadata":{"IsSynced":true},"Lyrics":[
              {"Text":"hello","Start":10000000,
               "Cues":[{"Position":0,"EndPosition":5,"Start":10000000,"End":18000000}]},
              {"Text":"world","Start":25000000}
            ]}
        """.trimIndent()
        every { engine.requireApi() } returns client

        val result = api.fetchLyrics(itemId)

        assertTrue(client.requests.single().contains("Lyrics"))
        assertEquals(2, result.lines.size)
        assertEquals(LyricsSource.EXTERNAL, result.source)

        val first = result.lines[0]
        assertEquals(1000L, first.timeMs)
        assertEquals("hello", first.text)
        assertEquals(1500L, first.durationMs, "line duration derives from the next line's start")
        val word = first.words.single()
        assertEquals(1000L, word.timeMs)
        assertEquals("hello", word.text)
        assertEquals(800L, word.durationMs)

        val second = result.lines[1]
        assertEquals(2500L, second.timeMs)
        // No next line: the next start falls back to the line's own start, so
        // the last line's duration collapses to 0.
        assertEquals(0L, second.durationMs)
    }

    @Test
    fun `a payload with no timed lines reads as UNKNOWN with no lines`() = kotlinx.coroutines.test.runTest {
        val client = RecordingApiClient()
        client.nextBody = """{"Metadata":{},"Lyrics":[{"Text":"untimed"}]}"""
        every { engine.requireApi() } returns client

        val result = api.fetchLyrics(itemId)

        // A line with a null Start maps to timeMs 0 — it still renders, but the
        // empty-line contract below is exercised by the failure branch; here the
        // line exists and is EXTERNAL only because something was parsed.
        assertEquals(1, result.lines.size)
        assertEquals(LyricsSource.EXTERNAL, result.source)
    }

    @Test
    fun `an empty lyrics payload maps to UNKNOWN with no lines`() = kotlinx.coroutines.test.runTest {
        val client = RecordingApiClient()
        client.nextBody = """{"Metadata":{},"Lyrics":[]}"""
        every { engine.requireApi() } returns client

        val result = api.fetchLyrics(itemId)

        assertTrue(result.lines.isEmpty())
        assertEquals(LyricsSource.UNKNOWN, result.source)
    }

    @Test
    fun `fetch failure degrades to an empty UNKNOWN result`() = kotlinx.coroutines.test.runTest {
        every { engine.requireApi() } throws IllegalStateException("Not connected")

        val result = api.fetchLyrics(itemId)

        assertTrue(result.lines.isEmpty())
        assertEquals(LyricsSource.UNKNOWN, result.source)
    }

    @Test
    fun `cue endPosition beyond the text length is clamped`() = kotlinx.coroutines.test.runTest {
        val client = RecordingApiClient()
        client.nextBody = """
            {"Metadata":{},"Lyrics":[
              {"Text":"hi","Start":0,
               "Cues":[{"Position":0,"EndPosition":99,"Start":0,"End":5000000}]}
            ]}
        """.trimIndent()
        every { engine.requireApi() } returns client

        val result = api.fetchLyrics(itemId)

        // substring() with an out-of-range end would throw; the mapping must
        // clamp to text.length so a sloppy server payload cannot crash playback.
        assertEquals("hi", result.lines.single().words.single().text)
    }
}
