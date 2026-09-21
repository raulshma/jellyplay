package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.model.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
/**
 * Initial-buffering watchdog window. If the engine has not reached READY
 * within this window since load, the playback-error dialog is surfaced so the
 * user can retry with another engine. Long enough to cover legitimate
 * cold-start buffering on slow networks, short enough to feel responsive when
 * playback is genuinely stuck (e.g. undecodable content). This is the DEFAULT
 * — each host passes its own window via [EngineEventCoordinator.Config]
 * (both shipped players use 20 s; the live tuner passes it explicitly at its
 * construction site).
 */
internal const val BUFFERING_TIMEOUT_MS = 20_000L

/**
 * A single engine event interpreted as a session-level decision. The
 * [EngineEventCoordinator] owns the *policy* (when a decision fires); the
 * owning player (the VOD [com.raulshma.jellyplay.feature.player.video.PlaybackSession],
 * the live `LiveTvPlayerViewModel`) owns the *execution* (what a decision does
 * to uiState, the engine, repositories and collaborators) via its decisions
 * fan-out collector.
 */
sealed interface EngineDecision {
    /**
     * A structured engine error should be surfaced on the playback-error
     * dialog. [clearBuffering] is `true` for the start-up watchdog timeout,
     * which must also lift the stuck buffering spinner.
     */
    data class ShowError(val error: EngineError, val clearBuffering: Boolean) : EngineDecision

    /**
     * A direct-play load failed and the coordinator converted the failure
     * into an automatic transcode retry at [fromPositionMs]. Emitted either
     * by the FORCE_DIRECT_PLAY one-shot latch (policy
     * [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT]) or by the explicit
     * [EngineEventCoordinator.onTranscodeFallbackRequested] intake
     * (policy [FallbackPolicy.EXTERNAL_REQUEST_ONLY]).
     */
    data class FallbackToTranscode(val fromPositionMs: Long) : EngineDecision

    /** The engine reached [EnginePlaybackState.ENDED]. */
    data object PlaybackEnded : EngineDecision

    /** Pass-out protection tripped — pause playback and notify the user. */
    data object PassOutPause : EngineDecision

    /** A transient, message-only event (subtitle toasts, fallback toast). */
    data class InformUser(val message: String) : EngineDecision
}

/**
 * How widely the initial-buffering watchdog may arm. The two shipped hosts
 * pinned DIFFERENT scopes before the coordinator was shared — both are
 * preserved verbatim via this knob:
 *  - [INITIAL_BUFFER_ONLY] — the VOD player's pin: arm only before the
 *    engine instance's first READY, so legitimate mid-playback rebuffers
 *    (seeks, quality switches, network blips) never trip it.
 *  - [EVERY_BUFFERING_EPISODE] — the live tuner's pin: a stalled live tuner
 *    can sit in BUFFERING mid-playback without ever raising a
 *    PlaybackException, so every BUFFERING episode (re-)arms a fresh window.
 */
enum class WatchdogScope {
    /** Arm only before the engine instance's first READY (VOD pin). */
    INITIAL_BUFFER_ONLY,

    /** Re-arm on every BUFFERING episode (live tuner pin). */
    EVERY_BUFFERING_EPISODE,
}

/**
 * Who may trigger a transcode fallback, and how often. Again the two shipped
 * hosts pinned different models — preserved verbatim:
 *  - [FORCE_DIRECT_PLAY_ONE_SHOT] — the VOD model: a runtime error under the
 *    FORCE_DIRECT_PLAY preference is converted into an automatic transcode
 *    retry exactly once per item/mode-change (latch resets via
 *    [EngineEventCoordinator.onNewItem] / [EngineEventCoordinator.onPlaybackModeChanged]);
 *    any later error surfaces the dialog.
 *  - [EXTERNAL_REQUEST_ONLY] — the live model: engine errors always surface;
 *    fallback decisions fire only through the explicit
 *    [EngineEventCoordinator.onTranscodeFallbackRequested] intake, UNLATCHED —
 *    the live tuner engine owns its own per-load one-shot fallback phase
 *    machine, so a second intake call is a second legitimate fallback request.
 */
enum class FallbackPolicy {
    /** FORCE_DIRECT_PLAY error → one-shot latched transcode fallback (VOD pin). */
    FORCE_DIRECT_PLAY_ONE_SHOT,

    /** Errors always surface; fallback decisions only via the explicit intake (live pin). */
    EXTERNAL_REQUEST_ONLY,
}

