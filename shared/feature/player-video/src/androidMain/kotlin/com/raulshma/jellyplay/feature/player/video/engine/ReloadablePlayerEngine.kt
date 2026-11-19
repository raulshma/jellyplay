package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.shareIn

/**
 * Deep module for the three *reloadable* [MediaEngine] adapters (ExoPlayer / MPV / libVLC).
 *
 * This layer hoists the behaviour that was previously re-derived per adapter:
 *  - snapshot / restore of `position + playbackSpeed + isPlaying` across a rebuild
 *  - the four volume/mute commands + the remembered unmute level — composed
 *    over commonMain's [VolumeCommandChassis] (the clamp / mute decisions
 *    live in [PlaybackVolumePolicy]; the `MediaStreamVolume` sync stays this
 *    engine's seam)
 *  - the `callbackFlow + EnginePositionTicker` shell for `positionFlow`
 *  - the shared replay-1 internal position flow ([InternalPositionSource])
 *  - the `EngineVideoStats` change-guard
 *
 * [NoOpEngine] intentionally does NOT extend this class — it never reloads,
 * has no scope/handler, and would require overriding half the members back to
 * no-ops. See [BasePlayerEngine] for the full rationale.
 */
abstract class ReloadablePlayerEngine(
    protected val appContext: Context,
) : BasePlayerEngine(), InternalPositionSource {

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

    // ── Volume / mute — the chassis over the common templates ───────────────
    // The four MediaEngine volume/mute commands ride the commonMain
    // VolumeCommandChassis — the VolumeCommandTemplates finals (over
    // PlaybackVolumePolicy), the dispatch containment and the unmute memory
    // (remember-before-write, the audible-only rule, the pre-mute snapshot)
    // all live THERE now, so the desktop mpv adapter composes the identical
    // chassis instead of hand-copying it and the two can no longer drift.
    // What genuinely diverges per engine stays on this class — the three
    // engines' seam implementations are exactly where they were — bridged to
    // the chassis through [volumeCommands]:
    //  - the dispatch shell — [dispatchVolumeCommand] (ExoPlayer's
    //    player-thread routing + no-player abort, mpv/libVLC's swallow-all
    //    try/catch),
    //  - the native write mechanism (player.volume / setPropertyDouble /
    //    int-percent) — [applyNativeVolume],
    //  - the native current-level read (the delta base) — [readNativeVolume],
    //  - the boost ceiling — [volumeBoostCeiling] (libVLC amplifies to 2.0),
    //  - the mute-restore vocabulary — [nativeVolumeRestore],
    //  - the native mute FLAG (mpv only) — [applyNativeMuteFlag],
    //  - the mute-template gate — [muteTemplateEnabled],
    //  - the pre-mute snapshot source (the system music stream) — the seam's
    //    preMuteSnapshotLevel,
    //  - the system music-stream sync — the seam's syncSystemStream.

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

    final override fun setVolume(value: Float, isUserChange: Boolean) = volumeChassis.setVolume(value, isUserChange)

    final override fun increaseVolume(delta: Float) = volumeChassis.increaseVolume(delta)

    final override fun decreaseVolume(delta: Float) = volumeChassis.decreaseVolume(delta)

    final override fun setMuted(muted: Boolean) = volumeChassis.setMuted(muted)

    /**
     * Bridges the protected adapter seams (kept exactly as they were — see the
     * member KDocs above) to the chassis. The system music-stream sync is the
     * Android-only surface here; the user-change capture stays a no-op —
     * Android video deliberately stays on the system STREAM_MUSIC model and
     * never arms the per-content-type memory (see VideoPlayerViewModel's
     * volume-memory wiring, desktop-only).
     */
    private val volumeCommands = object : NativeVolumeSeam {
        override val volumeBoostCeiling: Float get() = this@ReloadablePlayerEngine.volumeBoostCeiling
        override val muteTemplateEnabled: Boolean get() = this@ReloadablePlayerEngine.muteTemplateEnabled()
        override fun readNativeVolume(): Float? = this@ReloadablePlayerEngine.readNativeVolume()
        override fun applyNativeVolume(normalized: Float) = this@ReloadablePlayerEngine.applyNativeVolume(normalized)
        override fun applyNativeMuteFlag(muted: Boolean) = this@ReloadablePlayerEngine.applyNativeMuteFlag(muted)
        override fun nativeVolumeRestore(muted: Boolean) = this@ReloadablePlayerEngine.nativeVolumeRestore(muted)
        override fun preMuteSnapshotLevel(): Float = MediaStreamVolume.getNormalized(appContext)
        override fun syncSystemStream(normalized: Float) {
            MediaStreamVolume.setNormalized(appContext, normalized)
        }
        override fun onUserVolumeChanged(level: Float) {}
    }

    /**
     * The composed volume chassis: the templates + the dispatch containment +
     * the unmute memory over [volumeCommands], dispatched through
     * [dispatchVolumeCommand] so the engines' containment overrides keep
     * their virtual dispatch. `protected` because ExoPlayer's release resets
     * the chassis-owned memory ([VolumeCommandChassis.resetUnmuteMemory]).
     */
    protected val volumeChassis = VolumeCommandChassis(
        native = volumeCommands,
        dispatch = ::dispatchVolumeCommand,
    )

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
            scopeProvider = { engineScope },
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

    /**
     * The ONE shared, replay-1 position stream internal consumers ride
     * ([InternalPositionSource]) — `shareIn` over this engine's public
     * [positionFlow], so the virtual dispatch picks up whatever the subclass
     * published (the mpv/VLC `positionFlowWithTicker` shell, ExoPlayer's
     * listener-driven `callbackFlow`): the shared upstream collector is the
     * flow's only collection and therefore spawns the engine's only
     * internally-owned ticker, no matter how many internal consumers subscribe.
     *
     * `WhileSubscribed(0, 0)` — both timeouts zero — keeps the sharing
     * lifecycle exactly as tight as the cold flow's own per-collector
     * `awaitClose`: the upstream ticker stops the moment the last subscriber
     * leaves (a released session adapter must not leave a native-handle
     * polling ticker behind it — `Lazily`/`Eagerly` would leak exactly that),
     * and the replay cache expires with the sharing so a fresh subscription
     * never replays a stale playhead (the cold flow's leading
     * `trySend(currentPositionMs)` re-seeds it immediately anyway).
     *
     * Scope lifetime, stated plainly: the sharing job pins the `engineScope`
     * generation read at first subscription. The public flow's own collectors
     * carry the identical exposure — an internal release()-as-reset inside a
     * `load()` cancels the generation the running ticker launched on, which
     * closes that collection; the provider accessor only helps NEW collections.
     * This flow is therefore never worse than the flow it shares, and the
     * session controller's engine-identity check rebuilds the adapter (and
     * with it this subscription) whenever the engine instance changes.
     */
    override val internalPositionFlow: Flow<Long> by lazy {
        positionFlow.shareIn(
            engineScope,
            SharingStarted.WhileSubscribed(/* stopTimeoutMillis = */ 0L, /* replayExpirationMillis = */ 0L),
            replay = 1,
        )
    }

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
