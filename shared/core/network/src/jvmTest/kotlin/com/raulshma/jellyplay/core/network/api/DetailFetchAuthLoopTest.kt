package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.LyricsApi
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Feedback loop for the "media detail opens as access-denied" regression:
 * drives the REAL engine + [LibraryApiClientImpl.getMediaDetail] against a
 * local HTTP server and pins the two facts the symptom depends on —
 *
 *  1. the detail request carries the session's access token in its
 *     Authorization header (a dropped/empty token is exactly what turns
 *     every detail open into HTTP 401 → `isAccessDenied` → the
 *     "You don't have access to this item." screen), and
 *  2. a 401/403 detail response maps to an [ApiException] flagged
 *     `isAccessDenied` (the UI's routing signal), while a 200 maps to a
 *     successful detail (no false access-denied).
 */
class DetailFetchAuthLoopTest {

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()

    private var respondWith: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            return respondWith(request)
        }
    }

    private val jellyfin: Jellyfin = createJellyfin {
        clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "JellyPlayTest", version = "1.0.0")
        deviceInfo = org.jellyfin.sdk.model.DeviceInfo(id = "loop-device", name = "LoopTest")
    }

    private fun newEngine(): JellyfinApiEngine = JellyfinApiEngine(
        Lazy { jellyfin },
        Lazy { OkHttpClient() },
        DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
        ServerAddressRouter(),
    )

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = dispatcher
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun itemJson(id: UUID) = """
        {"Items":[{"Id":"$id","Name":"Loop Movie","Type":"Movie","MediaType":"Video"}],
         "TotalRecordCount":1,"StartIndex":0}
    """.trimIndent()

    @Test
    fun `detail request carries session token and succeeds`() = runBlocking {
        val engine = newEngine()
        val itemId = UUID.randomUUID()
        respondWith = { req ->
            if (req.path?.startsWith("/Items") == true) {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(itemJson(itemId))
            } else {
                MockResponse().setResponseCode(404)
            }
        }

        // The exact setUser sequence AuthApiClientImpl runs at restore/login:
        // server adopted, authenticated client pushed, THEN user published.
        engine.updateServer(serverInfo())
        engine.updateApi(engine.jellyfin.createApi(
            baseUrl = server.url("/").toString(),
            accessToken = TOKEN,
        ))
        engine.updateUser(userInfo())

        val client = LibraryApiClientImpl(engine, mockk<LyricsApi>(relaxed = true))
        val result = client.getMediaDetail(itemId.toString())

        assertTrue(result.isSuccess, "detail fetch failed: ${result.exceptionOrNull()}")

        val detailCall = requests.firstOrNull { it.path?.startsWith("/Items") == true }
        assertTrue(detailCall != null, "no /Items request was issued; saw: ${requests.mapNotNull { it.path }}")
        val auth = detailCall.getHeader("Authorization") ?: detailCall.getHeader("X-Emby-Authorization")
        assertTrue(
            auth != null && auth.contains(TOKEN),
            "detail request went out without the session token; Authorization=$auth",
        )
    }

    @Test
    fun `401 detail response maps to access-denied ApiException`() = runBlocking {
        val engine = newEngine()
        val itemId = UUID.randomUUID()
        respondWith = { MockResponse().setResponseCode(401).setBody("Unauthorized") }

        engine.updateServer(serverInfo())
        engine.updateApi(engine.jellyfin.createApi(
            baseUrl = server.url("/").toString(),
            accessToken = TOKEN,
        ))
        engine.updateUser(userInfo())

        val client = LibraryApiClientImpl(engine, mockk<LyricsApi>(relaxed = true))
        val result = client.getMediaDetail(itemId.toString())

        val error = result.exceptionOrNull()
        assertTrue(error is ApiException, "expected ApiException, got $error")
        assertEquals(true, (error as ApiException).isAccessDenied, "401 must classify as access-denied")
        assertEquals(401, error.httpCode)
    }

    private fun serverInfo() = ServerInfo(
        id = "srv",
        name = "Loop Server",
        address = server.url("/").toString(),
    )

    private fun userInfo() = UserInfo(
        id = "user-1",
        name = "loop-user",
        serverAddress = server.url("/").toString(),
        accessToken = TOKEN,
    )

    private companion object {
        const val TOKEN = "loop-token-123"
    }
}
