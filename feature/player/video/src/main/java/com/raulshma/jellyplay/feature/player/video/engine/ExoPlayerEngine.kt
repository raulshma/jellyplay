package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.core.model.DecoderMode

class ExoPlayerEngine(
    private val context: Context,
) : PlayerEngine {

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var onStateChanged: ((Boolean) -> Unit)? = null
    private var onTracksChanged: (() -> Unit)? = null
    private var currentDecoderMode: DecoderMode = DecoderMode.HW_PREFERRED

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onStateChanged?.invoke(isPlaying)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            onTracksChanged?.invoke()
        }
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            this@ExoPlayerEngine.onTracksChanged?.invoke()
        }
    }

    val rawPlayer: ExoPlayer? get() = player
    val rawTrackSelector: DefaultTrackSelector? get() = trackSelector

    override fun initialize(url: String, title: String, startPositionMs: Long) {
        release()

        val selector = DefaultTrackSelector(context)
        trackSelector = selector

        val rendererMode = when (currentDecoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)

        val audioAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(selector)
            .setAudioAttributes(audioAttrs, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exo.addListener(listener)
        player = exo

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        exo.setMediaItem(mediaItem)
        exo.prepare()
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.play()
    }

    override fun release() {
        player?.removeListener(listener)
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        trackSelector = null
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }
    override fun seekForward(amountMs: Long) { player?.seekForward() }
    override fun seekBack(amountMs: Long) { player?.seekBack() }
    override fun setPlaybackSpeed(speed: Float) { player?.setPlaybackSpeed(speed) }

    override fun setAudioDelay(ms: Long) {
        // ExoPlayer doesn't natively support audio delay adjustment
    }

    override fun setDecoderMode(mode: DecoderMode) {
        currentDecoderMode = mode
    }

    override fun setAudioPassthrough(enabled: Boolean) {
        val p = player ?: return
        p.audioAttributes
    }

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        playerView?.setResizeMode(mode)
        if (ratio != null && ratio > 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(ratio)
        } else if (ratio == null || ratio == 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(0f)
        }
    }

    override val isPlaying: Boolean get() = player?.isPlaying == true
    override val currentPositionMs: Long get() = player?.currentPosition ?: 0L
    override val durationMs: Long get() = player?.duration?.coerceAtLeast(0L) ?: 0L
    override val playbackSpeed: Float get() = player?.playbackParameters?.speed ?: 1f

    override val audioTracks: List<PlayerEngine.TrackInfo>
        get() = buildTracks(C.TRACK_TYPE_AUDIO, PlayerEngine.TrackType.AUDIO)

    override val subtitleTracks: List<PlayerEngine.TrackInfo>
        get() = buildTracks(C.TRACK_TYPE_TEXT, PlayerEngine.TrackType.SUBTITLE)

    override fun selectAudioTrack(index: Int) {
        val selector = trackSelector ?: return
        val params = selector.buildUponParameters()
        if (index < 0) {
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        }
        selector.setParameters(params)
    }

    override fun selectSubtitleTrack(index: Int) {
        val selector = trackSelector ?: return
        val params = selector.buildUponParameters()
        if (index < 0) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        }
        selector.setParameters(params)
    }

    override fun createPlayerView(context: Context): View {
        val pv = PlayerView(context).apply {
            this.player = this@ExoPlayerEngine.player
            useController = false
        }
        playerView = pv
        return pv
    }

    override fun setOnStateChanged(callback: ((Boolean) -> Unit)?) {
        onStateChanged = callback
    }

    override fun setOnTracksChanged(callback: (() -> Unit)?) {
        onTracksChanged = callback
    }

    private fun buildTracks(trackType: Int, type: PlayerEngine.TrackType): List<PlayerEngine.TrackInfo> {
        val p = player ?: return emptyList()
        val tracks = p.currentTracks
        val result = mutableListOf<PlayerEngine.TrackInfo>()
        for (group in tracks.groups) {
            if (group.type != trackType) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result.add(
                    PlayerEngine.TrackInfo(
                        index = i,
                        label = buildTrackLabel(format),
                        language = format.language,
                        isSelected = group.isTrackSelected(i),
                        type = type,
                    )
                )
            }
        }
        return result
    }

    private fun buildTrackLabel(format: Format): String {
        val lang = format.language?.let {
            try { java.util.Locale(it).displayLanguage.ifBlank { it } }
            catch (_: Exception) { it }
        }
        val codec = format.sampleMimeType
        val channels = when (format.channelCount) {
            1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null
        }
        return listOfNotNull(lang, codec, channels).joinToString(" · ").ifBlank { "Unknown" }
    }
}
