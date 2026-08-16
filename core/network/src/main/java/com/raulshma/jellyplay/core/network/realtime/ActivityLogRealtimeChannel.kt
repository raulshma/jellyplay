package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.toActivityLogEntry
import com.raulshma.jellyplay.core.network.websocket.buildSocketUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cold flow of live server activity-log entries over a dedicated WebSocket.
 *
 * Unlike [ScheduledTasksRealtimeChannel] (app-lifetime, ref-counted on the
 * shared socket), this channel is screen-lifetime: collection opens the
 * socket, cancelling the collector closes it. No orphaned reconnect or
 * polling jobs survive cancellation — they all live in the collector's scope.
 *
 * Resilience mirrors the previous in-ViewModel implementation: the access
 * token travels in the `X-Emby-Token` header (never a query param), the
 * device id is the app's stable id, [onFailure] retries with exponential
 * backoff, and after [MAX_RECONNECT_ATTEMPTS] failures the channel falls
 * back to REST polling of the activity-log endpoint.
 *
 * [knownIds] seeds dedupe for the polling fallback so the first poll does not
 * replay entries the caller already shows.
 */
@Singleton
class ActivityLogRealtimeChannel @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val engine: JellyfinApiEngine,
    private val serverIdentityStore: ServerIdentityStore,
) {

    fun entries(knownIds: Set<Long> = emptySet()): Flow<ActivityLogEntry> = channelFlow {
        val attempts = AtomicInteger(0)
        val seenIds = knownIds.toMutableSet()
        var webSocket: WebSocket? = null
        var reconnectJob: Job? = null

        // Socket failures arrive on OkHttp threads; route them into the
        // channel's scope so [scheduleReconnect] runs under structured
        // concurrency (and a failure burst coalesces into one reconnect).
        val failures = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST.let {
            kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = it)
        }

        suspend fun connect() {
            val serverUrl = apiClient.getServerUrl() ?: return
            val token = apiClient.getAccessToken() ?: return
            val device = serverIdentityStore.ensureDeviceId()

            val wsUrl = buildSocketUrl(
                serverAddress = serverUrl,
                deviceId = device,
                deviceName = "JellyPlay",
                client = "JellyPlay",
            )

            val request = Request.Builder()
                .url(wsUrl)
                // Header (not query param) so the token never appears in URLs/logs.
                // This is the same header Jellyfin's auth interceptor uses for REST.
                .header("X-Emby-Token", token)
                .build()
            webSocket?.cancel()
            webSocket = engine.okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    attempts.set(0)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val envelope = org.json.JSONObject(text)
                        if (envelope.optString("MessageType") == "ActivityLogEntry") {
                            val entry = envelope.optJSONObject("MessageData")?.toActivityLogEntry() ?: return
                            seenIds.add(entry.id)
                            trySend(entry)
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failures.tryEmit(Unit)
                }
            })
        }

        fun scheduleReconnect() {
            val attempt = attempts.incrementAndGet()
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                // Give up on the socket; poll REST instead until the collector cancels.
                launch { pollingFallbackFlow(seenIds).collect { trySend(it) } }
                return
            }
            reconnectJob?.cancel()
            reconnectJob = launch {
                delay(reconnectDelayMs(attempt))
                connect()
            }
        }

        launch { failures.collect { scheduleReconnect() } }
        connect()

        awaitClose {
            // cancel() (not a graceful close) so teardown never lingers on a
            // half-closed handshake — the collector is gone either way.
            webSocket?.cancel()
            webSocket = null
            reconnectJob?.cancel()
        }
    }

    /**
     * REST polling fallback: every 5s fetch the newest entries and emit only
     * ones not seen before on this channel.
     */
    internal fun pollingFallbackFlow(seenIds: MutableSet<Long>): Flow<ActivityLogEntry> = flow {
        while (true) {
            delay(POLL_INTERVAL_MS)
            val result = apiClient.getActivityLogEntries(limit = POLL_PAGE_SIZE)
            result.onSuccess { entries ->
                entries.filter { it.id !in seenIds }.forEach { entry ->
                    seenIds.add(entry.id)
                    emit(entry)
                }
            }
        }
    }

    internal companion object {
        internal const val MAX_RECONNECT_ATTEMPTS = 5
        internal const val POLL_INTERVAL_MS = 5_000L
        internal const val POLL_PAGE_SIZE = 10

        /** Exponential backoff: 1s, 2s, 4s, 8s, 16s — capped at 30s. */
        internal fun reconnectDelayMs(attempt: Int): Long =
            (1000L * (1L shl (attempt - 1).coerceAtMost(4))).coerceAtMost(30_000L)
    }
}
