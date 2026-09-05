package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Deep module for the three *reloadable* [MediaEngine] adapters (ExoPlayer / MPV / libVLC).
 *
 * This layer hoists the behaviour that was previously re-derived per adapter:
 *  - snapshot / restore of `position + playbackSpeed + isPlaying` across a rebuild
 *  - remembered unmute level + `MediaStreamVolume` sync (the clamp / mute
 *    decisions themselves live in commonMain's [PlaybackVolumePolicy])
 *  - the `callbackFlow + EnginePositionTicker` shell for `positionFlow`
 *  - the `EngineVideoStats` change-guard
 *
 * [NoOpEngine] intentionally does NOT extend this class — it never reloads,
 * has no scope/handler, and would require overriding half the members back to
 * no-ops. See [BasePlayerEngine] for the full rationale.
 */
abstract class ReloadablePlayerEngine(
    protected val appContext: Context,
) : BasePlayerEngine() {

    // ── Reload-preserving playback ──────────────────────────────────────────

    data class PlaybackSnapshot(
        val positionMs: Long,
        val wasPlaying: Boolean,
        val playbackSpeed: Float,
    )

    protected open fun captureSnapshot(): PlaybackSnapshot =
        PlaybackSnapshot(
            positionMs = currentPositionMs.coerceAtLeast(0L),
            wasPlaying = snapshotIsPlaying(),
            playbackSpeed = playbackSpeed,
        )

    /** Engine-specific isPlaying read — ExoPlayer overrides to read `player.isPlaying` synchronously. */
    protected open fun snapshotIsPlaying(): Boolean = _isPlaying.value

    protected open fun currentIsPlaying(): Boolean = snapshotIsPlaying()

    /**
     * Executes [rebuild] with the pre-rebuild snapshot, then restores play-state
     * and speed. Position restoration is the caller's responsibility inside
     * [rebuild] (e.g. `setMediaItem(item, snap.positionMs)` or `mp.time =
     * snap.positionMs`) — this method only handles the *post*-rebuild
     * `play()` / speed restore that was duplicated 5×.
     *
     * Speed is restored only when the engine reports a different value after the
     * rebuild, so a no-op rebuild does not churn the audio pipeline.
     */
    protected inline fun withPreservedPlayback(crossinline rebuild: (PlaybackSnapshot) -> Unit) {
        val snap = captureSnapshot()
        rebuild(snap)
        if (playbackSpeed != snap.playbackSpeed) {
            runCatching { setPlaybackSpeed(snap.playbackSpeed) }
        }
        if (snap.wasPlaying && !currentIsPlaying()) {
            // Some engines flip `_isPlaying` synchronously inside `play()`,
            // others do it on the next callback. Check the live flow value to
            // avoid double-play while still ensuring resume after a rebuild that
            // cleared the flag.
            runCatching { play() }
        }
    }

    // ── Volume / mute — shared memory + system-stream sync ──────────────────
    // The clamp / remember / mute-unmute decisions live in the pure
    // commonMain PlaybackVolumePolicy; these members are only the shared
    // storage and the system-stream snapshot the adapters apply around the
    // policy's plans.

    @Volatile
    protected var lastUnmuteVolume: Float = 1f

    protected fun rememberUnmuteVolumeIfAudible(volume01: Float) {
        if (volume01 > 0f) lastUnmuteVolume = volume01
    }

    protected fun snapshotSystemVolumeForMute() {
        val sys = MediaStreamVolume.getNormalized(appContext)
        if (sys > 0f) lastUnmuteVolume = sys
    }

    // ── positionFlow shell ──────────────────────────────────────────────────

    /**
     * Shared `callbackFlow + EnginePositionTicker` shell. Each adapter keeps
     * only its `onActive` body (buffered pos + stats). ExoPlayer wraps this
     * with its extra `Player.Listener` for discontinuities.
     */
    protected fun positionFlowWithTicker(
        isCurrentlyPlaying: () -> Boolean = { _isPlaying.value },
        onActive: () -> Unit,
    ): Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = _isPlaying,
            isCurrentlyPlaying = isCurrentlyPlaying,
            onActive = {
                trySend(currentPositionMs)
                onActive()
            },
        ).launch()
        awaitClose { ticker.cancel() }
    }.conflate()

    // ── videoStats change-guard ─────────────────────────────────────────────

    protected var lastVideoStats: EngineVideoStats? = null
        private set

    protected fun publishStatsIfChanged(newStats: EngineVideoStats) {
        if (newStats != lastVideoStats) {
            lastVideoStats = newStats
            _videoStats.value = newStats
        }
    }

    protected fun resetStatsGuard() {
        lastVideoStats = null
    }
}
