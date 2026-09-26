package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** The decision fan-out's TaskBundle slot key (cancel-and-replace per re-arm). */
private const val DECISION_FAN_OUT = "EngineSessionShell.decisionFanOut"

/**
 * One playback session's engine-event STRUCTURAL machinery — the lifecycle
 * plumbing both shipped hosts (the VOD
 * [com.raulshma.jellyplay.feature.player.video.PlaybackSession] and the live
 * `LiveTvPlayerViewModel`) used to re-derive as private copies around their
 * [EngineEventCoordinator]:
 *
 *  1. coordinator CONSTRUCTION over the host's hot engine-source stream,
 *     pinned by the host's [Config] knobs (the same Config-not-copies rule
 *     the coordinator itself applies — VOD keeps
 *     [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT] +
 *     [WatchdogScope.INITIAL_BUFFER_ONLY] defaults, live pins
 *     [FallbackPolicy.EXTERNAL_REQUEST_ONLY] +
 *     [WatchdogScope.EVERY_BUFFERING_EPISODE] + its own timeout);
 *  2. RE-ARM: [dispose] kills the live coordinator for good (teardown must
 *     never let a policy collector observe a released engine); [reArm]
 *     re-creates it (and restarts the decision fan-out, and pokes
 *     [Config.onRearmed] so the host can re-subscribe its mirror collectors)
 *     — a no-op while the current coordinator is alive, so a host can call it
 *     on every load/entry and only actually re-arm after a dispose (the VOD
 *     session's per-initialize re-arm) or after the host tore the session
 *     down (the live player's per-screen-entry re-arm);
 *  3. the DECISIONS FAN-OUT: one collector on the current coordinator's
 *     [decisions] stream routing each [EngineDecision] to the host's
 *     [onDecision] executor — cancelled by [dispose], restarted by [reArm];
 *  4. the one-shot SESSION EVENT pipe ([events] + [emitEvent]): the
 *     SessionEvent-style `tryEmit`-only, DROP_OLDEST surface the hosts'
 *     decision executors and reload paths surface user-visible outcomes
 *     through (a mid-teardown emission never suspends — the same contract as
 *     the coordinator's decision stream). The live host previously backed its
 *     channel with `Channel(BUFFERED)`, which retained emissions across a
 *     collector gap; this pipe does not — an event emitted before a collector
 *     subscribes (or while none is active) is lost, and overflow evicts the
 *     oldest. That is the deliberate convergence onto the VOD session's
 *     semantics: both hosts keep a collector alive for the session's whole
 *     lifetime, so the gap window is the screen-teardown edge where a stale
 *     retained event was itself the bug.
 *
 * What the shell deliberately does NOT own (it stays host-side):
 *  - producing the [EngineEventSource] slices — mapping an engine onto its
 *    raw-event slice is engine-type-specific (VOD:
 *    [toEngineEventSource] over `PlayerSessionManager.engineFlow`; live: the
 *    tuner's state enum mapped onto [EnginePlaybackState] behind a hot
 *    StateFlow the host publishes per tune);
 *  - decision EXECUTION — the hosts' policies stay unmerged: the VOD
 *    session's reload choreography + reporting and the live host's
 *    uiState writes + TRANSCODE re-resolve live behind [onDecision];
 *  - the hosts' lifecycle latches (`released` / `initialized`) and the
 *    live-specific PiP discharge + pending-zap single-flight.
 *
 * The shell is generic over the host's event vocabulary ([E] — VOD
 * `SessionEvent`, live `LivePlayerEvent`): the pipe's SEMANTICS are the
 * shared fact, not the event type.
 *
 * Not single-use: [dispose]/[reArm] cycles are the intended shape — the
 * owning scope's cancellation ends the shell like any other session object.
 */
