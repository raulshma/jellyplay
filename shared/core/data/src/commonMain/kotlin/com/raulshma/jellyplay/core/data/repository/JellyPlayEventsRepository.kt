package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlaySseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Typed SSE event pushed by the plugin's events stream.
 */
sealed interface JellyPlayPluginEvent {
    /** A new movie or a grouped season drop. [itemId] deep-links; [episodeCount] > 1 marks a group. */
    data class NewMedia(
        val itemId: String,
        val seriesId: String?,
        val seasonIndex: Int?,
        val title: String,
        val episodeCount: Int,
    ) : JellyPlayPluginEvent

    data class Broadcast(val title: String, val body: String, val url: String?) : JellyPlayPluginEvent

    data class SessionStarted(val username: String) : JellyPlayPluginEvent

    data class PlaybackStarted(val username: String) : JellyPlayPluginEvent

    data class UserLockedOut(val username: String) : JellyPlayPluginEvent

    /** Unrecognized event name — forward-compatible pass-through. */
    data class Unknown(val event: String, val data: String) : JellyPlayPluginEvent
}

/**
 * The events/messages face of the jellyfin-plugin-jellyplay plugin: device
 * registration, the live events SSE stream decoded into
 * [JellyPlayPluginEvent]s, and the admin inbox messages.
 *
 * [start] launches ONE stream consumer in [scope]; reconnects use linear
 * backoff capped at 60s, reset on a successful connection (events are
 * best-effort live pushes — the inbox covers durability, no replay needed).
 * Consumers collect [events]; UI also reads [inbox]. Session-scoped state
 * resets via the plugin status store's identity invalidation.
 */
class JellyPlayEventsRepository(
    private val apiClient: JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val devicePlatform: String,
    private val appVersion: String,
    private val deviceIdProvider: suspend () -> String,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<JellyPlayPluginEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<JellyPlayPluginEvent> = _events.asSharedFlow()

    private val _inbox = MutableStateFlow<List<JellyPlayMessage>>(emptyList())
    val inbox: StateFlow<List<JellyPlayMessage>> = _inbox.asStateFlow()

    private val _streamConnected = MutableStateFlow(false)
    val streamConnected: StateFlow<Boolean> = _streamConnected.asStateFlow()

    private var streamJob: Job? = null

    /** Registers this device and starts the live stream. Safe to call repeatedly (idempotent). */
    suspend fun start() {
        if (streamJob?.isActive == true) return
        if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
            statusStore.refresh()
            if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) return
        }

        deviceIdProvider().let { deviceId ->
            apiClient.registerDevice(deviceId, deviceName, devicePlatform, appVersion)
        }
        refreshInbox()

        streamJob = scope.launch { runEventStream() }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _streamConnected.value = false
    }

    suspend fun refreshInbox() {
        _inbox.value = apiClient.getMessages().getOrDefault(_inbox.value)
    }

    suspend fun markRead(messageId: String) {
        apiClient.markMessageRead(messageId)
        _inbox.value = _inbox.value.map { if (it.id == messageId) it.copy(read = true) else it }
    }

    private suspend fun runEventStream() {
        var backoffMillis = 1_000L
        while (streamJob?.isActive == true) {
            try {
                apiClient.eventsStream().collect { sse ->
                    backoffMillis = 1_000L
                    _streamConnected.value = true
                    decode(sse)?.let { _events.emit(it) }
                }
                // Server closed the stream cleanly — still a drop for our purposes.
                _streamConnected.value = false
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _streamConnected.value = false
            }

            if (streamJob?.isActive != true) break
            kotlinx.coroutines.delay(backoffMillis)
            backoffMillis = (backoffMillis * 2).coerceAtMost(60_000L)
            // Re-probe: a plugin installed mid-session should light the stream up.
            if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
                statusStore.refresh()
            }
        }
    }

    private fun decode(sse: JellyPlaySseEvent): JellyPlayPluginEvent? = try {
        when (sse.event) {
            "new-media" -> {
                val obj = json.parseToJsonElement(sse.data).let { it as? kotlinx.serialization.json.JsonObject }
                    ?: return null
                JellyPlayPluginEvent.NewMedia(
                    itemId = (obj["itemId"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null,
                    seriesId = (obj["seriesId"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
                    seasonIndex = (obj["seasonIndex"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull(),
                    title = (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(),
                    episodeCount = (obj["episodeCount"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1,
                )
            }
            "broadcast" -> {
                val obj = json.parseToJsonElement(sse.data).let { it as? kotlinx.serialization.json.JsonObject }
                    ?: return null
                JellyPlayPluginEvent.Broadcast(
                    title = (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(),
                    body = (obj["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(),
                    url = (obj["url"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
                )
            }
            "session-started", "playback-started", "user-locked-out" -> {
                val obj = json.parseToJsonElement(sse.data).let { it as? kotlinx.serialization.json.JsonObject }
                    ?: return null
                val username = (obj["username"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                when (sse.event) {
                    "session-started" -> JellyPlayPluginEvent.SessionStarted(username)
                    "playback-started" -> JellyPlayPluginEvent.PlaybackStarted(username)
                    else -> JellyPlayPluginEvent.UserLockedOut(username)
                }
            }
            else -> JellyPlayPluginEvent.Unknown(sse.event, sse.data)
        }
    } catch (t: kotlinx.serialization.SerializationException) {
        null
    }
}
