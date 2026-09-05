package com.raulshma.jellyplay.feature.player.video.engine

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared boilerplate for the three concrete [MediaEngine] implementations
 * (ExoPlayer / MPV / libVLC). These previously each re-declared an identical
 * block of 8 StateFlow/SharedFlow backing fields + exposures, an identical
 * `engineScope` + `mainHandler` pair, identical polling/stats-toggle fields and
 * setters, and an identical `updateConfig` early-return prologue.
 *
 * This base absorbs only the byte-identical plumbing. Each subclass still owns
 * its genuinely engine-specific surface (the native player handle, track /
 * subtitle logic, video-stats projection, volume/mute contract, positionFlow
 * wiring) — those diverge fundamentally across engines and are NOT lifted here.
 *
 * [NoOpEngine] intentionally does NOT extend this class: it is a 90-line stub
 * whose `errorFlow` is `emptyFlow()`, whose polling default is `0L`, and which
 * has no engineScope / mainHandler / currentConfig. Forcing it through this
 * base would require overriding half the lifted members back to no-ops.
 *
 * Threading: [engineScope] and [mainHandler] are main-thread-affine, matching
 * the prior per-engine convention. StateFlow mutation is safe from any thread
 * (StateFlow is thread-safe), but the engines have historically mutated from
 * the main thread / native callbacks; that contract is unchanged.
 */
abstract class BasePlayerEngine : MediaEngine {

    // -------------------------------------------------------------------------------------------
    // StateFlow / SharedFlow backing fields + public exposures.
    // Identical across Exo / MPV / libVLC; lifted verbatim.
    // -------------------------------------------------------------------------------------------

    protected val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    protected val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    protected val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    protected val _currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
    override val currentCues: StateFlow<List<TimedCue>> = _currentCues.asStateFlow()

    // Default no-op: only mpv overrides (its sub-text property). ExoPlayer
    // reparents its native SubtitleView instead; libVLC has no cue source.
    override val liveSubtitleCue: StateFlow<CharSequence?> = MutableStateFlow(null)

    protected val _errorFlow = MutableSharedFlow<EngineError>(extraBufferCapacity = 1)
    override val errorFlow: Flow<EngineError> = _errorFlow.asSharedFlow()

    protected val _subtitleEvents = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 1)
    override val subtitleEvents: Flow<SubtitleEvent> = _subtitleEvents.asSharedFlow()

    protected val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    protected val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    protected val _pollingIntervalMs = MutableStateFlow(DEFAULT_POLLING_INTERVAL_MS)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()

    protected val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    final override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    final override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    // -------------------------------------------------------------------------------------------
    // Published-state resets (C5). Each adapter's release() used to re-derive
    // its own reset list over these base-owned flows; the lists live here now
    // so a field can never be silently dropped from one engine's teardown.
    // -------------------------------------------------------------------------------------------

    /**
     * Resets the per-item published leaves (cues / tracks / buffered /
     * stats) and fires [onResetItemScopedState]. The granularity ExoPlayer's
     * reuse path needs — it deliberately does NOT touch the transport leaves
     * (`_playbackState` / `_isPlaying`), which a mid-session item swap must
     * not flip.
     */
    protected fun resetItemScopedPublishedState() {
        _currentCues.value = emptyList()
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
        onResetItemScopedState()
    }

    /**
     * Full published-state reset for teardown: everything
     * [resetItemScopedPublishedState] clears plus the transport leaves
     * (`_playbackState` → IDLE, `_isPlaying` → false). `_pollingIntervalMs` /
     * `_videoStatsEnabled` are session prefs and stay; the SharedFlows have
     * no state to clear.
     */
    protected fun resetPublishedEngineState() {
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        resetItemScopedPublishedState()
    }

    /**
     * Per-engine residue cleared alongside the base resets — genuinely
     * adapter-owned fields (mpv's cached position/duration mirrors and
     * `liveSubtitleCue`, ExoPlayer's decoder counters + stats guard, libVLC's
     * cached duration). Called from both resets above, mirroring the former
     * inline placement inside each adapter's reset list.
     */
    protected open fun onResetItemScopedState() {}

    // -------------------------------------------------------------------------------------------
    // Main-thread scope + handler. Identical field declarations across engines.
    // -------------------------------------------------------------------------------------------

    /**
     * Main-thread coroutine scope. Cancelled on [release] and recreated in
     * [load] (see [recreateEngineScopeIfInactive]). `protected var` so each
     * engine can recreate it on its own load/release schedule.
     */
    protected var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private set

    /**
     * Recreate [engineScope] when the previous one has been cancelled (e.g.
     * after [release]). Guards against the case where load() is called on an
     * already-cancelled scope. The guarded form is the safe common denominator
     * across engines — Exo previously recreated unconditionally (equivalent
     * because release cancels first), MPV/libVLC guarded.
     */
    protected fun recreateEngineScopeIfInactive() {
        if (!engineScope.isActive) {
            engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    protected val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------------------------
    // EngineConfig diff prologue. Identical 3-line guard across engines.
    // -------------------------------------------------------------------------------------------

    protected var currentConfig = EngineConfig()
        protected set

    /**
     * Implements the dedup guard + assignment that every engine had copy-pasted
     * at the top of `updateConfig`, then delegates the per-field diff to
     * [onConfigChanged]. Subclasses override the hook, NOT this method.
     */
    final override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        val oldConfig = currentConfig
        currentConfig = config
        onConfigChanged(oldConfig, config)
    }

    /**
     * Engine-specific reaction to a config change. Called only when [updateConfig]
     * detected a real diff (old != new). Receives both snapshots so each engine
     * can diff the individual fields it cares about (audio effects, subtitle
     * style, video filters, …) without re-implementing the guard.
     */
    protected abstract fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig)

    private companion object {
        /** Default position-ticker cadence (ms). NoOp uses 0L; real engines share this. */
        private const val DEFAULT_POLLING_INTERVAL_MS = 1000L
    }
}
