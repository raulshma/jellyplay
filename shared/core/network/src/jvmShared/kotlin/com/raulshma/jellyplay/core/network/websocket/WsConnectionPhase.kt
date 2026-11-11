package com.raulshma.jellyplay.core.network.websocket

import com.raulshma.jellyplay.core.network.WebSocketBackoffPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The realtime socket's connection phase — the five states the
 * [JellyfinWebSocketClient] machine can occupy. This hierarchy replaced an
 * implicit machine that was smeared across the client's fields: a null-ness
 * sentinel over five credential strings ("logical session ended"),
 * a `reconnectAttempts` AtomicInteger carrying THREE meanings (fast-ladder
 * attempt index, phase selector via `fastBackoff.delayMs(attempts) == null`,
 * and the poison value `disconnect()` wrote to silence pending ticks), an
 * `backgroundRetryDelayMs` AtomicLong, and a `hadConnectedOnce` one-shot.
 * The phase IS the truth now — there is no poison counter, no hidden flag:
 * every one of those roles is a property of the current phase, recorded
 * structurally by [WsConnectionMachine]'s transitions.
 *
 * **Retry phases are in-flight states.** `Reconnecting`/`BackgroundRetry`
 * remain the phase while their scheduled attempt is actually running — the
 * tick's `OpenSocket` decision deliberately does NOT move the phase to
 * `Connecting`. That is what lets the attempt's own failure/close advance
 * the single ladder ([WsConnectionMachine.onFailure]) while the open that
 * follows a reconnect can be told apart from a first connect (reconnect
 * emission — see the machine's `onOpen`).
 */
sealed interface WsConnectionPhase {

    /**
     * No logical session: no socket owed to the server, no retry pending.
     * Entered by an explicit [JellyfinWebSocketClient.disconnect] (from ANY
     * phase — the machine needs no poison counter to suppress pending ticks,
     * because `onRetryDue` from here is a no-op) and by the teardown a
     * fresh [JellyfinWebSocketClient.connect] performs. The "logical
     * session ended" sentinel the five credential nulls used to double as.
     */
    data object Disconnected : WsConnectionPhase

    /**
     * A connect requested by the app is handshaking (the first open of the
     * logical session has not arrived yet). An `onOpen` here is a FIRST
     * connect — it must not emit on `reconnects`.
     */
    data object Connecting : WsConnectionPhase

    /**
     * The socket is open and keep-alive is running. The only phase from
     * which `isConnected` reads true (the client projects it as
     * `phase.map { it is Connected }`).
     */
    data object Connected : WsConnectionPhase

    /**
     * The fast backoff lane (1s → 2s → 4s → 8s → 16s, + jitter): an
     * in-flight attempt index [attempt] (1-based) whose next re-open waits
     * [nextDelayMs]. The phase STAYS here while that attempt runs (see the
     * class KDoc); its failure advances to `attempt + 1`, and exhausting
     * the fast schedule (past
     * [WebSocketBackoffPolicy.maxAttempts]) leaves this lane for
     * [BackgroundRetry].
     */
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long) : WsConnectionPhase

    /**
     * The slow background-retry lane: the fast schedule is exhausted, and
     * the next re-open waits [nextDelayMs] (60s initial, doubling per
     * failed cycle, 15min cap). Like [Reconnecting], this stays the phase
     * while the in-flight attempt runs; a failure of that attempt restarts
     * the FAST ladder at attempt 1 — the old code's `reconnectAttempts`
     * reset before the background `connectInternal()`, verbatim.
     */
    data class BackgroundRetry(val nextDelayMs: Long) : WsConnectionPhase
}

/**
 * One side effect the OkHttp adapter must perform for a [WsDecision]. Pure
 * vocabulary — the machine knows nothing about OkHttp, flows or jobs; the
 * adapter applies these in list order (the order is load-bearing: e.g. an
 * open's `EmitReconnect` precedes `StartKeepAlive`, mirroring the old
 * `onOpen` sequence).
 */
