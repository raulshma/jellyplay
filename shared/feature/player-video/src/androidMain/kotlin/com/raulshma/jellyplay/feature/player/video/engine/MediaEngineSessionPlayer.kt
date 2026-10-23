package com.raulshma.jellyplay.feature.player.video.engine

import android.os.Looper
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
 * [State] on every [invalidateState] (each flow emission triggers one).
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
        // only on real changes; the position ticker drives the advancing
        // session progress bar (the same cadence the VOD UI polls at).
        listOf(
            boundEngine.playbackState,
            boundEngine.bufferedPositionMs,
        ).forEach { flow ->
            scope.launch {
                flow.collect { if (!released) invalidateState() }
            }
        }
        scope.launch {
            boundEngine.positionFlow.collect { if (!released) invalidateState() }
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
