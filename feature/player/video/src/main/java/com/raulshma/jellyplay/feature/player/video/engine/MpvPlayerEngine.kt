package com.raulshma.jellyplay.feature.player.video.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.raulshma.jellyplay.core.data.playback.AudioNormalizationHelper
import com.raulshma.jellyplay.core.data.playback.ChannelMixHelper
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
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
        private const val LOW_RAM_THRESHOLD_MB = 2048L
        private const val DEMUXER_MAX_BYTES_LOW = 32 * 1024 * 1024L
        private const val DEMUXER_MAX_BYTES_NORMAL = 64 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_LOW = 16 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_NORMAL = 32 * 1024 * 1024L
        private val MPV_SUBTITLE_LOG_PATTERN =
            Regex("(?i)(sub|subtitle|libass|webvtt|vtt|srt|ssa|ass|ffmpeg|http|stream)")
    }

    private val isLowRamDevice by lazy { detectLowRamDevice() }
    private var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val capabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = false,
        supportsOcr = false,
        supportsCues = true,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true,
        supportsDialogueBoost = true,
        supportsNightMode = true,
        supportsAudioNormalization = true,
        supportsChannelMixing = true,
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

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private var mpvView: PlayerMPVView? = null
    private var pendingRequest: PlaybackRequest? = null
    private var pendingSubtitles: List<SubtitleSource> = emptyList()
    private var pendingPreferredSubtitleLanguage: String? = null
    private var lastLoggedSubtitleText: String? = null
    
    private var currentConfig = EngineConfig()
    private val dialogueBoost = DialogueBoostHelper()
    private val nightMode = NightModeHelper()
    private val audioNormalization = AudioNormalizationHelper()
    private val channelMix = ChannelMixHelper()

    private var wasPlayingBeforeActivityPause = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun detectLowRamDevice(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.isLowRamDevice || am.memoryClass <= 256
        } else {
            am.memoryClass <= 256
        }
    }

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
                if (property == "sub-visibility") {
                    Log.d(TAG, "MPV subtitle visibility changed to $value")
                }
            }
            override fun eventProperty(property: String, value: String) {
                if (property == "sid" || property == "aid") {
                    Log.d(TAG, "MPV $property changed to ${redactSensitive(value)}")
                    refreshTracks("property:$property")
                }
                if (property == "sub-text" && value != lastLoggedSubtitleText) {
                    lastLoggedSubtitleText = value
                    _currentCues.value = value.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                    if (value.isNotBlank()) {
                        Log.v(TAG, "MPV active subtitle text: ${value.take(120)}")
                    }
                }
            }
            override fun eventProperty(property: String, value: MPVNode) {
                if (property == "track-list") {
                    refreshTracks("property:track-list")
                }
            }
            override fun event(eventId: Int, data: MPVNode) {
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        Log.d(TAG, "MPV start file")
                        _playbackState.value = EnginePlaybackState.BUFFERING
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "MPV file loaded; adding ${pendingSubtitles.size} Jellyfin subtitle source(s)")
                        addPendingSubtitles(mpv)
                        _playbackState.value = EnginePlaybackState.READY
                        refreshTracks("file-loaded")
                        refreshTracks("file-loaded-delayed", delayMs = 750)
                    }
                    MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                        Log.d(TAG, "MPV end file")
                        _playbackState.value = EnginePlaybackState.ENDED
                    }
                }
            }
        }

        private val logObserver = object : MPV.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) {
                logMpvMessage(prefix, level, text)
            }
        }

        override fun initOptions() {
            mpv.setOptionString("hwdec", when (currentConfig.decoderMode) {
                DecoderMode.HW_PREFERRED -> "mediacodec,mediacodec-copy,no"
                DecoderMode.HW_ONLY -> "mediacodec,mediacodec-copy"
                DecoderMode.SW_ONLY -> "no"
            })
            mpv.setOptionString("hwdec-codecs", "all")

            mpv.setOptionString("ao", "audiotrack,aaudio")
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("sub-visibility", "yes")
            mpv.setOptionString("secondary-sub-visibility", "yes")
            mpv.setOptionString("blend-subtitles", "video")
            mpv.setOptionString("sub-ass", "yes")
            mpv.setOptionString("sub-ass-override", "force")
            mpv.setOptionString("keep-open", "yes")
            applySubtitleStyleOptions(mpv, currentConfig.subtitleStyle)

            if (isLowRamDevice) {
                mpv.setOptionString("demuxer-max-bytes", DEMUXER_MAX_BYTES_LOW.toString())
                mpv.setOptionString("demuxer-max-back-bytes", DEMUXER_MAX_BACK_BYTES_LOW.toString())
                mpv.setOptionString("vd-lavc-skiploopfilter", "bidir")
                mpv.setOptionString("vd-lavc-skipframe", "nonref")
                mpv.setOptionString("opengl-swapinterval", "1")
            } else {
                mpv.setOptionString("demuxer-max-bytes", DEMUXER_MAX_BYTES_NORMAL.toString())
                mpv.setOptionString("demuxer-max-back-bytes", DEMUXER_MAX_BACK_BYTES_NORMAL.toString())
            }

            mpv.setOptionString("msg-level", "all=warn")

            if (currentConfig.decoderMode == DecoderMode.SW_ONLY) {
                mpv.setOptionString("profile", "fast")
                if (isLowRamDevice) {
                    mpv.setOptionString("vf", "format=yuv420p")
                }
            }

            if (currentConfig.audioPassthrough) {
                mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            }

            when (currentConfig.audioEffects.channelMixMode) {
                ChannelMixMode.STEREO_DOWNMIX -> mpv.setOptionString("audio-channels", "stereo")
                ChannelMixMode.MONO -> mpv.setOptionString("audio-channels", "mono")
                ChannelMixMode.SURROUND_UPMIX -> mpv.setOptionString("audio-channels", "5.1")
                ChannelMixMode.AUTO -> mpv.setOptionString("audio-channels", "auto")
            }

            if (currentConfig.audioEffects.audioNormalizationEnabled) {
                val afFilters = mutableListOf<String>()
                when (currentConfig.audioEffects.audioNormalizationMode) {
                    AudioNormalizationMode.DYNAMIC -> {
                        afFilters.add("acompressor=ratio=3:threshold=0.05:attack=10:release=200")
                    }
                    AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> {
                        afFilters.add("loudnorm=I=-23:LRA=7:tp=-1")
                    }
                    AudioNormalizationMode.NONE -> {}
                }
                if (afFilters.isNotEmpty()) {
                    mpv.setOptionString("af", afFilters.joinToString(","))
                }
            }
        }

        override fun postInitOptions() {
            mpv.addObserver(observer)
            mpv.addLogObserver(logObserver)
            mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NODE)
            mpv.observeProperty("sub-text", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("sub-visibility", MPV.mpvFormat.MPV_FORMAT_FLAG)
        }

        override fun observeProperties() {}

        fun removeObserver() {
            try { mpv.removeObserver(observer) } catch (_: Exception) {}
            try { mpv.removeLogObserver(logObserver) } catch (_: Exception) {}
        }
    }

    override fun load(request: PlaybackRequest) {
        pendingRequest = request
        pendingSubtitles = request.externalSubtitles
        pendingPreferredSubtitleLanguage = request.preferredSubtitleLanguage
        Log.d(
            TAG,
            "MPV load requested: uri=${redactSensitive(request.uri)}, start=${request.startPositionMs}ms, " +
                "externalSubtitles=${request.externalSubtitles.size}, headers=${request.headers.keys}"
        )

        mpvView?.let { view ->
            try {
                configureMpvForRequest(view, request)
                view.playFile(request.uri)
                pendingRequest = null
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
                _errorFlow.tryEmit(e.message ?: "Failed to start MPV playback")
            }
        }
    }

    override fun release() {
        pendingRequest = null
        pendingSubtitles = emptyList()
        pendingPreferredSubtitleLanguage = null
        lastLoggedSubtitleText = null
        _currentCues.value = emptyList()
        mainHandler.removeCallbacksAndMessages(null)
        dialogueBoost.detach()
        nightMode.detach()
        audioNormalization.detach()
        channelMix.detach()
        mpvView?.let { view ->
            view.removeObserver()
            try { view.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
        }
        mpvView = null
        engineScope.cancel()
    }

    override fun play() {
        _isPlaying.value = true
        try {
            if (_playbackState.value == EnginePlaybackState.ENDED) {
                mpvView?.mpv?.command("seek", "0", "absolute")
            }
            mpvView?.mpv?.setPropertyBoolean("pause", false)
        } catch (_: Exception) {}
    }

    override fun pause() {
        _isPlaying.value = false
        try { mpvView?.mpv?.setPropertyBoolean("pause", true) } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try { mpvView?.mpv?.command("seek", "%.6f".format(positionMs / 1000.0), "absolute") } catch (_: Exception) {}
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
                DecoderMode.HW_PREFERRED -> "mediacodec,mediacodec-copy,no"
                DecoderMode.HW_ONLY -> "mediacodec,mediacodec-copy"
                DecoderMode.SW_ONLY -> "no"
            })
            
            if (config.audioPassthrough) {
                mpvView?.mpv?.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            } else {
                mpvView?.mpv?.setOptionString("audio-spdif", "")
            }

            applySubtitleStyleInternal(config.subtitleStyle)

            when (config.audioEffects.channelMixMode) {
                ChannelMixMode.STEREO_DOWNMIX -> mpvView?.mpv?.setPropertyString("audio-channels", "stereo")
                ChannelMixMode.MONO -> mpvView?.mpv?.setPropertyString("audio-channels", "mono")
                ChannelMixMode.SURROUND_UPMIX -> mpvView?.mpv?.setPropertyString("audio-channels", "5.1")
                ChannelMixMode.AUTO -> mpvView?.mpv?.setPropertyString("audio-channels", "auto")
            }

            if (config.audioEffects.audioNormalizationEnabled) {
                val afFilters = mutableListOf<String>()
                when (config.audioEffects.audioNormalizationMode) {
                    AudioNormalizationMode.DYNAMIC -> {
                        afFilters.add("acompressor=ratio=3:threshold=0.05:attack=10:release=200")
                    }
                    AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> {
                        afFilters.add("loudnorm=I=-23:LRA=7:tp=-1")
                    }
                    AudioNormalizationMode.NONE -> {}
                }
                val filterString = afFilters.joinToString(",")
                if (filterString.isNotEmpty()) {
                    mpvView?.mpv?.setPropertyString("af", filterString)
                } else {
                    mpvView?.mpv?.command("af", "clr", "")
                }
            } else {
                mpvView?.mpv?.command("af", "clr", "")
            }
            
            val sid = audioSessionId
            if (sid != 0) {
                dialogueBoost.attach(sid)
                dialogueBoost.setStrength(config.audioEffects.dialogueBoostStrength)
                dialogueBoost.setEnabled(config.audioEffects.dialogueBoostEnabled)

                nightMode.attach(sid)
                nightMode.setStrength(config.audioEffects.nightModeStrength)
                nightMode.setEnabled(config.audioEffects.nightModeEnabled)

                audioNormalization.attach(sid)
                audioNormalization.setMode(config.audioEffects.audioNormalizationMode)
                audioNormalization.setEnabled(config.audioEffects.audioNormalizationEnabled)

                channelMix.attach(sid)
                channelMix.setMode(config.audioEffects.channelMixMode)
                channelMix.setEnabled(config.audioEffects.channelMixEnabled)
            }
        } catch (_: Exception) {}
    }

    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?) {
        try {
            val m = mpvView?.mpv ?: return
            if (type == TrackType.AUDIO) {
                Log.d(TAG, "Selecting MPV audio track id=$index")
                if (index < 0) m.setPropertyString("aid", "auto")
                else m.setPropertyString("aid", "$index")
            } else {
                Log.d(TAG, "Selecting MPV subtitle track id=$index")
                if (index < 0) {
                    _currentCues.value = emptyList()
                    lastLoggedSubtitleText = null
                    m.setPropertyString("sid", "no")
                } else {
                    m.setPropertyString("sid", "$index")
                }
            }
            refreshTracks("select-${type.name.lowercase()}")
            logSubtitleRenderState("select-${type.name.lowercase()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select MPV ${type.name.lowercase()} track id=$index", e)
        }
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // MPV does not support mid-stream bitrate changes for non-adaptive streams.
        // The value is stored and applied when the next load() is called.
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

        pendingRequest?.let { request ->
            pendingRequest = null
            try {
                configureMpvForRequest(view, request)
                view.playFile(request.uri)
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
            applySubtitleStyleProperties(m, style)
            logSubtitleRenderState("style")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply MPV subtitle style", e)
        }
    }

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        val aspectValue = when {
            mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "-1"
            mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "-1"
            ratio != null && ratio > 0f -> {
                val w = (ratio * 100).toInt()
                val h = 100
                val gcd = gcd(w, h)
                "${w / gcd}:${h / gcd}"
            }
            else -> "-1"
        }
        try { mpvView?.mpv?.setPropertyString("video-aspect-override", aspectValue) } catch (_: Exception) {}
    }

    override fun captureViewBitmap(): Bitmap? = null

    override val currentPositionMs: Long
        get() = try {
            Math.round((mpvView?.mpv?.getPropertyDouble("time-pos") ?: 0.0) * 1000)
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
        var lastPlayingState = _isPlaying.value
        val ticker = engineScope.launch {
            while (isActive) {
                delay(500)
                trySend(currentPositionMs)
                val currentlyPlaying = _isPlaying.value
                if (currentlyPlaying || currentlyPlaying != lastPlayingState) {
                    updateBufferAndStats()
                }
                lastPlayingState = currentlyPlaying
            }
        }
        awaitClose { ticker.cancel() }
    }

    private fun updateBufferAndStats() {
        val m = mpvView?.mpv ?: return
        try {
            val duration = (m.getPropertyDouble("duration") ?: 0.0) * 1000.0
            if (duration > 0) {
                val cacheDuration = try {
                    m.getPropertyDouble("demuxer-cache-duration") ?: 0.0
                } catch (_: Exception) { 0.0 }
                val posMs = currentPositionMs
                _bufferedPositionMs.value = (posMs + (cacheDuration * 1000.0)).toLong().coerceAtMost(duration.toLong())
            }
        } catch (_: Exception) {}

        try {
            val newStats = EngineVideoStats(
                videoCodec = try { m.getPropertyString("video-format") } catch (_: Exception) { null },
                videoDecoder = try { m.getPropertyString("hwdec-current") } catch (_: Exception) { null },
                videoResolution = buildString {
                    val w = try { m.getPropertyInt("width") } catch (_: Exception) { null }
                    val h = try { m.getPropertyInt("height") } catch (_: Exception) { null }
                    if (w != null && h != null && w > 0 && h > 0) append("${w}x${h}")
                }.ifEmpty { null },
                videoFrameRate = try {
                    m.getPropertyDouble("container-fps")?.let { fps ->
                        if (fps > 0f) fps.toFloat() else null
                    }
                } catch (_: Exception) { null },
                videoBitrate = try {
                    m.getPropertyDouble("video-bitrate")?.let { br ->
                        if (br > 0) br.toInt() else null
                    }
                } catch (_: Exception) { null },
                audioCodec = try { m.getPropertyString("audio-codec") } catch (_: Exception) { null },
                audioSampleRate = try {
                    m.getPropertyInt("audio-params/samplerate")?.let { sr ->
                        if (sr > 0) sr else null
                    }
                } catch (_: Exception) { null },
                audioChannels = try {
                    m.getPropertyInt("audio-params/channel-count")?.let { ch ->
                        if (ch > 0) ch else null
                    }
                } catch (_: Exception) { null },
                audioBitrate = try {
                    m.getPropertyDouble("audio-bitrate")?.let { br ->
                        if (br > 0) br.toInt() else null
                    }
                } catch (_: Exception) { null },
                estimatedBandwidthBps = 0L,
                droppedFrames = try {
                    m.getPropertyInt("decoder-frame-drop-count")?.toLong() ?: 0L
                } catch (_: Exception) { 0L },
                bufferedPositionMs = _bufferedPositionMs.value,
            )
            val currentStats = _videoStats.value
            if (newStats != currentStats) {
                _videoStats.value = newStats
            }
        } catch (_: Exception) {}
    }

    private fun buildTracks(): List<MediaTrack> {
        val m = mpvView?.mpv ?: return emptyList()
        val trackList = try {
            m.getPropertyNode("track-list")?.asArray()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val result = mutableListOf<MediaTrack>()
        for (node in trackList) {
            val track = node.asMap() ?: continue
            val t = track["type"]?.asString() ?: continue
            val trackType = when (t) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> continue
            }
            val id = track["id"].asTrackId() ?: continue
            val lang = track["lang"]?.asString()
            val title = track["title"]?.asString()
            val codec = track["codec"]?.asString()
            val selected = track["selected"]?.asBoolean() ?: false
            val label = buildTrackLabel(trackType, id, lang, title, codec)
            
            result.add(
                MediaTrack(
                    id = "mpv_${t}_${id}",
                    index = id,
                    label = label,
                    language = lang,
                    isSelected = selected,
                    type = trackType,
                )
            )
        }
        return result
    }

    private fun configureMpvForRequest(view: PlayerMPVView, request: PlaybackRequest) {
        if (request.startPositionMs > 0) {
            view.mpv.setOptionString("start", "+${request.startPositionMs / 1000.0}")
        }

        view.mpv.setOptionString("sub-visibility", "yes")
        view.mpv.setPropertyBoolean("sub-visibility", true)
        view.mpv.setPropertyBoolean("secondary-sub-visibility", true)
        request.preferredAudioLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString("alang", normalizeLanguageList(language))
        }
        request.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString("slang", normalizeLanguageList(language))
        }

        if (request.headers.isNotEmpty()) {
            val headerStr = request.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            view.mpv.setOptionString("http-header-fields", headerStr)
            Log.d(TAG, "Applied MPV HTTP headers: ${request.headers.keys}")
        }
    }

    private fun addPendingSubtitles(mpv: MPV) {
        val subtitles = pendingSubtitles
        if (subtitles.isEmpty()) return

        var selectedSubtitleAdded = false
        subtitles.forEach { sub ->
            val shouldSelect = !selectedSubtitleAdded && shouldSelectSubtitle(sub, pendingPreferredSubtitleLanguage)
            val flags = if (shouldSelect) "select" else "auto"
            try {
                Log.d(
                    TAG,
                    "Adding Jellyfin subtitle to MPV: id=${sub.id}, label=${sub.label}, lang=${sub.language}, " +
                        "codec=${sub.codec}, default=${sub.isDefault}, forced=${sub.isForced}, flags=$flags, " +
                        "url=${redactSensitive(sub.url)}"
                )
                if (sub.language.isNullOrBlank()) {
                    mpv.command("sub-add", sub.url, flags, sub.label)
                } else {
                    mpv.command("sub-add", sub.url, flags, sub.label, sub.language)
                }
                if (shouldSelect) selectedSubtitleAdded = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add Jellyfin subtitle: ${redactSensitive(sub.url)}", e)
            }
        }
        pendingSubtitles = emptyList()
        logSubtitleRenderState("sub-add")
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val mpv = mpvView?.mpv ?: return
        try {
            if (source.language.isNullOrBlank()) {
                mpv.command("sub-add", source.url, "select", source.label)
            } else {
                mpv.command("sub-add", source.url, "select", source.label, source.language)
            }
            refreshTracks("addExternalSubtitle", delayMs = 500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add external subtitle: ${source.url}", e)
        }
    }

    private fun shouldSelectSubtitle(subtitle: SubtitleSource, preferredLanguage: String?): Boolean {
        if (subtitle.isDefault || subtitle.isForced) return true
        val preferred = preferredLanguage?.takeIf { it.isNotBlank() } ?: return false
        return languageMatches(subtitle.language, preferred)
    }

    private fun languageMatches(candidate: String?, preferredLanguageList: String): Boolean {
        val candidateValues = normalizedLanguageValues(candidate) ?: return false
        return preferredLanguageList
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .any { preferred ->
                val preferredValues = normalizedLanguageValues(preferred) ?: return@any false
                candidateValues.any { it in preferredValues }
            }
    }

    private fun normalizedLanguageValues(language: String?): Set<String>? {
        val raw = language?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val tag = raw.replace('_', '-').lowercase()
        val base = tag.substringBefore('-')
        val display = try {
            java.util.Locale.forLanguageTag(tag).displayLanguage.lowercase()
        } catch (_: Exception) {
            null
        }
        return buildSet {
            add(tag)
            add(base)
            if (!display.isNullOrBlank()) add(display)
        }
    }

    private fun normalizeLanguageList(language: String): String =
        language.split(',', ';')
            .map { it.trim().replace('_', '-') }
            .filter { it.isNotBlank() }
            .joinToString(",")

    private fun refreshTracks(reason: String, delayMs: Long = 0L) {
        val action = Runnable {
            try {
                val tracks = buildTracks()
                val previous = _availableTracks.value
                _availableTracks.value = tracks
                if (tracks != previous || reason.startsWith("select")) {
                    Log.d(TAG, "MPV tracks refreshed ($reason): ${describeTracks(tracks)}")
                    logSubtitleRenderState(reason)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh MPV tracks ($reason)", e)
            }
        }
        if (delayMs > 0) mainHandler.postDelayed(action, delayMs) else mainHandler.post(action)
    }

    private fun describeTracks(tracks: List<MediaTrack>): String {
        val audio = tracks.filter { it.type == TrackType.AUDIO }
        val subtitles = tracks.filter { it.type == TrackType.SUBTITLE }
        val selectedSubtitle = subtitles.firstOrNull { it.isSelected }?.let { "${it.index}:${it.label}" } ?: "none"
        return "audio=${audio.size}, subtitles=${subtitles.size}, selectedSubtitle=$selectedSubtitle, " +
            "subtitleTracks=${subtitles.take(8).joinToString { "${it.index}:${it.label}${if (it.isSelected) "*" else ""}" }}" +
            if (subtitles.size > 8) ", ..." else ""
    }

    private fun applySubtitleStyleOptions(mpv: MPV, style: SubtitleStyle) {
        val values = subtitleStyleValues(style)
        mpv.setOptionString("sub-color", values.textColor)
        mpv.setOptionString("sub-back-color", values.backgroundColor)
        mpv.setOptionString("sub-outline-color", values.edgeColor)
        mpv.setOptionString("sub-shadow-color", values.edgeColor)
        mpv.setOptionString("sub-border-style", "outline-and-shadow")
        mpv.setOptionString("sub-font", "sans-serif")
        mpv.setOptionString("sub-font-size", values.fontSize.toString())
        mpv.setOptionString("sub-scale", "1.0")
        mpv.setOptionString("sub-pos", "100")
        mpv.setOptionString("sub-margin-y", values.marginY.toString())
        mpv.setOptionString("sub-outline-size", values.outlineSize.toString())
        mpv.setOptionString("sub-shadow-offset", values.shadowOffset.toString())
    }

    private fun applySubtitleStyleProperties(mpv: MPV, style: SubtitleStyle) {
        val values = subtitleStyleValues(style)
        mpv.setPropertyBoolean("sub-visibility", true)
        mpv.setPropertyBoolean("secondary-sub-visibility", true)
        mpv.setPropertyString("sub-color", values.textColor)
        mpv.setPropertyString("sub-back-color", values.backgroundColor)
        mpv.setPropertyString("sub-outline-color", values.edgeColor)
        mpv.setPropertyString("sub-shadow-color", values.edgeColor)
        mpv.setPropertyString("sub-border-style", "outline-and-shadow")
        mpv.setPropertyDouble("sub-font-size", values.fontSize.toDouble())
        mpv.setPropertyDouble("sub-scale", 1.0)
        mpv.setPropertyInt("sub-pos", 100)
        mpv.setPropertyInt("sub-margin-y", values.marginY)
        mpv.setPropertyDouble("sub-outline-size", values.outlineSize)
        mpv.setPropertyDouble("sub-shadow-offset", values.shadowOffset)
    }

    private data class MpvSubtitleStyleValues(
        val textColor: String,
        val backgroundColor: String,
        val edgeColor: String,
        val fontSize: Int,
        val marginY: Int,
        val outlineSize: Double,
        val shadowOffset: Double,
    )

    private fun subtitleStyleValues(style: SubtitleStyle): MpvSubtitleStyleValues {
        val marginY = (style.verticalPosition.coerceIn(0f, 0.4f) * 720).toInt().coerceAtLeast(0)
        val outlineSize: Double
        val shadowOffset: Double
        when (style.edgeType) {
            SubtitleEdgeType.NONE -> {
                outlineSize = 0.0
                shadowOffset = 0.0
            }
            SubtitleEdgeType.OUTLINE -> {
                outlineSize = 2.0
                shadowOffset = 0.0
            }
            SubtitleEdgeType.DROP_SHADOW -> {
                outlineSize = 0.0
                shadowOffset = 2.0
            }
            SubtitleEdgeType.RAISED,
            SubtitleEdgeType.DEPRESSED -> {
                outlineSize = 1.0
                shadowOffset = 1.5
            }
        }
        return MpvSubtitleStyleValues(
            textColor = colorToMpvHex(style.fontColor.value, 1f),
            backgroundColor = colorToMpvHex(style.backgroundColor.value, style.backgroundOpacity),
            edgeColor = colorToMpvHex(style.edgeColor.value, 1f),
            fontSize = style.fontSize.coerceIn(10, 72),
            marginY = marginY,
            outlineSize = outlineSize,
            shadowOffset = shadowOffset,
        )
    }

    private fun colorToMpvHex(color: Int, opacity: Float): String {
        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        val rgb = color and 0x00FFFFFF
        return String.format("#%02X%06X", alpha, rgb)
    }

    private fun buildTrackLabel(
        trackType: TrackType,
        id: Int,
        language: String?,
        title: String?,
        codec: String?,
    ): String {
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        if (trackType == TrackType.SUBTITLE && cleanTitle != null) return cleanTitle

        val languageLabel = language?.takeIf { it.isNotBlank() }?.let { lang ->
            try {
                java.util.Locale.forLanguageTag(lang.replace('_', '-')).displayLanguage.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                lang
            }
        }
        val parts = listOfNotNull(languageLabel, cleanTitle, codec?.takeIf { it.isNotBlank() })
            .distinctBy { it.lowercase() }
        val fallbackPrefix = if (trackType == TrackType.SUBTITLE) "Subtitle" else "Audio"
        return parts.joinToString(" · ").ifBlank { "$fallbackPrefix $id" }
    }

    private fun MPVNode?.asTrackId(): Int? =
        this?.asInt()?.toInt() ?: this?.asString()?.toIntOrNull()

    private fun logSubtitleRenderState(reason: String) {
        val m = mpvView?.mpv ?: return
        try {
            val sid = m.getPropertyString("sid")
            val visible = m.getPropertyBoolean("sub-visibility")
            val subText = try { m.getPropertyString("sub-text") } catch (_: Exception) { null }
            val selected = buildTracks().firstOrNull { it.type == TrackType.SUBTITLE && it.isSelected }
            Log.d(
                TAG,
                "MPV subtitle render state ($reason): sid=$sid, visible=$visible, " +
                    "fontSize=${m.getPropertyDouble("sub-font-size")}, marginY=${m.getPropertyInt("sub-margin-y")}, " +
                    "pos=${m.getPropertyInt("sub-pos")}, selected=${selected?.index}:${selected?.label}, " +
                    "activeText=${subText?.take(80).orEmpty()}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read MPV subtitle render state ($reason)", e)
        }
    }

    private fun logMpvMessage(prefix: String, level: Int, text: String) {
        val cleanText = redactSensitive(text.trim()).takeIf { it.isNotBlank() } ?: return
        if (level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN && !MPV_SUBTITLE_LOG_PATTERN.containsMatchIn("$prefix $cleanText")) {
            return
        }

        val message = "MPV ${mpvLogLevelName(level)} [$prefix] $cleanText"
        when {
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> Log.e(TAG, message)
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> Log.w(TAG, message)
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> Log.i(TAG, message)
            else -> Log.d(TAG, message)
        }
    }

    private fun mpvLogLevelName(level: Int): String = when {
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_FATAL -> "fatal"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> "error"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> "warn"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> "info"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_V -> "verbose"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_DEBUG -> "debug"
        else -> "trace"
    }

    private fun redactSensitive(value: String): String =
        value
            .replace(Regex("(?i)(api_key=)[^&\\s]+"), "\$1***")
            .replace(Regex("(?i)(api_key%3D)[^&\\s]+"), "\$1***")
            .replace(Regex("(?i)(X-Emby-Token:\\s*)[^,\\s]+"), "\$1***")

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