sealed interface WsAction {

    /** Cancel both pending retry jobs (the fast-lane and background-lane ticks). */
    data object CancelRetryJobs : WsAction

    /** Stop the keep-alive ping loop. */
    data object StopKeepAlive : WsAction

    /** Start the keep-alive ping loop (on Connected entry only). */
    data object StartKeepAlive : WsAction

    /** Open a socket (`connectInternal()` — the current credentials apply). */
    data object OpenSocket : WsAction

    /** Close and forget the current socket, if any (close code 1000). */
    data object CloseSocket : WsAction

    /** Emit on the client's `reconnects` flow (a true post-first-open reconnect). */
    data object EmitReconnect : WsAction

    /**
     * Schedule the pending retry tick: after [delayMs] the adapter asks the
     * machine `onRetryDue()` — which re-validates the phase before any
     * socket is opened.
     */
    data class ScheduleReconnect(val delayMs: Long) : WsAction
}

/**
 * What a transition decided: [phase] is the machine's phase AFTER the
 * transition (already applied — a decision REPORTS the new phase, it does
 * not command it), and [actions] is the ordered side-effect list for the
 * adapter. An empty list is the Noop decision.
 */
data class WsDecision(
    val phase: WsConnectionPhase,
    val actions: List<WsAction>,
)

/**
 * The realtime socket's connection decision core — the ONE place that maps
 * a connection event to the new [WsConnectionPhase] plus the [WsAction]
 * list the OkHttp adapter performs. [JellyfinWebSocketClient] is a thin
 * adapter around this class, the way `ExoLiveEngine` is around
 * `LiveFallbackPhase`: it maps raw OkHttp callbacks to events, executes the
 * decisions' actions, and owns nothing about the schedule logic itself.
 *
 * The phase itself is observable (the client publishes it and projects
 * `isConnected` off it), but every behavior is pinned through the decisions
 * transitions produce — [WsConnectionMachineTest] — and against a real
 * socket through [JellyfinWebSocketClientReconnectTest].
 *
 * ## The fix this machine exists for
 *
 * The former implicit machine had a dead end: the `onClosed` callback
 * (pre-rewrite L193–197) only flipped the connected flag and stopped
 * keep-alive — a CLEAN server-side close (server restart, idle policy,
 * load-balancer drain) never scheduled a reconnect, stranding remote-control
 * and SyncPlay pushes until the next auth edge. Only `onFailure` scheduled.
 * Here the fix is a transition: `onClosedClean` from a live
 * (`Connected`/`Connecting`) or mid-ladder phase re-enters the SAME backoff
 * ladder as a failure. Pinned by [WsConnectionMachineTest] and, against a
 * real MockWebServer socket, by [JellyfinWebSocketClientReconnectTest]'s
 * clean-close case.
 *
 * ## Why two sockets can never race (replaces the cancel-replace patch)
 *
 * The former `scheduleReconnect` needed the cancel-replace patch (pre-rewrite
 * L274–277: "Cancel any in-flight reconnect attempt before scheduling a new
 * one. Without this, a rapid connect/disconnect cycle … could leak the
 * deferred connectInternal() call and end up with two WebSockets racing each
 * other") because its implicit machine allowed a schedule decision from ANY
 * state — including a second terminal callback while a retry was already
 * pending, which is exactly the two-jobs-scheduled shape. This machine makes
 * that state unreachable structurally:
 *
 *  1. A `ScheduleReconnect` action is only ever produced by a live drop
 *     (Connected/Connecting → ladder entry) or a ladder advance — never by a
 *     drop or close observed from `Disconnected`, and never twice for the
 *     same event.
 *  2. Both retry lanes share one pending-tick slot each, and a lane is only
 *     entered from the drop of the other lane's in-flight attempt (whose tick
 *     has already fired), so at most ONE retry tick is pending at any time.
 *  3. The tick itself re-validates: `onRetryDue` returns an `OpenSocket`
 *     action ONLY from `Reconnecting`/`BackgroundRetry`. A
 *     [JellyfinWebSocketClient.disconnect] that lands while a delay is
 *     pending moves the phase to `Disconnected`, so when the stale tick wakes
 *     it cannot open a socket — this replaces BOTH the old creds-null
 *     re-check loop around the background delay AND the poison
 *     `reconnectAttempts` write in `disconnect()`.
 *  4. The adapter completes the invariant with a socket-identity guard on
 *     the OkHttp listener: a replaced socket's terminal callback is dropped
 *     THERE, before it can masquerade as the live socket's event — the
 *     machine reasons about phases and cannot see socket instances (see
 *     [JellyfinWebSocketClient.connectInternal]'s listener). Without the
 *     guard, the closed socket's `onClosed` landing during the replacement's
 *     `Connecting` handshake mis-read as the NEW socket's clean close and
 *     started a phantom ladder — the machine's decisions stayed correct for
 *     the event it was given; the wrong event was the adapter's to filter.
 *
 * With points 1–4 together, every terminal callback that reaches
 * [onFailure]/[onClosedClean] belongs to the CURRENT socket, and each either
 * enters or advances the one ladder — never duplicates a schedule. This
 * structurally retires the former cancel-replace patch, which existed to
 * paper over exactly these two shapes (double scheduling from any state, and
 * events of dead sockets racing a fresh session).
 *
 * ## First-open semantics (replaces `hadConnectedOnce`)
 *
 * The deleted `hadConnectedOnce` one-shot ("cleared only by disconnect")
 * is reproduced exactly by the phase the open arrives in: an open while the
 * machine sits in `Connecting` is the logical session's FIRST connect (no
 * emission); an open while it sits in `Reconnecting`/`BackgroundRetry` is a
 * reconnect (emit); an explicit disconnect lands `Disconnected`, so the next
 * explicit connect's open is a first connect again. Verified equivalent to
 * the old semantics — the failure/close handlers kept the old flag set, and
 * here the retry phases (which only follow a drop of an open connection)
 * play that role.
 *
 * All access is serialized on the machine's lock: transitions arrive from
 * OkHttp callback threads and the client's IO dispatcher concurrently.
 */
