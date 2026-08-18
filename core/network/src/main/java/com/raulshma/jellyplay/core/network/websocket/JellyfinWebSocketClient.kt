package com.raulshma.jellyplay.core.network.websocket

import android.util.Log
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * One entry in the inbound Jellyfin WebSocket message stream.
 *
 * @param type raw Jellyfin `MessageType` string (e.g. `"ScheduledTasksInfo"`, `"Sessions"`)
 * @param data the `Data` payload as a [JSONObject] for object-payload message types. Empty
 *   when the payload is not an object — array payloads are exposed via [dataArray], and
 *   primitive payloads (`ForceKeepAlive.Data` is a number) never reach consumers.
 * @param dataArray the `Data` payload as a [JSONArray] for array-payload message types
 *   (`Sessions` = `SessionInfo[]`, `ScheduledTasksInfo` = `TaskInfo[]`), else null.
 *   Consumers of those types must read this field instead of `data`.
 */
data class WebSocketEvent(
    val type: String,
    val data: JSONObject,
    val dataArray: JSONArray? = null,
    val rawText: String,
)

@Singleton
class JellyfinWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var serverUrl: String? = null
    @Volatile private var token: String? = null
    @Volatile private var deviceId: String? = null
    @Volatile private var deviceName: String? = null
    @Volatile private var clientName: String? = null
    private val reconnectAttempts = AtomicInteger(0)
    private val maxReconnectAttempts = 5
    private var backgroundRetryJob: Job? = null
    private var reconnectJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // KeepAlive engine -------------------------------------------------------
    // The server drops the socket after its idle timeout (default 60s) unless the
    // client pings. Jellyfin's TS SDK handles this inside the socket service; we
    // mirror it here so the app-lifetime socket owned by MainViewModel stays up
    // for non-SyncPlay consumers (admin dashboards, remote-control receivers).
    // Previously this ran only while a SyncPlay session was active, which timed
    // out the socket in admin-only sessions and broke realtime task updates.
    private var keepAliveJob: Job? = null

    fun connect(credentials: ConnectionCredentials) {
        if (isConnected.value &&
            serverUrl == credentials.serverAddress &&
            token == credentials.accessToken &&
            deviceId == credentials.deviceId &&
            deviceName == credentials.deviceName &&
            clientName == credentials.clientName
        ) {
            Log.d(TAG, "WebSocket already connected to this server, skipping connect")
            return
        }
        disconnect()
        serverUrl = credentials.serverAddress
        token = credentials.accessToken
        deviceId = credentials.deviceId
        deviceName = credentials.deviceName
        clientName = credentials.clientName
        reconnectAttempts.set(0)
        connectInternal()
    }

    private fun connectInternal() {
        val serverAddress = serverUrl ?: return
        val accessToken = token ?: return
        val device = deviceId ?: return

        val wsUrl = buildSocketUrl(serverAddress, device, deviceName, clientName, accessToken)
        // Log only the base endpoint — the full URL carries the access token
        // and deviceId as query parameters.
        Log.d(TAG, "Connecting WebSocket to ${serverAddress.trimEnd('/')}/socket")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                reconnectAttempts.set(0)
                _isConnected.value = true
                startKeepAlive()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "WebSocket message: ${text.take(200)}")
                }
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure", t)
                _isConnected.value = false
                stopKeepAlive()
                webSocket.close(1000, "reconnecting after failure")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _isConnected.value = false
                stopKeepAlive()
            }
        })
    }

    /**
     * Self-pings the server at the current keep-alive interval. Default 30s is safe
     * under the server's default 60s timeout; an inbound `ForceKeepAlive` message
     * (Data = timeoutMs) tightens the interval to timeoutMs/2.
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            try {
                while (true) {
                    delay(keepAliveIntervalMs.get())
                    try {
                        sendKeepAlive()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected on disconnect / reconnect.
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private val keepAliveIntervalMs = java.util.concurrent.atomic.AtomicLong(DEFAULT_KEEP_ALIVE_INTERVAL_MS)

    private fun handleForceKeepAlive(timeoutMs: Long) {
        val half = (timeoutMs / 2).coerceAtLeast(1_000L)
        if (half != keepAliveIntervalMs.get()) {
            keepAliveIntervalMs.set(half)
            Log.d(TAG, "ForceKeepAlive: server timeout=${timeoutMs}ms, pinging every ${half}ms")
            // Restart the loop so the new interval takes effect immediately.
            if (_isConnected.value) startKeepAlive()
        }
    }

    private fun scheduleReconnect() {
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts > maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts ($maxReconnectAttempts) reached, scheduling slow background retry")
            backgroundRetryJob?.cancel()
            backgroundRetryJob = scope.launch {
                while (serverUrl != null && token != null) {
                    delay(60_000L)
                    if (serverUrl != null && token != null) {
                        Log.d(TAG, "Background WebSocket retry")
                        reconnectAttempts.set(0)
                        connectInternal()
                        return@launch
                    }
                }
            }
            return
        }
        val delayMs = (1000L * (1L shl (attempts - 1).coerceAtMost(4)) + (0..1000L).random()).coerceAtMost(30_000L)
        // Cancel any in-flight reconnect attempt before scheduling a new one.
        // Without this, a rapid connect/disconnect cycle (e.g. user toggling
        // SyncPlay on a flaky network) could leak the deferred connectInternal()
        // call and end up with two WebSockets racing each other.
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (serverUrl != null && token != null) {
                Log.d(TAG, "Reconnecting WebSocket (attempt $attempts)")
                connectInternal()
            }
        }
    }

    fun disconnect() {
        serverUrl = null
        token = null
        deviceId = null
        backgroundRetryJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempts.set(maxReconnectAttempts + 1)
        _isConnected.value = false
        stopKeepAlive()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val messageType = json.optString("MessageType", "")
            // ForceKeepAlive is protocol-level: it adjusts the keep-alive cadence
            // and must not leak to feature consumers.
            if (messageType == "ForceKeepAlive") {
                val timeoutMs = json.opt("Data")?.toString()?.toLongOrNull() ?: return
                handleForceKeepAlive(timeoutMs)
                return
            }

            // Child `Data` construction only happens for emitted types — most
            // inbound traffic (UserDataChanged, LibraryChanged, …) is dropped
            // here and must not pay for object construction.
            when (messageType) {
                "SyncPlayCommand",
                "SyncPlayGroupUpdate",
                "Play",
                "Playstate",
                "GeneralCommand",
                "KeepAlive",
                "GroupJoined",
                "GroupLeft" -> {
                    val data = json.optJSONObject("Data") ?: JSONObject()
                    _events.tryEmit(WebSocketEvent(type = messageType, data = data, rawText = text))
                }
                // Array payloads: `Data` is a JSON array, which the object-typed
                // `data` field cannot represent. `Sessions` consumers decode the
                // raw envelope text directly (array-aware DTO in SessionsPayload.kt),
                // so no org.json child construction happens on this hot path —
                // it arrives on every session/playstate change of any client.
                // (Sessions previously fell into optJSONObject → always `{}`.)
                "Sessions" -> {
                    _events.tryEmit(WebSocketEvent(type = messageType, data = JSONObject(), rawText = text))
                }
                // Scheduled-task realtime updates. Server only pushes these once
                // the client sends ScheduledTasksInfoStart (handled by the
                // realtime channel, not here). Consumers read `dataArray`.
                "ScheduledTasksInfo" -> {
                    _events.tryEmit(
                        WebSocketEvent(
                            type = messageType,
                            data = JSONObject(),
                            dataArray = json.optJSONArray("Data"),
                            rawText = text,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WebSocket message", e)
        }
    }

    fun sendKeepAlive() {
        val msg = JSONObject().put("MessageType", "KeepAlive")
        Log.d(TAG, "Sending keep-alive")
        webSocket?.send(msg.toString())
    }

    fun sendMessage(messageType: String, data: JSONObject = JSONObject()) {
        val msg = JSONObject().apply {
            put("MessageType", messageType)
            put("Data", data)
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Sending WS message: ${msg.toString().take(200)}")
        }
        webSocket?.send(msg.toString())
    }

    /**
     * Sends a message whose payload is a plain string rather than a JSON object —
     * used by `ScheduledTasksInfoStart` whose `Data` is `"0,1000"`
     * (initialDelayMs,intervalMs).
     */
    fun sendMessageWithDataString(messageType: String, dataString: String) {
        val msg = JSONObject().apply {
            put("MessageType", messageType)
            put("Data", dataString)
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Sending WS message: ${msg.toString().take(200)}")
        }
        webSocket?.send(msg.toString())
    }

    companion object {
        private const val TAG = "JellyfinWS"

        // Safe under the server's default 60s idle timeout. A server-sent
        // ForceKeepAlive can tighten this at runtime.
        private const val DEFAULT_KEEP_ALIVE_INTERVAL_MS = 30_000L
    }
}
