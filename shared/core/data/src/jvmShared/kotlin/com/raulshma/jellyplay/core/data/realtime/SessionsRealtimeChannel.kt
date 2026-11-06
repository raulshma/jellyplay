package com.raulshma.jellyplay.core.data.realtime

import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SessionPlayState
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Realtime stream of Jellyfin's active sessions over the shared
 * [JellyfinWebSocketClient] — the dashboard's session list counterpart of the
 * `ScheduledTasksRealtimeChannel` task feed.
 *
 * Unlike scheduled tasks, sessions need NO subscription handshake: the server
 * pushes the full `Sessions` (`SessionInfo[]`) envelope to the admin socket on
 * session/playstate changes (the remote-play transport sync already rides the
 * same push). This channel only decodes and republishes — no protocol messages
 * are sent.
 *
 * The push is decoded from the event's raw envelope text with the PascalCase
 * wire DTOs below ([SessionInfo] itself has no `@SerialName` annotations, so
 * decoding straight into it silently produces all-default objects — the
 * SessionsPayload.kt note in :core:network). The wire object is the same
 * server-side SessionInfo the REST /Sessions endpoint returns, so the mapped
 * model carries the fields the dashboard's session card renders (user, device,
 * client, now-playing) — not the minimal remote-play subset.
 *
 * Shared via [shareIn] + [SharingStarted.WhileSubscribed] like the task
 * channel: nothing emits before the first push, the last emission replays to
 * new collectors (judge recency from [lastPushAtMs], never from collection
 * time), and a malformed push is skipped entirely — no emission and no
 * [lastPushAtMs] stamp, so the last good list stays.
 */
class SessionsRealtimeChannel(
    private val webSocketClient: JellyfinWebSocketClient,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    /**
     * Wall time of the last successfully parsed Sessions push (0 = none this
     * process). Freshness signal for [sessions] consumers — see the class
     * KDoc.
     */
    @Volatile var lastPushAtMs: Long = 0L
        private set

    private val rawSessions: Flow<List<SessionInfo>> = callbackFlow {
        val eventsJob: Job = scope.launch {
            webSocketClient.events.collect { event ->
                if (event.type != MESSAGE_SESSIONS) return@collect
                val parsed = parseSessions(event) ?: return@collect
                lastPushAtMs = System.currentTimeMillis()
                trySend(parsed)
            }
        }
        awaitClose { eventsJob.cancel() }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = UNSUBSCRIBE_GRACE_MS),
            replay = 1,
        )

    /**
     * The full session list as last pushed by the server. Emits on every
     * push whose parsed contents differ from the previous emission; the
     * per-push presentation gate (what the session card can actually render)
     * stays with the collector — the task feed's gate idiom.
     */
    val sessions: Flow<List<SessionInfo>> = rawSessions

    private fun parseSessions(event: WebSocketEvent): List<SessionInfo>? = try {
        json.decodeFromString(WsSessionsEnvelopeDto.serializer(), event.rawText).data.map { it.toSessionInfo() }
    } catch (_: Exception) {
        null
    }

    private companion object {
        private const val MESSAGE_SESSIONS = "Sessions"
        private const val UNSUBSCRIBE_GRACE_MS = 5_000L
    }
}

/** Envelope of a `Sessions` WS push (`Data` = the server's `SessionInfo[]`). */
@Serializable
private data class WsSessionsEnvelopeDto(
    @SerialName("Data") val data: List<WsAdminSessionDto> = emptyList(),
)

/**
 * The dashboard-side view of the wire SessionInfo: the fields the session
 * card renders plus identity/remote-control flags. Everything else on the
 * wire stays ignored under `ignoreUnknownKeys`.
 */
@Serializable
private data class WsAdminSessionDto(
    @SerialName("Id") val id: String = "",
    @SerialName("DeviceId") val deviceId: String? = null,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserName") val userName: String? = null,
    @SerialName("Client") val client: String? = null,
    @SerialName("DeviceName") val deviceName: String? = null,
    @SerialName("DeviceType") val deviceType: String? = null,
    @SerialName("LastActivityDate") val lastActivityDate: String? = null,
    @SerialName("IsActive") val isActive: Boolean = false,
    @SerialName("SupportsRemoteControl") val supportsRemoteControl: Boolean = false,
    @SerialName("PlayState") val playState: WsAdminSessionPlayStateDto? = null,
    @SerialName("NowPlayingItem") val nowPlayingItem: WsAdminSessionNowPlayingItemDto? = null,
)

@Serializable
private data class WsAdminSessionPlayStateDto(
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("IsPaused") val isPaused: Boolean? = null,
    @SerialName("IsMuted") val isMuted: Boolean? = null,
    @SerialName("VolumeLevel") val volumeLevel: Int? = null,
    @SerialName("RepeatMode") val repeatMode: String? = null,
    @SerialName("PlayMethod") val playMethod: String? = null,
)

@Serializable
private data class WsAdminSessionNowPlayingItemDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
)

/** Maps the wire DTO onto the shared [SessionInfo] model (the REST mapper's shape). */
private fun WsAdminSessionDto.toSessionInfo() = SessionInfo(
    id = id,
    deviceId = deviceId.orEmpty(),
    userId = userId.orEmpty(),
    userName = userName.orEmpty(),
    client = client.orEmpty(),
    lastActivityDate = lastActivityDate.orEmpty(),
    deviceName = deviceName.orEmpty(),
    deviceType = deviceType.orEmpty(),
    nowPlayingItem = nowPlayingItem?.let {
        SessionNowPlayingItem(
            id = it.id.orEmpty(),
            name = it.name.orEmpty(),
            type = it.type.orEmpty(),
            mediaType = it.mediaType,
            runTimeTicks = it.runTimeTicks,
            primaryImageTag = it.imageTags?.entries?.firstOrNull { entry -> entry.key == "Primary" }?.value
                ?: it.imageTags?.entries?.firstOrNull()?.value,
            seriesName = it.seriesName,
            backdropImageTag = it.backdropImageTags?.firstOrNull(),
        )
    },
    playState = playState?.let {
        SessionPlayState(
            positionTicks = it.positionTicks,
            isPaused = it.isPaused ?: false,
            isMuted = it.isMuted ?: false,
            volumeLevel = it.volumeLevel,
            repeatMode = it.repeatMode ?: "RepeatNone",
            playMethod = it.playMethod,
        )
    },
    isActive = isActive,
    supportsRemoteControl = supportsRemoteControl,
)