class WsConnectionMachine(
    /**
     * Additive jitter source (ms) applied to BOTH lanes' delays — the one
     * injection point that makes the schedule deterministic for tests. The
     * default is the client's historical shape,
     * [WebSocketBackoffPolicy.uniformJitter] (`(0..1000L).random()`); the
     * fast lane feeds it into [fastBackoff], the slow lane applies it around
     * its doubling base exactly like the old inline
     * `(baseDelayMs + (0..1000L).random())`.
     */
    private val jitter: () -> Long = WebSocketBackoffPolicy.uniformJitter(),
) {

    /**
     * The fast reconnect schedule (1s → 2s → 4s → 8s → 16s, +[jitter]
     * additive, capped at 30s) — the module's one backoff law
     * ([WebSocketBackoffPolicy]) with this machine's jitter source. Past
     * [WebSocketBackoffPolicy.maxAttempts] attempts the ladder leaves the
     * fast schedule for the slow background retry (see
     * [enterBackgroundRetry]).
     */
    private val fastBackoff = WebSocketBackoffPolicy(jitterMs = jitter)

    private val lock = Any()

    private val _phase = MutableStateFlow<WsConnectionPhase>(WsConnectionPhase.Disconnected)

    /** The machine's current phase — the client publishes this verbatim. */
    val phase: StateFlow<WsConnectionPhase> = _phase.asStateFlow()

    /**
     * Current base of the slow-path background retry: starts at
     * [BACKGROUND_RETRY_INITIAL_DELAY_MS], doubles each time the lane is
     * entered, capped at [BACKGROUND_RETRY_MAX_DELAY_MS]. Reset to the
     * initial delay on every successful open and on every explicit
     * connect-proceed — a user-driven (re)connect re-earns the fast
     * schedule and the initial slow delay. Guarded by [lock] (moved here
     * from the client's former `backgroundRetryDelayMs` AtomicLong — the
     * lock makes read-modify-write atomic where the volatile fields of the
     * old client were check-act races).
     */
    private var backgroundBaseDelayMs = BACKGROUND_RETRY_INITIAL_DELAY_MS

    // ── Events ──────────────────────────────────────────────────────────────

    /**
     * A [JellyfinWebSocketClient.connect] arrived. [credentialsMatch] is the
     * adapter's dedupe: the phase is `Connected` AND all five credential
     * fields equal the live session's. A match is a Noop (the skip fast-path
     * — no teardown, no socket churn); anything else tears the previous
     * session down ([teardownActions]) and lands `Disconnected`, after which
     * the adapter assigns the new credentials and calls [onConnectStart].
     * The proceed arm also resets the background base to the initial delay —
     * an explicit connect re-earns the fast schedule.
     */
    fun onConnectRequested(credentialsMatch: Boolean): WsDecision = synchronized(lock) {
        val current = _phase.value
        if (current is WsConnectionPhase.Connected && credentialsMatch) {
            commit(WsDecision(current, emptyList()))
        } else {
            backgroundBaseDelayMs = BACKGROUND_RETRY_INITIAL_DELAY_MS
            commit(WsDecision(WsConnectionPhase.Disconnected, teardownActions))
        }
    }

    /**
     * The adapter has torn the previous session down and assigned the new
     * credentials (contract: only call after a proceed from
     * [onConnectRequested]): begin the logical session's first handshake.
     * The phase leaves `Disconnected` here — which is what makes the
     * session's first open a first connect rather than a reconnect.
     */
    fun onConnectStart(): WsDecision = synchronized(lock) {
        commit(WsDecision(WsConnectionPhase.Connecting, listOf(WsAction.OpenSocket)))
    }

    /**
     * OkHttp `onOpen`. First-connect vs reconnect is decided by the phase
     * the open arrives in (see the class KDoc): `Connecting` → first connect
     * (keep-alive only); a retry phase → reconnect (emit BEFORE keep-alive
     * starts, mirroring the old `onOpen` order). A successful open also
     * resets the background base — the next failure streak starts the slow
     * lane at the initial 60s. A stray open from `Disconnected` (a handshake
     * that raced a disconnect) is contained: the socket is closed, the phase
     * does NOT resurrect to connected (the old client set `isConnected=true`
     * here for a socket about to be torn down). A duplicate open while
     * already `Connected` is ignored.
     */
    fun onOpen(): WsDecision = synchronized(lock) {
        commit(
            when (val current = _phase.value) {
                WsConnectionPhase.Connecting -> {
                    backgroundBaseDelayMs = BACKGROUND_RETRY_INITIAL_DELAY_MS
                    WsDecision(WsConnectionPhase.Connected, listOf(WsAction.StartKeepAlive))
                }
                is WsConnectionPhase.Reconnecting, is WsConnectionPhase.BackgroundRetry -> {
                    backgroundBaseDelayMs = BACKGROUND_RETRY_INITIAL_DELAY_MS
                    WsDecision(
                        WsConnectionPhase.Connected,
                        listOf(WsAction.EmitReconnect, WsAction.StartKeepAlive),
                    )
                }
                WsConnectionPhase.Disconnected ->
                    WsDecision(current, listOf(WsAction.CloseSocket))
                WsConnectionPhase.Connected ->
                    WsDecision(current, emptyList())
            },
        )
    }

    /**
     * OkHttp `onFailure`. Same ladder as a clean close (see [onClosedClean])
     * — both terminal callbacks are exclusive per socket, so they funnel
     * through one decision; they are separate events only for KDoc/telemetry
     * clarity at the adapter.
     */
    fun onFailure(): WsDecision = synchronized(lock) { commit(onDropped()) }

    /**
     * OkHttp `onClosed` — the clean server-side close that was the former
     * machine's DEAD END (see the class KDoc). From a live or mid-ladder
     * phase this re-enters the same backoff ladder as a failure: a server
     * restart or idle-policy close now self-heals instead of stranding
     * remote-control/SyncPlay pushes until the next auth edge. From
     * `Disconnected` it is a Noop — critically, this is the echo of the
     * adapter's own `close(1000)` inside [onDisconnectRequested]/teardown,
     * which must never resurrect a schedule after an explicit disconnect
     * (the old client was safe here only because its `onClosed` was the bug).
     */
    fun onClosedClean(): WsDecision = synchronized(lock) { commit(onDropped()) }

    /**
     * An explicit [JellyfinWebSocketClient.disconnect]. From ANY phase —
     * idempotent teardown (cancelling finished jobs and closing a null
     * socket are no-ops) — landing `Disconnected`. No poison counter: the
     * pending ticks, if any, are neutralized by [onRetryDue]'s phase check
     * when they wake.
     */
    fun onDisconnectRequested(): WsDecision = synchronized(lock) {
        commit(WsDecision(WsConnectionPhase.Disconnected, teardownActions))
    }

    /**
     * A scheduled retry tick woke after its [WsAction.ScheduleReconnect]
     * delay. `Reconnecting`/`BackgroundRetry` → the attempt actually fires
     * (`OpenSocket`; the phase intentionally stays put — see the phase
     * hierarchy's KDoc). ANY other phase → Noop: a disconnect that raced the
     * delay (or any teardown) structurally cannot open a socket — this is
     * the guard that replaced the old creds-null loop and poison counter
     * (see the class KDoc's invariant section).
     */
    fun onRetryDue(): WsDecision = synchronized(lock) {
        commit(
            when (val current = _phase.value) {
                is WsConnectionPhase.Reconnecting, is WsConnectionPhase.BackgroundRetry ->
                    WsDecision(current, listOf(WsAction.OpenSocket))
                else -> WsDecision(current, emptyList())
            },
        )
    }

    // ── State commit ────────────────────────────────────────────────────────

    /**
     * Applies a transition's decision to the machine's state: [WsDecision]
     * is a REPORT of the phase after the transition — "already applied", per
     * its KDoc — and this is where that application happens. Every public
     * event method funnels its computed decision through [commit] before
     * returning it to the adapter, so by the time the adapter runs the
     * decision's actions ([WsAction.OpenSocket] creating a socket whose
     * callbacks transition the machine, [WsAction.ScheduleReconnect] whose
     * lane the adapter reads off [phase], [isConnected][JellyfinWebSocketClient.isConnected]'s
     * projection) the published phase already reflects the transition.
     *
     * Forgetting this write is not a benign omission: the phase is the ONLY
     * machine state (there is no counter, no flag — see the class KDoc), so
     * an uncommitted decision leaves the machine re-reading the stale phase
     * on the next event — every transition silently replays against the old
     * state, and the ladder never leaves `Disconnected`. Guarded by [lock]
     * like every transition (all access serialized — see the class KDoc);
     * always called from inside a transition's `synchronized` block, never
     * concurrently.
     */
    private fun commit(decision: WsDecision): WsDecision {
        _phase.value = decision.phase
        return decision
    }

    // ── Drop funnel ─────────────────────────────────────────────────────────

    /**
     * The shared terminal-callback funnel (`onFailure`/`onClosedClean`).
     * Live phases and the background lane (re)enter the fast ladder at
     * attempt 1 — for the background lane this reproduces the old
     * `reconnectAttempts.set(0)` before the background `connectInternal()`,
     * whose subsequent failure re-entered `scheduleReconnect` at attempts=1.
     * A mid-ladder drop advances the single ladder, exhausting into the
     * slow lane. `Disconnected` stays put: no socket is owed, no retry is
     * owed.
     */
    private fun onDropped(): WsDecision {
        return when (val current = _phase.value) {
            WsConnectionPhase.Connected,
            WsConnectionPhase.Connecting,
            is WsConnectionPhase.BackgroundRetry,
            -> enterFastLadder(attempt = 1)
            is WsConnectionPhase.Reconnecting -> advanceLadder(current.attempt)
            WsConnectionPhase.Disconnected ->
                WsDecision(current, emptyList())
        }
    }

    /** Fast-ladder entry: attempt [attempt]'s delay, or fall through to the slow lane. */
    private fun enterFastLadder(attempt: Int): WsDecision {
        val delayMs = fastBackoff.delayMs(attempt)
            ?: return enterBackgroundRetry()
        return WsDecision(
            WsConnectionPhase.Reconnecting(attempt, delayMs),
            droppedActions + WsAction.ScheduleReconnect(delayMs),
        )
    }

    /**
     * Fast-ladder advance from in-flight [attempt]: the next rung, or — past
     * [WebSocketBackoffPolicy.maxAttempts] — slow-lane entry. This is the
     * shape that makes the ladder total: `Reconnecting` can never be built
     * with an attempt outside `1..maxAttempts`.
     */
    private fun advanceLadder(attempt: Int): WsDecision {
        val nextAttempt = attempt + 1
        val delayMs = fastBackoff.delayMs(nextAttempt)
            ?: return enterBackgroundRetry()
        return WsDecision(
            WsConnectionPhase.Reconnecting(nextAttempt, delayMs),
            droppedActions + WsAction.ScheduleReconnect(delayMs),
        )
    }

    /**
     * Slow-lane entry after the fast schedule is exhausted: the wait is the
     * current base plus jitter, capped — then the base doubles for the NEXT
     * entry, capped at [BACKGROUND_RETRY_MAX_DELAY_MS]. Exponential cadence
     * 60s → 2m → 4m → … → 15min, so a permanently dead server degrades to at
     * most one attempt storm per quarter hour (each cycle is one slow attempt
     * plus the five fast rungs, ~31s). Verbatim arithmetic from the old
     * `scheduleReconnect` background arm.
     */
    private fun enterBackgroundRetry(): WsDecision {
        val nextDelayMs = (backgroundBaseDelayMs + jitter())
            .coerceAtMost(BACKGROUND_RETRY_MAX_DELAY_MS)
        backgroundBaseDelayMs = (backgroundBaseDelayMs * 2)
            .coerceAtMost(BACKGROUND_RETRY_MAX_DELAY_MS)
        return WsDecision(
            WsConnectionPhase.BackgroundRetry(nextDelayMs),
            droppedActions + WsAction.ScheduleReconnect(nextDelayMs),
        )
    }

    // ── Constants & shared action lists ─────────────────────────────────────

    /**
     * Teardown of the current logical session: cancel any pending retry
     * ticks, stop keep-alive, close the socket. The disconnect's and the
     * connect-restart's shared arm — idempotent on every element.
     */
    private val teardownActions = listOf(
        WsAction.CancelRetryJobs,
        WsAction.StopKeepAlive,
        WsAction.CloseSocket,
    )

    /**
     * A terminal callback's non-schedule actions (stop keep-alive — parity
     * with the old unconditional `stopKeepAlive()` — and close the dead
     * socket, as the old `onFailure` did). `ScheduleReconnect` is appended
     * per-ladder-position by the entry/advance helpers.
     */
    private val droppedActions = listOf(
        WsAction.StopKeepAlive,
        WsAction.CloseSocket,
    )

    companion object {
        /**
         * Slow-path background retry cadence (see [backgroundBaseDelayMs]):
         * initial 60s, doubling per failed cycle, capped at 15 min. Moved
         * verbatim from the client's companion — the cadence is machine
         * policy, not adapter plumbing.
         */
        private const val BACKGROUND_RETRY_INITIAL_DELAY_MS = 60_000L
        private const val BACKGROUND_RETRY_MAX_DELAY_MS = 900_000L
    }
}