/**
 * The raw per-engine event surface the coordinator's policies read — the
 * minimal slice any playback backend must expose to be policed. Decouples
 * the coordinator from [MediaEngine]: the live TV tuner engine (a
 * `LivePlayerEngine`, deliberately NOT a [MediaEngine]) feeds its own
 * instance, mapping its native state enum onto [EnginePlaybackState]; the
 * VOD engines arrive via [toEngineEventSource].
 *
 * Backends without an event channel pass the defaults ([errors] /
 * [subtitleEvents] empty — the coordinator then never emits the
 * corresponding decisions; [currentPositionMs] zero-returning — only read
 * for [EngineDecision.FallbackToTranscode]'s payload).
 */
class EngineEventSource(
    /** Guarded play-state mirror input; also the pass-out poller's liveness read. */
    val isPlaying: StateFlow<Boolean>,
    /** Coarse playback state: buffering mirror + watchdog arm/cancel input + ENDED detection. */
    val playbackState: StateFlow<EnginePlaybackState>,
    /** Structured engine errors. Empty when the backend has no error channel. */
    val errors: Flow<EngineError> = emptyFlow(),
    /** One-shot subtitle events. Empty when the backend never produces them. */
    val subtitleEvents: Flow<SubtitleEvent> = emptyFlow(),
    /**
     * Position captured while emitting a fallback decision (the retry
     * resumes from it). A lambda, not a value, so the read happens at
     * emission time against the CURRENT position.
     */
    val currentPositionMs: () -> Long = { 0L },
)

/**
 * The VOD engines' adapter: every [MediaEngine] already exposes exactly the
 * slice [EngineEventSource] needs, so the mapping is a closure over the
 * engine instance (note [currentPositionMs] stays a live read of the engine,
 * matching the pre-extraction semantics).
 */
fun MediaEngine.toEngineEventSource(): EngineEventSource = EngineEventSource(
    isPlaying = isPlaying,
    playbackState = playbackState,
    errors = errorFlow,
    subtitleEvents = subtitleEvents,
    currentPositionMs = { currentPositionMs },
)

/**
 * Owns the eight engine-event policies that previously lived interleaved in
 * the VOD [com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel]
 * init block's eight-child collector tree (and, as hand-rolled copies, in the
 * live TV player):
 *
 *  1. play-state mirror (guarded)  → [isPlaying]
 *  2. buffering mirror (guarded)   → [isBuffering]; ENDED → [EngineDecision.PlaybackEnded]
 *  3. transcode fallback policy    → [EngineDecision.FallbackToTranscode]
 *     (per [FallbackPolicy]: the VOD one-shot FORCE_DIRECT_PLAY latch, or
 *     the live engine-callback intake)
 *  4. subtitle-event toasts        → [EngineDecision.InformUser]
 *  5. initial-buffering watchdog   → [EngineDecision.ShowError] of [EngineError.Timeout]
 *     (per [WatchdogScope])
 *  6. pass-out protection (interaction clock + poller) → [EngineDecision.PassOutPause]
 *
 * Depth comes from a narrow interface: raw engine flows in ([EngineEventSource]),
 * decisions out. The coordinator never writes uiState, never touches
 * repositories, and never invokes engine *commands* — the only engine reads
 * are the source's state flows and `currentPositionMs` captured while
 * emitting the fallback decision. That makes every policy assertable with
 * plain flow fixtures and an injected [clock] — zero mocks.
 *
 * Lives in `:shared:core:player-contract` (the engine-agnostic home beside
 * [MediaEngine] and [EnginePositionTicker]) so BOTH players consume one
 * policy implementation: the VOD `PlaybackSession` constructs/re-arms it
 * over `PlayerSessionManager.engineFlow`, the live `LiveTvPlayerViewModel`
 * over its tuner engine — each host pins its historically-pinned behavior
 * via [Config] knobs instead of a hand-rolled copy.
 *
 * Policy semantics are pinned verbatim from the pre-extraction ViewModel,
 * including the watchdog's per-engine-instance latch quirk under
 * [WatchdogScope.INITIAL_BUFFER_ONLY] (see the coordinator suite's
 * `watchdog_*` quirk pins) and the fallback latch's re-arm inputs
 * ([onNewItem]/[onPlaybackModeChanged]).
 *
 * Instances are single-use: [dispose] tears down the internal scope for good.
 * Owning scopes re-create the coordinator (and re-collect its outputs) per
 * session/re-initialization rather than trying to revive a disposed one.
 */
private const val ENGINE_POLICIES = "EngineEventCoordinator.enginePolicies"

