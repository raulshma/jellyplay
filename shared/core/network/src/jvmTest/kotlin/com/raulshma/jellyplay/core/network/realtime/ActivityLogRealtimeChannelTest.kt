package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ActivityLogRealtimeChannelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiClient: JellyfinApiClient
    private lateinit var engine: JellyfinApiEngine
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var channel: ActivityLogRealtimeChannel

    private var serverSocket: WebSocket? = null
    private val opened = CountDownLatch(1)

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiClient = mockk()
        engine = mockk()
        serverIdentityStore = mockk()
        every { engine.okHttpClient } returns OkHttpClient()
        every { apiClient.getServerUrl() } returns server.url("/").toString().trimEnd('/')
        every { apiClient.getAccessToken() } returns "token-123"
        coEvery { serverIdentityStore.ensureDeviceId() } returns "device-1"
        channel = ActivityLogRealtimeChannel(apiClient, engine, serverIdentityStore)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    /** Serves a WebSocket upgrade, records the server-side socket once open. */
    private fun enqueueUpgrade() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    serverSocket = webSocket
                    opened.countDown()
                }
            }),
        )
    }

    private fun awaitOpened() {
        assertTrue(opened.await(5, TimeUnit.SECONDS), "socket never opened")
    }

    // Real time: the socket handshake happens on OkHttp threads, outside any
    // test scheduler.

    @Test
    fun `socket request carries token header, device id, and no token in URL`() = runBlocking {
        enqueueUpgrade()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { channel.entries().collect { } }
        awaitOpened()

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/socket?deviceId=device-1&deviceName=JellyPlay&client=JellyPlay", recorded.path)
        assertEquals("token-123", recorded.getHeader("X-Emby-Token"))
        // The token must never leak into the URL.
        assertTrue(recorded.path!!.contains("token-123").not())
        job.cancel()
    }

    @Test
    fun `ActivityLogEntry pushes are parsed with PascalCase fields and emitted`() = runBlocking {
        enqueueUpgrade()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            val entry = channel.entries().first()
            assertEquals(42L, entry.id)
            assertEquals("User logged in", entry.name)
        }
        awaitOpened()
        server.takeRequest(5, TimeUnit.SECONDS)

        val payload = """{"MessageType":"ActivityLogEntry","MessageData":{"Id":42,"Name":"User logged in","Type":"1","Severity":"Warn"}}"""
        withTimeout(10_000) {
            // Push until the collector has processed it (first pushes may race the open).
            while (job.isActive) {
                serverSocket?.send(payload)
                kotlinx.coroutines.delay(50)
            }
        }
        assertTrue(job.isCompleted)
    }

    @Test
    fun `non-ActivityLogEntry messages are ignored`() = runBlocking {
        enqueueUpgrade()
        var emitted = false
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { channel.entries().first(); emitted = true }
        awaitOpened()
        server.takeRequest(5, TimeUnit.SECONDS)

        withTimeout(1_000) {
            serverSocket?.send("""{"MessageType":"KeepAlive"}""")
            kotlinx.coroutines.delay(200)
        }
        assertTrue(job.isActive) // still waiting: nothing emitted
        assertTrue(!emitted)
        job.cancel()
    }

    @Test
    fun `cancelling the collector closes the socket`() = runBlocking {
        enqueueUpgrade()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { channel.entries().collect { } }
        awaitOpened()
        server.takeRequest(5, TimeUnit.SECONDS)
        kotlinx.coroutines.delay(100)

        job.cancel()
        kotlinx.coroutines.delay(300)
        // No reconnect attempt followed the cancellation (no further request).
        assertEquals(1, server.requestCount)
    }

    // Virtual time: pure-coroutine policy tests below.

    @Test
    fun `reconnect backoff grows exponentially and caps at 16s`() {
        // 1s, 2s, 4s, 8s, 16s — the exponent saturates at 2^4 (the 30s outer
        // cap is unreachable with MAX_RECONNECT_ATTEMPTS = 5; both constants
        // mirror the previous in-ViewModel implementation).
        assertEquals(1_000L, ActivityLogRealtimeChannel.reconnectDelayMs(1))
        assertEquals(2_000L, ActivityLogRealtimeChannel.reconnectDelayMs(2))
        assertEquals(4_000L, ActivityLogRealtimeChannel.reconnectDelayMs(3))
        assertEquals(8_000L, ActivityLogRealtimeChannel.reconnectDelayMs(4))
        assertEquals(16_000L, ActivityLogRealtimeChannel.reconnectDelayMs(5))
        assertEquals(16_000L, ActivityLogRealtimeChannel.reconnectDelayMs(10))
    }

    @Test
    fun `polling fallback emits only unseen entries`() = runTest {
        val seen = mutableSetOf(1L, 2L)
        coEvery { apiClient.getActivityLogEntries(limit = 10) } returns Result.success(
            listOf(
                ActivityLogEntry(id = 1, name = "seen"),
                ActivityLogEntry(id = 2, name = "seen"),
                ActivityLogEntry(id = 3, name = "new"),
            ),
        )

        val entries = channel.pollingFallbackFlow(seen).take(1).toList()

        assertEquals(listOf(3L), entries.map { it.id })
        // The newly-emitted id is now dedupe-tracked.
        assertTrue(3L in seen)
    }

    @Test
    fun `polling fallback keeps polling until new entries appear`() = runTest {
        val seen = mutableSetOf<Long>()
        var call = 0
        coEvery { apiClient.getActivityLogEntries(limit = 10) } answers {
            call++
            Result.success(
                if (call < 3) emptyList() else listOf(ActivityLogEntry(id = 9, name = "late")),
            )
        }

        val entries = channel.pollingFallbackFlow(seen).take(1).toList()

        assertEquals(listOf(9L), entries.map { it.id })
        assertEquals(3, call)
    }
}
