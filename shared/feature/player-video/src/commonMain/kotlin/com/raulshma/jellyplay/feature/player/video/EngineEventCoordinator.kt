package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
/**
 * Initial-buffering watchdog window. If the engine has not reached READY
 * within this window since load, the playback-error dialog is surfaced so the
 * user can retry with another engine. Long enough to cover legitimate
 * cold-start buffering on slow networks, short enough to feel responsive when
 * playback is genuinely stuck (e.g. undecodable content).
 */
internal const val BUFFERING_TIMEOUT_MS = 20_000L

/**
 * A single engine event interpreted as a session-level decision. The
 * [EngineEventCoordinator] owns the *policy* (when a decision fires); the
 * [VideoPlayerViewModel] owns the *execution* (what a decision does to uiState,
 * the engine, repositories and collaborators) via its decisions fan-out
 * collector.
 */
sealed interface EngineDecision {
    /**
     * A structured engine error should be surfaced on the playback-error
     * dialog. [clearBuffering] is `true` for the start-up watchdog timeout,
     * which must also lift the stuck buffering spinner.
     */
    data class ShowError(val error: EngineError, val clearBuffering: Boolean) : EngineDecision

    /**
     * A FORCE_DIRECT_PLAY load failed with a runtime error the coordinator's
     * one-shot latch converted into an automatic transcode retry at
     * [fromPositionMs].
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
 * Owns the eight engine-event policies that previously lived interleaved in
 * the [VideoPlayerViewModel] init block's eight-child collector tree:
 *
 *  1. play-state mirror (guarded)  → [isPlaying]
 *  2. buffering mirror (guarded)   → [isBuffering]; ENDED → [EngineDecision.PlaybackEnded]
 *  3. FORCE_DIRECT_PLAY → transcode one-shot fallback latch → [EngineDecision.FallbackToTranscode]
 *  4. subtitle-event toasts        → [EngineDecision.InformUser]
 *  5. initial-buffering watchdog   → [EngineDecision.ShowError] of [EngineError.Timeout]
 *  6. pass-out protection (interaction clock + poller) → [EngineDecision.PassOutPause]
 *
 * Depth comes from a narrow interface: raw engine flows in, decisions out.
 * The coordinator never writes uiState, never touches repositories, and never
 * invokes engine *commands* — the only engine reads are state flows
 * (`isPlaying`, `playbackState`) and `currentPositionMs` captured while
 * emitting the fallback decision. That makes every policy assertable with a
 * plain fake over the [MediaEngine] contract and an injected [clock] —
 * zero mocks.
 *
 * Policy semantics are pinned verbatim from the pre-extraction ViewModel,
 * including the watchdog's per-engine-instance latch quirk (see
 * [collectWatchdog]) and the fallback latch's re-arm inputs
 * ([onNewItem]/[onPlaybackModeChanged]).
 *
 * Instances are single-use: [dispose] tears down the internal scope for good.
 * The owning Activity-scoped ViewModel survives `release()` across media, so
 * it re-creates the coordinator (and re-collects its outputs) on each
 * re-initialization rather than trying to revive a disposed one.
 */
class EngineEventCoordinator(
    /** Parent scope (the ViewModel's). A [SupervisorJob] child is derived internally. */
    scope: CoroutineScope,
    /** Hot engine handle stream — the same [PlayerSessionManager.engineFlow]. */
    engineFlow: StateFlow<MediaEngine?>,
    /** Synchronous playback-mode read (the ViewModel's `_uiState.value.uiPrefs.playbackMode`). */
    private val getPlaybackMode: () -> PlaybackMode,
    /**
     * Localized FORCE_DIRECT_PLAY fallback notice for [EngineDecision.InformUser];
     * takes the originating engine error's [EngineError.message].
     */
    private val directPlayFallbackNotice: suspend (String) -> String,
    /** Pass-out protection hours; values <= 0 disable the poller. */
    passOutHours: Flow<Int>,
    /** Monotonic clock, injectable for tests. Defaults to elapsed-realtime. */
    private val clock: () -> Long = ::monotonicNowMillis,
    private val config: Config = Config(),
) {
    /**
     * @param bufferingTimeoutMs watchdog window before the first READY.
     * @param passOutPollIntervalMs how often the pass-out poller checks the
     *   interaction clock while playback is active.
     */
    data class Config(
        val bufferingTimeoutMs: Long = BUFFERING_TIMEOUT_MS,
        val passOutPollIntervalMs: Long = 60_000L,
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
     * Decision stream consumed by the ViewModel's fan-out executor.
     * `tryEmit`-only — a mid-teardown emission never suspends.
     */
    val decisions: SharedFlow<EngineDecision> = _decisions.asSharedFlow()

    // ── Policy 3 state: one-shot fallback latch ─────────────────────────────

    /**
     * Guards the FORCE_DIRECT_PLAY → FORCE_TRANSCODE fallback so the runtime
     * error triggered by an undecodable direct-played codec only retries once.
     * Reset by [onNewItem] (new item load) and [onPlaybackModeChanged]
     * (explicit user mode change), matching the pre-extraction semantics.
     */
    @Volatile
    private var directPlayFallbackOffered = false

    // ── Policy 6 state: pass-out interaction clock ──────────────────────────

    @Volatile
    private var lastInteractionElapsedMs: Long = clock()

    /** Current engine, read by the pass-out poller (never commanded here). */
    @Volatile
    private var currentEngine: MediaEngine? = null

    private var enginePolicyJob: Job? = null

    init {
        coordinatorScope.launch {
            engineFlow.collect { engine ->
                enginePolicyJob?.cancel()
                currentEngine = engine
                if (engine != null) {
                    enginePolicyJob = launchEnginePolicies(engine)
                }
            }
        }
        coordinatorScope.launch { collectPassOutPoller(passOutHours) }
    }

    /**
     * Per-engine policy collectors. Relaunched on every engine swap; a
     * supervisor child so one failed collector does not cancel the rest.
     */
    private fun launchEnginePolicies(engine: MediaEngine): Job = coordinatorScope.launch {
        // Pass-out interaction clock: a resume (false → true transition)
        // resets the clock so a long paused period doesn't immediately trip
        // the timer once playback resumes.
        launch {
            var wasPlaying = false
            engine.isPlaying.collect { playing ->
                _isPlaying.value = playing
                if (playing && !wasPlaying) {
                    lastInteractionElapsedMs = clock()
                }
                wasPlaying = playing
            }
        }
        launch {
            engine.playbackState.collect { state ->
                _isBuffering.value = state == EnginePlaybackState.BUFFERING
                if (state == EnginePlaybackState.ENDED) {
                    _decisions.tryEmit(EngineDecision.PlaybackEnded)
                }
            }
        }
        launch { collectWatchdog(engine) }
        launch {
            engine.errorFlow.collect { error -> onEngineError(engine, error) }
        }
        launch {
            engine.subtitleEvents.collect { event ->
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
     * [Config.bufferingTimeoutMs] during the *initial* buffer (before first
     * READY), surface the playback-error dialog so the user can retry with a
     * different engine. Without this, ExoPlayer can sit in STATE_BUFFERING
     * forever for undecodable content — no PlaybackException is raised, so the
     * error path never fires and the spinner spins indefinitely. Only armed
     * before first READY so it does not trigger on legitimate mid-playback
     * rebuffer (seeks, quality switches, network blips).
     *
     * follow-up: the `hasReachedReady` latch is scoped to this
     * collector, which lives as long as the engine *instance*. All real
     * reload paths swap in a fresh engine instance (PlayerSessionManager
     * always re-creates via the factory), so the watchdog re-arms per reload —
     * but an engine instance that survives a reload (e.g. a reclaimed
     * mini-player engine, or a same-instance retry) stays disarmed for the
     * subsequent load. Pinned as-is ("moves policy, does not
     * change it"); see EngineEventCoordinatorTest.watchdog_* quirk pins.
     */
    private suspend fun collectWatchdog(engine: MediaEngine) {
        var hasReachedReady = false
        var watchdogJob: Job? = null
        engine.playbackState.collect { state ->
            when (state) {
                EnginePlaybackState.BUFFERING -> {
                    if (!hasReachedReady && watchdogJob == null) {
                        watchdogJob = coordinatorScope.launch {
                            delay(config.bufferingTimeoutMs)
                            if (!hasReachedReady) {
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
     * FORCE_DIRECT_PLAY uses the "direct play all" profile — the server hands
     * back a static URL even for codecs the player can't decode, so a runtime
     * error here usually means the direct-played container/codec is
     * undecodable. Offer a one-shot automatic transcode fallback rather than
     * surfacing a dead-end error dialog; any later error surfaces the
     * structured dialog. The client forced this fallback, so the toast carries
     * the originating engine error rather than the server's generic reasons.
     */
    private suspend fun onEngineError(engine: MediaEngine, error: EngineError) {
        if (getPlaybackMode() == PlaybackMode.FORCE_DIRECT_PLAY && !directPlayFallbackOffered) {
            directPlayFallbackOffered = true
            _decisions.tryEmit(
                EngineDecision.InformUser(directPlayFallbackNotice(error.message))
            )
            _decisions.tryEmit(
                EngineDecision.FallbackToTranscode(fromPositionMs = engine.currentPositionMs)
            )
        } else {
            _decisions.tryEmit(EngineDecision.ShowError(error, clearBuffering = false))
        }
    }

    /**
     * Pass-out protection: pause playback after `hours` of no user
     * interaction. Polls every [Config.passOutPollIntervalMs] while an hour
     * budget is configured; a disabled value (<= 0) stops the loop. The
     * coordinator only *decides* — the ViewModel pauses the engine and emits
     * the user-facing pass-out event.
     */
    private suspend fun collectPassOutPoller(passOutHours: Flow<Int>) {
        passOutHours.collectLatest { hours ->
            if (hours <= 0) return@collectLatest
            // Loop exits via cancellation (scope dispose / collectLatest
            // re-entry); delay is the cancellation point.
            while (true) {
                delay(config.passOutPollIntervalMs)
                val engine = currentEngine ?: continue
                if (!engine.isPlaying.value) continue
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

    /** Resets the pass-out interaction clock (a user interaction occurred). */
    fun onUserInteraction() {
        lastInteractionElapsedMs = clock()
    }

    /** True after [dispose] — the coordinator must be re-created to run again. */
    val disposed: Boolean get() = !coordinatorScope.coroutineContext.isActive

    /**
     * Cancels all internal collectors and watchdog child jobs. Call before
     * releasing the engine so no collector observes a released engine. The
     * parent scope cancellation (ViewModel clear) also covers this.
     */
    fun dispose() {
        coordinatorScope.cancel()
    }
}
