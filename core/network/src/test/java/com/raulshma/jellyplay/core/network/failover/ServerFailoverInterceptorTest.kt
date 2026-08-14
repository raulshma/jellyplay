package com.raulshma.jellyplay.core.network.failover

import com.raulshma.jellyplay.core.model.ServerInfo
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ServerFailoverInterceptorTest {

    private val primaryServer = MockWebServer()
    private val alternateServer = MockWebServer()

    /** A bound-then-closed local port: connections are refused instantly. */
    private var deadPort: Int = 0

    private lateinit var router: ServerAddressRouter
    private lateinit var client: OkHttpClient

    private val primaryAddress: String
        get() = primaryServer.url("/").let { "http://${it.host}:${it.port}" }

    private val alternateAddress: String
        get() = alternateServer.url("/").let { "http://${it.host}:${it.port}" }

    private val deadAddress: String
        get() = "http://localhost:$deadPort"

    @Before
    fun setup() {
        primaryServer.start()
        alternateServer.start()
        ServerSocket(0).use { socket -> deadPort = socket.localPort }
        router = ServerAddressRouter()
        client = OkHttpClient.Builder()
            .addInterceptor(ServerFailoverInterceptor(router))
            .build()
    }

    @After
    fun tearDown() {
        primaryServer.shutdown()
        alternateServer.shutdown()
    }

    private fun configureServer(primary: String, alternate: String) {
        router.configure(
            ServerInfo(
                id = "server-1",
                name = "Test Server",
                address = primary,
                alternateAddresses = listOf(alternate),
            )
        )
    }

    @Test
    fun `requests to foreign hosts pass through untouched`() {
        primaryServer.enqueue(MockResponse().setBody("github"))
        configureServer(primary = deadAddress, alternate = alternateAddress)

        val response = client.newCall(
            Request.Builder().url(primaryServer.url("/releases")).build()
        ).execute()

        // The URL is not a known endpoint of the configured server, so the
        // interceptor must not rewrite it even though routing is configured.
        assertEquals("github", response.body.string())
        assertEquals("/releases", primaryServer.takeRequest().path)
    }

    @Test
    fun `request to the primary is rewritten onto the active alternate`() {
        alternateServer.enqueue(MockResponse().setBody("ok"))
        configureServer(primary = deadAddress, alternate = alternateAddress)
        router.markActive(alternateAddress)

        val response = client.newCall(
            Request.Builder().url("$deadAddress/System/Info").build()
        ).execute()

        assertEquals("ok", response.body.string())
        val recorded = alternateServer.takeRequest()
        assertEquals("/System/Info", recorded.path)
        // The dead primary never received anything.
        assertEquals(0, primaryServer.requestCount)
    }

    @Test
    fun `connect failure on the active endpoint fails over to the alternate`() {
        alternateServer.enqueue(MockResponse().setBody("ok"))
        configureServer(primary = deadAddress, alternate = alternateAddress)
        // Active is the primary, which refuses connections: the interceptor's
        // first attempt must fail with a ConnectException and transparently
        // retry against the alternate.
        assertEquals(deadAddress, router.activeAddress.value)

        val response = client.newCall(
            Request.Builder().url("$deadAddress/Items?api_key=x").build()
        ).execute()

        assertEquals("ok", response.body.string())
        assertEquals("/Items?api_key=x", alternateServer.takeRequest().path)
        // The successful candidate became the sticky active endpoint.
        assertEquals(alternateAddress, router.activeAddress.value)
    }

    @Test
    fun `throws when every endpoint refuses connections`() {
        configureServer(primary = deadAddress, alternate = deadAddress)

        try {
            client.newCall(Request.Builder().url("$deadAddress/Items").build()).execute()
            fail("expected IOException")
        } catch (expected: IOException) {
            assertTrue(router.activeAddress.value == deadAddress)
        }
    }

    @Test
    fun `path and query survive the rewrite`() = runTest {
        alternateServer.enqueue(MockResponse().setBody("ok"))
        configureServer(primary = deadAddress, alternate = alternateAddress)
        router.markActive(alternateAddress)

        client.newCall(
            Request.Builder().url("$deadAddress/Videos/abc/master.m3u8?api_key=secret").build()
        ).execute()

        val recorded = alternateServer.takeRequest()
        assertEquals("/Videos/abc/master.m3u8?api_key=secret", recorded.path)
    }
}