class EngineEventCoordinator(
    /** Parent scope (the owning ViewModel/session's). A [SupervisorJob] child is derived internally. */
    scope: CoroutineScope,
    /**
     * Hot engine-source stream — one [EngineEventSource] per engine instance,
     * `null` while no engine exists (policies suspend until the next swap).
     */
    engineSource: Flow<EngineEventSource?>,
    /**
     * Synchronous playback-mode read (the VOD host's ui-prefs mirror). Only
     * consulted under [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT]; the
     * default suits hosts without a playback-mode preference.
     */
    private val getPlaybackMode: () -> PlaybackMode = { PlaybackMode.AUTO },
    /**
     * Localized FORCE_DIRECT_PLAY fallback notice for [EngineDecision.InformUser];
     * takes the originating engine error's [EngineError.message]. Only used
     * under [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT].
     */
    private val directPlayFallbackNotice: suspend (String) -> String = { it },
    /** Pass-out protection hours; values <= 0 disable the poller. */
    passOutHours: Flow<Int> = emptyFlow(),
    /** Monotonic clock, injectable for tests. Defaults to elapsed-realtime. */
    private val clock: () -> Long = ::monotonicNowMillis,
    private val config: Config = Config(),
) {
    /**
     * @param bufferingTimeoutMs watchdog window before the first READY.
     * @param watchdogScope how widely the watchdog may arm — see [WatchdogScope].
     * @param passOutPollIntervalMs how often the pass-out poller checks the
     *   interaction clock while playback is active.
     * @param fallbackPolicy who triggers the transcode fallback — see [FallbackPolicy].
     */
    data class Config(
        val bufferingTimeoutMs: Long = BUFFERING_TIMEOUT_MS,
        val watchdogScope: WatchdogScope = WatchdogScope.INITIAL_BUFFER_ONLY,
        val passOutPollIntervalMs: Long = 60_000L,
        val fallbackPolicy: FallbackPolicy = FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT,
    )

    /**
     * Own supervisor scope derived from [scope]'s dispatcher + job: one failed
     * policy collector must not cancel its siblings, and everything dies with
     * the parent scope on ViewModel clear. [dispose] cancels it explicitly
     * ahead of engine release.
     */
    private val coordinatorScope = CoroutineScope(
        SupervisorJob(scope.coroutineContext[Job]) + scope.coroutineContext.minusKey(Job)
    )

    private val _isPlaying = MutableStateFlow(false)
    /** Guarded play-state mirror: conflated (same-value emissions skip). */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    /** Guarded buffering mirror: conflated (same-value emissions skip). */
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _decisions = MutableSharedFlow<EngineDecision>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    /**
     * Decision stream consumed by the owner's fan-out executor.
     * `tryEmit`-only — a mid-teardown emission never suspends.
     */
    val decisions: SharedFlow<EngineDecision> = _decisions.asSharedFlow()

    // ── Fallback policy state: one-shot latch (FORCE_DIRECT_PLAY_ONE_SHOT) ──

    /**
     * Guards the FORCE_DIRECT_PLAY → FORCE_TRANSCODE fallback so the runtime
     * error triggered by an undecodable direct-played codec only retries once.
     * Reset by [onNewItem] (new item load) and [onPlaybackModeChanged]
     * (explicit user mode change), matching the pre-extraction semantics.
     * Never consulted under [FallbackPolicy.EXTERNAL_REQUEST_ONLY].
     */
    @Volatile
    private var directPlayFallbackOffered = false

    // ── Pass-out state: interaction clock ───────────────────────────────────

    @Volatile
    private var lastInteractionElapsedMs: Long = clock()

    /** Current engine source, read by the pass-out poller (never commanded here). */
    @Volatile
    private var currentSource: EngineEventSource? = null

    // The per-engine policy slot: cancel-and-replace on every engine swap
    // (null engine cancels and forgets). TaskBundle keeps the choreography
    // once; the slot key is the only local fact.
    private val enginePolicyTasks = TaskBundle(coordinatorScope)

    init {
        coordinatorScope.launch {
            engineSource.collect { source ->
                currentSource = source
                if (source != null) {
                    enginePolicyTasks.replace(ENGINE_POLICIES) { launchEnginePolicies(source) }
                } else {
                    enginePolicyTasks.cancel(ENGINE_POLICIES)
                }
            }
        }
        coordinatorScope.launch { collectPassOutPoller(passOutHours) }
    }

    /**
     * Per-engine policy collectors. Relaunched on every engine swap; a
     * supervisor child so one failed collector does not cancel the rest.
     */
    private fun launchEnginePolicies(source: EngineEventSource): Job = coordinatorScope.launch {
        // Pass-out interaction clock: a resume (false → true transition)
        // resets the clock so a long paused period doesn't immediately trip
        // the timer once playback resumes.
        launch {
            var wasPlaying = false
            source.isPlaying.collect { playing ->
                _isPlaying.value = playing
                if (playing && !wasPlaying) {
                    lastInteractionElapsedMs = clock()
                }
                wasPlaying = playing
            }
        }
        launch {
            source.playbackState.collect { state ->
                _isBuffering.value = state == EnginePlaybackState.BUFFERING
                if (state == EnginePlaybackState.ENDED) {
                    _decisions.tryEmit(EngineDecision.PlaybackEnded)
                }
            }
        }
        launch { collectWatchdog(source) }
        launch {
            source.errors.collect { error -> onEngineError(error) }
        }
        launch {
            source.subtitleEvents.collect { event ->
                // `when` over a sealed interface: Kotlin flags non-exhaustiveness
                // once a second variant is added to SubtitleEvent, forcing this
                // site to handle it instead of silently dropping it.
                when (event) {
                    SubtitleEvent.MalformedTrackDisabled ->
                        _decisions.tryEmit(
                            EngineDecision.InformUser(
                                "Subtitles disabled — malformed subtitle track detected"
                            )
                        )
                }
            }
        }
    }

    /**
     * Initial-buffering watchdog: if the engine never reaches READY within
     * [Config.bufferingTimeoutMs], surface the playback-error dialog so the
     * user can retry. Without this, a player can sit in BUFFERING forever for
     * undecodable content or (live) a stalled tuner — no exception is raised,
     * so the error path never fires and the spinner spins indefinitely.
     *
     * How widely it arms is the host's [WatchdogScope] pin:
     *  - [WatchdogScope.INITIAL_BUFFER_ONLY] (VOD): only armed before first
     *    READY so it does not trigger on legitimate mid-playback rebuffer.
     *  - [WatchdogScope.EVERY_BUFFERING_EPISODE] (live): every BUFFERING
     *    episode re-arms a fresh window, because a tuner can stall
     *    mid-playback exactly like a cold start.
     *
     * follow-up: under INITIAL_BUFFER_ONLY the `hasReachedReady` latch is
     * scoped to this collector, which lives as long as the engine *instance*.
     * All real VOD reload paths swap in a fresh engine instance
     * (PlayerSessionManager always re-creates via the factory), so the
     * watchdog re-arms per reload — but an engine instance that survives a
     * reload (e.g. a reclaimed mini-player engine, or a same-instance retry)
     * stays disarmed for the subsequent load. Pinned as-is ("moves policy,
     * does not change it"); see the coordinator suite's `watchdog_*` quirk pins.
     */
    private suspend fun collectWatchdog(source: EngineEventSource) {
        var hasReachedReady = false
        var watchdogJob: Job? = null
        source.playbackState.collect { state ->
            when (state) {
                EnginePlaybackState.BUFFERING -> {
                    val scopeAllowsArming = when (config.watchdogScope) {
                        WatchdogScope.INITIAL_BUFFER_ONLY -> !hasReachedReady
                        WatchdogScope.EVERY_BUFFERING_EPISODE -> true
                    }
                    if (scopeAllowsArming && watchdogJob == null) {
                        watchdogJob = coordinatorScope.launch {
                            delay(config.bufferingTimeoutMs)
                            // Fire guard, per scope pin: INITIAL_BUFFER_ONLY
                            // keeps the pre-extraction latch read;
                            // EVERY_BUFFERING_EPISODE re-reads the live state
                            // so a flip away from BUFFERING that raced the
                            // expiry never fires (the live tuner's original
                            // `state.value == BUFFERING` guard).
                            val stillArmed = when (config.watchdogScope) {
                                WatchdogScope.INITIAL_BUFFER_ONLY -> !hasReachedReady
                                WatchdogScope.EVERY_BUFFERING_EPISODE ->
                                    source.playbackState.value == EnginePlaybackState.BUFFERING
                            }
                            if (stillArmed) {
                                // Route through the EngineError taxonomy rather
                                // than hand-rolling a string, so the timeout
                                // path matches the errorFlow path's contract.
                                // Start-up timeout is recoverable on the same
                                // engine (often a slow first-segment fetch) —
                                // the dialog offers retry too, not just
                                // switch-engine.
                                _decisions.tryEmit(
                                    EngineDecision.ShowError(
                                        error = EngineError.Timeout(),
                                        clearBuffering = true,
                                    )
                                )
                            }
                        }
                    }
                }
                EnginePlaybackState.READY -> {
                    hasReachedReady = true
                    watchdogJob?.cancel()
                    watchdogJob = null
                }
                else -> {
                    watchdogJob?.cancel()
                    watchdogJob = null
                }
            }
        }
    }

    /**
     * Transcode fallback policy on a runtime engine error. Under
     * [FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT]: FORCE_DIRECT_PLAY uses the
     * "direct play all" profile — the server hands back a static URL even for
     * codecs the player can't decode, so a runtime error here usually means
     * the direct-played container/codec is undecodable. Offer a one-shot
     * automatic transcode fallback rather than surfacing a dead-end error
     * dialog; any later error surfaces the structured dialog. The client
     * forced this fallback, so the toast carries the originating engine error
     * rather than the server's generic reasons.
     *
     * Under [FallbackPolicy.EXTERNAL_REQUEST_ONLY] errors ALWAYS surface —
     * the fallback trigger belongs to the backend (the live tuner engine's
     * per-load phase machine drives the intake below).
     */
    private suspend fun onEngineError(error: EngineError) {
        if (
            config.fallbackPolicy == FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT &&
            getPlaybackMode() == PlaybackMode.FORCE_DIRECT_PLAY &&
            !directPlayFallbackOffered
        ) {
            directPlayFallbackOffered = true
            _decisions.tryEmit(
                EngineDecision.InformUser(directPlayFallbackNotice(error.message))
            )
            _decisions.tryEmit(
                EngineDecision.FallbackToTranscode(
                    fromPositionMs = currentSource?.currentPositionMs?.invoke() ?: 0L
                )
            )
        } else {
            _decisions.tryEmit(EngineDecision.ShowError(error, clearBuffering = false))
        }
    }

    /**
     * Pass-out protection: pause playback after `hours` of no user
     * interaction. Polls every [Config.passOutPollIntervalMs] while an hour
     * budget is configured; a disabled value (<= 0) stops the loop. The
     * coordinator only *decides* — the owner pauses the engine and emits the
     * user-facing pass-out event.
     */
    private suspend fun collectPassOutPoller(passOutHours: Flow<Int>) {
        passOutHours.collectLatest { hours ->
            if (hours <= 0) return@collectLatest
            // Loop exits via cancellation (scope dispose / collectLatest
            // re-entry); delay is the cancellation point.
            while (true) {
                delay(config.passOutPollIntervalMs)
                val source = currentSource ?: continue
                if (!source.isPlaying.value) continue
                val elapsedMs = clock() - lastInteractionElapsedMs
                val thresholdMs = hours * 3_600_000L
                if (elapsedMs >= thresholdMs) {
                    _decisions.tryEmit(EngineDecision.PassOutPause)
                }
            }
        }
    }

    // ── Public control surface ───────────────────────────────────────────────

    /** Resets the fallback latch (a new item is loading). */
    fun onNewItem() {
        directPlayFallbackOffered = false
    }

    /** Re-arms the fallback latch (the user explicitly changed the mode). */
    fun onPlaybackModeChanged() {
        directPlayFallbackOffered = false
    }

    /**
     * External fallback intake for backends whose fallback trigger is an
     * engine-side callback rather than an error-flow event — the live tuner
     * engine invokes its host when a direct/direct-stream load fails (the
     * engine's own per-load one-shot phase machine decides WHEN; the
     * coordinator converts the request into [EngineDecision.FallbackToTranscode]
     * so the host keeps ONE decision intake). Deliberately UNLATCHED: the
     * caller's engine owns the retry-counting policy, so repeated requests
     * each emit a decision.
     */
    fun onTranscodeFallbackRequested() {
        _decisions.tryEmit(
            EngineDecision.FallbackToTranscode(
                fromPositionMs = currentSource?.currentPositionMs?.invoke() ?: 0L
            )
        )
    }

    /** Resets the pass-out interaction clock (a user interaction occurred). */
    fun onUserInteraction() {
        lastInteractionElapsedMs = clock()
    }

    /** True after [dispose] — the coordinator must be re-created to run again. */
    val disposed: Boolean get() = !coordinatorScope.coroutineContext.isActive

    /**
     * Cancels all internal collectors and watchdog child jobs. Call before
     * releasing the engine so no collector observes a released engine. The
     * parent scope cancellation (owner teardown) also covers this.
     */
    fun dispose() {
        coordinatorScope.cancel()
    }
}
