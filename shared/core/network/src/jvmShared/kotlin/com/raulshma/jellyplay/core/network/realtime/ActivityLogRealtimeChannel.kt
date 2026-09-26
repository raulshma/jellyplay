package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.trimToSize
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.WebSocketBackoffPolicy
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.toActivityLogEntry
import com.raulshma.jellyplay.core.network.auth.tokenAuthHeader
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

/**
 * Cold flow of live server activity-log entries over a dedicated WebSocket.
 *
 * Unlike [ScheduledTasksRealtimeChannel] (app-lifetime, ref-counted on the
 * shared socket), this channel is screen-lifetime: collection opens the
 * socket, cancelling the collector closes it. No orphaned reconnect or
 * polling jobs survive cancellation — they all live in the collector's scope.
 *
 * Resilience mirrors the previous in-ViewModel implementation: the access
 * token travels in the `Authorization` header (never a query param; the
 * legacy `X-Emby-Token` header 401s against Jellyfin 12 servers, which gate
 * it behind `EnableLegacyAuthorization`), the device id is the app's stable
 * id, [onFailure] retries with the module's exponential
 * [WebSocketBackoffPolicy] (deterministic here — no jitter), and once that
 * budget is exhausted the channel falls back to REST polling of the
 * activity-log endpoint.
 *
 * [knownIds] seeds dedupe for the polling fallback so the first poll does not
 * replay entries the caller already shows.
 */
class ActivityLogRealtimeChannel(
    private val apiClient: JellyfinApiClient,
    private val engine: JellyfinApiEngine,
    private val serverIdentityStore: ServerIdentityStore,
) {

    /**
     * The reconnect schedule (1s → 2s → 4s → 8s → 16s, capped at 30s) — the
     * module's one backoff law. No jitter: this channel keeps its previous
     * deterministic behavior. A `null` delay (past
     * [WebSocketBackoffPolicy.maxAttempts] failures) is the leave-the-socket
     * signal → the polling fallback below.
     */
    internal val backoff = WebSocketBackoffPolicy()

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
                // The SDK's scheme — the one auth form every server version accepts
                // (token-only is sufficient; the server reads just the Token param).
                .tokenAuthHeader(token)
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
                            seenIds.addCapped(entry.id)
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
            val delayMs = backoff.delayMs(attempts.incrementAndGet())
            if (delayMs == null) {
                // Give up on the socket; poll REST instead until the collector cancels.
                launch { pollingFallbackFlow(seenIds).collect { trySend(it) } }
                return
            }
            reconnectJob?.cancel()
            reconnectJob = launch {
                delay(delayMs)
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
                    seenIds.addCapped(entry.id)
                    emit(entry)
                }
            }
        }
    }

    internal companion object {
        internal const val POLL_INTERVAL_MS = 5_000L
        internal const val POLL_PAGE_SIZE = 10

        /**
         * Cap for the dedup id set — the polling fallback otherwise grows it
         * ~10 ids / 5 s for the life of the channel (~7k/hour).
         */
        internal const val MAX_SEEN_IDS = 1_000
    }
}

/** [MutableSet.add] that evicts the oldest entry past [ActivityLogRealtimeChannel.MAX_SEEN_IDS]. */
private fun MutableSet<Long>.addCapped(id: Long) {
    add(id)
    trimToSize(ActivityLogRealtimeChannel.MAX_SEEN_IDS)
}
