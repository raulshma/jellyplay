package com.raulshma.jellyplay.core.network.websocket

import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.WebSocketBackoffPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

// This client keeps its org.json-based message routing verbatim.
// Every emitted [WebSocketEvent] carries the `Data` payload as an
// org.json object/array because the :shared:core:data consumers
// (RemoteControlReceiver, SyncPlayManager) read those typed fields directly
// and must keep compiling unchanged. jvmShared compiles org.json against
// android.jar on the Android target and against the real org.json artifact
// on the desktop target (see the module build script).

/**
 * The app-lifetime realtime socket — a THIN OkHttp adapter around the
 * [WsConnectionMachine] decision core. Every connection-level decision
 * (when to reconnect, on which ladder, whether an open is a reconnect)
 * lives in the machine; this class maps OkHttp callbacks to machine events,
 * executes the returned [WsAction]s, and owns the transport plumbing only
 * (socket, message routing, keep-alive loop).
 *
 * What the machine replaced here (full inventory in
 * [WsConnectionMachine]'s KDoc): the `reconnectAttempts` AtomicInteger
 * (attempt index + phase selector + `disconnect()` poison value), the
 * `hadConnectedOnce` one-shot, the `backgroundRetryDelayMs` AtomicLong, the
 * `_isConnected` MutableStateFlow, and the five credential strings' role as
 * a "logical session ended" null-ness sentinel.
 *
 * ## The clean-close fix
 *
 * The former `onClosed` callback flipped the connected flag and stopped
 * keep-alive but NEVER scheduled a reconnect — a clean server-side close
 * (server restart, idle policy, load-balancer drain) stranded
 * remote-control/SyncPlay pushes until the next auth edge. Now
 * `onClosedClean` re-enters the same backoff ladder as a failure. Pinned by
 * [WsConnectionMachineTest] and by
 * [JellyfinWebSocketClientReconnectTest]'s clean-close case.
 */