class EngineSessionShell<E>(
    /** Parent scope (the owning session/ViewModel's). Fan-out tasks launch here. */
    private val scope: CoroutineScope,
    /**
     * Hot engine-source stream — one [EngineEventSource] per engine instance,
     * `null` while no engine exists. Every coordinator this shell builds
     * consumes it; re-arming re-subscribes a FRESH coordinator to the SAME
     * stream, so the host's publish points never change.
     */
    private val engineSources: Flow<EngineEventSource?>,
    /** What a decision DOES on this host — the decisions fan-out executor. */
    private val onDecision: (EngineDecision) -> Unit,
    /** Host behavior pins — see [Config]. */
    private val config: Config = Config(),
) {
    /**
     * @param coordinator the [EngineEventCoordinator.Config] policy knobs
     *   (watchdog window + scope, fallback policy) — the hosts' behavior pin.
     * @param getPlaybackMode synchronous playback-mode read for the
     *   FORCE_DIRECT_PLAY one-shot latch (VOD's ui-prefs mirror; the default
     *   suits hosts without the preference).
     * @param directPlayFallbackNotice localized FORCE_DIRECT_PLAY fallback
     *   notice (VOD only under [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT]).
     * @param passOutHours pass-out protection hours; values <= 0 disable.
     * @param clock monotonic clock, injectable for tests.
     * @param onRearmed invoked after [reArm] actually re-created the
     *   coordinator — the VOD session's hook for restarting the VM-side
     *   mirror collectors against the new instance.
     */
    data class Config(
        val coordinator: EngineEventCoordinator.Config = EngineEventCoordinator.Config(),
        val getPlaybackMode: () -> PlaybackMode = { PlaybackMode.AUTO },
        val directPlayFallbackNotice: suspend (String) -> String = { it },
        val passOutHours: Flow<Int> = emptyFlow(),
        val clock: () -> Long = ::monotonicNowMillis,
        val onRearmed: () -> Unit = {},
    )

    /**
     * The live coordinator. Directly reachable for the slices the hosts keep
     * calling on the instance itself: the mirror flows
     * ([EngineEventCoordinator.isPlaying] / [EngineEventCoordinator.isBuffering]),
     * the latch resets, the interaction clock and the external fallback
     * intake (all also forwarded below). Changes on [reArm].
     */
    var coordinator: EngineEventCoordinator = createCoordinator()
        private set

    /** Builds a coordinator over the host's stream + [Config] pins. */
    private fun createCoordinator() = EngineEventCoordinator(
        scope = scope,
        engineSource = engineSources,
        getPlaybackMode = config.getPlaybackMode,
        directPlayFallbackNotice = config.directPlayFallbackNotice,
        passOutHours = config.passOutHours,
        clock = config.clock,
        config = config.coordinator,
    )

    // ── One-shot session event pipe ──────────────────────────────────────────

    private val _events = MutableSharedFlow<E>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Session-level outcomes for the host's screen (the VOD `SessionEvent` /
     * live `LivePlayerEvent` vocabularies). Single-collector by construction
     * (the screen's forwarder); `tryEmit`-only emitters never suspend.
     */
    val events: SharedFlow<E> = _events.asSharedFlow()

    /** Publishes a one-shot session event (never suspends, drops on overflow). */
    fun emitEvent(event: E) {
        _events.tryEmit(event)
    }

    // ── Decision fan-out ──────────────────────────────────────────────────────

    private val fanOutTasks = TaskBundle(scope)

    init {
        startDecisionFanOut()
    }

    /** Starts (or restarts, after a re-arm) the [onDecision] executor. */
    private fun startDecisionFanOut() {
        fanOutTasks.replace(DECISION_FAN_OUT) {
            launch { coordinator.decisions.collect(onDecision) }
        }
    }

    // ── Coordinator forwarders (the current instance's control surface) ──────

    /** The current coordinator's decision stream. */
    val decisions: SharedFlow<EngineDecision> get() = coordinator.decisions

    /** Guarded play-state mirror (the current coordinator's). */
    val isPlaying: StateFlow<Boolean> get() = coordinator.isPlaying

    /** Guarded buffering mirror (the current coordinator's). */
    val isBuffering: StateFlow<Boolean> get() = coordinator.isBuffering

    /** Resets the fallback latch (a new item is loading) — VOD's per-load reset. */
    fun onNewItem() = coordinator.onNewItem()

    /** Re-arms the fallback latch (the user explicitly changed the mode). */
    fun onPlaybackModeChanged() = coordinator.onPlaybackModeChanged()

    /** Resets the pass-out interaction clock (a user interaction occurred). */
    fun onUserInteraction() = coordinator.onUserInteraction()

    /** External transcode-fallback intake (the live tuner engine's trigger). */
    fun onTranscodeFallbackRequested() = coordinator.onTranscodeFallbackRequested()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** True after [dispose] until the next successful [reArm]. */
    val disposed: Boolean get() = coordinator.disposed

    /**
     * Re-creates the coordinator after a [dispose] and restarts the decision
     * fan-out against it, then pokes [Config.onRearmed]. A no-op while the
     * current coordinator is alive — callers may invoke this on every
     * load/screen-entry; only a disposed shell actually re-arms. Returns
     * whether a re-arm happened.
     */
    fun reArm(): Boolean {
        if (!coordinator.disposed) return false
        coordinator = createCoordinator()
        startDecisionFanOut()
        config.onRearmed()
        return true
    }

    /**
     * Tears the session's engine-event machinery down: disposes the
     * coordinator (no policy collector may observe a released engine) and
     * cancels the decision fan-out. Idempotent. The [events] pipe stays
     * usable — the hosts' guard their emissions with their own lifecycle
     * latches, exactly as before the shell existed.
     */
    fun dispose() {
        fanOutTasks.cancel(DECISION_FAN_OUT)
        coordinator.dispose()
    }
}
