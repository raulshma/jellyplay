package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvAudioOutput
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvVideoOutput
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlinx.coroutines.channels.awaitClose
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
        private const val DEMUXER_MAX_BYTES_LOW = 32 * 1024 * 1024L
        private const val DEMUXER_MAX_BYTES_NORMAL = 64 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_LOW = 16 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_NORMAL = 32 * 1024 * 1024L
        private val MPV_SUBTITLE_LOG_PATTERN =
            Regex("(?i)(sub|subtitle|libass|webvtt|vtt|srt|ssa|ass|ffmpeg|http|stream)")
        private val REDACT_API_KEY = Regex("(?i)(api_key=)[^&\\s]+")
        private val REDACT_API_KEY_ENCODED = Regex("(?i)(api_key%3D)[^&\\s]+")
        private val REDACT_EMBY_TOKEN = Regex("(?i)(X-Emby-Token:\\s*)[^,\\s]+")
    }

    private val isLowRamDevice by lazy { EngineDeviceProfile.isLowRamDevice(context) }
    // `var` so [load] can recreate it if a prior [release] cancelled the
    // SupervisorJob. Without this the engine is permanently unusable after
    // release() (positionFlow's ticker launches on this scope and would
    // silently never emit). Recreated lazily, only when inactive.
    private var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val capabilities = EngineCapabilityMatrix.MPV

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(1000L)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()
    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    private var mpvView: PlayerMPVView? = null
    private var pendingRequest: PlaybackRequest? = null
    @Volatile private var pendingSubtitles: List<SubtitleSource> = emptyList()
    @Volatile private var lastLoggedSubtitleText: String? = null
    // Android audio session id generated via AudioManager and pushed into
    // mpv's audiotrack/aaudio outputs so Android AudioEffects (dialogue
    // boost, night mode) can bind to mpv's output. Previously read back
    // the string property "audio-device-id" as an int, which always
    // threw and returned 0 — leaving the effect chain unbound.
    @Volatile private var generatedAudioSessionId: Int = 0
    
    private var currentConfig = EngineConfig()
    // mpv handles its own internal EQ via af filters; this helper exists
    // solely to host the dialogue-boost overlay (see DialogueBoostHelper
    // kdoc) on the engine's audio session. User EQ settings never flow
    // through it — the helper stays at FLAT base levels with only the
    // boost offsets overlaid.
    private val equalizerHelper = EqualizerHelper()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper)
    private val nightMode = NightModeHelper()

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
                        Log.d(TAG, "MPV start file; adding ${pendingSubtitles.size} Jellyfin subtitle source(s)")
                        addPendingSubtitles(mpv)
                        _playbackState.value = EnginePlaybackState.BUFFERING
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "MPV file loaded")
                        _playbackState.value = EnginePlaybackState.READY
                        refreshTracks("file-loaded")
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
            val configDir = java.io.File(context.filesDir, "mpv")
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("config-dir", configDir.absolutePath)

            val fontsDir = java.io.File(context.cacheDir, "fonts")
            mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath + "/")
            mpv.setOptionString("sub-font-provider", "none")

            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()

            val hwdecValue = mpvCfg.hwdecOverride?.key ?: when (currentConfig.decoderMode) {
                DecoderMode.HW_PREFERRED -> "mediacodec-copy,mediacodec,no"
                DecoderMode.HW_ONLY -> "mediacodec-copy,mediacodec"
                DecoderMode.SW_ONLY -> "no"
            }
            mpv.setOptionString("hwdec", hwdecValue)
            mpv.setOptionString("hwdec-codecs", "all")

            val aoValue = buildString {
                append(mpvCfg.audioOutput.key)
                mpvCfg.audioFallback?.let { append(",").append(it.key) }
            }
            mpv.setOptionString("ao", aoValue)
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("opengl-es", "yes")
            // Size subtitles against the video frame, not the OS window. With
            // "yes" (window-relative), rotating to portrait grows the window
            // height ~2x and blows the captions up, while the video itself is
            // letterboxed; "no" keeps captions proportional to the video, so
            // they stay correct and consistent across rotation — matching how
            // ExoPlayer (fixed SP) and VLC (video-relative freetype) behave.
            // See issue #66-A.
            mpv.setOptionString("sub-scale-with-window", "no")
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("sub-visibility", "yes")
            mpv.setOptionString("secondary-sub-visibility", "yes")
            mpv.setOptionString("sub-ass-override", "scale")
            mpv.setOptionString("keep-open", "yes")
            applySubtitleStyleOptions(mpv, currentConfig.subtitleStyle)
            mpv.setOptionString("panscan", "0.0")
            mpv.setOptionString("sub-use-margins", "no")
            mpv.setOptionString("sub-ass-force-margins", "no")

            mpv.setOptionString("scale", mpvCfg.scaler.key)
            if (mpvCfg.deband) {
                mpv.setOptionString("deband", "yes")
            }
            if (mpvCfg.interpolation) {
                mpv.setOptionString("interpolation", "yes")
                mpv.setOptionString("video-sync", "display-resample")
            }
            mpv.setOptionString("framedrop", mpvCfg.frameDrop.key)
            mpv.setOptionString("vd-lavc-skiploopfilter", mpvCfg.skipLoopFilter.key)

            val demuxerMax = when (mpvCfg.demuxerMaxBytes) {
                MpvDemuxerMaxBytes.AUTO -> {
                    if (isLowRamDevice) DEMUXER_MAX_BYTES_LOW else DEMUXER_MAX_BYTES_NORMAL
                }
                else -> mpvCfg.demuxerMaxBytes.bytes
            }
            val demuxerMaxBack = when (mpvCfg.demuxerMaxBytes) {
                MpvDemuxerMaxBytes.AUTO -> {
                    if (isLowRamDevice) DEMUXER_MAX_BACK_BYTES_LOW else DEMUXER_MAX_BACK_BYTES_NORMAL
                }
                else -> mpvCfg.demuxerMaxBytes.bytes / 2
            }
            mpv.setOptionString("demuxer-max-bytes", demuxerMax.toString())
            mpv.setOptionString("demuxer-max-back-bytes", demuxerMaxBack.toString())

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
            assignAudioSessionId()
        }

        override fun observeProperties() {}

        fun removeObserver() {
            try { mpv.removeObserver(observer) } catch (_: Exception) {}
            try { mpv.removeLogObserver(logObserver) } catch (_: Exception) {}
        }
    }

    /**
     * Allocates a real Android audio session id and pushes it into mpv's
     * audiotrack / aaudio outputs so Android [android.media.audiofx.AudioEffect]
     * instances (dialogue boost, night mode) can bind to mpv's output.
     * Must run after [PlayerMPVView.initialize] has created the mpv handle
     * and before playback starts.
     */
    private fun assignAudioSessionId() {
        val mpv = mpvView?.mpv ?: return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sid = audioManager?.generateAudioSessionId() ?: AudioManager.ERROR
            if (sid == AudioManager.ERROR) {
                generatedAudioSessionId = 0
                return
            }
            generatedAudioSessionId = sid
            try { mpv.setPropertyInt("audiotrack-session-id", sid) } catch (_: Exception) {}
            try { mpv.setPropertyInt("aaudio-session-id", sid) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to assign MPV audio session id", e)
        }
    }

    override fun load(request: PlaybackRequest) {
        if (!engineScope.isActive) {
            engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
        pendingRequest = request
        pendingSubtitles = request.externalSubtitles
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
        lastLoggedSubtitleText = null
        generatedAudioSessionId = 0
        mainHandler.removeCallbacksAndMessages(null)
        dialogueBoost.detach()
        equalizerHelper.detach()
        nightMode.detach()
        engineScope.cancel()
        val view = mpvView
        mpvView = null
        view?.let {
            it.removeObserver()
            try { it.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
        }
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
    }

    override fun play() {
        try {
            if (_playbackState.value == EnginePlaybackState.ENDED) {
                mpvView?.mpv?.command("seek", "0", "absolute")
            }
            mpvView?.mpv?.setPropertyBoolean("pause", false)
        } catch (e: Exception) { Log.w(TAG, "play failed", e) }
    }

    override fun pause() {
        try { mpvView?.mpv?.setPropertyBoolean("pause", true) } catch (e: Exception) { Log.w(TAG, "pause failed", e) }
    }

    override fun stop() {
        try {
            mpvView?.mpv?.command("stop")
        } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
    }

    override fun seekTo(positionMs: Long) {
        try { mpvView?.mpv?.command("seek", "%.6f".format(positionMs / 1000.0), "absolute") } catch (e: Exception) { Log.w(TAG, "seekTo failed", e) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        try { mpvView?.mpv?.setPropertyDouble("speed", speed.toDouble()) } catch (e: Exception) { Log.w(TAG, "setPlaybackSpeed failed", e) }
    }

    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        val oldConfig = currentConfig
        currentConfig = config
        val mpvCfg = (config.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()

        try {
            val mpv = mpvView?.mpv ?: return

            if (oldConfig.audioDelayMs != config.audioDelayMs) {
                mpv.setPropertyDouble("audio-delay", config.audioDelayMs / 1000.0)
            }
            if (oldConfig.subtitleDelayMs != config.subtitleDelayMs) {
                mpv.setPropertyDouble("sub-delay", config.subtitleDelayMs / 1000.0)
            }

            if (oldConfig.decoderMode != config.decoderMode || (oldConfig.engineSpecific as? MpvEngineConfig)?.hwdecOverride != mpvCfg.hwdecOverride) {
                val hwdecValue = mpvCfg.hwdecOverride?.key ?: when (config.decoderMode) {
                    DecoderMode.HW_PREFERRED -> "mediacodec-copy,mediacodec,no"
                    DecoderMode.HW_ONLY -> "mediacodec-copy,mediacodec"
                    DecoderMode.SW_ONLY -> "no"
                }
                mpv.setPropertyString("hwdec", hwdecValue)
            }

            if (oldConfig.audioPassthrough != config.audioPassthrough) {
                if (config.audioPassthrough) {
                    mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
                } else {
                    mpv.setOptionString("audio-spdif", "")
                }
            }

            val oldMpvCfg = oldConfig.engineSpecific as? MpvEngineConfig
            if (oldMpvCfg?.audioOutput != mpvCfg.audioOutput || oldMpvCfg?.audioFallback != mpvCfg.audioFallback) {
                val aoValue = buildString {
                    append(mpvCfg.audioOutput.key)
                    mpvCfg.audioFallback?.let { append(",").append(it.key) }
                }
                mpv.setPropertyString("ao", aoValue)
            }

            if (oldMpvCfg?.scaler != mpvCfg.scaler) {
                mpv.setPropertyString("scale", mpvCfg.scaler.key)
            }
            if (oldMpvCfg?.deband != mpvCfg.deband) {
                mpv.setPropertyString("deband", if (mpvCfg.deband) "yes" else "no")
            }
            if (oldMpvCfg?.interpolation != mpvCfg.interpolation) {
                mpv.setPropertyString("interpolation", if (mpvCfg.interpolation) "yes" else "no")
                if (mpvCfg.interpolation) {
                    mpv.setPropertyString("video-sync", "display-resample")
                }
            }
            if (oldMpvCfg?.frameDrop != mpvCfg.frameDrop) {
                mpv.setPropertyString("framedrop", mpvCfg.frameDrop.key)
            }
            if (oldMpvCfg?.skipLoopFilter != mpvCfg.skipLoopFilter) {
                mpv.setPropertyString("vd-lavc-skiploopfilter", mpvCfg.skipLoopFilter.key)
            }

            if (oldConfig.subtitleStyle != config.subtitleStyle) {
                applySubtitleStyleInternal(config.subtitleStyle)
            }

            if (oldConfig.audioEffects.channelMixMode != config.audioEffects.channelMixMode) {
                when (config.audioEffects.channelMixMode) {
                    ChannelMixMode.STEREO_DOWNMIX -> mpv.setPropertyString("audio-channels", "stereo")
                    ChannelMixMode.MONO -> mpv.setPropertyString("audio-channels", "mono")
                    ChannelMixMode.SURROUND_UPMIX -> mpv.setPropertyString("audio-channels", "5.1")
                    ChannelMixMode.AUTO -> mpv.setPropertyString("audio-channels", "auto")
                }
            }

            val oldAudioFx = oldConfig.audioEffects
            val newAudioFx = config.audioEffects
            if (oldAudioFx.audioNormalizationEnabled != newAudioFx.audioNormalizationEnabled ||
                oldAudioFx.audioNormalizationMode != newAudioFx.audioNormalizationMode
            ) {
                if (newAudioFx.audioNormalizationEnabled) {
                    val afFilters = mutableListOf<String>()
                    when (newAudioFx.audioNormalizationMode) {
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
                        mpv.setPropertyString("af", filterString)
                    } else {
                        mpv.command("af", "clr", "")
                    }
                } else {
                    mpv.command("af", "clr", "")
                }
            }

            if (oldConfig.videoEffects != config.videoEffects) {
                applyVideoFilters(config.videoEffects)
            }

            val sid = audioSessionId
            if (sid != 0) {
                if (oldAudioFx.dialogueBoostStrength != newAudioFx.dialogueBoostStrength ||
                    oldAudioFx.dialogueBoostEnabled != newAudioFx.dialogueBoostEnabled
                ) {
                    // The boost overlay rides on the EqualizerHelper's
                    // priority-0 Equalizer; attach + enable it whenever
                    // boost is on so the overlay has somewhere to land.
                    equalizerHelper.attach(sid)
                    equalizerHelper.setEnabled(newAudioFx.dialogueBoostEnabled)
                    dialogueBoost.attach(sid)
                    dialogueBoost.setStrength(newAudioFx.dialogueBoostStrength)
                    dialogueBoost.setEnabled(newAudioFx.dialogueBoostEnabled)
                }
                if (oldAudioFx.nightModeStrength != newAudioFx.nightModeStrength ||
                    oldAudioFx.nightModeEnabled != newAudioFx.nightModeEnabled
                ) {
                    nightMode.attach(sid)
                    nightMode.setStrength(newAudioFx.nightModeStrength)
                    nightMode.setEnabled(newAudioFx.nightModeEnabled)
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyVideoFilters(effects: VideoEffectsConfig) {
        try {
            val filters = mutableListOf<String>()
            val hasBrightness = effects.brightness != 0f
            val hasContrast = effects.contrast != 1f
            val hasSaturation = effects.saturation != 1f
            val hasHue = effects.hue != 0f
            val hasRgbGain = effects.redGain != 1f || effects.greenGain != 1f || effects.blueGain != 1f
            if (hasBrightness || hasContrast || hasSaturation || hasHue || hasRgbGain) {
                val eqParts = mutableListOf<String>()
                if (hasBrightness) eqParts.add("brightness=${effects.brightness}")
                if (hasContrast) eqParts.add("contrast=${effects.contrast}")
                if (hasSaturation) eqParts.add("saturation=${effects.saturation}")
                if (hasHue) eqParts.add("hue=${effects.hue}")
                if (effects.redGain != 1f) eqParts.add("gamma_r=${effects.redGain}")
                if (effects.greenGain != 1f) eqParts.add("gamma_g=${effects.greenGain}")
                if (effects.blueGain != 1f) eqParts.add("gamma_b=${effects.blueGain}")
                filters.add("eq=${eqParts.joinToString(":")}")
            }
            if (effects.sharpness > 0f) {
                filters.add("unsharp=5:5:${(effects.sharpness * 1.5f).coerceIn(0.5f, 3.0f)}")
            }
            if (effects.gaussianBlur > 0f) {
                // lavfi gblur sigma ~ half the user value to keep 0..10 range sensible
                filters.add("lavfi=[gblur=sigma=${effects.gaussianBlur / 2f}]")
            }
            val rawDiscrete = kotlin.math.round(effects.rotationDegrees / 90f).toInt() * 90
            val discrete = ((rawDiscrete % 360) + 360) % 360
            mpvView?.mpv?.setPropertyDouble("video-rotate", discrete.toDouble())

            if (filters.isNotEmpty()) {
                mpvView?.mpv?.setPropertyString("vf", filters.joinToString(","))
            } else {
                mpvView?.mpv?.command("vf", "clr", "")
            }
        } catch (_: Exception) {}
    }

    override fun selectTrack(type: TrackType, index: Int) {
        try {
            val m = mpvView?.mpv ?: return
            if (type == TrackType.AUDIO) {
                Log.d(TAG, "Selecting MPV audio track id=$index")
                if (index < 0) {
                    m.setPropertyString("aid", "auto")
                } else {
                    try {
                        m.setPropertyInt("aid", index)
                    } catch (_: Exception) {
                        m.setPropertyString("aid", "$index")
                    }
                }
            } else {
                Log.d(TAG, "Selecting MPV subtitle track id=$index")
                if (index < 0) {
                    lastLoggedSubtitleText = null
                    m.setPropertyString("sid", "no")
                } else {
                    try {
                        m.setPropertyInt("sid", index)
                    } catch (_: Exception) {
                        m.setPropertyString("sid", "$index")
                    }
                    m.setPropertyBoolean("sub-visibility", true)
                    m.setPropertyBoolean("secondary-sub-visibility", true)
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

    override val volume: Float
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("volume") ?: 100.0) / 100.0).toFloat().coerceIn(0f, 1f)
        } catch (_: Exception) { 1f }

    override fun setVolume(value: Float) {
        try {
            val clamped = value.coerceIn(0f, 1f)
            val pct = (clamped * 100.0).coerceIn(0.0, 200.0)
            mpvView?.mpv?.setPropertyDouble("volume", pct)
            MediaStreamVolume.setNormalized(context, clamped)
        } catch (_: Exception) {}
    }

    override fun increaseVolume(delta: Float) {
        try {
            val m = mpvView?.mpv ?: return
            val current = m.getPropertyDouble("volume") ?: 100.0
            val next = (current + delta * 100.0).coerceIn(0.0, 200.0)
            m.setPropertyDouble("volume", next)
            MediaStreamVolume.setNormalized(context, (next / 100.0).toFloat().coerceIn(0f, 1f))
        } catch (_: Exception) {}
    }

    override fun decreaseVolume(delta: Float) {
        try {
            val m = mpvView?.mpv ?: return
            val current = m.getPropertyDouble("volume") ?: 100.0
            val next = (current - delta * 100.0).coerceAtLeast(0.0)
            m.setPropertyDouble("volume", next)
            MediaStreamVolume.setNormalized(context, (next / 100.0).toFloat().coerceIn(0f, 1f))
        } catch (_: Exception) {}
    }

    override fun setMuted(muted: Boolean) {
        try { mpvView?.mpv?.setPropertyBoolean("mute", muted) } catch (_: Exception) {}
        try {
            MediaStreamVolume.setNormalized(context, if (muted) 0f else 1f)
        } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
        setupFonts(context)
        val configDir = java.io.File(context.filesDir, "mpv")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        writeFontsConf(context, configDir)
        try {
            writeFontsConf(context, context.filesDir)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write fallback fonts.conf to filesDir", e)
        }

        try {
            android.system.Os.setenv("FONTCONFIG_FILE", java.io.File(configDir, "fonts.conf").absolutePath, true)
            android.system.Os.setenv("FONTCONFIG_PATH", configDir.absolutePath, true)
            Log.d(TAG, "Set FONTCONFIG environment variables successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set FONTCONFIG environment variables via Os.setenv", t)
        }

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
            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()
            view.initialize(configDir.absolutePath, context.cacheDir.absolutePath)
            view.setVo(mpvCfg.videoOutput.key)
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
        val m = mpvView?.mpv ?: return
        try { m.setPropertyString("video-aspect-override", aspectValue) } catch (_: Exception) {}

        val isZoom = mode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        try {
            m.setPropertyDouble("panscan", if (isZoom) 1.0 else 0.0)
            m.setPropertyString("sub-use-margins", if (isZoom) "yes" else "no")
            m.setPropertyString("sub-ass-force-margins", if (isZoom) "yes" else "no")
        } catch (_: Exception) {}
    }

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
        get() = generatedAudioSessionId

    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        // The polling loop (bounded paused-wait, play↔pause edge detection) is
        // shared via [EnginePositionTicker].
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = _isPlaying,
            isCurrentlyPlaying = { _isPlaying.value },
            onActive = {
                trySend(currentPositionMs)
                updateBufferPosition()
                if (_videoStatsEnabled.value) {
                    updateVideoStatsOnly()
                }
            },
        ).launch()
        awaitClose { ticker.cancel() }
    }

    private fun updateBufferPosition() {
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
    }

    private fun updateVideoStatsOnly() {
        val m = mpvView?.mpv ?: return
        try {
            val videoBitrateBps = try {
                m.getPropertyDouble("video-bitrate")?.let { br ->
                    if (br > 0) br.toInt() else null
                }
            } catch (_: Exception) { null }
            val audioBitrateBps = try {
                m.getPropertyDouble("audio-bitrate")?.let { br ->
                    if (br > 0) br.toInt() else null
                }
            } catch (_: Exception) { null }
            val combinedBitrate = (videoBitrateBps ?: 0) + (audioBitrateBps ?: 0)
            val bufferHealthMs = (_bufferedPositionMs.value - currentPositionMs).coerceAtLeast(0L)
            val bufferSizeBytes = if (combinedBitrate > 0) combinedBitrate * bufferHealthMs / 8000 else 0L
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
                videoBitrate = videoBitrateBps,
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
                audioBitrate = audioBitrateBps,
                estimatedBandwidthBps = combinedBitrate.toLong(),
                droppedFrames = try {
                    m.getPropertyInt("decoder-frame-drop-count")?.toLong() ?: 0L
                } catch (_: Exception) { 0L },
                totalVideoFrames = try {
                    m.getPropertyInt("displayed-frame-count")?.toLong() ?: 0L
                } catch (_: Exception) { 0L },
                bufferedPositionMs = _bufferedPositionMs.value,
                bufferSizeBytes = bufferSizeBytes,
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
            val startVal = "+${request.startPositionMs / 1000.0}"
            try { view.mpv.setOptionString("start", startVal) } catch (_: Exception) {}
            try { view.mpv.setPropertyString("start", startVal) } catch (_: Exception) {}
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
            try { view.mpv.setOptionString("http-header-fields", headerStr) } catch (_: Exception) {}
            try { view.mpv.setPropertyString("http-header-fields", headerStr) } catch (_: Exception) {}
            Log.d(TAG, "Applied MPV HTTP headers: ${request.headers.keys}")
        }

        try {
            applySubtitleStyleProperties(view.mpv, currentConfig.subtitleStyle)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply subtitle style inside configureMpvForRequest", e)
        }
    }

    private fun addPendingSubtitles(mpv: MPV) {
        val subtitles = pendingSubtitles
        if (subtitles.isEmpty()) return

        subtitles.forEach { sub ->
            val flags = "auto"
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
        if (style.applyCustomStyle) {
            customSubtitleStyleEntries(style, values).forEach { (k, v) -> mpv.safeSetOption(k, v) }
        } else {
            mpv.safeSetOption("sub-ass-override", "no")
        }

        mpv.safeSetOption("sub-font", "sans-serif")
        mpv.safeSetOption("sub-font-size", "55")
        mpv.safeSetOption("sub-scale", (style.fontSize.toDouble() / 24.0).toString())
        val subPosValue = (100 - (style.verticalPosition * 100).toInt()).coerceIn(0, 100)
        mpv.safeSetOption("sub-pos", subPosValue.toString())
        mpv.safeSetOption("sub-margin-y", values.marginY.toString())
        mpv.safeSetOption("sub-delay", (currentConfig.subtitleDelayMs / 1000.0).toString())
    }

    private fun applySubtitleStyleProperties(mpv: MPV, style: SubtitleStyle) {
        val values = subtitleStyleValues(style)
        mpv.safeSetPropertyBoolean("sub-visibility", true)
        mpv.safeSetPropertyBoolean("secondary-sub-visibility", true)
        if (style.applyCustomStyle) {
            customSubtitleStyleEntries(style, values).forEach { (k, v) -> mpv.safeSetPropertyString(k, v) }
            // Numeric properties are typed (Double) for the runtime path.
            mpv.safeSetPropertyDouble("sub-outline-size", values.outlineSize)
            mpv.safeSetPropertyDouble("sub-shadow-offset", values.shadowOffset)
        } else {
            // Reset to defaults
            mpv.safeSetPropertyString("sub-color", "#FFFFFFFF")
            mpv.safeSetPropertyString("sub-back-color", "#00000000")
            mpv.safeSetPropertyString("sub-outline-color", "#FF000000")
            mpv.safeSetPropertyString("sub-shadow-color", "#FF000000")
            mpv.safeSetPropertyString("sub-border-style", "outline-and-shadow")
            mpv.safeSetPropertyString("sub-ass-override", "no")
            mpv.safeSetPropertyDouble("sub-outline-size", 3.0)
            mpv.safeSetPropertyDouble("sub-shadow-offset", 0.0)
        }

        mpv.safeSetPropertyString("sub-font", "sans-serif")
        mpv.safeSetPropertyDouble("sub-font-size", 55.0)
        mpv.safeSetPropertyDouble("sub-scale", style.fontSize.toDouble() / 24.0)
        val subPosValue = (100 - (style.verticalPosition * 100).toInt()).coerceIn(0, 100)
        mpv.safeSetPropertyInt("sub-pos", subPosValue)
        mpv.safeSetPropertyInt("sub-margin-y", values.marginY)
        mpv.safeSetPropertyDouble("sub-delay", currentConfig.subtitleDelayMs / 1000.0)
    }

    /**
     * The string-typed subtitle-style key/value pairs shared by both
     * [applySubtitleStyleOptions] (init-time, setOptionString) and
     * [applySubtitleStyleProperties] (runtime, setPropertyString). Extracted
     * (L6) so the two near-identical consumers can't drift on key names or
     * derived values. Callers apply each pair through their own setter.
     */
    private fun customSubtitleStyleEntries(
        style: SubtitleStyle,
        values: MpvSubtitleStyleValues,
    ): List<Pair<String, String>> = buildList {
        add("sub-color" to values.textColor)
        add("sub-back-color" to values.backgroundColor)
        add("sub-outline-color" to values.edgeColor)
        add("sub-shadow-color" to values.edgeColor)
        val borderStyle = if (style.backgroundOpacity > 0f) "background-box" else "outline-and-shadow"
        add("sub-border-style" to borderStyle)
        add("sub-ass-override" to "scale")
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
        if (!com.raulshma.jellyplay.feature.player.video.BuildConfig.DEBUG) return
        val m = mpvView?.mpv ?: return
        try {
            val sid = m.getPropertyString("sid")
            val visible = m.getPropertyBoolean("sub-visibility")
            val subText = try { m.getPropertyString("sub-text") } catch (_: Exception) { null }
            val selected = _availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE && it.isSelected }
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
        if (!com.raulshma.jellyplay.feature.player.video.BuildConfig.DEBUG && level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN) return
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
            .replace(REDACT_API_KEY, "\$1***")
            .replace(REDACT_API_KEY_ENCODED, "\$1***")
            .replace(REDACT_EMBY_TOKEN, "\$1***")

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

    private fun setupFonts(context: Context) {
        val destDirs = arrayOf(
            java.io.File(context.cacheDir, "fonts"),
            java.io.File(context.filesDir, "mpv"),
            java.io.File(java.io.File(context.filesDir, "mpv"), "fonts")
        )
        val fontNames = arrayOf("subfont.ttf", "sans-serif.ttf", "Arial.ttf")

        for (dir in destDirs) {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            for (name in fontNames) {
                val destFile = java.io.File(dir, name)
                copyFontToDest(context, destFile)
            }
        }
    }

    private fun copyFontToDest(context: Context, destFile: java.io.File): Boolean {
        if (destFile.exists() && destFile.length() > 0) return true

        // Try copying from assets
        try {
            context.assets.open("subfont.ttf").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Successfully copied font from assets to ${destFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy font from assets to ${destFile.absolutePath}: ${e.message}")
        }

        // Try copying system fallback fonts
        val systemFonts = arrayOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        )
        for (path in systemFonts) {
            val systemFontFile = java.io.File(path)
            if (systemFontFile.exists()) {
                try {
                    systemFontFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Successfully copied system font from $path to ${destFile.absolutePath}")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy system font from $path to ${destFile.absolutePath}: ${e.message}")
                }
            }
        }
        return false
    }

    private fun writeFontsConf(context: Context, configDir: java.io.File) {
        val configFile = java.io.File(configDir, "fonts.conf")
        val cacheDir = java.io.File(context.cacheDir, "fontconfig")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val config = """
            <fontconfig>
                <dir>/system/fonts/</dir>
                <dir>/product/fonts/</dir>

                <cachedir>${cacheDir.absolutePath}</cachedir>

                <alias>
                    <family>serif</family>
                    <prefer><family>Noto Serif</family></prefer>
                </alias>

                <alias>
                    <family>sans-serif</family>
                    <prefer>
                        <family>Roboto</family>
                        <family>Noto Sans</family>
                    </prefer>
                </alias>

                <alias>
                    <family>monospace</family>
                    <prefer><family>Droid Sans Mono</family></prefer>
                </alias>

                <match target="pattern">
                    <edit name="family" mode="append_last">
                        <string>sans-serif</string>
                    </edit>
                </match>
            </fontconfig>
        """.trimIndent()

        if (configFile.exists() && configFile.readText() == config) return

        try {
            configFile.writeText(config)
            Log.d(TAG, "Successfully wrote fonts.conf to ${configFile.absolutePath}")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Failed to write fonts.conf: $e")
        }
    }

    private fun MPV.safeSetOption(name: String, value: String) {
        try {
            setOptionString(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set option $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyString(name: String, value: String) {
        try {
            setPropertyString(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyDouble(name: String, value: Double) {
        try {
            setPropertyDouble(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyInt(name: String, value: Int) {
        try {
            setPropertyInt(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyBoolean(name: String, value: Boolean) {
        try {
            setPropertyBoolean(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }
}
