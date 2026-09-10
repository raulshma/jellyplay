package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.Jellyfin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Pins [JellyfinRawRequester] — the one seam the Plugin/MediaInfo/Playback
 * clients' raw-OkHttp endpoints ride (the SeerrApiClientTest harness shape:
 * real [JellyfinApiEngine] over a [MockWebServer]):
 *  1. base-address resolution follows [JellyfinApiEngine.activeServerAddress]
 *     — including after a failover to an alternate, the drift the pre-fold
 *     Plugin/MediaInfo URLs had (they built from the primary address and
 *     relied on the failover interceptor to rescue them);
 *  2. every member attaches the current user's `X-Emby-Token` header and the
 *     caller's path verbatim;
 *  3. non-2xx maps to `Exception("<failureMessage>: <code>")` for the
 *     throwing members and to null for [JellyfinRawRequester.getBodyText];
 *  4. the two session-guard flavours keep their historic texts
 *     (not-connected/not-authenticated vs no-server/no-user).
 */
class JellyfinRawRequesterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var router: ServerAddressRouter
    private lateinit var engine: JellyfinApiEngine
    private lateinit var requester: JellyfinRawRequester

    private val testUser = UserInfo(
        id = "user-1",
        name = "testuser",
        serverAddress = "",
        accessToken = "token-123",
    )

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        router = ServerAddressRouter()
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = router,
        )
        engine.updateServer(ServerInfo(id = "server-1", name = "Test", address = baseUrl))
        engine.updateUser(testUser)
        requester = JellyfinRawRequester(engine)
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun recorded() = mockWebServer.takeRequest()

    @Test
    fun `getJson resolves the base from activeServerAddress, sends the token header and decodes`() {
        mockWebServer.enqueue(MockResponse().setBody("""[{"Id":"p1","Name":"Plugins"}]"""))

        val names = requester.getJson("/Plugins", "Failed to get plugins") { body ->
            Json.parseToJsonElement(body!!.string()) as JsonArray
        }

        assertEquals(1, names.size)
        val request = recorded()
        assertEquals("/Plugins", request.path)
        assertEquals("token-123", request.getHeader("X-Emby-Token"))
    }

    @Test
    fun `getJson maps non-2xx to the caller's failure message with the code`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val error = assertFailsWith<Exception> {
            requester.getJson("/Packages", "Failed to get packages") { emptyList<String>() }
        }

        assertEquals("Failed to get packages: 500", error.message)
        assertEquals("/Packages", recorded().path)
    }

    @Test
    fun `postStatusOnly posts the JSON body and maps non-2xx to the failure message`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val error = assertFailsWith<Exception> {
            requester.postStatusOnly("/Repositories", "Failed to set repositories", "[]")
        }

        assertEquals("Failed to set repositories: 403", error.message)
        val request = recorded()
        assertEquals("POST", request.method)
        assertEquals("/Repositories", request.path)
        assertEquals("token-123", request.getHeader("X-Emby-Token"))
        assertEquals("[]", request.body.readUtf8())
    }

    @Test
    fun `deleteStatusOnly issues a DELETE and maps non-2xx to the failure message`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(409))

        val error = assertFailsWith<Exception> {
            requester.deleteStatusOnly("/Packages/Installing/pkg-1", "Failed to cancel installation")
        }

        assertEquals("Failed to cancel installation: 409", error.message)
        val request = recorded()
        assertEquals("DELETE", request.method)
        assertEquals("/Packages/Installing/pkg-1", request.path)
    }

    @Test
    fun `getBodyText returns the body on 2xx and null on failure`() {
        mockWebServer.enqueue(MockResponse().setBody("""{"ItemId":"i1"}"""))
        assertEquals("""{"ItemId":"i1"}""", requester.getBodyText("/Items/i1/IntroSkipTimestamps"))
        recorded()

        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        assertNull(requester.getBodyText("/Items/i1/IntroSkipTimestamps"))
        assertEquals("/Items/i1/IntroSkipTimestamps", recorded().path)
    }

    @Test
    fun `the base follows a failover to an alternate address`() {
        // The primary points at a dead port; the router's active endpoint is
        // the mock. A seam that built URLs from the server's PRIMARY address
        // would try the dead port and never reach the mock.
        val alternate = mockWebServer.url("/").toString().trimEnd('/')
        engine.updateServer(
            ServerInfo(
                id = "server-1",
                name = "Test",
                address = "http://localhost:1",
                alternateAddresses = listOf(alternate),
            ),
        )
        engine.updateUser(testUser)
        router.markActive(alternate)
        assertEquals(alternate, engine.activeServerAddress)

        mockWebServer.enqueue(MockResponse().setBody("[]"))
        requester.getJson("/Plugins", "Failed to get plugins") { emptyList<String>() }

        assertEquals("/Plugins", recorded().path)
    }

    @Test
    fun `requireSession keeps the not-connected and not-authenticated texts`() {
        val freshEngine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        val fresh = JellyfinRawRequester(freshEngine)

        assertEquals("Not connected", assertFailsWith<IllegalStateException> { fresh.requireSession() }.message)

        freshEngine.updateServer(ServerInfo(id = "s", name = "s", address = "https://s.example"))
        assertEquals("Not authenticated", assertFailsWith<IllegalStateException> { fresh.requireSession() }.message)
    }

    @Test
    fun `requirePlaybackSession keeps the no-server and no-user texts`() {
        val (base, token) = requester.requirePlaybackSession()
        assertEquals(mockWebServer.url("/").toString().trimEnd('/'), base)
        assertEquals("token-123", token)

        engine.updateServer(null)
        assertEquals("No server", assertFailsWith<IllegalStateException> { requester.requirePlaybackSession() }.message)

        val userlessEngine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        userlessEngine.updateServer(ServerInfo(id = "s", name = "s", address = "https://s.example"))
        assertEquals(
            "No user",
            assertFailsWith<IllegalStateException> { JellyfinRawRequester(userlessEngine).requirePlaybackSession() }.message,
        )
    }
}
