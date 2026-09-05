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

    // ── Volume / mute — templates over the policy ───────────────────────────
    // The four MediaEngine volume/mute commands are FINAL templates over the
    // adapter seams below; the clamp / remember / mute-unmute decisions live
    // in the pure commonMain PlaybackVolumePolicy. The remember call is
    // unified BEFORE the native write (the former Exo/Vlc order) — this fixes
    // mpv's drift, where the delta bodies wrote the native handle first and
    // remembered second. What genuinely diverges per engine stays a seam:
    //  - the native write mechanism (player.volume / setPropertyDouble /
    //    int-percent) — [applyNativeVolume],
    //  - the native current-level read (the delta base) — [readNativeVolume],
    //  - the boost ceiling — [volumeBoostCeiling] (libVLC amplifies to 2.0),
    //  - the mute-restore vocabulary — [nativeVolumeRestore],
    //  - the native mute FLAG (mpv only) — [applyNativeMuteFlag],
    //  - dispatch containment/threading — [dispatchVolumeCommand].

    @Volatile
    protected var lastUnmuteVolume: Float = 1f

    protected fun rememberUnmuteVolumeIfAudible(volume01: Float) {
        if (volume01 > 0f) lastUnmuteVolume = volume01
    }

    protected fun snapshotSystemVolumeForMute() {
        val sys = MediaStreamVolume.getNormalized(appContext)
        if (sys > 0f) lastUnmuteVolume = sys
    }

    /** Normalized boost ceiling for [PlaybackVolumePolicy.planLevel]. */
    protected open val volumeBoostCeiling: Float
        get() = PlaybackVolumePolicy.MAX_BOOST_NOMINAL

    /**
     * Writes [normalized] (0..[volumeBoostCeiling]) to the native volume
     * handle. Implementations own their null-tolerance exactly where the
     * former per-engine bodies had it (mpv/libVLC setVolume null-tolerate the
     * write and keep the system-stream sync; ExoPlayer aborts the whole call
     * via [dispatchVolumeCommand]).
     */
    protected abstract fun applyNativeVolume(normalized: Float)

    /**
     * The native handle's current normalized level — the delta base for
     * [increaseVolume] / [decreaseVolume]. Null when the handle is absent:
     * the delta templates abort exactly where the former bodies'
     * `?: return` early-exits did.
     */
    protected abstract fun readNativeVolume(): Float?

    /**
     * Which level the native handle should carry across a mute/unmute
     * transition ([muted] = the requested state): mpv owns a real mute flag
     * and leaves its volume alone on both; Media3 unmutes its handle to full
     * and lets the system stream carry the remembered level; libVLC mutes by
     * zeroing and restores the remembered level.
     */
    protected abstract fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore

    /**
     * Native mute-flag write — mpv's real silencing mechanism; the default
     * no-op covers engines whose silencing is the volume plan itself.
     */
    protected open fun applyNativeMuteFlag(muted: Boolean) {}

    /**
     * Whether the mute/unmute template may run at all. libVLC overrides to
     * abort without a handle (its former `mediaPlayer ?: return` guard — no
     * snapshot, no system-stream write); the default runs unconditionally,
     * which is mpv's contract (its system-stream sync IS the mute's surface
     * when the native handle is gone) and ExoPlayer's (its abort lives in
     * [dispatchVolumeCommand]).
     */
    protected open fun muteTemplateEnabled(): Boolean = true

    /**
     * Dispatch shell around every template run. ExoPlayer routes through its
     * player thread AND aborts when no player is attached (its former bodies
     * early-returned); mpv/libVLC wrap the run in their former swallow-all
     * try/catch.
     */
    protected open fun dispatchVolumeCommand(command: () -> Unit) {
        command()
    }

    final override fun setVolume(value: Float) = dispatchVolumeCommand {
        val plan = PlaybackVolumePolicy.planLevel(value, volumeBoostCeiling)
        rememberUnmuteVolumeIfAudible(plan.normalized)
        applyNativeVolume(plan.normalized)
        MediaStreamVolume.setNormalized(appContext, plan.systemStream)
    }

    final override fun increaseVolume(delta: Float) = dispatchVolumeCommand {
        val current = readNativeVolume() ?: return@dispatchVolumeCommand
        val plan = PlaybackVolumePolicy.planLevel(current + delta, volumeBoostCeiling)
        rememberUnmuteVolumeIfAudible(plan.normalized)
        applyNativeVolume(plan.normalized)
        MediaStreamVolume.setNormalized(appContext, plan.systemStream)
    }

    final override fun decreaseVolume(delta: Float) = dispatchVolumeCommand {
        val current = readNativeVolume() ?: return@dispatchVolumeCommand
        val plan = PlaybackVolumePolicy.planLevel(current - delta, volumeBoostCeiling)
        rememberUnmuteVolumeIfAudible(plan.normalized)
        applyNativeVolume(plan.normalized)
        MediaStreamVolume.setNormalized(appContext, plan.systemStream)
    }

    final override fun setMuted(muted: Boolean) = dispatchVolumeCommand {
        applyNativeMuteFlag(muted)
        if (!muteTemplateEnabled()) return@dispatchVolumeCommand
        if (muted) {
            val plan = PlaybackVolumePolicy.planMute(nativeVolumeRestore(muted = true))
            if (plan.snapshotSystemVolume) snapshotSystemVolumeForMute()
            plan.nativeVolume?.let { applyNativeVolume(it) }
            MediaStreamVolume.setNormalized(appContext, plan.systemStream)
        } else {
            val plan = PlaybackVolumePolicy.planUnmute(
                lastUnmuteVolume,
                nativeVolumeRestore(muted = false),
            )
            plan.nativeVolume?.let { applyNativeVolume(it) }
            MediaStreamVolume.setNormalized(appContext, plan.systemStream)
        }
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
