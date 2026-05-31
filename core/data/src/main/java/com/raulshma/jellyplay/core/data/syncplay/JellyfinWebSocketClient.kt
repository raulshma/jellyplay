package com.raulshma.jellyplay.core.data.syncplay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class WebSocketEvent(
    val type: String,
    val data: JSONObject,
)

@Singleton
class JellyfinWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverUrl: String? = null
    private var token: String? = null
    private var deviceId: String? = null
    private var deviceName: String? = null
    private var clientName: String? = null
    private val reconnectAttempts = AtomicInteger(0)
    private val maxReconnectAttempts = 5

    private val _isConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isConnected: kotlinx.coroutines.flow.StateFlow<Boolean> = _isConnected

    fun connect(serverAddress: String, accessToken: String, device: String, deviceName: String = "JellyPlay", client: String = "JellyPlay") {
        disconnect()
        serverUrl = serverAddress
        token = accessToken
        deviceId = device
        this.deviceName = deviceName
        clientName = client
        reconnectAttempts.set(0)
        connectInternal()
    }

    private fun connectInternal() {
        val serverAddress = serverUrl ?: return
        val accessToken = token ?: return
        val device = deviceId ?: return

        val wsUrl = buildWsUrl(serverAddress, accessToken, device)
        Log.d(TAG, "Connecting WebSocket to $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                reconnectAttempts.set(0)
                _isConnected.value = true
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
                webSocket.close(1000, "reconnecting after failure")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _isConnected.value = false
            }
        })
    }

    private fun scheduleReconnect() {
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts > maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts ($maxReconnectAttempts) reached, giving up")
            return
        }
        val delayMs = (1000L * attempts).coerceAtMost(30_000L)
        scope.launch {
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
        reconnectAttempts.set(maxReconnectAttempts + 1)
        _isConnected.value = false
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    private fun buildWsUrl(serverAddress: String, accessToken: String, device: String): String {
        val base = serverAddress.trim().trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val name = deviceName?.let { "&deviceName=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val client = clientName?.let { "&client=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""
        return "$base/socket?api_key=$accessToken&deviceId=$device$name$client"
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val messageType = json.optString("MessageType", "")
            val data = json.optJSONObject("Data") ?: JSONObject()

            when (messageType) {
                "SyncPlayCommand",
                "SyncPlayGroupUpdate",
                "Play",
                "Playstate",
                "GeneralCommand",
                "KeepAlive",
                "GroupJoined",
                "GroupLeft" -> {
                    _events.tryEmit(WebSocketEvent(type = messageType, data = data))
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

    companion object {
        private const val TAG = "JellyfinWS"
    }
}
