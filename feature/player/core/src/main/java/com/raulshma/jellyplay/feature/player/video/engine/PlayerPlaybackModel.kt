package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified, engine-agnostic consumption surface for playback state.
 *
 * `MediaEngine` is the *engine-facing* contract (each of the three backends
 * implements it idiomatically). This interface is the *consumption-facing*
 * contract that the ViewModel and UI read. The single `DefaultPlayerPlaybackModel`
 * implementation binds to one [MediaEngine] at a time and re-exposes its Flows
 * in a normalized shape:
 *
 *  - high-frequency `positionFlow` is conflated to a 1 Hz [positionMs] StateFlow
 *    so leaf composables that read it recompose at most once per second;
 *  - the bare-string `errorFlow` is mapped to a structured [EngineError] on
 *    [errors] (mapping is naive `Unknown` for now; engines emit structured
 *    errors directly);
 *  - `duration`, `bufferedPosition`, `tracks`, `videoStats`, `state`, and
 *    `capabilities` are forwarded directly.
 *
 * The ViewModel reads only this model's properties — never the engine's Flows
 * directly — so engine swaps (re-bind) don't require any VM/UI change.
 */
interface PlayerPlaybackModel {
    val state: StateFlow<EnginePlaybackState>
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val bufferedMs: StateFlow<Long>
    val availableTracks: StateFlow<List<MediaTrack>>
    val videoStats: StateFlow<EngineVideoStats>
    val errors: SharedFlow<EngineError>
    val capabilities: EngineCapabilities

    /**
     * Bind to [engine], re-deriving all exposed Flows from it. Safe to call
     * repeatedly: each call unbinds the previous engine first. Called by the
     * ViewModel on session start and on engine swap.
     */
    fun bind(engine: MediaEngine)

    /** Unbind the current engine, resetting state to IDLE. */
    fun unbind()
}

/**
 * Default [PlayerPlaybackModel]. Holds a single bound [MediaEngine] at a time.
 *
 * @param scope the coroutine scope used to drive the 1 Hz position conflation
 *   loop. In production this is the ViewModel's `viewModelScope`; in tests it
 *   is `runTest`'s `backgroundScope`. The scope must outlive any `bind` call.
 */
class DefaultPlayerPlaybackModel(
    private val scope: CoroutineScope,
) : PlayerPlaybackModel {

    private var engine: MediaEngine? = null
    private var positionJob: Job? = null
    private val reactiveJobs = mutableListOf<Job>()

    private val _state = MutableStateFlow(EnginePlaybackState.IDLE)
    override val state: StateFlow<EnginePlaybackState> = _state.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow(0L)
    override val bufferedMs: StateFlow<Long> = _bufferedMs.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    // `replay = 1` so a subscriber that attaches between an emission and its
    // collection (a race that is unavoidable under `runTest`'s virtual-time
    // scheduler, where `async { errors.first() }` does not subscribe until the
    // scheduler pumps) still observes the most recent error. A UI that
    // re-subscribes after an error will see the last error once — acceptable
    // for now.
    private val _errors = MutableSharedFlow<EngineError>(replay = 1, extraBufferCapacity = 4)
    override val errors: SharedFlow<EngineError> = _errors.asSharedFlow()

    override val capabilities: EngineCapabilities
        get() = engine?.capabilities ?: EngineCapabilities()

    override fun bind(engine: MediaEngine) {
        unbind()
        this.engine = engine

        _state.value = engine.playbackState.value
        _isPlaying.value = engine.isPlaying.value
        _durationMs.value = engine.durationMs
        _bufferedMs.value = engine.bufferedPositionMs.value
        _availableTracks.value = engine.availableTracks.value
        _videoStats.value = engine.videoStats.value

        // High-frequency position flow → 1 Hz conflated StateFlow.
        // `durationMs` is refreshed here too because [MediaEngine.durationMs] is
        // a plain `Long` (not a StateFlow) that resolves late for HLS/transcoded/
    // live streams — snapshotting it once at bind would pin an initial 0.
        positionJob = scope.launch {
            while (isActive) {
                _positionMs.value = engine.currentPositionMs
                val currentDuration = engine.durationMs
                if (currentDuration != _durationMs.value) {
                    _durationMs.value = currentDuration
                }
                delay(POSITION_INTERVAL_MS)
            }
        }

        // Reactive collection of the engine's state-bearing flows.
        //
        // `Dispatchers.Unconfined` is used deliberately: these collectors only
        // forward engine emissions into the model's own StateFlow/SharedFlow,
        // whose writes are atomic (`MutableStateFlow.value`, `tryEmit`).
        // Running them unconfined means an emission is mirrored into the model
        // synchronously on the emitting thread, with no dispatcher hop — so a
        // state change observed by the UI happens in the same pump as the
        // engine's emission. In production this is safe because every exposed
        // property is a thread-safe StateFlow/SharedFlow read on the Main
        // (Compose) thread; in tests it is what lets `runTest`'s virtual-time
        // scheduler drain the collectors via `advanceUntilIdle()` when the
        // scope is `backgroundScope` (kotlinx-coroutines-test 1.11 does not
        // otherwise resume background-scope StateFlow collectors).
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.playbackState.collect { _state.value = it }
        }
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.isPlaying.collect { _isPlaying.value = it }
        }
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.bufferedPositionMs.collect { _bufferedMs.value = it }
        }
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.availableTracks.collect { _availableTracks.value = it }
        }
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.videoStats.collect { _videoStats.value = it }
        }
        reactiveJobs += scope.launch(Dispatchers.Unconfined) {
            engine.errorFlow.collect { raw ->
                _errors.tryEmit(EngineError.Unknown(raw))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun unbind() {
        positionJob?.cancel()
        positionJob = null
        reactiveJobs.forEach { it.cancel() }
        reactiveJobs.clear()
        engine = null
        _state.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _bufferedMs.value = 0L
        _availableTracks.value = emptyList()
        _videoStats.value = EngineVideoStats()
        _errors.resetReplayCache()
    }

    companion object {
        /** Position-polling interval for the conflated [positionMs] flow. */
        const val POSITION_INTERVAL_MS = 1_000L
    }
}
