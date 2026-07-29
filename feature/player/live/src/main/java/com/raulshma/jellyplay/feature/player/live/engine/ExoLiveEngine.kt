package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * Media3 ExoPlayer-based live TV engine.
 *
 * Key design choices vs the previous VOD live path:
 *  - MIME is chosen by [LivePlayMethod]: transcoded streams are real Jellyfin
 *    HLS master playlists (`APPLICATION_M3U8`), while a direct live stream is
 *    the tuner's raw MPEG-TS piped through `/Videos/{id}/stream` (`VIDEO_MP2T`)
 *    — a plain progressive TS source, not an HLS playlist. (The server reports
 *    the source `container` as `hls`, but that labels the tuner feed; the
 *    `/stream` endpoint serves raw TS bytes, so an HLS hint makes ExoPlayer's
 *    parser fail with "no #EXTM3U header".) The previous code forced
 *    `APPLICATION_M3U8` for everything, which broke direct streams.
 *  - No `seekTo(startPositionMs)` — ExoPlayer joins at the live edge by
 *    default.
 *  - [DefaultLoadControl] is tuned for fast live join (10s min buffer, 5s
 *    rebuffer) rather than the VOD defaults.
 *  - On a [PlaybackException] while the current method is direct, the
 *    engine invokes [onTranscodeFallbackNeeded] so the ViewModel can
 *    re-resolve via `PlaybackRepository` with direct disabled.
 */
