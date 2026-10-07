package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.network.api.CAP_SILENT_PUSH
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
 * [start] launches ONE stream consumer in [scope]; reconnects use exponential
 * (doubling) backoff capped at 60s, reset on a successful connection (events
 * are best-effort live pushes — the inbox covers durability, no replay
 * needed). Consumers collect [events]; UI also reads [inbox]. Session-scoped
 * state resets via the plugin status store's identity invalidation.
 */
class JellyPlayEventsRepository(
    private val apiClient: JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val devicePlatform: String,
    private val appVersion: String,
    private val deviceIdProvider: suspend () -> String,
    /** Hardware model for the registry row (null where the platform has none). */
    private val deviceModel: String? = null,
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
        // The probe ladder AND the registry's `events` key — the ONE gating
        // mechanism (ADR 0010 §1/§6): a server whose handshake omits `events`
        // never gets a device registration or an SSE attempt.
        if (!statusStore.ensureAvailable() || !statusStore.hasFeature(FEATURE_EVENTS)) return

        deviceIdProvider().let { deviceId ->
            // The caps assertion rides EVERY registration (the wire REPLACES
            // caps): "silent-push" opts this device into the sync-nudge
            // silent push, whose client branch folds into requestSync.
            apiClient.registerDevice(
                deviceId,
                deviceName,
                devicePlatform,
                appVersion,
                caps = listOf(CAP_SILENT_PUSH),
                model = deviceModel,
            )
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
        val reconnect = SseReconnectLoop()
        while (streamJob?.isActive == true) {
            try {
                apiClient.eventsStream().collect { sse ->
                    reconnect.onConnected()
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
            reconnect.awaitRetryDelay()
            // Re-probe: a plugin installed mid-session should light the stream up.
            statusStore.ensureAvailable()
        }
    }

    private fun decode(sse: JellyPlaySseEvent): JellyPlayPluginEvent? = try {
        fun obj(data: String) = json.parseToJsonElement(data) as? kotlinx.serialization.json.JsonObject

        /** The one primitive read every payload field decodes through. */
        fun kotlinx.serialization.json.JsonObject.text(name: String): String? =
            (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content

        when (sse.event) {
            "new-media" -> {
                val payload = obj(sse.data) ?: return null
                JellyPlayPluginEvent.NewMedia(
                    itemId = payload.text("itemId") ?: return null,
                    seriesId = payload.text("seriesId"),
                    seasonIndex = payload.text("seasonIndex")?.toIntOrNull(),
                    title = payload.text("title").orEmpty(),
                    episodeCount = payload.text("episodeCount")?.toIntOrNull() ?: 1,
                )
            }
            "broadcast" -> {
                val payload = obj(sse.data) ?: return null
                JellyPlayPluginEvent.Broadcast(
                    title = payload.text("title").orEmpty(),
                    body = payload.text("body").orEmpty(),
                    url = payload.text("url"),
                )
            }
            "session-started", "playback-started", "user-locked-out" -> {
                val payload = obj(sse.data) ?: return null
                val username = payload.text("username").orEmpty()
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

    private companion object {
        /** The registry key this face gates on (ADR 0010 §6). */
        const val FEATURE_EVENTS = com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.Events
    }
}
