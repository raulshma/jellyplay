package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.Jellyfin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [PluginApiClientImpl] through the [JellyfinRawRequester] seam (the
 * SeerrApiClientTest harness shape — the client previously had no test lane):
 * the plugin-catalogue tolerant decode, the per-endpoint failure text after
 * apiResultWithRetry's typed-exception mapping, and that the request follows
 * the router's ACTIVE address after a failover to an alternate (the
 * stale-primary drift the seam exists to kill).
 */
class PluginApiClientImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var router: ServerAddressRouter
    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: PluginApiClientImpl

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
        client = PluginApiClientImpl(engine)
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getInstalledPlugins parses the catalogue and drops malformed entries`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """[{"Id":"p1","Name":"Foo","Version":"1.0"},"not-an-object"]""",
            ),
        )

        val plugins = client.getInstalledPlugins().getOrThrow()

        assertEquals(listOf("p1"), plugins.map { it.id })
        assertEquals("Foo", plugins.single().name)
        val request = mockWebServer.takeRequest()
        assertEquals("/Plugins", request.path)
        assertEquals("token-123", request.getHeader("X-Emby-Token"))
    }

    @Test
    fun `a non-2xx catalogue response keeps the endpoint's failure text`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = client.getInstalledPlugins()

        assertTrue(result.isFailure)
        assertEquals("Failed to get plugins: 500", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `uninstallPlugin issues a DELETE against the active endpoint`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val result = client.uninstallPlugin("p1")

        assertTrue(result.isSuccess)
        val request = mockWebServer.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/Plugins/p1", request.path)
    }

    @Test
    fun `requests follow the router's active address after a failover to an alternate`() = runTest {
        // Primary points at a dead port; the alternate is the mock. The
        // pre-seam client built URLs from the primary address — only the
        // failover interceptor's rewrite rescued those calls.
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

        mockWebServer.enqueue(MockResponse().setBody("[]"))

        val repositories = client.getRepositories().getOrThrow()

        assertTrue(repositories.isEmpty())
        assertEquals("/Repositories", mockWebServer.takeRequest().path)
    }
}
