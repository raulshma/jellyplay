package com.raulshma.jellyplay.core.network.websocket

import com.raulshma.jellyplay.core.model.ConnectionCredentials
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first test in the repo to drive a REAL [JellyfinWebSocketClient]
 * reconnect: a MockWebServer socket upgrade, an abrupt server-side drop
 * (the failure shape that schedules the client's backoff reconnect), and the
 * automatic re-open. Pins [JellyfinWebSocketClient.reconnects]:
 *
 *  1. the FIRST connect of a logical session does not emit;
 *  2. an open that follows a previous open (the backoff reconnect after the
 *     drop) emits exactly once;
 *  3. an explicit [JellyfinWebSocketClient.disconnect] clears the
 *     had-connected marker, so the next explicit connect's open is a fresh
 *     first connect and does not emit either.
 */
class JellyfinWebSocketClientReconnectTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyfinWebSocketClient

    /** Server-side end of every upgraded connection, in open order. */
    private val serverSockets = ConcurrentLinkedQueue<WebSocket>()
    private val terminalEvents = ConcurrentLinkedQueue<String>()

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets += webSocket
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // Complete the close handshake like a real server — without the
            // reply, MockWebServer's server-side writer never closes and its
            // connection task (and thus shutdown) wedges.
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            terminalEvents += "closed:$code"
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            terminalEvents += "failure:${t.javaClass.simpleName}"
        }
    }

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        // Every connection — the initial one AND the reconnect — is upgraded;
        // a queue-dispatched single response would starve the second attempt.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().withWebSocketUpgrade(serverListener)
        }
        server.start()
        client = JellyfinWebSocketClient(OkHttpClient())
    }

    @AfterTest
    fun tearDown() {
        // Disconnect the client FIRST, then let every server-side websocket
        // reach a terminal listener event before shutting the server down —
        // shutdown's latch can only go idle once each connection task ends.
        client.disconnect()
        val deadline = System.currentTimeMillis() + 3_000L
        while (terminalEvents.size < serverSockets.size && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        server.shutdown()
    }

    private fun credentials() = ConnectionCredentials(
        serverAddress = server.url("/").toString().trimEnd('/'),
        accessToken = "token-1",
        deviceId = "device-1",
        deviceName = "JellyPlay on Test",
        clientName = "JellyPlay",
    )

    private suspend fun awaitConnected() {
        withTimeout(10_000L) { while (!client.isConnected.value) delay(20) }
    }

    /**
     * Drops the server-side end of an upgraded connection with a bare FIN —
     * an EOF without the close handshake. Two public-API dead ends force this
     * shape: OkHttp 5.4's server-side `cancel()` NPEs (`call!!` — no call
     * exists server-side), while a graceful `close()` would land the client
     * in `onClosed`, which this client deliberately does NOT treat as a
     * reconnect trigger. A bare EOF is the `onFailure` shape that schedules
     * the backoff reconnect under test. (OkHttp 5.4's `RealWebSocket` keeps
     * the raw socket in a private `socket` field — the pre-5.x `streams`
     * member no longer exists.)
     */
    private fun dropAbruptly(serverSocket: WebSocket) {
        val socketField = serverSocket.javaClass.getDeclaredField("socket")
        socketField.isAccessible = true
        (socketField.get(serverSocket) as java.io.Closeable).close()
    }

    @Test
    fun `reconnects emits on the auto-reconnect open only, not on first or post-disconnect connects`() = runBlocking {
        val emissions = ConcurrentLinkedQueue<Unit>()
        // UNDISPATCHED: subscribed before the first connect below could open.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            client.reconnects.collect { emissions += it }
        }

        // 1. First connect of the logical session: opens, must NOT emit.
        client.connect(credentials())
        awaitConnected()
        delay(300)
        assertTrue(emissions.isEmpty(), "first connect must not emit a reconnect, got ${emissions.size}")

        // 2. Abrupt server-side drop → client onFailure → backoff schedule
        //    (1–2 s for attempt 1) → automatic re-open: one emission.
        dropAbruptly(serverSockets.peek())
        withTimeout(20_000L) { while (emissions.isEmpty()) delay(50) }
        assertEquals(1, emissions.size, "the reconnect open emits exactly once")
        assertTrue(client.isConnected.value, "the client is connected again after the drop")
        assertTrue(serverSockets.size >= 2, "the server saw the reconnect's upgraded connection")

        // 3. Explicit disconnect starts a FRESH logical session: the marker is
        //    cleared, so the next explicit connect's open is a first connect.
        client.disconnect()
        client.connect(credentials())
        awaitConnected()
        delay(300)
        assertEquals(1, emissions.size, "an explicit disconnect resets the had-connected marker")

        collector.cancel()
    }
}
