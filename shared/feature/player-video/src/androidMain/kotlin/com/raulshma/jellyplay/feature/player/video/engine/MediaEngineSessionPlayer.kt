package com.raulshma.jellyplay.feature.player.video.engine

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_GET_METADATA
import androidx.media3.common.Player.COMMAND_GET_TIMELINE
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_PREPARE
import androidx.media3.common.Player.COMMAND_SEEK_BACK
import androidx.media3.common.Player.COMMAND_SEEK_FORWARD
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION
import androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH
import androidx.media3.common.Player.COMMAND_STOP
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The media3 [Player] facade over a non-media3 [MediaEngine] (mpv / libVLC —
 * gh-178). The system MediaSession is built around a media3 `Player`, and the
 * engine-agnostic session controller previously no-op'd for engines that host
 * none ([Media3PlayerHost] resolves null), so headset / Bluetooth / lock-screen
 * media buttons were dead on those engines. This adapter supplies the missing
 * transport: media-button commands translate to media3 default handler
 * dispatch, which forwards to the engine's play / pause / seek / speed verbs,
 * and the engine's [EngineStateChassis] flows are folded into the resolved
 * [State] on every [invalidateState]. State/buffer/play-signal emissions each
 * trigger one immediately; the position stream is the one exception — its
 * emissions carry only a moving playhead, so they pass through
 * [PositionInvalidationGate] and invalidate at ~1 Hz instead of at the UI's
 * poll cadence. Nothing on the platform consumes more: media3's own session
 * machinery re-dispatches position info to `MediaController` clients on a
 * periodic timer (3 s by default), and the system MediaStyle notification
 * extrapolates the playhead from the (position, speed) snapshot it already
 * holds, so the per-tick invalidations were pure state-resolution + binder
 * work. Every invalidation that does run re-reads the engine's live position
 * in [getState], so an immediately-invalidated play/pause/buffer change always
 * carries a fresh playhead with it — the gate never starves that.
 *
 * Single-item playlist with a metadata-less placeholder item: the session
 * controller pins the visible title / artwork at the `ForwardingPlayer`
 * layer on top of this player (the same pin it applies to ExoPlayer), so the
 * item carries no metadata of its own. Duration from [MediaEngine.durationMs]
 * is stamped on the item (when known) so the session seek bar has bounds.
 *
 * Lifecycle: the owning controller creates one adapter per non-media3 engine
 * instance, and releases it (cancelling the collectors) when the session is
 * released or superseded — NOT via [Player.release] (the engine's lifecycle
 * stays with the playback session); `release()` here is the adapter's own
 * teardown, and a released adapter must never be handed to a new session.
 *
 * All interaction (engine verbs, flow collection, state reads) happens on the
 * main looper — the same thread the session controller and the engines' UI
 * entry points already use.
 */
