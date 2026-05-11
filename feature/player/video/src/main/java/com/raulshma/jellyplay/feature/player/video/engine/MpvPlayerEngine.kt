package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MpvPlayerEngine(
    private val context: Context,
) : MediaEngine {

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    private var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val capabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = false, // Mini-mode reparenting breaks SurfaceView
        supportsOcr = false,
        supportsCues = false,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true, // Basic MPV subtitle properties
        supportsDialogueBoost = true,
        supportsNightMode = false,
    )

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentCues = MutableStateFlow<List<String>>(emptyList())
    override val currentCues: StateFlow<List<String>> = _currentCues.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    private var mpvView: PlayerMPVView? = null
    private var pendingUrl: String? = null
    private var pendingStartPositionMs: Long = 0L
    private var pendingSubtitles: List<SubtitleSource> = emptyList()
    
    private var currentConfig = EngineConfig()
    private val dialogueBoost = DialogueBoostHelper()

    private var wasPlayingBeforeActivityPause = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityPause() {
        wasPlayingBeforeActivityPause = _isPlaying.value
        pause()
    }

    override fun onActivityResume() {
        if (wasPlayingBeforeActivityPause) {
            wasPlayingBeforeActivityPause = false
            play()
        }
    }

    private inner class PlayerMPVView(
        ctx: Context,
    ) : BaseMPVView(ctx, null) {

        private val observer = object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}
            override fun eventProperty(property: String, value: Double) {}
            override fun eventProperty(property: String, value: Boolean) {
                if (property == "pause") {
                    _isPlaying.value = !value
                }
                if (property == "paused-for-cache") {
                    _playbackState.value = if (value) EnginePlaybackState.BUFFERING else EnginePlaybackState.READY
                }
            }
            override fun eventProperty(property: String, value: String) {}
            override fun eventProperty(property: String, value: MPVNode) {}
            override fun event(eventId: Int, node: MPVNode) {
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        _playbackState.value = EnginePlaybackState.IDLE
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        pendingSubtitles.forEach { sub ->
                            try {
                                val cleanUrl = if (sub.url.contains("api_key=")) sub.url.substringBefore("?") else sub.url
                                mpv.command("sub-add", cleanUrl, "auto", sub.label)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to add subtitle: ${sub.url}", e)
                            }
                        }
                        pendingSubtitles = emptyList()
                        _playbackState.value = EnginePlaybackState.READY
                        _availableTracks.value = buildTracks()
                    }
                    MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                        _playbackState.value = EnginePlaybackState.ENDED
                    }
                }
            }
        }

        override fun initOptions() {
            mpv.setOptionString("hwdec", when (currentConfig.decoderMode) {
                DecoderMode.HW_PREFERRED, DecoderMode.HW_ONLY -> "auto"
                DecoderMode.SW_ONLY -> "no"
            })
            mpv.setOptionString("ao", "audiotrack,aaudio")
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("keep-open", "yes")
            if (currentConfig.audioPassthrough) {
                mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            }
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

    override fun load(request: PlaybackRequest) {
        pendingUrl = request.uri
        pendingStartPositionMs = request.startPositionMs
        pendingSubtitles = request.externalSubtitles

        mpvView?.let { view ->
            try {
                if (request.startPositionMs > 0) {
                    view.mpv.setOptionString("start", "+${request.startPositionMs / 1000.0}")
                }
                
                // HTTP Headers for auth (if needed for MPV)
                if (request.headers.isNotEmpty()) {
                    val headerStr = request.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                    view.mpv.setOptionString("http-header-fields", headerStr)
                }

                view.playFile(request.uri)
                pendingUrl = null
                pendingStartPositionMs = 0L
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
                _errorFlow.tryEmit(e.message ?: "Failed to start MPV playback")
            }
        }
    }

    override fun release() {
        pendingUrl = null
        pendingStartPositionMs = 0L
        pendingSubtitles = emptyList()
        dialogueBoost.detach()
        mpvView?.let { view ->
            view.removeObserver()
            try { view.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
        }
        mpvView = null
        engineScope.cancel()
    }

    override fun play() {
        try {
            if (_playbackState.value == EnginePlaybackState.ENDED) {
                mpvView?.mpv?.command("seek", "0", "absolute")
            }
            mpvView?.mpv?.setPropertyBoolean("pause", false)
        } catch (_: Exception) {}
    }

    override fun pause() {
        try { mpvView?.mpv?.setPropertyBoolean("pause", true) } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try { mpvView?.mpv?.command("seek", "${positionMs / 1000.0}", "absolute") } catch (_: Exception) {}
    }

    override fun setPlaybackSpeed(speed: Float) {
        try { mpvView?.mpv?.setPropertyDouble("speed", speed.toDouble()) } catch (_: Exception) {}
    }

    override fun updateConfig(config: EngineConfig) {
        currentConfig = config
        
        try {
            mpvView?.mpv?.setPropertyDouble("audio-delay", config.audioDelayMs / 1000.0)
            mpvView?.mpv?.setPropertyDouble("sub-delay", config.subtitleDelayMs / 1000.0)
            
            mpvView?.mpv?.setPropertyString("hwdec", when (config.decoderMode) {
                DecoderMode.HW_PREFERRED, DecoderMode.HW_ONLY -> "auto"
                DecoderMode.SW_ONLY -> "no"
            })
            
            if (config.audioPassthrough) {
                mpvView?.mpv?.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            } else {
                mpvView?.mpv?.setOptionString("audio-spdif", "")
            }

            applySubtitleStyleInternal(config.subtitleStyle)
            
            val sid = audioSessionId
            if (sid != 0) {
                dialogueBoost.attach(sid)
                dialogueBoost.setEnabled(config.audioEffects.dialogueBoostEnabled)
            }
        } catch (_: Exception) {}
    }

    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?) {
        try {
            val m = mpvView?.mpv ?: return
            if (type == TrackType.AUDIO) {
                if (index < 0) m.setPropertyString("aid", "auto")
                else m.setPropertyString("aid", "${index + 1}") // MPV 1-indexed?
            } else {
                if (index < 0) m.setPropertyString("sid", "no")
                else m.setPropertyString("sid", "${index + 1}")
            }
        } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
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
            applySubtitleStyleInternal(currentConfig.subtitleStyle)
        } catch (e: Exception) {
            Log.e(TAG, "MPV initialize failed", e)
            return view
        }

        pendingUrl?.let { url ->
            pendingUrl = null
            val startPos = pendingStartPositionMs
            pendingStartPositionMs = 0L
            try {
                if (startPos > 0) {
                    view.mpv.setOptionString("start", "+${startPos / 1000.0}")
                }
                view.playFile(url)
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
            }
        }

        return view
    }

    override fun applySubtitleStyleToView(view: View, style: SubtitleStyle) {
        // Not used via view in MPV, we apply via mpv properties
        applySubtitleStyleInternal(style)
    }
    
    private fun applySubtitleStyleInternal(style: SubtitleStyle) {
        try {
            val m = mpvView?.mpv ?: return
            val colorHex = String.format("#%06X", (0xFFFFFF and style.fontColor.value.toInt()))
            val bgHex = String.format("#%06X", (0xFFFFFF and style.backgroundColor.value.toInt()))
            val alphaHex = String.format("%02X", (style.backgroundOpacity * 255).toInt())
            
            m.setPropertyString("sub-color", colorHex)
            m.setPropertyString("sub-back-color", "#${alphaHex}${bgHex.substring(1)}")
            m.setPropertyDouble("sub-scale", (style.fontSize / 16.0).coerceIn(0.5, 3.0))
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

    override fun captureViewBitmap(): Bitmap? = null

    override val currentPositionMs: Long
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
        } catch (_: Exception) { 0L }

    override val durationMs: Long
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("duration") ?: 0.0) * 1000).toLong().coerceAtLeast(0)
        } catch (_: Exception) { 0L }

    override val playbackSpeed: Float
        get() = try {
            mpvView?.mpv?.getPropertyDouble("speed")?.toFloat() ?: 1f
        } catch (_: Exception) { 1f }

    override val audioSessionId: Int
        get() = try {
            mpvView?.mpv?.getPropertyInt("audio-device-id") ?: 0
        } catch (_: Exception) { 0 }

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = engineScope.launch {
            while (isActive) {
                delay(250)
                trySend(currentPositionMs)
            }
        }
        awaitClose { ticker.cancel() }
    }

    private fun buildTracks(): List<MediaTrack> {
        val m = mpvView?.mpv ?: return emptyList()
        val count = try { m.getPropertyInt("track-list/count") ?: 0 } catch (_: Exception) { 0 }
        val result = mutableListOf<MediaTrack>()
        for (i in 0 until count) {
            val t = m.getPropertyString("track-list/$i/type") ?: continue
            val trackType = when (t) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> continue
            }
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
                MediaTrack(
                    id = "mpv_${t}_${id}",
                    index = id - 1, // MPV indices might be 1-based but UI selects 0-based index? Wait, the UI selector maps `index` directly.
                    label = label,
                    language = lang,
                    isSelected = selected,
                    type = trackType,
                )
            )
        }
        return result
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
}
