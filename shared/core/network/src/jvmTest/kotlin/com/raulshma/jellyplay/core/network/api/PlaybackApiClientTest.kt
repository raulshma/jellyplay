package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.Jellyfin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackApiClientImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var engine: JellyfinApiEngine
    private lateinit var playbackClient: PlaybackApiClientImpl

    private val testServer = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = "",
    )

    private val testUser = UserInfo(
        id = "user-1",
        name = "testuser",
        serverAddress = "",
        accessToken = "token-123",
        serverId = "server-1",
    )

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')

        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val okHttpClient = OkHttpClient()
        engine = JellyfinApiEngine(
            jellyfinLazy = dagger.Lazy { jellyfin },
            okHttpClientLazy = dagger.Lazy { okHttpClient },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        engine.updateServer(testServer.copy(address = baseUrl))
        engine.updateUser(testUser.copy(serverAddress = baseUrl))

        playbackClient = PlaybackApiClientImpl(
            engine = engine,
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            playbackStore = mockk(relaxed = true),
        )
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getStreamUrl constructs correct URL`() {
        val url = playbackClient.getStreamUrl("item-1", "source-1", 5000L)
        assertTrue(url.contains("/Videos/item-1/stream"))
        assertTrue(url.contains("static=true"))
        assertTrue(url.contains("mediaSourceId=source-1"))
        assertTrue(url.contains("startTimeTicks=5000"))
        assertTrue(url.contains("api_key=token-123"))
    }

    @Test
    fun `getStreamUrl appends LiveStreamId and drops static for live sources`() {
        val url = playbackClient.getStreamUrl("channel-1", "source-1", 0L, liveStreamId = "live-abc")
        assertTrue(url.contains("/Videos/channel-1/stream"))
        // static=true would break a non-seekable live tuner stream
        assertFalse(url.contains("static=true"))
        assertTrue(url.contains("LiveStreamId=live-abc"))
        assertTrue(url.contains("mediaSourceId=source-1"))
        assertTrue(url.contains("api_key=token-123"))
    }

    @Test
    fun `getStreamUrl returns empty when no server`() {
        engine.updateServer(null)
        val url = playbackClient.getStreamUrl("item-1", "source-1", 0L)
        assertEquals("", url)
    }

    @Test
    fun `getSubtitleDeliveryUrl appends api key`() {
        val url = playbackClient.getSubtitleDeliveryUrl("/Videos/item/Subtitles/0")
        assertTrue(url.contains("api_key=token-123"))
    }

    @Test
    fun `getSubtitleDeliveryUrl handles query params in delivery URL`() {
        val url = playbackClient.getSubtitleDeliveryUrl("/Videos/item/Subtitles/0?format=srt")
        assertTrue(url.contains("&api_key=token-123"))
    }

    @Test
    fun `getSubtitleDeliveryUrl handles full URL`() {
        val url = playbackClient.getSubtitleDeliveryUrl("https://other.server/video.srt")
        assertTrue(url.contains("api_key=token-123"))
    }

    @Test
    fun `getSubtitleDeliveryUrl returns empty when no server`() {
        engine.updateServer(null)
        val url = playbackClient.getSubtitleDeliveryUrl("/video.srt")
        assertEquals("", url)
    }

    @Test
    fun `buildSubtitleDeliveryUrl with srt codec`() {
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "srt")
        assertTrue(url.contains("/Subtitles/0/Stream.srt"))
        assertTrue(url.contains("api_key=token-123"))
    }

    @Test
    fun `buildSubtitleDeliveryUrl with subrip codec converts to srt`() {
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "subrip")
        assertTrue(url.contains("/Subtitles/0/Stream.srt"))
    }

    @Test
    fun `buildSubtitleDeliveryUrl with null codec defaults to srt`() {
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, null)
        assertTrue(url.contains("/Subtitles/0/Stream.srt"))
    }

    @Test
    fun `buildSubtitleDeliveryUrl returns empty when no server`() {
        engine.updateServer(null)
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "srt")
        assertEquals("", url)
    }

    @Test
    fun `buildSubtitleDeliveryUrl refuses PGS image codec`() {
        // The Jellyfin subtitle endpoint cannot serve image formats; refusing
        // here lets the caller drop the stream cleanly instead of emitting a
        // URL that 404s at fetch time.
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "pgs")
        assertEquals("", url)
    }

    @Test
    fun `buildSubtitleDeliveryUrl refuses VOBSUB and DVB image codecs`() {
        assertEquals(
            "", playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "dvd_subtitle"),
        )
        assertEquals(
            "", playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "dvb_subtitle"),
        )
        assertEquals(
            "", playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "hdmv_pgs_subtitle"),
        )
    }

    @Test
    fun `buildSubtitleDeliveryUrl still serves ASS text codec`() {
        val url = playbackClient.buildSubtitleDeliveryUrl("item-1", "source-1", 0, "ass")
        assertTrue(url.contains("/Subtitles/0/Stream.ass"))
    }

    @Test
    fun `getIntroTimestamps parses response`() = kotlinx.coroutines.test.runTest {
        val json = """{"ItemId":"item-1","IntroStartTicks":0,"IntroEndTicks":0}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = playbackClient.getIntroTimestamps("item-1")
        assertTrue(result.isSuccess)
        assertEquals("item-1", result.getOrNull()!!.itemId)
    }

    @Test
    fun `getMediaSegments returns empty list for non-success response`() = kotlinx.coroutines.test.runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = playbackClient.getMediaSegments("item-1")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun `getCreditTimestamps returns default for non-success`() = kotlinx.coroutines.test.runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = playbackClient.getCreditTimestamps("item-1")
        assertTrue(result.isSuccess)
        assertEquals("item-1", result.getOrNull()!!.itemId)
    }
}