class ExoLiveEngine(
    context: Context,
    private val config: LiveEngineConfig,
    streamingClient: OkHttpClient,
) : LivePlayerEngine {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(LiveEngineState.IDLE)
    override val state: StateFlow<LiveEngineState> = _state.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(-1L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _errorDetail = MutableStateFlow<String?>(null)
    override val errorDetail: StateFlow<String?> = _errorDetail.asStateFlow()

    private val _isAtLiveEdge = MutableStateFlow(true)
    override val isAtLiveEdge: StateFlow<Boolean> = _isAtLiveEdge.asStateFlow()

    override var onTranscodeFallbackNeeded: (() -> Unit)? = null

    private var currentMethod: LivePlayMethod = LivePlayMethod.DIRECT_STREAM

    /** Sticky latch: once a terminal error surfaces (no fallback available),
     *  hold ERROR until the next [load] so the follow-up STATE_IDLE ExoPlayer
     *  emits after a PlaybackException does not mask it as BUFFERING. */
    @Volatile
    private var errorTerminal: Boolean = false

    /** Latch: once release() runs, every subsequent method short-circuits. */
    @Volatile
    private var released: Boolean = false

    /** Latch: prevent rebuffer storms from firing fallback repeatedly. */
    @Volatile
    private var fallbackInvoked: Boolean = false

    private val httpDataSourceFactory = OkHttpDataSource.Factory(streamingClient)
        .setUserAgent("JellyPlay")
        .setDefaultRequestProperties(
            buildMap {
                config.authToken?.let { put("X-Emby-Token", it) }
            }
        )

    private val dataSourceFactory: DataSource.Factory =
        DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(config.minBufferMs, config.maxBufferMs, 1_000, config.rebufferMs)
        .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val trackSelector = DefaultTrackSelector(appContext)

    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()
        .also { it.addListener(PlayerListener()) }

    /** Underlying ExoPlayer for [androidx.media3.ui.PlayerView] attachment. */
    override val media3Player: Player get() = exoPlayer

    override fun load(request: LivePlaybackRequest) {
        if (released) return
        _errorMessage.value = null
        _errorDetail.value = null
        currentMethod = request.playMethod
        // Per-load reset: a previous channel's fallback latch must not carry
        // over — otherwise a reused engine could never fire the transcode
        // fallback for a new channel after one fallback fired on the old.
        fallbackInvoked = false
        errorTerminal = false

        val mediaItem = MediaItem.Builder()
            .setUri(request.url)
            .setMimeType(mimeTypeFor(request.playMethod))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(request.title)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        // Live streams start at the live edge; never seek to a resume position.
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun play() = runIfNotReleased { exoPlayer.play() }

    override fun pause() = runIfNotReleased { exoPlayer.pause() }

    override fun seekToLiveEdge() = runIfNotReleased {
        // seekToDefaultPosition() is ExoPlayer's documented "return to live
        // edge" call for HLS — seeking to `duration` lands one segment before
        // the true edge because `duration` is the upper window bound.
        exoPlayer.seekToDefaultPosition()
    }

    override fun seekTo(positionMs: Long) = runIfNotReleased {
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return@runIfNotReleased
        exoPlayer.seekTo(positionMs.coerceIn(0L, duration))
    }

    override fun refreshLiveWindow() = runIfNotReleased {
        val duration = exoPlayer.duration
        _durationMs.value = if (duration == C.TIME_UNSET) -1L else duration
        _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
        _isAtLiveEdge.value = duration == C.TIME_UNSET ||
            duration <= 0L ||
            (duration - exoPlayer.currentPosition) <= LIVE_EDGE_TOLERANCE_MS
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { exoPlayer.release() }
    }

    /**
     * Short-circuit helper for the post-release guards. `release()` keeps its
     * own explicit guard because it must not become a no-op when already
     * released (it sets the latch first).
     */
    private inline fun runIfNotReleased(block: () -> Unit) {
        if (released) return
        block()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // After a terminal error ExoPlayer emits STATE_IDLE; ignore it so
            // it doesn't overwrite ERROR (which would re-show the spinner and
            // hide the error dialog). The latch clears on the next load().
            if (errorTerminal) {
                refreshLiveWindow()
                return
            }
            _state.value = when (playbackState) {
                Player.STATE_BUFFERING -> LiveEngineState.BUFFERING
                Player.STATE_READY -> LiveEngineState.READY
                Player.STATE_ENDED -> LiveEngineState.ENDED
                Player.STATE_IDLE -> LiveEngineState.IDLE
                else -> _state.value
            }
            refreshLiveWindow()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            // Capture both a short message and the full stacktrace-grade text
            // so the error overlay can show an expandable details section.
            _errorMessage.value = error.localizedMessage ?: "Playback error"
            _errorDetail.value = error.toString()
            // Gate on fallbackInvoked — ExoPlayer can fire onPlayerError
            // repeatedly during a rebuffer storm and we only want one trigger.
            if (!fallbackInvoked && currentMethod != LivePlayMethod.TRANSCODE) {
                // Stay in BUFFERING while the ViewModel re-resolves to
                // transcode; flipping to ERROR here flashes the error overlay
                // for a frame before the fallback clears it. If the fallback
                // also fails, it surfaces the error itself.
                fallbackInvoked = true
                _state.value = LiveEngineState.BUFFERING
                onTranscodeFallbackNeeded?.invoke()
            } else {
                // No fallback available (already on transcode, or already
                // retried) — surface the error and stop. Latch so the
                // follow-up STATE_IDLE does not mask it.
                errorTerminal = true
                _state.value = LiveEngineState.ERROR
            }
        }
    }

    private companion object {
        private const val LIVE_EDGE_TOLERANCE_MS = 10_000L
    }

    /**
     * Picks the MediaItem MIME hint that routes [DefaultMediaSourceFactory] to
     * the correct source for the resolved play method.
     *
     *  - [LivePlayMethod.TRANSCODE] — the server hands back a real Jellyfin HLS
     *    master playlist, so it needs [MimeTypes.APPLICATION_M3U8].
     *  - [LivePlayMethod.DIRECT_STREAM] / [LivePlayMethod.DIRECT_PLAY] — a
     *    direct live stream is the tuner's raw MPEG-TS piped through
     *    `/Videos/{id}/stream?LiveStreamId=…`, **not** an HLS playlist (the
     *    server reports the source `container` as `hls`, but that labels the
     *    tuner feed, not what the `/stream` endpoint serves). `VIDEO_MP2T`
     *    selects the progressive `TsExtractor`, which streams the growing TS
     *    without waiting for a `#EXTM3U` header. Forcing HLS here made the
     *    parser fail (`Input does not start with the #EXTM3U header`) and the
     *    engine fell back to transcode.
     */
    private fun mimeTypeFor(method: LivePlayMethod): String = when (method) {
        LivePlayMethod.TRANSCODE -> MimeTypes.APPLICATION_M3U8
        LivePlayMethod.DIRECT_STREAM,
        LivePlayMethod.DIRECT_PLAY -> MimeTypes.VIDEO_MP2T
    }
}
