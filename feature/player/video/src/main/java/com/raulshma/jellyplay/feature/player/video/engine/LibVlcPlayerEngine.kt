package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

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
    private var onError: ((String) -> Unit)? = null
    private var onPlaybackStateChanged: ((Int) -> Unit)? = null
    @Volatile private var _isPlaying = false
    @Volatile private var _speed = 1f
    @Volatile private var pendingPlay = false
    @Volatile private var _audioDelayMs = 0L
    @Volatile private var _subtitleDelayMs = 0L
    private var pendingSubtitles: List<Pair<String, String>>? = null // List of (url, label) pairs
    private var currentDecoderMode: DecoderMode = DecoderMode.HW_PREFERRED
    private var _passthroughEnabled = false
    private val dialogueBoost = DialogueBoostHelper()
    private var dialogueBoostEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _isPlaying = true
                mainHandler.post {
                    onStateChanged?.invoke(true)
                    onPlaybackStateChanged?.invoke(3) // READY=3
                    // Apply dialogue boost when playback starts
                    if (dialogueBoostEnabled) {
                        applyDialogueBoost()
                    }
                }
            }
            MediaPlayer.Event.Paused -> {
                _isPlaying = false
                mainHandler.post {
                    onStateChanged?.invoke(false)
                    onPlaybackStateChanged?.invoke(3) // READY=3
                }
            }
            MediaPlayer.Event.Stopped -> {
                _isPlaying = false
                mainHandler.post {
                    onStateChanged?.invoke(false)
                    onPlaybackStateChanged?.invoke(1) // IDLE=1
                }
            }
            MediaPlayer.Event.Buffering -> {
                mainHandler.post { onPlaybackStateChanged?.invoke(2) } // BUFFERING=2
            }
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected -> {
                mainHandler.post { onTracksChanged?.invoke() }
            }
            MediaPlayer.Event.EncounteredError -> {
                mainHandler.post { onError?.invoke("VLC encountered an error during playback") }
            }
        }
    }

    override fun initialize(url: String, title: String, startPositionMs: Long) {
        release()

        val options = arrayListOf(
            "--aout=aaudio",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter", "1",
            "--avcodec-skip-frame", "0",
            "--avcodec-skip-idct", "0",
            "--network-caching=3000",
        )

        if (_audioDelayMs != 0L) {
            options.add("--audio-desync=${_audioDelayMs.toInt()}")
        }

        if (_passthroughEnabled) {
            options.add("--aout=android_audiotrack")
            options.add("--codec=ac3,eac3,dts,dtshd,truehd")
        }

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
        val hwDecoding = currentDecoderMode != DecoderMode.SW_ONLY
        media.setHWDecoderEnabled(hwDecoding, false)

        if (startPositionMs > 0) {
            media.addOption(":start-time=${startPositionMs / 1000.0}")
        }

        mp.media = media
        media.release()
        
        // Add external subtitles after media is set
        // LibVLC addSlave: type (0=subtitle, 1=audio), priority, uri
        pendingSubtitles?.forEach { (subUrl, _) ->
            try {
                mp.addSlave(0, subUrl, false) // 0 = subtitle type
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add subtitle: $subUrl", e)
            }
        }

        pendingPlay = true
        pendingSubtitles = null
    }
    
    /**
     * Configure external subtitles to be loaded with the media.
     * Must be called before initialize().
     */
    fun configureExternalSubtitles(subtitles: List<Pair<String, String>>) {
        pendingSubtitles = subtitles
    }

    override fun release() {
        pendingPlay = false
        pendingSubtitles = null
        dialogueBoost.detach()
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

    override fun setAudioDelay(ms: Long) {
        _audioDelayMs = ms
        try {
            val mp = mediaPlayer ?: return
            mp.setAudioDelay(ms)
        } catch (_: Exception) {}
    }

    override fun setSubtitleDelay(ms: Long) {
        _subtitleDelayMs = ms
        try {
            val mp = mediaPlayer ?: return
            mp.setSpuDelay(ms * 1000L) // libvlc expects microseconds
        } catch (_: Exception) {}
    }

    override fun setDecoderMode(mode: DecoderMode) {
        currentDecoderMode = mode
    }

    override fun setAudioPassthrough(enabled: Boolean) {
        _passthroughEnabled = enabled
    }

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        try {
            val mp = mediaPlayer ?: return
            when {
                mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
                mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
                ratio != null && ratio > 0f -> {
                    mp.aspectRatio = ratio.toString()
                }
                else -> {
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
            }
        } catch (_: Exception) {}
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    override val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
    override val audioSessionId: Int
        get() = try {
            // LibVLC doesn't expose audio session ID directly, but we can use a workaround
            // by getting the audio track ID which can be used for audio effects
            mediaPlayer?.audioTrack ?: 0
        } catch (_: Exception) { 0 }
    override val supportsAudioDelay: Boolean get() = true
    override val supportsSubtitleDelay: Boolean get() = true
    override val supportsAudioPassthrough: Boolean get() = true
    override val supportsSubtitleStyle: Boolean get() = false
    override val supportsDialogueBoost: Boolean get() = true
    override val supportsNightMode: Boolean get() = false
    override val supportsOcr: Boolean get() = false
    override val supportsCues: Boolean get() = false

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

    override fun setOnError(callback: ((String) -> Unit)?) {
        onError = callback
    }

    override fun setOnPlaybackStateChanged(callback: ((Int) -> Unit)?) {
        onPlaybackStateChanged = callback
    }

    override fun setDialogueBoostEnabled(enabled: Boolean) {
        dialogueBoostEnabled = enabled
        if (isPlaying) {
            applyDialogueBoost()
        }
    }

    private fun applyDialogueBoost() {
        val sid = audioSessionId
        if (sid != 0) {
            dialogueBoost.attach(sid)
            dialogueBoost.setEnabled(dialogueBoostEnabled)
        }
    }
}
