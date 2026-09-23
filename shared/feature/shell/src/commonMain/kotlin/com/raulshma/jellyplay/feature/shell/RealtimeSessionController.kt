package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The authenticated-edge realtime choreography both shells duplicated
 * (Android `SessionCoordinator` / desktop `DesktopSessionCoordinator`):
 * connect the session WebSocket on the auth true edge, start the
 * remote-control receiver with it, disconnect + stop the receiver on the
 * false edge, and re-arm capabilities on every socket (re)connect.
 *
 * Lives in shared/feature/shell as the second half of the session-policy
 * wiring (ADR 0001's construction pattern, beside [ShellSessionController]):
 * pure inputs only — flows and lambdas, no Koin binding, no repository types —
 * so the module keeps its repository-free dependency set. Each shell
 * constructs it directly with its own [CoroutineScope], adapting its
 * `RealtimeConnection` / receiver to the narrow lambda seams below.
 * Per-shell remains: the client name and desktop's session restore
 * (stays in `DesktopAppRoot`); the Android-only fan-out (health monitor,
 * widgets, cache maintenance, library folders) hangs off [onConnected].
 *
 * Choreography (identical in both former copies):
 *  - auth true: resolve (server, user); when both exist, [connect] with
 *    credentials whose server address prefers the transport's ACTIVE
 *    endpoint ([serverUrl] — the reachable address after failover) over
 *    `ServerInfo.address` (a possibly-dead primary), then [startReceiver],
 *    then [onConnected] with the SAME resolved pair. Edge: the arm reads
 *    the flows with `first()` — if auth flips true before the server/user
 *    StateFlows are populated it no-ops and never retries (auth will not
 *    re-emit), skipping BOTH the credentials and [onConnected].
 *    Behavior-identical to both retired host copies; hosts populate
 *    server/user before setting auth true.
 *  - auth false: [disconnect] + [stopReceiver] (logout teardown rides the
 *    same edge).
 *  - socket opens: wait for the FIRST connect off [isConnected] (the
 *    immediate arm — [reconnects] carries no replay, so this is the only
 *    already-up arm), then every true reconnect. Each arm gates on the live
 *    [isAuthenticated] state at dispatch time so a stray connect during
 *    teardown never fires a stale [onReconnect], and swallows [onReconnect]
 *    failures (cancellation passes through) so one failed post cannot kill
 *    the reconnect watcher.
 *
 * The capabilities timing subtlety the arms encode: the Jellyfin server
 * computes a session's `SupportsRemoteControl` from the posted capabilities
 * AND an attached WebSocket controller, and `POST /Sessions/Capabilities/Full`
 * throws when it races ahead of the handshake — so the post must land after
 * the server session exists, and must be RE-sent after every drop (the
 * controller is gone after a socket drop and must be re-armed).
 *
 * [stop] cancels both collectors and stops the receiver — desktop calls it
 * from composition teardown; Android calls it before rebuilding the
 * controller on a lifecycle restart so occupants never duplicate.
 *
 * @param scope the host's own coroutine scope (Android: the activity-scoped
 *   ViewModel scope; desktop: the composition scope) — every collector dies
 *   with it, exactly as the inlined copies did.
 * @param isAuthenticated the auth state to choreograph around and to gate
 *   reconnect arms with (the hosts pass `AuthRepository.isAuthenticated`).
 * @param currentServer the signed-in server flow (`null` when signed out).
 * @param currentUser the signed-in user flow (`null` when signed out);
 *   supplies the access token and the device-name user portion.
 * @param clientName the host's reported client identity ("JellyPlay" /
 *   "JellyPlay Desktop").
 * @param serverUrl the transport's currently active endpoint, or `null` when
 *   no server is connected (`RealtimeConnection.serverUrl`).
 * @param isConnected the transport's live handshake state.
 * @param reconnects the transport's true-reconnect stream (no first-connect
 *   replay — see `RealtimeConnection.reconnects`).
 * @param connect opens (or re-opens) the socket for the assembled
 *   credentials.
 * @param disconnect closes the socket.
 * @param startReceiver arms the remote-control receiver on the auth true edge.
 * @param stopReceiver disarms it on the false edge and in [stop].
 * @param ensureDeviceId resolves the persistent device id (the identity
 *   store's `ensureDeviceId`).
 * @param onReconnect the per-(re)connect work — the capabilities re-post.
 * @param onConnected the host's authenticated fan-out, fired once per
 *   auth-true edge AFTER [startReceiver] with the same resolved
 *   (server, user) pair the credentials used (the flow values — the
 *   server's `address` is the possibly-dead primary, not the
 *   endpoint-selected URL). Auth-edge work only: socket reconnects re-arm
 *   capabilities via [onReconnect] but never re-fire this; a fresh
 *   sign-in does. The pair-null never-retry no-op skips it exactly as it
 *   skips the credentials. Default no-op (desktop passes nothing).
 */
class RealtimeSessionController(
    private val scope: CoroutineScope,
    private val isAuthenticated: Flow<Boolean>,
    private val currentServer: Flow<ServerInfo?>,
    private val currentUser: Flow<UserInfo?>,
    private val clientName: String,
    private val serverUrl: () -> String?,
    private val isConnected: StateFlow<Boolean>,
    private val reconnects: SharedFlow<Unit>,
    private val connect: (ConnectionCredentials) -> Unit,
    private val disconnect: () -> Unit,
    private val startReceiver: () -> Unit,
    private val stopReceiver: () -> Unit,
    private val ensureDeviceId: suspend () -> String,
    private val onReconnect: suspend () -> Unit,
    private val onConnected: suspend (ServerInfo, UserInfo) -> Unit = { _, _ -> },
) {

    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch {
            isAuthenticated.collect { isAuth ->
                if (isAuth) {
                    val server = currentServer.first()
                    val user = currentUser.first()
                    if (server != null && user != null) {
                        connect(
                            ConnectionCredentials(
                                serverAddress = serverUrl() ?: server.address,
                                accessToken = user.accessToken,
                                deviceId = ensureDeviceId(),
                                deviceName = ConnectionCredentials.deviceNameFor(user.name),
                                clientName = clientName,
                            )
                        )
                        startReceiver()
                        // The host's authenticated fan-out rides the same
                        // arm, same pair, same null-gate — never re-fired
                        // by the reconnect watcher below.
                        onConnected(server, user)
                    }
                } else {
                    disconnect()
                    stopReceiver()
                }
            }
        }
        jobs += scope.launch {
            if (!isConnected.value) {
                isConnected.first { it }
            }
            armCapabilities()
            reconnects.collect {
                armCapabilities()
            }
        }
    }

    /**
     * Cancels both collectors and stops the receiver. Desktop: composition
     * teardown. Android: before rebuilding the controller on a lifecycle
     * restart (the [stop]-then-rebuild is the shared controller's share of
     * the `RestartableJob` cancel-then-replace discipline).
     */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        stopReceiver()
    }

    /**
     * One capabilities arm: launched INSIDE the reconnect watcher ([this] is
     * the watcher's own scope, not [scope]) so an in-flight post is cancelled
     * with the collector on [stop] — the previous per-shell copies scoped
     * their posts the same way. Gated on the LIVE auth state at dispatch time
     * (a stray connect during teardown must not fire a stale post), failures
     * swallowed so one failed post cannot kill the watcher.
     */
    private fun CoroutineScope.armCapabilities() {
        launch {
            if (isAuthenticated.first()) {
                runCatchingRethrowingCancellation { onReconnect() }
            }
        }
    }
}
