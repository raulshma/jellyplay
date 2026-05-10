package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper

class MpvPlayerEngine(
    private val context: Context,
) : PlayerEngine {

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    private var mpvView: PlayerMPVView? = null
    private var onStateChanged: ((Boolean) -> Unit)? = null
    private var onTracksChanged: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onPlaybackStateChanged: ((Int) -> Unit)? = null
    @Volatile private var _isPlaying = false
    @Volatile private var _speed = 1f
    @Volatile private var _audioDelayMs = 0L
    @Volatile private var _subtitleDelayMs = 0L
    private var pendingUrl: String? = null
    private var pendingSubtitles: List<Pair<String, String>>? = null // List of (url, label) pairs
    private var currentDecoderMode: DecoderMode = DecoderMode.HW_PREFERRED
    private var _passthroughEnabled = false
    private val dialogueBoost = DialogueBoostHelper()
    private var dialogueBoostEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private inner class PlayerMPVView(
        ctx: Context,
    ) : BaseMPVView(ctx, null) {

        private val observer = object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}
            override fun eventProperty(property: String, value: Double) {
                if (property == "speed") _speed = value.toFloat()
            }
            override fun eventProperty(property: String, value: Boolean) {
                if (property == "pause") {
                    _isPlaying = !value
                    mainHandler.post { onStateChanged?.invoke(_isPlaying) }
                }
                if (property == "paused-for-cache") {
                    val state = if (value) 2 else 3 // BUFFERING=2, READY=3
                    mainHandler.post { onPlaybackStateChanged?.invoke(state) }
                }
            }
            override fun eventProperty(property: String, value: String) {}
            override fun eventProperty(property: String, value: MPVNode) {}
            override fun event(eventId: Int, node: MPVNode) {
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        mainHandler.post { onPlaybackStateChanged?.invoke(1) } // IDLE=1
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        // Add external subtitles after file is loaded
                        pendingSubtitles?.forEach { (subUrl, label) ->
                            try {
                                Log.d(TAG, "Adding subtitle after file loaded: $subUrl")
                                mpv.command("sub-add", subUrl, "select", label)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to add subtitle after file loaded: $subUrl", e)
                            }
                        }
                        pendingSubtitles = null
                        mainHandler.post {
                            onPlaybackStateChanged?.invoke(3) // READY=3
                            onTracksChanged?.invoke()
                        }
                    }
                }
            }
        }

        override fun initOptions() {
            mpv.setOptionString("hwdec", when (currentDecoderMode) {
                DecoderMode.HW_PREFERRED, DecoderMode.HW_ONLY -> "auto"
                DecoderMode.SW_ONLY -> "no"
            })
            mpv.setOptionString("ao", "audiotrack,aaudio")
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("keep-open", "yes")
        }

        override fun postInitOptions() {
            mpv.addObserver(observer)
            mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
        }

        override fun observeProperties() {}

        fun removeObserver() {
            try { mpv.removeObserver(observer) } catch (_: Exception) {}
        }
    }

    override fun initialize(url: String, title: String, startPositionMs: Long) {
        pendingUrl = url

        mpvView?.let { view ->
            try {
                if (startPositionMs > 0) {
                    view.mpv.setOptionString("start", "+${startPositionMs / 1000.0}")
                }
                view.playFile(url)
                // Note: External subtitles will be added in MPV_EVENT_FILE_LOADED event
                pendingUrl = null
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
            }
        }
    }
    
    /**
     * Configure external subtitles to be loaded with the media.
     * Must be called before initialize() or after createPlayerView().
     */
    fun configureExternalSubtitles(subtitles: List<Pair<String, String>>) {
        pendingSubtitles = subtitles
        Log.d(TAG, "Configured ${subtitles.size} external subtitles")
    }

    override fun release() {
        pendingUrl = null
        pendingSubtitles = null
        dialogueBoost.detach()
        mpvView?.let { view ->
            view.removeObserver()
            try { view.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
        }
        mpvView = null
    }

    override fun play() {
        try { mpvView?.mpv?.setPropertyBoolean("pause", false) } catch (_: Exception) {}
    }

    override fun pause() {
        try { mpvView?.mpv?.setPropertyBoolean("pause", true) } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try { mpvView?.mpv?.command("seek", "${positionMs / 1000.0}", "absolute") } catch (_: Exception) {}
    }

    override fun seekForward(amountMs: Long) {
        try { mpvView?.mpv?.command("seek", "${amountMs / 1000.0}", "relative") } catch (_: Exception) {}
    }

    override fun seekBack(amountMs: Long) {
        try { mpvView?.mpv?.command("seek", "-${amountMs / 1000.0}", "relative") } catch (_: Exception) {}
    }

    override fun setPlaybackSpeed(speed: Float) {
        _speed = speed
        try { mpvView?.mpv?.setPropertyDouble("speed", speed.toDouble()) } catch (_: Exception) {}
    }

    override fun setAudioDelay(ms: Long) {
        _audioDelayMs = ms
        try { mpvView?.mpv?.setPropertyDouble("audio-delay", ms / 1000.0) } catch (_: Exception) {}
    }

    override fun setSubtitleDelay(ms: Long) {
        _subtitleDelayMs = ms
        try { mpvView?.mpv?.setPropertyDouble("sub-delay", ms / 1000.0) } catch (_: Exception) {}
    }

    override fun setDecoderMode(mode: DecoderMode) {
        currentDecoderMode = mode
        try {
            mpvView?.mpv?.setPropertyString("hwdec", when (mode) {
                DecoderMode.HW_PREFERRED, DecoderMode.HW_ONLY -> "auto"
                DecoderMode.SW_ONLY -> "no"
            })
        } catch (_: Exception) {}
    }

    override fun setAudioPassthrough(enabled: Boolean) {
        _passthroughEnabled = enabled
        try {
            if (enabled) {
                mpvView?.mpv?.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            } else {
                mpvView?.mpv?.setOptionString("audio-spdif", "")
            }
        } catch (_: Exception) {}
    }

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        val aspectValue = when {
            mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "-1"
            mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "fit"
            ratio != null && ratio > 0f -> {
                val w = (ratio * 100).toInt()
                val h = 100
                val gcd = gcd(w, h)
                "${w / gcd}:${h / gcd}"
            }
            else -> "fit"
        }
        try { mpvView?.mpv?.setPropertyString("video-aspect-override", aspectValue) } catch (_: Exception) {}
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

    override val isPlaying: Boolean get() = _isPlaying
    override val audioSessionId: Int
        get() = try {
            mpvView?.mpv?.getPropertyInt("audio-device-id") ?: 0
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
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
        } catch (_: Exception) { 0L }

    override val durationMs: Long
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("duration") ?: 0.0) * 1000).toLong().coerceAtLeast(0)
        } catch (_: Exception) { 0L }

    override val playbackSpeed: Float get() = _speed

    override val audioTracks: List<PlayerEngine.TrackInfo>
        get() = try { getTracksOfType("audio", PlayerEngine.TrackType.AUDIO) } catch (_: Exception) { emptyList() }

    override val subtitleTracks: List<PlayerEngine.TrackInfo>
        get() = try { getTracksOfType("sub", PlayerEngine.TrackType.SUBTITLE) } catch (_: Exception) { emptyList() }

    override fun selectAudioTrack(index: Int) {
        try {
            val m = mpvView?.mpv ?: return
            if (index < 0) m.setPropertyString("aid", "auto")
            else m.setPropertyString("aid", "${index + 1}")
        } catch (_: Exception) {}
    }

    override fun selectSubtitleTrack(index: Int) {
        try {
            val m = mpvView?.mpv ?: return
            if (index < 0) m.setPropertyString("sid", "no")
            else m.setPropertyString("sid", "${index + 1}")
        } catch (_: Exception) {}
    }

    override fun createPlayerView(context: Context): View {
        val view = try {
            PlayerMPVView(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PlayerMPVView", e)
            return View(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        }
        mpvView = view

        try {
            view.initialize("gpu", "android")
        } catch (e: Exception) {
            Log.e(TAG, "MPV initialize failed", e)
            return view
        }

        pendingUrl?.let { url ->
            pendingUrl = null
            try {
                view.playFile(url)
                // Note: External subtitles will be added in MPV_EVENT_FILE_LOADED event
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
            }
        }

        return view
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
        val sid = audioSessionId
        if (sid != 0) {
            dialogueBoost.attach(sid)
            dialogueBoost.setEnabled(enabled)
        }
    }

    private fun getTracksOfType(type: String, trackType: PlayerEngine.TrackType): List<PlayerEngine.TrackInfo> {
        val m = mpvView?.mpv ?: return emptyList()
        val count = m.getPropertyInt("track-list/count") ?: 0
        val result = mutableListOf<PlayerEngine.TrackInfo>()
        for (i in 0 until count) {
            val t = m.getPropertyString("track-list/$i/type") ?: continue
            if (t != type) continue
            val id = m.getPropertyInt("track-list/$i/id") ?: continue
            val lang = m.getPropertyString("track-list/$i/lang")
            val title = m.getPropertyString("track-list/$i/title")
            val codec = m.getPropertyString("track-list/$i/codec")
            val selected = m.getPropertyBoolean("track-list/$i/selected") ?: false
            val label = listOfNotNull(
                lang?.let { l -> try { java.util.Locale(l).displayLanguage } catch (_: Exception) { l } },
                title,
                codec,
            ).joinToString(" · ").ifBlank { "Track $id" }
            result.add(
                PlayerEngine.TrackInfo(
                    index = id - 1,
                    label = label,
                    language = lang,
                    isSelected = selected,
                    type = trackType,
                )
            )
        }
        return result
    }
}