class JellyfinWebSocketClient(
    private val okHttpClient: OkHttpClient,
) {
    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The live session's credentials — connect()'s DEDUPE input and
     * `connectInternal()`'s URL/sender source, nothing more. They no longer
     * double as the "logical session ended" sentinel (the phase machine's
     * `Disconnected` state is that truth now), which is why
     * [disconnect] clears all five instead of the former three — stale
     * `deviceName`/`clientName` were harmless only while the null-check
     * sentinel made them unread. @Volatile: written from any thread calling
     * [connect]/[disconnect], read from OkHttp callback threads and the
     * keep-alive loop.
     */
    @Volatile private var serverUrl: String? = null
    @Volatile private var token: String? = null
    @Volatile private var deviceId: String? = null
    @Volatile private var deviceName: String? = null
    @Volatile private var clientName: String? = null

    /** The one connection decision core — see [WsConnectionMachine]. */
    private val machine = WsConnectionMachine()

    /**
     * The machine's live phase, published verbatim. ADDITIVE public member:
     * consumers keep reading [isConnected]; this exposes the richer
     * vocabulary (`Reconnecting(attempt, nextDelayMs)`,
     * `BackgroundRetry(nextDelayMs)`) for diagnostics and future consumers
     * without anyone having to re-derive it from event flows.
     */
    val phase: StateFlow<WsConnectionPhase> = machine.phase

    /**
     * Live socket connection state — a DERIVED PROJECTION of [phase]:
     * `true` exactly while the machine sits in `Connected`. The former
     * `_isConnected` MutableStateFlow (hand-maintained at every callback)
     * is gone; `SharingStarted.Eagerly` keeps the projection collecting
     * from construction so synchronous `.value` reads (e.g.
     * ScheduledTasksRealtimeChannel's initial arm and SyncPlayManager's
     * `first { it }`) are always correct, and the `StateFlow<Boolean>` type
     * is unchanged for every consumer.
     */
    val isConnected: StateFlow<Boolean> = machine.phase
        .map { it is WsConnectionPhase.Connected }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    /**
     * Emits on every WebSocket open that follows a previous open — a true
     * reconnect, whether via the fast backoff schedule or the slow background
     * retry. This is the reconnect vocabulary's single home; consumers no
     * longer re-derive the false→true edge of [isConnected] themselves.
     *
     * **First-connect semantics:** decided by the machine's phase at open
     * time — an open arriving while the phase is `Connecting` is the logical
     * session's FIRST connect and does not emit; an open arriving while the
     * phase is `Reconnecting`/`BackgroundRetry` (the automatic reconnect's
     * open) does. An explicit [disconnect] lands the machine in
     * `Disconnected`, so the next explicit [connect] starts a fresh logical
     * session whose open is a first connect again. (The former
     * `hadConnectedOnce` flag this was once gated on — cleared only by
     * `disconnect()`, deliberately kept set across failures so the automatic
     * reconnect's open would emit — is reproduced exactly by those two phase
     * arms; see [WsConnectionMachine] and [WsConnectionMachineTest].)
     *
     * Per-consumer arming choices (the pre-`reconnects` edge derivations
     * differed in whether they also fired on the first connect):
     *  - [ScheduledTasksRealtimeChannel][com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel]
     *    arms on the first connect itself (immediate or deferred Start) and
     *    subscribes this flow for every subsequent re-subscribe — unchanged
     *    behavior.
     *  - `SessionCoordinator` (app shell) keeps an explicit initial arm on
     *    [isConnected]: its former `lastConnected` edge also fired on the
     *    first connect (the collector starts before the auth fan-out
     *    connects), and that first post is load-bearing (pins the device's
     *    capabilities once the server session exists).
     *  - `SyncPlayManager.startReconnectWatcher` subscribes this flow ONLY —
     *    declared behavior change: its former `isConnected.drop(1).filter {
     *    it }` raced the first join's handshake and, whenever the collector
     *    won, fired a duplicate of `joinGroup`'s own re-assert sequence and
     *    stamped `lastReconnectAtMs` on a non-reconnect. A true mid-session
     *    reconnect re-asserts exactly as before.
     */
    val reconnects: SharedFlow<Unit> = _reconnects.asSharedFlow()

    /** Fast-lane pending retry tick (the 1s→16s schedule). See [scheduleRetry]. */
    private var reconnectJob: Job? = null

    /** Slow-lane pending retry tick (the 60s→15min background schedule). See [scheduleRetry]. */
    private var backgroundRetryJob: Job? = null

    // KeepAlive engine -------------------------------------------------------
    // The server drops the socket after its idle timeout (default 60s) unless the
    // client pings. Jellyfin's TS SDK handles this inside the socket service; we
    // mirror it here so the app-lifetime socket owned by MainViewModel stays up
    // for non-SyncPlay consumers (admin dashboards, remote-control receivers).
    // Previously this ran only while a SyncPlay session was active, which timed
    // out the socket in admin-only sessions and broke realtime task updates.
    private var keepAliveJob: Job? = null

    fun connect(credentials: ConnectionCredentials) {
        val credentialsMatch = machine.phase.value is WsConnectionPhase.Connected &&
            serverUrl == credentials.serverAddress &&
            token == credentials.accessToken &&
            deviceId == credentials.deviceId &&
            deviceName == credentials.deviceName &&
            clientName == credentials.clientName
        val requested = machine.onConnectRequested(credentialsMatch)
        if (requested.actions.isEmpty()) {
            NetworkLog.d(TAG, "WebSocket already connected to this server, skipping connect")
            return
        }
        apply(requested)
        serverUrl = credentials.serverAddress
        token = credentials.accessToken
        deviceId = credentials.deviceId
        deviceName = credentials.deviceName
        clientName = credentials.clientName
        apply(machine.onConnectStart())
    }

    /**
     * Opens one socket against the live credentials. Called by the machine's
     * `OpenSocket` decisions — both the explicit connect's handshake and the
     * retry ticks' re-open. Null-guards kept as defense-in-depth: the machine
     * never schedules an attempt out of `Disconnected`, so they are
     * unreachable in correct operation.
     */
    private fun connectInternal() {
        val serverAddress = serverUrl ?: return
        val accessToken = token ?: return
        val device = deviceId ?: return

        val wsUrl = buildSocketUrl(serverAddress, device, deviceName, clientName, accessToken)
        // Log only the base endpoint — the full URL carries the access token
        // and deviceId as query parameters.
        NetworkLog.d(TAG, "Connecting WebSocket to ${serverAddress.trimEnd('/')}/socket")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            /**
             * Stale-socket containment, the adapter half of the machine's
             * no-double-schedule invariant. OkHttp delivers every callback
             * with the specific [WebSocket] instance it belongs to, but the
             * machine reasons about PHASES and cannot see socket instances
             * (see [WsConnectionMachine]) — so a socket the client has
             * already replaced or closed must be filtered HERE, by identity.
             * Otherwise its terminal callback mis-feeds the machine: the
             * closed socket's `onClosed` landing while the replacement's
             * `Connecting` handshake is in flight reads as the NEW socket's
             * clean close, starts a phantom reconnect ladder, and the
             * replacement's open then arrives from a retry phase — emitting
             * a `reconnects` emission the first-connect semantics forbid
             * (exactly the duplication [JellyfinWebSocketClientReconnectTest]
             * caught). The machine guarantees it can never double-schedule;
             * this guard guarantees dead sockets cannot feed it at all.
             * Together the two halves structurally retire the former
             * cancel-replace patch in [scheduleRetry], which existed because
             * the implicit machine accepted schedule decisions from any
             * state — including events of sockets that were no longer the
             * client's. Every replaced/closed socket always got an explicit
             * `close()` from a `CloseSocket` action before the field moved
             * on, so a dropped socket's own close handshake still completes
             * without this listener's involvement. (`onMessage` stays
             * unguarded, as before: a replaced socket's last buffered
             * messages carry no machine semantics.)
             */
            private fun isCurrentSocket(socket: WebSocket): Boolean {
                if (socket !== this@JellyfinWebSocketClient.webSocket) {
                    NetworkLog.d(TAG, "Ignoring stale WebSocket event (socket replaced or closed)")
                    return false
                }
                return true
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(webSocket)) return
                NetworkLog.d(TAG, "WebSocket connected")
                // The machine decides first-connect vs reconnect (emit order:
                // `reconnects` before keep-alive, mirroring the old onOpen).
                apply(machine.onOpen())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (NetworkLog.isDebugEnabled(TAG)) {
                    NetworkLog.d(TAG, "WebSocket message: ${text.take(200)}")
                }
                handleMessage(text)
            }

            /**
             * Peer-initiated graceful close: OkHttp delivers `onClosing` when
             * the peer's close frame is read, and — per `RealWebSocket`'s
             * `finishReader` (`sendOnClosed = !failed && writer == null &&
             * receivedCloseCode != -1`) — NEVER fires `onClosed`/`onFailure`
             * unless the application completes the handshake with
             * [WebSocket.close], which is what releases the writer. Without
             * this override the clean-close fix's `onClosedClean` transition
             * is unreachable: a server restart or idle-policy close never
             * reached the machine at all. Replying with a wire-valid 1000
             * (echoing an abnormal peer code such as 1005 would throw in
             * OkHttp's close validation) completes the handshake; the peer's
             * own code and reason still reach [onClosed], because OkHttp
             * reports `receivedCloseCode` there, not our reply's.
             */
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) return
                webSocket.close(1000, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentSocket(webSocket)) return
                NetworkLog.w(TAG, "WebSocket failure", t)
                // CloseSocket (parity with the old in-callback close) +
                // ladder ScheduleReconnect are the decision's actions.
                apply(machine.onFailure())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) return
                NetworkLog.d(TAG, "WebSocket closed: $code $reason")
                // THE FIX: a clean close re-enters the same ladder as a
                // failure instead of dead-ending (the former handler only
                // flipped the flag and stopped keep-alive — and, without an
                // `onClosing` reply, never even ran for peer-initiated
                // closes; see [onClosing]).
                apply(machine.onClosedClean())
            }
        })
    }

    /**
     * Executes a [WsDecision]'s actions in order. The single seam between
     * the pure machine and the OkHttp/coroutine world.
     */
    private fun apply(decision: WsDecision) {
        for (action in decision.actions) {
            when (action) {
                WsAction.CancelRetryJobs -> {
                    reconnectJob?.cancel()
                    reconnectJob = null
                    backgroundRetryJob?.cancel()
                    backgroundRetryJob = null
                }
                WsAction.StopKeepAlive -> stopKeepAlive()
                WsAction.StartKeepAlive -> startKeepAlive()
                WsAction.OpenSocket -> connectInternal()
                WsAction.CloseSocket -> {
                    webSocket?.close(1000, "Client disconnecting")
                    webSocket = null
                }
                WsAction.EmitReconnect -> _reconnects.tryEmit(Unit)
                is WsAction.ScheduleReconnect -> scheduleRetry(action.delayMs)
            }
        }
    }

    /**
     * Launches the pending retry tick for [delayMs]. The lane is read off
     * the phase the machine just entered — the fast schedule logs
     * recognizable per-attempt text, the slow lane logs its own cadence.
     * The old unconditional cancel-replace (needed because the implicit
     * machine could schedule from any state — see
     * [WsConnectionMachine]'s invariant section) shrinks to same-lane
     * hygiene: the machine produces at most one pending tick, and the
     * tick's `onRetryDue` re-validation is what keeps a disconnect-raced
     * delay from ever opening a socket. The OTHER half the old patch used
     * to cover — stale terminal callbacks of replaced sockets mis-feeding
     * the machine — lives in the listener's socket-identity guard (see
     * [connectInternal]).
     */
    private fun scheduleRetry(delayMs: Long) {
        when (machine.phase.value) {
            is WsConnectionPhase.BackgroundRetry -> {
                NetworkLog.w(
                    TAG,
                    "Max reconnect attempts (${WebSocketBackoffPolicy.DEFAULT_MAX_ATTEMPTS}) " +
                        "reached, scheduling slow background retry",
                )
                backgroundRetryJob?.cancel()
                NetworkLog.d(TAG, "Background retry in ${delayMs / 1000}s")
                backgroundRetryJob = scope.launch {
                    delay(delayMs)
                    fireRetryDue()
                }
            }
            else -> {
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    delay(delayMs)
                    fireRetryDue()
                }
            }
        }
    }

    /**
     * A retry tick woke: re-validate the phase via [WsConnectionMachine.onRetryDue]
     * (a disconnect racing the delay lands `Disconnected` → Noop — no socket),
     * log the attempt in the lane's established voice, then apply the
     * decision.
     */
    private fun fireRetryDue() {
        val phaseAtTick = machine.phase.value
        val decision = machine.onRetryDue()
        if (decision.actions.contains(WsAction.OpenSocket)) {
            when (phaseAtTick) {
                is WsConnectionPhase.Reconnecting ->
                    NetworkLog.d(TAG, "Reconnecting WebSocket (attempt ${phaseAtTick.attempt})")
                is WsConnectionPhase.BackgroundRetry ->
                    NetworkLog.d(TAG, "Background WebSocket retry")
                else -> Unit
            }
        }
        apply(decision)
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
            NetworkLog.d(TAG, "ForceKeepAlive: server timeout=${timeoutMs}ms, pinging every ${half}ms")
            // Restart the loop so the new interval takes effect immediately.
            if (machine.phase.value is WsConnectionPhase.Connected) startKeepAlive()
        }
    }

    /**
     * Closes the realtime session from ANY machine state: the phase lands
     * `Disconnected` (no poison counter — pending ticks neutralize
     * themselves via `onRetryDue`), ALL five credentials are cleared (the
     * machine no longer needs null-creds to mean "don't retry"; the former
     * three-field nullify left `deviceName`/`clientName` stale), keep-alive
     * stops, both retry jobs cancel and the socket closes. The next explicit
     * [connect] starts a fresh logical session — its first open is a first
     * connect, not a reconnect (see [reconnects]).
     */
    fun disconnect() {
        serverUrl = null
        token = null
        deviceId = null
        deviceName = null
        clientName = null
        apply(machine.onDisconnectRequested())
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
            // inbound traffic (LibraryChanged, …) is dropped here and must not
            // pay for object construction.
            when (messageType) {
                "SyncPlayCommand",
                "SyncPlayGroupUpdate",
                "Play",
                "Playstate",
                "GeneralCommand",
                // Older/alternative shape of the remote browse command: some
                // clients send DisplayContent as its own message type with the
                // item payload directly in Data (the modern shape rides
                // GeneralCommand with Name=DisplayContent). The receiver
                // normalizes both.
                "DisplayContent",
                "KeepAlive",
                "GroupJoined",
                "GroupLeft",
                // User-data pushes (played / favorite flips, playback position —
                // e.g. from another client) are object payloads too; the server
                // sends them to the authenticated user's socket with no
                // Start/Stop subscription.
                "UserDataChanged" -> {
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
            NetworkLog.w(TAG, "Failed to parse WebSocket message", e)
        }
    }

    fun sendKeepAlive() {
        val msg = JSONObject().put("MessageType", "KeepAlive")
        NetworkLog.d(TAG, "Sending keep-alive")
        webSocket?.send(msg.toString())
    }

    fun sendMessage(messageType: String, data: JSONObject = JSONObject()) {
        val msg = JSONObject().apply {
            put("MessageType", messageType)
            put("Data", data)
        }
        if (NetworkLog.isDebugEnabled(TAG)) {
            NetworkLog.d(TAG, "Sending WS message: ${msg.toString().take(200)}")
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
        if (NetworkLog.isDebugEnabled(TAG)) {
            NetworkLog.d(TAG, "Sending WS message: ${msg.toString().take(200)}")
        }
        webSocket?.send(msg.toString())
    }

    companion object {
        private const val TAG = "JellyfinWS"

        // Safe under the server's default 60s idle timeout. A server-sent
        // ForceKeepAlive can tighten this at runtime.
        private const val DEFAULT_KEEP_ALIVE_INTERVAL_MS = 30_000L

        // The background-retry cadence constants (initial 60s, 15min cap)
        // moved to WsConnectionMachine — they are schedule policy, not
        // adapter plumbing.
    }
}
