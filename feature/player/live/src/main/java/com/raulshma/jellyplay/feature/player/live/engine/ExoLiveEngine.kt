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
 *  - MIME is **always** [MimeTypes.APPLICATION_M3U8] so the
 *    `HlsMediaSource` is selected. The VOD path pinned `VIDEO_MP2T` for
 *    extension-less URLs, which routed the stream through the wrong source
 *    factory and never played.
 *  - No `seekTo(startPositionMs)` — ExoPlayer joins at the live edge by HLS
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

    private val _isAtLiveEdge = MutableStateFlow(true)
    override val isAtLiveEdge: StateFlow<Boolean> = _isAtLiveEdge.asStateFlow()

    override var onTranscodeFallbackNeeded: (() -> Unit)? = null

    private var currentMethod: LivePlayMethod = LivePlayMethod.DIRECT_STREAM

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
        _errorMessage.value = null
        currentMethod = request.playMethod

        val mediaItem = MediaItem.Builder()
            .setUri(request.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
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

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()

    override fun seekToLiveEdge() {
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        exoPlayer.seekTo(duration)
    }

    override fun seekTo(positionMs: Long) {
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        exoPlayer.seekTo(positionMs.coerceIn(0L, duration))
    }

    override fun release() {
        exoPlayer.release()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
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
            // on a direct-stream/direct-play failure,
            // ask the ViewModel to re-resolve with transcoding forced.
            _state.value = LiveEngineState.ERROR
            _errorMessage.value = error.localizedMessage ?: "Playback error"
            if (currentMethod != LivePlayMethod.TRANSCODE) {
                onTranscodeFallbackNeeded?.invoke()
            }
        }
    }

    override fun refreshLiveWindow() {
        val duration = exoPlayer.duration
        _durationMs.value = if (duration == C.TIME_UNSET) -1L else duration
        _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
        _isAtLiveEdge.value = duration == C.TIME_UNSET ||
            duration <= 0L ||
            (duration - exoPlayer.currentPosition) <= LIVE_EDGE_TOLERANCE_MS
    }

    private companion object {
        private const val LIVE_EDGE_TOLERANCE_MS = 10_000L
    }
}
