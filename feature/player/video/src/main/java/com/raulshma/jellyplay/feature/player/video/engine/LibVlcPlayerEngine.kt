package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * [PlayerEngine] backed by LibVLC — the VLC media engine with maximum format compatibility.
 *
 * Key lifecycle constraints:
 *  - [LibVLC] must be created with the Application context
 *  - [MediaPlayer.attachViews] must be called AFTER the [VLCVideoLayout] is in the view hierarchy
 *  - [MediaPlayer.play] is deferred until views are attached
 *  - All event callbacks are posted to the main thread for Compose safety
 */
class LibVlcPlayerEngine(
    private val context: Context,
) : PlayerEngine {

    companion object {
        private const val TAG = "LibVlcPlayerEngine"
    }

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var onStateChanged: ((Boolean) -> Unit)? = null
    private var onTracksChanged: (() -> Unit)? = null
    @Volatile private var _isPlaying = false
    @Volatile private var _speed = 1f

    /** Play is deferred until VLCVideoLayout is attached to the window. */
    @Volatile private var pendingPlay = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // VLC events fire on VLC's native thread — post to main for Compose safety.
    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _isPlaying = true
                mainHandler.post { onStateChanged?.invoke(true) }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                _isPlaying = false
                mainHandler.post { onStateChanged?.invoke(false) }
            }
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected -> {
                mainHandler.post { onTracksChanged?.invoke() }
            }
        }
    }

    override fun initialize(url: String, title: String, startPositionMs: Long) {
        release()

        val options = arrayListOf(
            "--aout=opensles",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter", "1",
            "--avcodec-skip-frame", "0",
            "--avcodec-skip-idct", "0",
            "--network-caching=1500",
        )

        val vlc = try {
            LibVLC(context.applicationContext, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LibVLC", e)
            return
        }
        libVLC = vlc

        val mp = MediaPlayer(vlc)
        mp.setEventListener(eventListener)
        mediaPlayer = mp

        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)

        if (startPositionMs > 0) {
            media.addOption(":start-time=${startPositionMs / 1000.0}")
        }

        mp.media = media
        media.release() // MediaPlayer retains its own reference

        // Don't play yet — wait until VLCVideoLayout is attached to the window hierarchy
        pendingPlay = true
    }

    override fun release() {
        pendingPlay = false
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        mp.setEventListener(null)
        try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
        try { mp.detachViews() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
        try { libVLC?.release() } catch (_: Exception) {}
        libVLC = null
        videoLayout = null
    }

    override fun play() {
        try { mediaPlayer?.play() } catch (_: Exception) {}
    }

    override fun pause() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try { mediaPlayer?.time = positionMs } catch (_: Exception) {}
    }

    override fun seekForward(amountMs: Long) {
        try {
            val mp = mediaPlayer ?: return
            mp.time = (mp.time + amountMs).coerceAtMost(mp.length.coerceAtLeast(0))
        } catch (_: Exception) {}
    }

    override fun seekBack(amountMs: Long) {
        try {
            val mp = mediaPlayer ?: return
            mp.time = (mp.time - amountMs).coerceAtLeast(0)
        } catch (_: Exception) {}
    }

    override fun setPlaybackSpeed(speed: Float) {
        _speed = speed
        try { mediaPlayer?.rate = speed } catch (_: Exception) {}
    }

    override val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }

    override val currentPositionMs: Long
        get() = try { mediaPlayer?.time ?: 0L } catch (_: Exception) { 0L }

    override val durationMs: Long
        get() = try { (mediaPlayer?.length ?: 0L).coerceAtLeast(0L) } catch (_: Exception) { 0L }

    override val playbackSpeed: Float get() = _speed

    override val audioTracks: List<PlayerEngine.TrackInfo>
        get() {
            val mp = mediaPlayer ?: return emptyList()
            return try {
                val tracks = mp.getAudioTracks() ?: return emptyList()
                val currentId = try { mp.audioTrack } catch (_: Exception) { -1 }
                tracks.mapIndexed { index, desc ->
                    PlayerEngine.TrackInfo(
                        index = index,
                        label = desc.name ?: "Audio ${index + 1}",
                        language = null,
                        isSelected = desc.id == currentId,
                        type = PlayerEngine.TrackType.AUDIO,
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    override val subtitleTracks: List<PlayerEngine.TrackInfo>
        get() {
            val mp = mediaPlayer ?: return emptyList()
            return try {
                val tracks = mp.getSpuTracks() ?: return emptyList()
                val currentId = try { mp.spuTrack } catch (_: Exception) { -1 }
                tracks.mapIndexed { index, desc ->
                    PlayerEngine.TrackInfo(
                        index = index,
                        label = desc.name ?: "Subtitle ${index + 1}",
                        language = null,
                        isSelected = desc.id == currentId,
                        type = PlayerEngine.TrackType.SUBTITLE,
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    override fun selectAudioTrack(index: Int) {
        try {
            val mp = mediaPlayer ?: return
            val tracks = mp.getAudioTracks() ?: return
            if (index in tracks.indices) mp.audioTrack = tracks[index].id
        } catch (_: Exception) {}
    }

    override fun selectSubtitleTrack(index: Int) {
        try {
            val mp = mediaPlayer ?: return
            if (index < 0) { mp.spuTrack = -1; return }
            val tracks = mp.getSpuTracks() ?: return
            if (index in tracks.indices) mp.spuTrack = tracks[index].id
        } catch (_: Exception) {}
    }

    override fun createPlayerView(context: Context): View {
        val layout = VLCVideoLayout(context)
        videoLayout = layout

        // Defer attachViews + play until the layout is actually in the window hierarchy.
        // Calling attachViews before that crashes because VLC needs a valid surface.
        layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val mp = mediaPlayer ?: return
                try {
                    mp.attachViews(layout, null, false, false)
                    if (pendingPlay) {
                        pendingPlay = false
                        mp.play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "attachViews/play failed", e)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                try { mediaPlayer?.detachViews() } catch (_: Exception) {}
            }
        })

        return layout
    }

    override fun setOnStateChanged(callback: ((Boolean) -> Unit)?) {
        onStateChanged = callback
    }

    override fun setOnTracksChanged(callback: (() -> Unit)?) {
        onTracksChanged = callback
    }
}
