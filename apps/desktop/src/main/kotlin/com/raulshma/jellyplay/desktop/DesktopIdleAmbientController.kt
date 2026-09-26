package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import com.raulshma.jellyplay.core.network.websocket.parseSessionsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The idle "Ready to play" ambient seam (extracted from
 * [DesktopNavScaffold], the same move DesktopUpdateCheckController made for
 * the About row): ONE object owns the three things the scaffold's overlay
 * needs, so the scaffold keeps only the collect, the start/stop
 * [DisposableEffect] and the [DesktopIdleOverlay] call.
 *
 *  1. the [DesktopIdleMonitor] (idle decision + input resets — see its KDoc
 *     for the pinned ladder), constructed over the same injected probes the
 *     scaffold used to inline;
 *  2. the active-remote-session count off the receiver socket's `Sessions`
 *     WS push — the raw envelope text is decoded with the array-aware DTO
 *     ([parseSessionsMessage], one kotlinx pass, no org.json detour), and a
 *     malformed push degrades to 0 rather than crashing the collector. The
 *     collection is idle-GATED exactly as the inlined `LaunchedEffect(isIdle)`
 *     was: the count is only written while the overlay is up, and it STAYS at
 *     its last value when idle ends (the next idle period refreshes it on the
 *     first `Sessions` push — deliberately not reset on dismissal);
 *  3. the overlay's identity lines ([resolveIdleOverlayIdentity] — the pure
 *     server/user fold, pinned by [DesktopIdleAmbientControllerTest]).
 *
 * All probes are `() -> …` lambdas and the socket arrives as a
 * `Flow<WebSocketEvent>` (the [DesktopIdleMonitor] idiom), so the whole seam
 * is JVM-testable without AWT, engines, DataStore or OkHttp.
 *
 * @param sessionsEvents the receiver's socket event stream
 *   (`webSocketClient.events` at the call site).
 */
internal class DesktopIdleAmbientController(
    settings: () -> IdleAmbientSettings,
    isAudioPlaying: () -> Boolean,
    isVideoActive: () -> Boolean,
    isWindowActive: () -> Boolean,
    private val sessionsEvents: Flow<WebSocketEvent>,
    nowMs: () -> Long = System::currentTimeMillis,
) {

    private val monitor = DesktopIdleMonitor(
        settings = settings,
        isAudioPlaying = isAudioPlaying,
        isVideoActive = isVideoActive,
        isWindowActive = isWindowActive,
        nowMs = nowMs,
    )

    val isIdle: StateFlow<Boolean> get() = monitor.isIdle

    private val _activeRemoteSessionCount = MutableStateFlow(0)
    val activeRemoteSessionCount: StateFlow<Int> = _activeRemoteSessionCount.asStateFlow()

    private var countJob: Job? = null

    /** Any key/pointer input: reset the debounce and dismiss immediately. */
    fun onUserInput() {
        monitor.onUserInput()
    }

    /**
     * One idle evaluation — internal so the test can step the machine
     * manually (the same seam [DesktopIdleMonitor.tick] exposes; the started
     * 1 s loop normally drives it).
     */
    internal fun tick() {
        monitor.tick()
    }

    /** Starts the idle ticks + the idle-gated session-count collector; idempotent. */
    fun start(scope: CoroutineScope) {
        monitor.start(scope)
        if (countJob?.isActive == true) return
        countJob = scope.launch {
            // collectLatest == the inlined effect's `LaunchedEffect(isIdle)`
            // keying: an idle edge cancels the previous collection, and a
            // non-idle value collects nothing (events: SharedFlow, no replay,
            // so nothing stale is delivered on re-entry either).
            monitor.isIdle.collectLatest { idle ->
                if (!idle) return@collectLatest
                sessionsEvents.collect { event ->
                    if (event.type == SESSIONS_MESSAGE_TYPE) {
                        _activeRemoteSessionCount.value = runCatching {
                            parseSessionsMessage(sessionJson, event.rawText)
                        }.getOrDefault(emptyList()).count { it.nowPlayingItem != null }
                    }
                }
            }
        }
    }

    fun stop() {
        countJob?.cancel()
        countJob = null
        monitor.stop()
    }

    private companion object {
        const val SESSIONS_MESSAGE_TYPE = "Sessions"

        /** Same decoder config the inlined scaffold effect used. */
        val sessionJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The overlay's identity lines, as one pure fold: the current user's server
 * (matched by stored server id OR by address — the legacy two-key match the
 * inlined composition used) and the user's display name; both null when
 * signed out. Top-level like [shouldEnterIdle] so the test pins it directly.
 */
internal fun resolveIdleOverlayIdentity(
    currentUser: UserInfo?,
    servers: List<ServerInfo>,
): IdleOverlayIdentity = IdleOverlayIdentity(
    serverName = currentUser?.let { user ->
        servers.firstOrNull { it.id == user.serverId || it.address == user.serverAddress }?.name
    },
    userName = currentUser?.name,
)

/** The [DesktopIdleOverlay] identity line pair ([resolveIdleOverlayIdentity]). */
internal data class IdleOverlayIdentity(
    val serverName: String?,
    val userName: String?,
)
