package com.raulshma.jellyplay.feature.player.video.engine

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive

/**
 * Shared boilerplate for the three concrete [MediaEngine] implementations
 * (ExoPlayer / MPV / libVLC). The published-state chassis (the flow fields +
 * exposures, polling/stats setters, published-state resets and the
 * `updateConfig` diff prologue) lives in player-contract's
 * [EngineStateChassis] — this class extends it and keeps ONLY the pieces that
 * cannot cross into commonMain: the Android threading pair and the activity
 * pause/resume template.
 *
 * Each subclass still owns its genuinely engine-specific surface (the native
 * player handle, track / subtitle logic, video-stats projection,
 * volume/mute contract, positionFlow wiring) — those diverge fundamentally
 * across engines and are NOT lifted anywhere.
 *
 * [NoOpEngine] intentionally does NOT extend this class (nor the chassis): it
 * is a 90-line stub whose `errorFlow` is `emptyFlow()`, whose polling default
 * is `0L`, and which has no engineScope / mainHandler / currentConfig. Forcing
 * it through this base would require overriding half the lifted members back
 * to no-ops.
 *
 * Threading: [engineScope] and [mainHandler] are main-thread-affine, matching
 * the prior per-engine convention. StateFlow mutation is safe from any thread
 * (StateFlow is thread-safe), but the engines have historically mutated from
 * the main thread / native callbacks; that contract is unchanged.
 */
abstract class BasePlayerEngine : EngineStateChassis() {

    // -------------------------------------------------------------------------------------------
    // Activity pause/resume template. The three engines used to carry byte-
    // identical bodies: remember isPlaying → pause on pause; restore play on
    // resume only if the engine was playing before. ExoPlayer does not detach
    // views here (PlayerView owns the surface lifecycle), mpv has no view
    // churn either — only libVLC must detach/attach its preview views around
    // the pause, which is what the [onPausedNative]/[onResumingNative] hooks
    // exist for. The resume hook runs BEFORE the play restore (VLC's views
    // must be re-attached before playback restarts).
    // -------------------------------------------------------------------------------------------

    // `var` (not private-set) because ExoPlayer's onResetItemScopedState clears
    // it alongside the item-scoped residue — a stale latch must never survive
    // into the next item's pause.
    protected var wasPlayingBeforeActivityPause = false

    final override fun onActivityPause() {
        wasPlayingBeforeActivityPause = _isPlaying.value
        pause()
        onPausedNative()
    }

    final override fun onActivityResume() {
        onResumingNative()
        if (wasPlayingBeforeActivityPause) {
            wasPlayingBeforeActivityPause = false
            play()
        }
    }

    /**
     * Engine-specific work after the pause in [onActivityPause] — libVLC
     * detaches its preview views. No-op by default (ExoPlayer / mpv).
     */
    protected open fun onPausedNative() {}

    /**
     * Engine-specific work before the play restore in [onActivityResume] —
     * libVLC re-attaches its preview views. No-op by default (ExoPlayer / mpv).
     */
    protected open fun onResumingNative() {}

    // -------------------------------------------------------------------------------------------
    // Main-thread scope + handler.
    // -------------------------------------------------------------------------------------------

    /**
     * The scope invariant, held structurally by this base instead of by a
     * per-adapter convention: a live coroutine scope per load; only the
     * terminal release teardown kills one.
     *
     * Every read of [engineScope] returns a LIVE scope — a generation
     * cancelled by `release()` (or by an internal release()-as-reset inside
     * a load) is replaced on the next read. No load path can ever run on a
     * cancelled scope, and downstream consumers that capture the scope
     * (positionFlow's [EnginePositionTicker], mpv's track-refresh coalescer)
     * can never launch into a dead one — the frozen-seek-bar failure mode
     * the per-engine revive choreography (d6b764964) used to patch one call
     * site at a time. Those per-engine revive calls are deleted; the
     * invariant lives here, at the only place the scope is handed out.
     *
     * The only cancel sites remain the engines' own `release()` bodies
     * (terminal teardown); their semantics are unchanged by this refactor.
     *
     * Threading: unchanged — main-thread-affine like the `protected var` it
     * replaces. The check-then-replace is unsynchronized exactly like the
     * former helper; a hypothetical concurrent double-replace leaks one
     * childless SupervisorJob to GC, which is benign.
     */
    private var scopeGeneration = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected val engineScope: CoroutineScope
        get() {
            if (!scopeGeneration.isActive) {
                scopeGeneration = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            }
            return scopeGeneration
        }

    protected val mainHandler = Handler(Looper.getMainLooper())
}
