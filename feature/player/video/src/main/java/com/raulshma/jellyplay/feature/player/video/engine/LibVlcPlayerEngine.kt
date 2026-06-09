package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcPlayerEngine(
    private val context: Context,
) : MediaEngine {

    companion object {
        private const val TAG = "LibVlcPlayerEngine"
        private const val LOW_RAM_THRESHOLD_MB = 2048L
    }

    private val isLowRamDevice by lazy { EngineDeviceProfile.isLowRamDevice(context) }
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val capabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = false,
        supportsOcr = false,
        supportsCues = false,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true,
        supportsDialogueBoost = true,
        supportsNightMode = true,
        supportsAudioNormalization = true,
        supportsChannelMixing = true,
        supportsVideoFilters = true,
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

    private val _pollingIntervalMs = MutableStateFlow(1000L)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()
    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    private var libVLC: LibVLC? = null
    val libVlc: LibVLC? get() = libVLC
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var currentPlaybackRequest: PlaybackRequest? = null
    
    private var currentConfig = EngineConfig()

    private var pendingPlay = false
    private var wasPlayingBeforeActivityPause = false
    private var hasRenderer = false
    private var pendingRendererItem: RendererItem? = null
    private var cachedDurationMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityPause() {
        wasPlayingBeforeActivityPause = _isPlaying.value
        pause()
        try { mediaPlayer?.detachViews() } catch (_: Exception) {}
    }

    override fun onActivityResume() {
        videoLayout?.let { layout ->
            try {
                mediaPlayer?.attachViews(layout, null, false, false)
            } catch (e: Exception) {
                Log.e(TAG, "attachViews failed on resume", e)
            }
        }
        if (wasPlayingBeforeActivityPause) {
            wasPlayingBeforeActivityPause = false
            play()
        }
    }

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _isPlaying.value = true
                _playbackState.value = EnginePlaybackState.READY
            }
            MediaPlayer.Event.Paused -> {
                _isPlaying.value = false
                _playbackState.value = EnginePlaybackState.READY
            }
            MediaPlayer.Event.Stopped -> {
                _isPlaying.value = false
                _playbackState.value = EnginePlaybackState.IDLE
            }
            MediaPlayer.Event.EndReached -> {
                _isPlaying.value = false
                _playbackState.value = EnginePlaybackState.ENDED
            }
            MediaPlayer.Event.Buffering -> {
                val bufPercent = event.buffering
                _playbackState.value = if (bufPercent < 100f) EnginePlaybackState.BUFFERING else EnginePlaybackState.READY
                val dur = durationMs
                if (dur > 0) {
                    _bufferedPositionMs.value = ((bufPercent / 100f) * dur).toLong()
                }
            }
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected -> {
                _availableTracks.value = buildTracks()
            }
            MediaPlayer.Event.EncounteredError -> {
                _playbackState.value = EnginePlaybackState.ERROR
                _errorFlow.tryEmit("VLC encountered an error during playback")
            }
        }
    }

    override fun load(request: PlaybackRequest) {
        releaseInternal(releaseVlc = true)

        currentPlaybackRequest = request

        val vlcCfg = (currentConfig.engineSpecific as? LibVlcEngineConfig) ?: LibVlcEngineConfig()

        val options = arrayListOf(
            "--aout=${vlcCfg.audioOutput.key}",
            "--vout=${vlcCfg.videoOutput.key}",
        )

        if (vlcCfg.audioTimeStretch) {
            options.add("--audio-time-stretch")
        }

        if (vlcCfg.skipLoopFilter > 0) {
            options.add("--avcodec-skiploopfilter")
            options.add(vlcCfg.skipLoopFilter.toString())
        }
        if (vlcCfg.skipFrame > 0) {
            options.add("--avcodec-skip-frame")
            options.add(vlcCfg.skipFrame.toString())
        }
        options.add("--avcodec-skip-idct")
        options.add("0")

        if (vlcCfg.decoderThreads > 0) {
            options.add("--avcodec-threads=${vlcCfg.decoderThreads}")
        }

        val networkCaching = when {
            vlcCfg.networkCaching > 0 -> vlcCfg.networkCaching
            isLowRamDevice -> 1500
            else -> 3000
        }
        options.add("--network-caching=$networkCaching")

        if (isLowRamDevice) {
            options.add("--clock-jitter=0")
            options.add("--clock-synchro=0")
        }

        if (vlcCfg.dropLateFrames) {
            options.add("--drop-late-frames")
        }
        if (vlcCfg.skipFrames) {
            options.add("--skip-frames")
        }

        if (currentConfig.audioDelayMs != 0L) {
            options.add("--audio-desync=${currentConfig.audioDelayMs.toInt()}")
        }

        if (currentConfig.audioPassthrough) {
            if (!hasRenderer) {
                options.add("--codec=ac3,eac3,dts,dtshd,truehd")
            }
        }

        if (hasRenderer) {
            options.add("--sout-keep")
            options.add("--sout-chromecast-conversion-quality=2")
        }

        when (currentConfig.audioEffects.channelMixMode) {
            ChannelMixMode.STEREO_DOWNMIX -> options.add("--stereo-mode=stereo")
            ChannelMixMode.MONO -> options.add("--stereo-mode=mono")
            ChannelMixMode.SURROUND_UPMIX -> options.add("--stereo-mode=surround")
            ChannelMixMode.AUTO -> {}
        }

        if (currentConfig.audioEffects.audioNormalizationEnabled) {
            when (currentConfig.audioEffects.audioNormalizationMode) {
                AudioNormalizationMode.DYNAMIC -> {
                    options.add("--audio-filter=compressor")
                    options.add("--compressor-ratio=3")
                    options.add("--compressor-threshold=-18")
                }
                AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> {
                    options.add("--audio-filter=normvol")
                    options.add("--norm-max-level=0.8")
                }
                AudioNormalizationMode.NONE -> {}
            }
        }

        val vlc = try {
            LibVLC(context.applicationContext, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LibVLC", e)
            currentPlaybackRequest = null
            _errorFlow.tryEmit("Failed to initialize VLC Engine")
            return
        }
        libVLC = vlc

        val mp = MediaPlayer(vlc)
        mp.setEventListener(eventListener)
        mediaPlayer = mp

        pendingRendererItem?.let { renderer ->
            try { mp.setRenderer(renderer) } catch (_: Exception) {}
        }
        
        val media = buildMedia(
            vlc = vlc,
            request = request,
            subtitleStyle = currentConfig.subtitleStyle,
            startPositionMs = request.startPositionMs,
        )

        if (hasRenderer) {
            try { media.parse() } catch (_: Exception) {}
        }

        mp.media = media
        media.release()

        // Apply pre-set speed
        mp.rate = 1f
        mp.setSpuDelay(currentConfig.subtitleDelayMs * 1000L) // microseconds

        pendingPlay = true
        
        // Re-attach view if it exists
        videoLayout?.let {
            try {
                mp.attachViews(it, null, false, false)
                mp.play()
                pendingPlay = false
            } catch (e: Exception) {
                Log.e(TAG, "attachViews/play failed", e)
            }
        }
    }

    override fun release() {
        engineScope.cancel()
        releaseInternal(releaseVlc = true)
        hasRenderer = false
        pendingRendererItem = null
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
        cachedDurationMs = 0L
    }

    private fun releaseInternal(releaseVlc: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        pendingPlay = false
        currentPlaybackRequest = null
        mediaPlayer?.let { mp ->
            mediaPlayer = null
            mp.setEventListener(null)
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.detachViews() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        if (releaseVlc) {
            libVLC?.let { try { it.release() } catch (_: Exception) {} }
            libVLC = null
            videoLayout = null
        }
    }

    override fun play() {
        try {
            val mp = mediaPlayer ?: return
            if (_playbackState.value == EnginePlaybackState.ENDED) {
                mp.time = 0L
            }
            mp.play()
        } catch (_: Exception) {}
    }

    override fun pause() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }

    override fun stop() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
    }

    override fun seekTo(positionMs: Long) {
        try { mediaPlayer?.time = positionMs } catch (_: Exception) {}
    }

    override fun setPlaybackSpeed(speed: Float) {
        try { mediaPlayer?.rate = speed } catch (_: Exception) {}
    }

    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        val oldConfig = currentConfig
        currentConfig = config
        
        try {
            val mp = mediaPlayer ?: return
            
            if (oldConfig.audioDelayMs != config.audioDelayMs) {
                mp.setAudioDelay(config.audioDelayMs)
            }
            if (oldConfig.subtitleDelayMs != config.subtitleDelayMs) {
                mp.setSpuDelay(config.subtitleDelayMs * 1000L)
            }
            
            if (oldConfig.subtitleStyle != config.subtitleStyle) {
                reloadMediaForSubtitleStyleChange()
            }

            if (oldConfig.videoEffects != config.videoEffects) {
                applyVideoFilters(config.videoEffects)
            }
        } catch (_: Exception) {}
    }

    private fun applyVideoFilters(effects: VideoEffectsConfig) {
        try {
            val mp = mediaPlayer ?: return
            val hasAdjust = effects.brightness != 0f || effects.contrast != 1f || effects.saturation != 1f
            if (hasAdjust) {
                val brightnessVal = (1.0f + effects.brightness).coerceIn(0.0f, 2.0f)
                val contrastVal = effects.contrast.coerceIn(0.0f, 2.0f)
                val saturationVal = effects.saturation.coerceIn(0.0f, 3.0f)
                val media = mp.media
                media?.addOption(":brightness=${(brightnessVal * 100).toInt()}")
                media?.addOption(":contrast=${(contrastVal * 100).toInt()}")
                media?.addOption(":saturation=${(saturationVal * 100).toInt()}")
            }
        } catch (_: Exception) {}
    }

    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?) {
        try {
            val mp = mediaPlayer ?: return
            if (type == TrackType.AUDIO) {
                val tracks = mp.getAudioTracks() ?: return
                if (index in tracks.indices) mp.audioTrack = tracks[index].id
            } else {
                if (index < 0) { mp.spuTrack = -1; return }
                val tracks = mp.getSpuTracks() ?: return
                if (index in tracks.indices) mp.spuTrack = tracks[index].id
            }
        } catch (_: Exception) {}
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val mp = mediaPlayer ?: return
        try {
            mp.addSlave(
                org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                source.url,
                true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add external subtitle: ${source.url}", e)
        }
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // VLC does not support mid-stream bitrate changes for non-adaptive streams.
        // The value is stored and applied when the next load() is called.
    }

    @Volatile
    private var lastUnmuteVolume: Float = 1f

    override val volume: Float
        get() = try {
            (mediaPlayer?.volume ?: 100).coerceIn(0, 100) / 100f
        } catch (_: Exception) { 1f }

    override fun setVolume(value: Float) {
        try {
            val clamped = value.coerceIn(0f, 1f)
            val v = (clamped * 100).toInt().coerceIn(0, 100)
            if (v > 0) lastUnmuteVolume = v / 100f
            mediaPlayer?.volume = v
            MediaStreamVolume.setNormalized(context, clamped)
        } catch (_: Exception) {}
    }

    override fun increaseVolume(delta: Float) {
        try {
            val mp = mediaPlayer ?: return
            val next = (mp.volume + (delta * 100).toInt()).coerceIn(0, 100)
            if (next > 0) lastUnmuteVolume = next / 100f
            mp.volume = next
            MediaStreamVolume.setNormalized(context, next / 100f)
        } catch (_: Exception) {}
    }

    override fun decreaseVolume(delta: Float) {
        try {
            val mp = mediaPlayer ?: return
            val next = (mp.volume - (delta * 100).toInt()).coerceIn(0, 100)
            if (next > 0) lastUnmuteVolume = next / 100f
            mp.volume = next
            MediaStreamVolume.setNormalized(context, next / 100f)
        } catch (_: Exception) {}
    }

    override fun setMuted(muted: Boolean) {
        // libVLC 3.7.x MediaPlayer has no native mute API — emulate it via volume.
        try {
            val mp = mediaPlayer ?: return
            val restored = lastUnmuteVolume.coerceIn(0f, 1f)
            val target = if (muted) 0f else restored
            mp.volume = (target * 100).toInt().coerceIn(0, 100)
            MediaStreamVolume.setNormalized(context, target)
        } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
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

    override fun applySubtitleStyleToView(view: View, style: SubtitleStyle) {
        if (currentConfig.subtitleStyle != style) {
            currentConfig = currentConfig.copy(subtitleStyle = style)
            reloadMediaForSubtitleStyleChange()
        }
    }

    private fun reloadMediaForSubtitleStyleChange() {
        val mp = mediaPlayer ?: return
        val vlc = libVLC ?: return
        val request = currentPlaybackRequest ?: return

        val currentPositionMs = try { mp.time.coerceAtLeast(0L) } catch (_: Exception) { 0L }
        val wasPlaying = try { mp.isPlaying } catch (_: Exception) { false }

        try {
            val media = buildMedia(
                vlc = vlc,
                request = request,
                subtitleStyle = currentConfig.subtitleStyle,
                startPositionMs = currentPositionMs,
            )
            if (hasRenderer) {
                try { media.parse() } catch (_: Exception) {}
            }
            mp.media = media
            media.release()
            if (currentPositionMs > 0) {
                try { mp.time = currentPositionMs } catch (_: Exception) {}
            }
            if (wasPlaying) {
                try { mp.play() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reload media for subtitle style change", e)
        }
    }

    fun reloadForRenderer(renderer: Any?) {
        val mp = mediaPlayer ?: return
        val vlc = libVLC ?: return
        val request = currentPlaybackRequest ?: return
        try {
            val item = renderer as? RendererItem
            mp.setRenderer(item)
            hasRenderer = item != null
            pendingRendererItem = item
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set renderer", e)
        }
        if (!hasRenderer) return

        val currentPositionMs = try { mp.time.coerceAtLeast(0L) } catch (_: Exception) { 0L }
        val wasPlaying = try { mp.isPlaying } catch (_: Exception) { false }

        try {
            val media = buildMedia(
                vlc = vlc,
                request = request,
                subtitleStyle = currentConfig.subtitleStyle,
                startPositionMs = currentPositionMs,
            )
            try { media.parse() } catch (_: Exception) {}
            mp.media = media
            media.release()
            if (currentPositionMs > 0) {
                try { mp.time = currentPositionMs } catch (_: Exception) {}
            }
            if (wasPlaying) {
                try { mp.play() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reload media for renderer", e)
        }
    }

    private fun buildMedia(
        vlc: LibVLC,
        request: PlaybackRequest,
        subtitleStyle: SubtitleStyle,
        startPositionMs: Long,
    ): Media {
        val media = Media(vlc, Uri.parse(request.uri))
        val hwDecoding = currentConfig.decoderMode != DecoderMode.SW_ONLY
        media.setHWDecoderEnabled(hwDecoding, hwDecoding)

        if (isLowRamDevice) {
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
        }

        if (startPositionMs > 0) {
            media.addOption(":start-time=${startPositionMs / 1000.0}")
        }

        if (request.headers.isNotEmpty()) {
            media.addOption(":http-user-agent=JellyPlay")
            request.headers.forEach { (key, value) ->
                media.addOption(":http-header=${key}: ${value}")
            }
        }

        request.externalSubtitles.forEach { sub ->
            try {
                media.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave(
                    org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                    0,
                    sub.url,
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add subtitle: ${sub.url}", e)
            }
        }

        if (request.title.isNotBlank()) {
            media.addOption(":meta-title=${request.title}")
        }
        if (!request.artworkUri.isNullOrBlank()) {
            media.addOption(":meta-artworkurl=${request.artworkUri}")
        }

        if (hasRenderer) {
            media.addOption(":sout-chromecast-audio-passthrough=true")
            media.addOption(":sout-chromecast-conversion-quality=2")
        }

        media.applySubtitleStyle(subtitleStyle)
        return media
    }

    private fun Media.applySubtitleStyle(style: SubtitleStyle) {
        val fontColor = style.fontColor.value and 0x00FFFFFF
        val backgroundColor = style.backgroundColor.value and 0x00FFFFFF
        val edgeColor = style.edgeColor.value and 0x00FFFFFF

        addOption(":freetype-rel-fontsize=${style.fontSize}")
        addOption(":freetype-color=$fontColor")
        addOption(":freetype-background-color=$backgroundColor")
        addOption(":freetype-background-opacity=${(style.backgroundOpacity * 255).toInt().coerceIn(0, 255)}")
        addOption(":freetype-outline-color=$edgeColor")

        when (style.edgeType) {
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.NONE -> addOption(":freetype-outline-thickness=0")
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> addOption(":freetype-outline-thickness=2")
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> {
                addOption(":freetype-outline-thickness=0")
                addOption(":freetype-shadow-opacity=255")
            }
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED,
            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> {
                addOption(":freetype-outline-thickness=1")
                addOption(":freetype-shadow-opacity=255")
            }
        }

        val screenHeight = context.resources.displayMetrics.heightPixels
        val marginPixels = (style.verticalPosition * screenHeight).toInt()
        addOption(":sub-margin=$marginPixels")
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

    override fun captureViewBitmap(): Bitmap? = null

    val vlcMediaPlayer: MediaPlayer? get() = mediaPlayer

    override fun setRenderer(renderer: Any?) {
        try {
            val item = renderer as? RendererItem
            mediaPlayer?.setRenderer(item)
            hasRenderer = item != null
            pendingRendererItem = item
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set renderer", e)
        }
    }

    override val currentPositionMs: Long
        get() = try { mediaPlayer?.time ?: 0L } catch (_: Exception) { 0L }

    override val durationMs: Long
        get() = try {
            val len = (mediaPlayer?.length ?: 0L).coerceAtLeast(0L)
            if (len > 0) { cachedDurationMs = len; len }
            else if (hasRenderer && cachedDurationMs > 0) cachedDurationMs
            else 0L
        } catch (_: Exception) { if (hasRenderer && cachedDurationMs > 0) cachedDurationMs else 0L }

    override val playbackSpeed: Float
        get() = try { mediaPlayer?.rate ?: 1f } catch (_: Exception) { 1f }

    override val audioSessionId: Int
        get() = try { mediaPlayer?.audioTrack ?: 0 } catch (_: Exception) { 0 }

    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        var lastPlayingState = _isPlaying.value
        val ticker = engineScope.launch {
            while (isActive) {
                if (!_isPlaying.value) {
                    _isPlaying.first { it }
                }
                delay(_pollingIntervalMs.value)
                runCatching {
                    trySend(currentPositionMs)
                    val currentlyPlaying = _isPlaying.value
                    if (currentlyPlaying || currentlyPlaying != lastPlayingState) {
                        _bufferedPositionMs.value = durationMs.coerceAtLeast(0L).let { dur ->
                            if (dur > 0 && _bufferedPositionMs.value <= currentPositionMs) dur
                            else _bufferedPositionMs.value
                        }
                        if (_videoStatsEnabled.value) {
                            updateVideoStats()
                        }
                    }
                    lastPlayingState = currentlyPlaying
                }
            }
        }
        awaitClose { ticker.cancel() }
    }

    private fun updateVideoStats() {
        val mp = mediaPlayer ?: return
        try {
            val newStats = EngineVideoStats(
                audioCodec = try {
                    val tracks = mp.getAudioTracks()
                    val currentId = mp.audioTrack
                    tracks?.find { it.id == currentId }?.name
                } catch (_: Exception) { null },
                bufferedPositionMs = _bufferedPositionMs.value,
            )
            val currentStats = _videoStats.value
            if (newStats != currentStats) {
                _videoStats.value = newStats
            }
        } catch (_: Exception) {}
    }

    private fun buildTracks(): List<MediaTrack> {
        val mp = mediaPlayer ?: return emptyList()
        val result = mutableListOf<MediaTrack>()
        
        try {
            val audioTracks = mp.getAudioTracks()
            if (audioTracks != null) {
                val currentId = try { mp.audioTrack } catch (_: Exception) { -1 }
                audioTracks.forEachIndexed { index, desc ->
                    result.add(
                        MediaTrack(
                            id = "vlc_audio_${desc.id}",
                            index = index,
                            label = desc.name ?: "Audio ${index + 1}",
                            language = null,
                            isSelected = desc.id == currentId,
                            type = TrackType.AUDIO,
                        )
                    )
                }
            }
            
            val spuTracks = mp.getSpuTracks()
            if (spuTracks != null) {
                val currentId = try { mp.spuTrack } catch (_: Exception) { -1 }
                spuTracks.forEachIndexed { index, desc ->
                    result.add(
                        MediaTrack(
                            id = "vlc_sub_${desc.id}",
                            index = index,
                            label = desc.name ?: "Subtitle ${index + 1}",
                            language = null,
                            isSelected = desc.id == currentId,
                            type = TrackType.SUBTITLE,
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        
        return result
    }

}