@OptIn(UnstableApi::class)
internal class MediaEngineSessionPlayer(
    /** The engine this adapter fronts; exposed so the controller can detect engine swaps. */
    val boundEngine: MediaEngine,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var released = false

    /**
     * The position-only invalidation throttle. Main-thread-only (the position
     * collector is this adapter's sole [PositionInvalidationGate.admit] call
     * site), clock injected for the JVM-pinned policy tests.
     */
    private val positionInvalidationGate = PositionInvalidationGate(
        minIntervalMs = POSITION_INVALIDATION_MIN_INTERVAL_MS,
        jumpThresholdMs = POSITION_INVALIDATION_JUMP_THRESHOLD_MS,
        nowMs = { SystemClock.elapsedRealtime() },
    )

    /**
     * The play state the session reports, refined from two directions: media
     * buttons write it directly ([handleSetPlayWhenReady]) and the engine's
     * settled-state [MediaEngine.isPlaying] mirrors refine it. Held separately
     * from a derived mirror so a pause issued DURING a rebuffer survives it:
     * while BUFFERING the engine's isPlaying is false, and deriving
     * playWhenReady off that would re-render the held pause as play-requested
     * — the next headset toggle would then dispatch play and clobber the
     * pause. A rebuffer still never renders the session "paused" (the field
     * keeps its pre-buffering value through BUFFERING; media3's isPlaying
     * stays false because the state isn't READY), and engine-initiated pauses
     * (pass-out protection) still mirror through, since they land in a
     * settled state. Seeded from the engine at construction — the adapter is
     * built mid-session, after the flows' current values, so a first tick may
     * never come.
     */
    private var requestedPlayWhenReady: Boolean =
        boundEngine.isPlaying.value || boundEngine.playbackState.value == EnginePlaybackState.BUFFERING

    init {
        // Re-resolve state on every engine signal. StateFlow collections fire
        // only on real changes; the position stream drives the advancing
        // session progress bar (gated to ~1 Hz below — the UI's poll cadence,
        // 4 Hz with controls visible, is far beyond what any session consumer
        // renders).
        listOf(
            boundEngine.playbackState,
            boundEngine.bufferedPositionMs,
        ).forEach { flow ->
            scope.launch {
                flow.collect { if (!released) invalidateState() }
            }
        }
        // The position stream: ONE collector on the engine's SHARED internal
        // flow ([InternalPositionSource]) rather than a second, private
        // collection of the cold public flow. Riding the shared flow means the
        // engine instance spawns a single internally-owned
        // [EnginePositionTicker] no matter how many in-process consumers
        // subscribe (today: this adapter; future session-side readers join the
        // same upstream instead of multiplying ticker wake-ups and per-tick
        // readback work). Engines that don't implement the capability (the
        // no-op / test doubles) fall back to the public flow unchanged.
        //
        // Each admitted emission re-resolves the full State — the throttle is
        // what bounds that work to ~1 Hz for the smooth playhead. Seeks stay
        // immediate through the gate's jump arm (a discontinuity vs the last
        // admitted position bypasses the interval window); play/pause/buffer
        // never touch the gate at all (their own collectors above + the
        // handlers' done-future re-resolution below are already immediate).
        val positionSource = (boundEngine as? InternalPositionSource)?.internalPositionFlow
            ?: boundEngine.positionFlow
        scope.launch {
            positionSource.collect { positionMs ->
                if (released) return@collect
                if (positionInvalidationGate.admit(positionMs)) invalidateState()
            }
        }
        // The isPlaying mirror writes [requestedPlayWhenReady] and is wired
        // separately: only settled-state emissions refine it (see the field's
        // KDoc), and it needs the state snapshot at emission time.
        scope.launch {
            boundEngine.isPlaying.collect { playing ->
                if (released) return@collect
                if (boundEngine.playbackState.value != EnginePlaybackState.BUFFERING) {
                    requestedPlayWhenReady = playing
                }
                invalidateState()
            }
        }
    }

    /**
     * Cancels the state collectors. The controller owns this lifecycle.
     * Deliberately NOT a [Player.release] override — the engine's lifecycle
     * stays with the playback session, and `Player.release` must not fire
     * when only the session's backing player is being retired.
     */
    fun releaseAdapter() {
        released = true
        scope.cancel()
    }

    override fun getState(): State {
        val engine = boundEngine
        val playbackState = when (engine.playbackState.value) {
            EnginePlaybackState.IDLE -> Player.STATE_IDLE
            EnginePlaybackState.BUFFERING -> Player.STATE_BUFFERING
            EnginePlaybackState.READY -> Player.STATE_READY
            EnginePlaybackState.ENDED -> Player.STATE_ENDED
            EnginePlaybackState.ERROR -> Player.STATE_IDLE
        }
        // playWhenReady: the requested state (media buttons + the engine's
        // settled-state mirrors — see [requestedPlayWhenReady]), reported with
        // the media3 convention isPlaying = playWhenReady && state == READY,
        // so a rebuffer renders as buffering rather than paused.
        val playWhenReady = requestedPlayWhenReady
        val durationMs = engine.durationMs
        val itemData = MediaItemData.Builder(engine.displayName)
            .setMediaItem(MediaItem.Builder().setMediaId(engine.displayName).build())
            .apply { if (durationMs > 0) setDurationUs(durationMs * 1000) }
            .build()
        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setIsLoading(engine.playbackState.value == EnginePlaybackState.BUFFERING)
            .setPlaybackParameters(PlaybackParameters(engine.playbackSpeed))
            .setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(engine.currentPositionMs.coerceAtLeast(0L))
            .setContentBufferedPositionMs(PositionSupplier.getConstant(engine.bufferedPositionMs.value))
            .build()
    }

    // ── Media-button / session command handlers ──────────────────────────
    // Engine calls are synchronous (the engines' own UI entry points use the
    // same verbs from the same thread); the futures only satisfy the
    // SimpleBasePlayer contract.
    //
    // Immediacy, by construction rather than by explicit call: every handler
    // returns an already-done future, and SimpleBasePlayer re-resolves
    // getState() synchronously the moment it sees that done future — so a
    // lock-screen play / pause / seek / speed change refreshes the session
    // state (including the live currentPositionMs read) in the same main-loop
    // pass as the engine verb, with no throttle in between. An explicit
    // invalidateState() here would not even land: it early-returns while a
    // pending operation is open, which is exactly the handler's own frame.
    // The position gate never sees these paths — it throttles only the
    // engine's position STREAM, not transport commands.

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<Void?> {
        requestedPlayWhenReady = playWhenReady
        if (playWhenReady) boundEngine.play() else boundEngine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<Void?> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<Void?> {
        boundEngine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<Void?> {
        boundEngine.seekTo(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<Void?> {
        boundEngine.setPlaybackSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    // Engine release stays with the playback session — the adapter dying must
    // not stop playback that is being re-homed to a replacement session.
    override fun handleRelease(): ListenableFuture<Void?> = Futures.immediateVoidFuture()

    private companion object {
        /**
         * The smooth-playhead invalidation cadence for the position stream
         * (see [PositionInvalidationGate]): ~1 Hz, the rate the engine ticker
         * itself settles at with the controls hidden, and at or above every
         * cadence the platform consumes — media3 re-dispatches position info
         * to controllers on a 3 s periodic timer, and the system notification
         * extrapolates between snapshots. The former behavior invalidated at
         * the full poll cadence (4 Hz with visible controls).
         */
        const val POSITION_INVALIDATION_MIN_INTERVAL_MS = 1_000L

        /**
         * A position emission at least this far from the last ADMITTED
         * position is a seek/correction and invalidates immediately. Two
         * times the 1 Hz step is enough margin that steady playback stays on
         * the interval arm up to ~2x speed; at 4x the jump arm admits every
         * other emission and past ~8x the gate matches the historical
         * invalidate-per-emission rate — the deliberate, safe degradation
         * direction (more invalidations, never fewer, exactly where playhead
         * extrapolation misleads). Scaling by live speed was rejected: it
         * would add a per-emission playbackSpeed read to the hot path (mpv's
         * speed getter is a JNI property read).
         */
        const val POSITION_INVALIDATION_JUMP_THRESHOLD_MS = 2_000L

        /**
         * The command set a single-item, queue-less, engine-fronting
         * transport can actually honor. COMMAND_PLAY_PAUSE is the
         * media-button critical path (headset / Bluetooth play-pause); the
         * seek + speed + read-only entries let lock-screen / Wear controllers
         * render usable transport UI. (media3 has no COMMAND_PLAY /
         * COMMAND_PAUSE entries — play() and pause() ride PLAY_PAUSE +
         * PREPARE, which translate to handleSetPlayWhenReady.)
         */
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE,
                COMMAND_STOP,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD,
                COMMAND_SEEK_TO_DEFAULT_POSITION,
                COMMAND_SET_SPEED_AND_PITCH,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_TIMELINE,
                COMMAND_GET_METADATA,
            )
            .build()
    }
}
