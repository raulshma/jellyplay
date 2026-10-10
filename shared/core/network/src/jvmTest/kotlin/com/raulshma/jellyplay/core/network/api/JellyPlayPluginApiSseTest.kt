package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.Jellyfin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settings SSE stream's resume contract (the Phase 1 `Last-Event-ID` wave):
 * a non-zero `resumeFromEventId` rides the `Last-Event-ID` header so the
 * seq-anchored server replays the disconnect gap, zero/omitted stays the
 * byte-identical legacy connect — and the hand-rolled parser still delivers
 * ids/events/data untouched.
 */
class JellyPlayPluginApiSseTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: JellyPlayPluginApiClientImpl

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        engine.updateServer(ServerInfo(id = "server-1", name = "Test Server", address = baseUrl))
        engine.updateUser(
            UserInfo(
                id = "user-1",
                name = "testuser",
                serverAddress = baseUrl,
                accessToken = "token-123",
                serverId = "server-1",
            ),
        )
        client = JellyPlayPluginApiClientImpl(engine)
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun sseResponse() = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        // Every frame terminates with a blank line — including the last (the
        // wire's real shape; the parser emits a frame only on the blank line).
        .setBody(
            "id: 41\n" +
                "event: settings.changed\n" +
                "data: {\"head\":41}\n" +
                "\n" +
                "id: 42\n" +
                "event: settings.reset\n" +
                "data: {}\n" +
                "\n",
        )

    @Test
    fun resumeFromEventId_ridesLastEventIdHeader_eventsStillParse() = runBlocking {
        mockWebServer.enqueue(sseResponse())

        val events = withTimeout(10_000) {
            client.settingsStream(resumeFromEventId = 42L).toList()
        }

        // The parser's ids/events/data, unchanged by the resume half.
        assertEquals(2, events.size)
        assertEquals(41L, events[0].id)
        assertEquals("settings.changed", events[0].event)
        assertTrue(events[0].data.contains("\"head\":41"))
        assertEquals(42L, events[1].id)
        assertEquals("settings.reset", events[1].event)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/jellyplay/settings/stream", recorded.path)
        assertEquals("42", recorded.getHeader("Last-Event-ID"))
    }

    @Test
    fun zeroResumeId_connectsLegacy_noHeader() = runBlocking {
        mockWebServer.enqueue(sseResponse())

        val events = withTimeout(10_000) {
            client.settingsStream().toList()
        }

        assertEquals(2, events.size)

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Last-Event-ID"), "the legacy connect must stay byte-identical — no resume header")
    }
}
