package com.raulshma.jellyplay.core.data.cast

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * The per-strategy cast connection phase machine — the ONE decision core for
 * "what does this strategy's session event mean for its connection state".
 * Extracted from the strategies' former hand-rolled `_isConnected` /
 * `_isConnecting` boolean pairs (GoogleCastStrategy, DlnaCastStrategy,
 * JellyfinRemotePlayCastStrategy each kept a copy), whose two subtleties they
 * can no longer drift on:
 *
 *  1. **Failed is a real state.** A failed session start used to only clear
 *     `isConnecting`, so a strategy whose PREVIOUS session was still marked
 *     connected kept reporting connected=true on a dead session — the phantom
 *     connection GoogleCastStrategy.onSessionStartFailed could produce.
 *     [failed] lands in [Failed], whose [isConnected] projection is false.
 *  2. **Every event is defined from every phase.** The transitions are total
 *     on purpose: each SDK callback is an ABSOLUTE statement of truth
 *     ("a session is starting", "the session ended"), so an out-of-order or
 *     duplicated callback degrades to the nearest honest phase instead of
 *     wedging the boolean pair into a state no callback sequence explains
 *     (the old pairs could describe connected+connecting or
 *     connecting-with-no-attempt).
 *
 * Division of labor (the house pure-machine/thin-host pattern, cf.
 * LiveFallbackPhase in player-live): the strategies own only the I/O — each
 * holds ONE `MutableStateFlow<CastSessionPhase>`, routes its SDK callbacks
 * through the transitions below, and publishes the historical
 * `isConnected` / `isConnecting` StateFlow surface as [projected] reads of
 * the phase. The projections are deliberately READ-THROUGH, not
 * `map{}.stateIn(Eagerly)`: consumers read `.value` synchronously on the
 * writer's thread (DlnaCastStrategyTest and JellyfinRemotePlayCastStrategyTest
 * assert `isConnected.value` immediately after `connect()`; CastManager's
 * ticker / strategy-observer seed and AudioPlaybackManager's Play-On gate do
 * the same in production), and a stateIn projection trails the write by at
 * least one dispatcher hop — it would break the pinned synchronous
 * Idle→Connected flip that DLNA / Jellyfin `connect()` intentionally keeps.
 *
 * The phase itself is observable per strategy (`val phase`), but nothing
 * outside the strategies needs it today: the projections are the consumers'
 * vocabulary and CastManager composes them with the commonMain
 * [castStateFanout] exactly as before. The transitions and projections are
 * pinned in CastSessionPhaseTest (jvmTest); the per-strategy wirings in their
 * own lanes (DlnaCastStrategyTest / JellyfinRemotePlayCastStrategyTest /
 * the store lane's GoogleCastStrategyTest).
 */
sealed interface CastSessionPhase {

    /** No session: the pre-connect rest state and the post-disconnect target. */
    data object Idle : CastSessionPhase

    /**
     * A connection attempt is in flight (a cast route was selected, a session
     * is starting or resuming). NOT observable in the DLNA / Jellyfin
     * transports, whose `connect()` flips straight through to [Connected]
     * synchronously — preserved as-is, see the wirings.
     */
    data object Connecting : CastSessionPhase

    /** The session is live and the strategy can transport to it. */
    data object Connected : CastSessionPhase

    /**
     * The last connection attempt failed. Carries the platform's reason when
     * it has one (Google's GMS error int, stringified; null when the
     * transport has nothing to say). Terminal until the next event: a retry
     * passes through [beginConnecting], a teardown or session end lands back
     * in [Idle] via [disconnected].
     */
    data class Failed(val reason: String?) : CastSessionPhase

    /**
     * The consumers' connected projection — the only arm that means "traffic
     * can flow". [Failed] is deliberately NOT connected even when it grew out
     * of a callback sequence whose old boolean pair kept a stale `true`
     * standing (subtlety 1 above).
     */
    val isConnected: Boolean get() = this is Connected

    /** The consumers' connecting projection — only a live attempt reports it. */
    val isConnecting: Boolean get() = this is Connecting
}

/**
 * A connection attempt begins (Google's `onSessionStarting` /
 * `onSessionResuming`; DLNA / Jellyfin's synchronous `connect()` entry).
 * Total: also fires from [CastSessionPhase.Connected] when a new route is
 * selected mid-session — the connected projection drops for the re-route,
 * which is what the strategy observers see as one Disconnected→Connected
 * event pair either way.
 */
fun CastSessionPhase.beginConnecting(): CastSessionPhase = CastSessionPhase.Connecting

/**
 * A session is live (Google's `onSessionStarted` / `onSessionResumed`; the
 * tail of DLNA / Jellyfin's synchronous `connect()`). Total: an unannounced
 * "started" (resume after process death) recovers from any phase — honesty
 * over ceremony.
 */
fun CastSessionPhase.connected(): CastSessionPhase = CastSessionPhase.Connected

/**
 * A connection attempt failed (Google's `onSessionStartFailed` /
 * `onSessionResumeFailed`). Total. DECLARED FIX: this is the transition the
 * former start-failed writer lacked — clearing only `isConnecting` left a
 * prior session's `isConnected=true` standing on a dead session; landing in
 * [CastSessionPhase.Failed] makes both projections honest.
 */
fun CastSessionPhase.failed(reason: String?): CastSessionPhase =
    CastSessionPhase.Failed(reason)

/**
 * The session ended or the strategy is being torn down — reset-to-Idle.
 * Total: defined from every phase (including already-[CastSessionPhase.Idle],
 * where it is a no-op), so a duplicate end callback cannot wedge the machine.
 */
fun CastSessionPhase.disconnected(): CastSessionPhase = CastSessionPhase.Idle

/**
 * Read-through projection of a [StateFlow] through [selector]: the returned
 * flow's `value` is computed from the upstream's CURRENT value at every read,
 * with zero dispatch latency, and collection replays the current selection
 * then only distinct selections (the exact observable contract of a
 * MutableStateFlow the writer would have kept updated by hand).
 *
 * This is the seam that lets the strategies keep ONE phase flow while their
 * public `isConnected` / `isConnecting` surface stays a synchronous
 * StateFlow. `map{}.stateIn(Eagerly)` was rejected for that role on purpose:
 * an Eagerly-shared flow updates its value in a launched coroutine, so a
 * `.value` read on the writer's thread right after a write sees the STALE
 * selection — which would break the synchronous connect flip the DLNA /
 * Jellyfin suites pin and the manager's Play-On gates rely on.
 *
 * Pure Kotlin, jvmTest-pinned (CastSessionPhaseTest) alongside the machine it
 * projects.
 */
fun <T, R> StateFlow<T>.projected(selector: (T) -> R): StateFlow<R> =
    PhaseProjection(upstream = this, selector)

/**
 * The [projected] implementation. Collection rides the upstream's own
 * [StateFlow.collect] (which replays the current value, then only distinct
 * upstream values, and never completes) through a collapsing collector: a
 * phase transition between two states with the same projection (Idle→Failed:
 * both not-connected) does not re-emit to boolean collectors — exactly what
 * the former hand-maintained MutableStateFlow<Boolean> pairs did.
 */
// Opt-in: kotlinx.coroutines requires an explicit subclass opt-in to
// implement StateFlow (the contract is the load-bearing part here — this
// class exists to FULFILL it, read-through value + conflated collect).
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class PhaseProjection<T, R>(
    private val upstream: StateFlow<T>,
    private val selector: (T) -> R,
) : StateFlow<R> {
    override val value: R
        get() = selector(upstream.value)

    override val replayCache: List<R>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing =
        upstream.collect(CollapsingCollector(downstream = collector, selector = selector))
}

/**
 * Collapses adjacent equal selections, the conflated-write behavior of the
 * MutableStateFlow the projection replaces.
 */
private class CollapsingCollector<T, R>(
    private val downstream: FlowCollector<R>,
    private val selector: (T) -> R,
) : FlowCollector<T> {
    private var hasEmitted = false
    private var last: R? = null

    override suspend fun emit(value: T) {
        val selected = selector(value)
        if (!hasEmitted || selected != last) {
            hasEmitted = true
            last = selected
            downstream.emit(selected)
        }
    }
}
