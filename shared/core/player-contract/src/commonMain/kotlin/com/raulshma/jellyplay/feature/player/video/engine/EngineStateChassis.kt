package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The published-state chassis of a [MediaEngine]: the backing-flow block +
 * exposures, the polling/stats setters, the published-state resets and the
 * `updateConfig` diff prologue that [com.raulshma.jellyplay.feature.player.video.engine.BasePlayerEngine]
 * (Android) used to own and the desktop mpv engine used to RE-DECLARE verbatim
 * (twelve fields + the reset lists, duplicated across androidMain and
 * apps/desktop with only the SharedFlow buffer capacities diverging).
 *
 * It implements the *state-surface slice* of [MediaEngine] and stays abstract
 * for everything an engine genuinely owns (the native handle, load/transport,
 * tracks, position, capabilities). Two hosts extend it:
 *
 *  - [BasePlayerEngine] (androidMain) — the three Android adapters
 *    (ExoPlayer / mpv / libVLC) via [ReloadablePlayerEngine]; it keeps the
 *    Android-only threading pair (`engineScope` self-healing law +
 *    `mainHandler`) and the activity pause/resume template, both of which
 *    cannot cross into commonMain (`android.os.Handler`).
 *  - Desktop `MpvDesktopEngine` (apps/desktop, JVM) — which historically
 *    could not extend the androidMain base and instead duplicated the state
 *    surface. Its deliberate divergences are constructor parameters here:
 *    `errorReplay`/`errorExtraBufferCapacity`/`subtitleExtraBufferCapacity`
 *    (desktop: construction-time error replay is load-bearing — the
 *    "missing libmpv" black screen — plus an 8-slot buffer each; Android
 *    base: no replay, 1-slot buffers, defaults).
 *
 * Buffer capacities are constructor parameters rather than open vals because
 * they are consumed at construction time — the flows are `val` fields built
 * in the initializer, and the desktop's replay=1 semantics only exist if the
 * value is fixed before any emission can happen.
 *
 * Threading: StateFlow mutation is safe from any thread; `currentConfig` is
 * `@Volatile` because hosts read it from non-main threads (mpv event pumps,
 * stats pollers) while the UI thread writes it through [updateConfig].
 */
abstract class EngineStateChassis(
    /** [errorFlow] replay count — desktop passes 1 (late-subscriber delivery). */
    errorReplay: Int = 0,
    /** [errorFlow] extra buffer capacity — desktop passes 8, Android default 1. */
    errorExtraBufferCapacity: Int = 1,
    /** [subtitleEvents] extra buffer capacity — desktop passes 8, Android default 1. */
    subtitleExtraBufferCapacity: Int = 1,
) : MediaEngine {

    // -------------------------------------------------------------------------------------------
    // StateFlow / SharedFlow backing fields + public exposures.
    // Identical across Exo / mpv (Android) / mpv (desktop) / libVLC; lifted
    // verbatim from the former BasePlayerEngine block (the desktop twin's
    // twelve re-declarations were deleted in the same move).
    // -------------------------------------------------------------------------------------------

    protected val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    protected val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    protected val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    protected val _currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
    override val currentCues: StateFlow<List<TimedCue>> = _currentCues.asStateFlow()

    // Writable default: hosts whose cue source is engine-internal write it
    // directly (desktop mpv's sub-text property). Android's mpv engine keeps
    // its own override (a former base default the subclass re-declares);
    // ExoPlayer reparents its native SubtitleView and libVLC has no cue
    // source, so for those the flow simply stays null forever.
    protected val _liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> = _liveSubtitleCue.asStateFlow()

    protected val _errorFlow = MutableSharedFlow<EngineError>(
        replay = errorReplay,
        extraBufferCapacity = errorExtraBufferCapacity,
    )
    override val errorFlow: Flow<EngineError> = _errorFlow.asSharedFlow()

    protected val _subtitleEvents = MutableSharedFlow<SubtitleEvent>(
        extraBufferCapacity = subtitleExtraBufferCapacity,
    )
    override val subtitleEvents: Flow<SubtitleEvent> = _subtitleEvents.asSharedFlow()

    protected val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    /**
     * Multi-band buffered surface — the range-level generalization of
     * [_bufferedPositionMs]. Maintained by each adapter alongside the scalar;
     * both reset together in [resetItemScopedPublishedState].
     */
    protected val _bufferedRanges = MutableStateFlow<List<LongRange>>(emptyList())
    override val bufferedRanges: StateFlow<List<LongRange>> = _bufferedRanges.asStateFlow()

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
    // its own reset list over these chassis-owned flows; the lists live here
    // now so a field can never be silently dropped from one engine's teardown.
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
        _bufferedRanges.value = emptyList()
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
     * adapter-owned fields (the mpv engines' `liveSubtitleCue`, ExoPlayer's
     * decoder counters + stats guard, libVLC's cached duration). Called from
     * both resets above, mirroring the former inline placement inside each
     * adapter's reset list.
     */
    protected open fun onResetItemScopedState() {}

    // -------------------------------------------------------------------------------------------
    // EngineConfig diff prologue. Identical guard across engines (the desktop
    // twin carried its own copy with an extra ownership-refresh step — that
    // step moved to the top of its [onConfigChanged] override, which sees the
    // same call shape: only on a real diff, after the assignment).
    // -------------------------------------------------------------------------------------------

    @Volatile
    protected var currentConfig: EngineConfig = EngineConfig()
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

    protected companion object {
        /** Default position-ticker cadence (ms). NoOp uses 0L; real engines share this. */
        protected const val DEFAULT_POLLING_INTERVAL_MS = 1000L
    }
}
