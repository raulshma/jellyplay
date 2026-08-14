package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

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
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.model.parseLanguageFromLabel
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleDefaults
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcPlayerEngine(
    private val context: Context,
    private val fontProvider: FontProvider,
) : BasePlayerEngine() {

    companion object {
        private const val TAG = "LibVlcPlayerEngine"
        // libVLC uses 0 for the weakest slave priority and 4 for the strongest.
        // The tester's external track must win auto-selection immediately.
        private const val EXTERNAL_SUBTITLE_PRIORITY = 4
    }

    private val isLowRamDevice by lazy { EngineDeviceProfile.isLowRamDevice(context) }

    override val capabilities = EngineCapabilityMatrix.LIBVLC
    override val displayName: String = PlayerType.LIBVLC.displayName

    private var libVLC: LibVLC? = null
    val libVlc: LibVLC? get() = libVLC
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var currentPlaybackRequest: PlaybackRequest? = null

    private var pendingPlay = false
    private var wasPlayingBeforeActivityPause = false
    private var hasRenderer = false
    private var pendingRendererItem: RendererItem? = null
    private var cachedDurationMs: Long = 0L

    override fun onActivityPause() {
        wasPlayingBeforeActivityPause = _isPlaying.value
        pause()
        try { mediaPlayer?.detachViews() } catch (_: Exception) {}
    }

    override fun onActivityResume() {
        videoLayout?.let { layout ->
            try {
                mediaPlayer?.attachPreviewViews(layout)
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
                // Apply saved subtitle delay after tracks are initialized.
                // LibVLC resets SPU state during playback startup, so
                // setSpuDelay() called before play() has no effect.
                // VLC Android does the same in PlaylistManager.loadMediaMeta().
                mediaPlayer?.let { applySpuDelay(it) }
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
                _errorFlow.tryEmit(EngineError.Unknown("VLC encountered an error during playback"))
            }
        }
    }

    override fun load(request: PlaybackRequest) {
        recreateEngineScopeIfInactive()
        releaseInternal(releaseVlc = true)

        currentPlaybackRequest = request

        val vlcCfg = (currentConfig.engineSpecific as? LibVlcEngineConfig) ?: LibVlcEngineConfig()

        val options = arrayListOf(
            "--aout=${vlcCfg.audioOutput.key}",
        )

        request.preferredAudioLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            options.add("--audio-language=$language")
        }
        request.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            options.add("--sub-language=$language")
        }

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
            // Low-RAM tradeoff: VLC's clock synchronisation periodically
            // adjusts the playback clock to keep audio/video aligned, which
            // costs CPU on memory-constrained devices. Disabling it here
            // reduces decode contention (avoiding stutter / dropped frames)
            // at the cost of potential long-term A/V drift on long streams.
            // Revisit if drift reports surface for the low-RAM tier; the
            // escape hatch is to lower `decoderThreads`/`skipFrame` instead.
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
            _errorFlow.tryEmit(EngineError.Render(e))
            return
        }
        libVLC = vlc

        val mp = MediaPlayer(vlc)
        mp.setEventListener(eventListener)
        // VLC's VideoHelper otherwise uses the Activity orientation when it
        // calculates the child SurfaceView bounds. A 16:9 preview inside this
        // portrait screen is then treated as portrait and its width/height are
        // swapped, leaving a small centred video. Use the actual view bounds.
        mp.setUseOrientationFromBounds(true)
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
        // Note: subtitle delay (setSpuDelay) is NOT applied here — LibVLC
        // resets SPU state when it initialises tracks during mp.play().
        // The delay is applied in the MediaPlayer.Event.Playing callback
        // after tracks are loaded (matching VLC Android behaviour).

        pendingPlay = true
        
        // Re-attach view if it exists
        videoLayout?.let {
            try {
                mp.attachPreviewViews(it)
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
            val wasEnded = _playbackState.value == EnginePlaybackState.ENDED
            mp.play()
            // After EndReached VLC is in stopped state; setTime() on a
            // stopped player silently fails, so restart playback first then
            // seek to 0. The old order (time=0 then play) re-fired EndReached
            // immediately because the seek was a no-op.
            if (wasEnded) {
                mp.time = 0L
            }
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

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        try {
            val mp = mediaPlayer ?: return

            // Audio delay and subtitle delay apply live via libvlc setters.
            if (oldConfig.audioDelayMs != newConfig.audioDelayMs) {
                mp.setAudioDelay(newConfig.audioDelayMs)
            }
            if (oldConfig.subtitleDelayMs != newConfig.subtitleDelayMs) {
                mp.setSpuDelay(newConfig.subtitleDelayMs * 1000L)
            }

            // Known limitation: channel-mix mode, audio-normalization,
            // decoder mode, audio passthrough, and subtitle style are
            // load-time `--stereo-mode` / `--audio-filter` / `--avcodec` VLC
            // options — libvlc's runtime API surface on Android does not
            // expose setters for these, so toggling them mid-playback forces
            // a reload below (subtitle style does; the rest require the user
            // to back out and re-enter the player). Documented here so future
            // contributors don't assume the toggle is silently dropped.
            //
            // Subtitle delay is excluded from this reload decision: it rides on
            // SubtitleStyle.offsetMs (mirrored into subtitleDelayMs by
            // EngineConfigBuilder), but is applied live via setSpuDelay() above,
            // so a delay-only change must NOT rebuild the media. This mirrors
            // VLC for Android, which calls MediaPlayer.setSpuDelay() at runtime
            // without reloading (see scratch vlc-android: PlayerController/
            // PlaylistManager setSpuDelay — no stop/seek).
            if (styleChangedExcludingDelay(oldConfig.subtitleStyle, newConfig.subtitleStyle) ||
                oldConfig.videoEffects != newConfig.videoEffects
            ) {
                reloadMediaForSubtitleStyleChange()
            }
        } catch (_: Exception) {}
    }

    override fun selectTrack(type: TrackType, index: Int) {
        try {
            val mp = mediaPlayer ?: return
            if (type == TrackType.AUDIO) {
                val tracks = mp.getAudioTracks()?.filter { it.id != -1 } ?: return
                if (index < 0) {
                    if (tracks.isNotEmpty()) {
                        mp.audioTrack = tracks[0].id
                    }
                    return
                }
                if (index in tracks.indices) {
                    mp.audioTrack = tracks[index].id
                }
            } else {
                if (index < 0) {
                    mp.spuTrack = -1
                    return
                }
                val tracks = mp.getSpuTracks()?.filter { it.id != -1 } ?: return
                if (index in tracks.indices) {
                    mp.spuTrack = tracks[index].id
                }
            }
        } catch (_: Exception) {}
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val mp = mediaPlayer ?: return
        try {
            mp.addSlave(
                org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                Uri.parse(source.url),
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
            (mediaPlayer?.volume ?: 100).coerceIn(0, 200) / 100f
        } catch (_: Exception) { 1f }

    override fun setVolume(value: Float) {
        try {
            val clamped = value.coerceIn(0f, 2f)
            val v = (clamped * 100).toInt().coerceIn(0, 200)
            if (v > 0) lastUnmuteVolume = v / 100f
            mediaPlayer?.volume = v
            MediaStreamVolume.setNormalized(context, clamped.coerceIn(0f, 1f))
        } catch (_: Exception) {}
    }

    override fun increaseVolume(delta: Float) {
        try {
            val mp = mediaPlayer ?: return
            val next = (mp.volume + (delta * 100).toInt()).coerceIn(0, 200)
            if (next > 0) lastUnmuteVolume = next / 100f
            mp.volume = next
            MediaStreamVolume.setNormalized(context, (next / 100f).coerceIn(0f, 1f))
        } catch (_: Exception) {}
    }

    override fun decreaseVolume(delta: Float) {
        try {
            val mp = mediaPlayer ?: return
            val next = (mp.volume - (delta * 100).toInt()).coerceIn(0, 200)
            if (next > 0) lastUnmuteVolume = next / 100f
            mp.volume = next
            MediaStreamVolume.setNormalized(context, (next / 100f).coerceIn(0f, 1f))
        } catch (_: Exception) {}
    }

    override fun setMuted(muted: Boolean) {
        // libVLC 3.7.x MediaPlayer has no native mute API — emulate it via volume.
        try {
            val mp = mediaPlayer ?: return
            if (muted) {
                // Snapshot the system STREAM_MUSIC level the user actually hears
                // (set via gesture path / hardware keys, which bypass the engine
                // API). Snapshotting mp.volume is wrong — it stays near 100 when
                // volume was adjusted outside the engine, so unmute would
                // restore full volume.
                val sysVol = MediaStreamVolume.getNormalized(context)
                if (sysVol > 0f) lastUnmuteVolume = sysVol
                mp.volume = 0
                MediaStreamVolume.setNormalized(context, 0f)
            } else {
                // Restore the system stream to its pre-mute level and set VLC's
                // software gain back to unity (100). Do NOT also scale mp.volume
                // by the system level — that would double-attenuate.
                val restored = lastUnmuteVolume.coerceIn(0.05f, 1f)
                mp.volume = 100
                MediaStreamVolume.setNormalized(context, restored)
            }
        } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
        val layout = VLCVideoLayout(context)
        videoLayout = layout

        layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val mp = mediaPlayer ?: return
                try {
                    mp.attachPreviewViews(layout)
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

    /**
     * Attaches LibVLC after the Compose-hosted surface has entered the view tree.
     * The delayed surface update is necessary because VLC's initial layout event
     * can arrive before the 16:9 preview receives its final height.
     */
    private fun MediaPlayer.attachPreviewViews(layout: VLCVideoLayout) {
        // The third argument makes VideoHelper inflate and bind its transparent
        // subtitle SurfaceView as well as the video SurfaceView.
        attachViews(layout, null, true, false)
        layout.post {
            if (videoLayout === layout) updateVideoSurfaces()
        }
    }

    override fun applySubtitleStyle(style: SubtitleStyle) {
        // Always keep currentConfig in sync with the incoming style so the
        // engine's snapshot matches the ViewModel's, but only rebuild the
        // Media object for genuine visual-style changes (font/color/margins).
        // Delay-only changes are applied live via setSpuDelay() in
        // onConfigChanged — they must NOT trigger a reload here.
        if (currentConfig.subtitleStyle == style) return
        val needsReload = styleChangedExcludingDelay(currentConfig.subtitleStyle, style)
        currentConfig = currentConfig.copy(subtitleStyle = style)
        if (needsReload) {
            reloadMediaForSubtitleStyleChange()
        }
    }

    /**
     * Re-asserts the configured subtitle delay on [mp]. Playback startup and
     * media rebuilds reset LibVLC's SPU state, so every site that starts play
     * or reassigns the Media must call this afterwards. No-op while the saved
     * delay is zero; the live route for user edits (delay != old delay) is
     * [onConfigChanged]'s direct `setSpuDelay`, which must also propagate 0.
     */
    private fun applySpuDelay(mp: MediaPlayer) {
        if (currentConfig.subtitleDelayMs == 0L) return
        try { mp.setSpuDelay(currentConfig.subtitleDelayMs * 1000L) } catch (_: Exception) {}
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
            // Re-assert the subtitle delay after rebuilding the Media: the spu
            // delay is a player-level setting that may not survive the media
            // reassignment, so a saved correction would otherwise reset to zero
            // on a genuine style/font change. Delay-only changes never reach
            // this path (onConfigChanged routes them through setSpuDelay live).
            applySpuDelay(mp)
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

        // Apply video effects options
        val effects = currentConfig.videoEffects
        val hasAdjust = effects.brightness != 0f || effects.contrast != 1f || effects.saturation != 1f || effects.hue != 0f
        val filters = mutableListOf<String>()
        if (hasAdjust) {
            filters.add("adjust")
        }
        if (effects.rotationDegrees != 0f) {
            val rawDiscrete = kotlin.math.round(effects.rotationDegrees / 90f).toInt() * 90
            val discrete = ((rawDiscrete % 360) + 360) % 360
            if (discrete != 0) {
                filters.add("transform")
                media.addOption(":transform-type=$discrete")
            }
        }
        if (filters.isNotEmpty()) {
            media.addOption(":video-filter=${filters.joinToString(":")}")
        }
        if (hasAdjust) {
            val brightnessVal = (1.0f + effects.brightness).coerceIn(0.0f, 2.0f)
            val contrastVal = effects.contrast.coerceIn(0.0f, 2.0f)
            val saturationVal = effects.saturation.coerceIn(0.0f, 3.0f)
            val hueVal = effects.hue.toInt().coerceIn(0, 360)
            media.addOption(":brightness=$brightnessVal")
            media.addOption(":contrast=$contrastVal")
            media.addOption(":saturation=$saturationVal")
            media.addOption(":hue=$hueVal")
        }

        if (isLowRamDevice) {
            // Mirrors the load() options above; see the low-RAM comment there.
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
                    EXTERNAL_SUBTITLE_PRIORITY,
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
        // All `:freetype-*` color/size/opacity options come from the tested
        // pure mapping — this is the single source, so the shipped path is the
        // tested path (no inline shadow). Branching on applyCustomStyle lives
        // inside freetypeOptions; the dispatcher picks custom vs default.
        LibVlcSubtitleStyleMapping.freetypeOptions(style).forEach(::addOption)

        // The freetype module accepts a font file path. Supplying the same
        // bundled fallback used by the other engines makes its synthetic
        // bold/italic variants deterministic instead of depending on the
        // device's generic sans-serif family.
        LibVlcSubtitleStyleMapping
            .typefaceOptions(style, fontProvider.bundledFallbackPath())
            .forEach(::addOption)

        // Vertical margin in pixels from the bottom of the frame. subMarginPixels
        // clamps verticalPosition to [0, 0.4] — the inline copy previously used
        // an unclamped raw fraction, which could push captions off-screen.
        val screenHeight = context.resources.displayMetrics.heightPixels
        addOption(LibVlcSubtitleStyleMapping.subMarginPixels(style, screenHeight))
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        try {
            val mp = mediaPlayer ?: return
            val aspectValue = ratio.ratio
            when {
                aspectValue != null && aspectValue > 0f -> {
                    mp.aspectRatio = aspectValue.toString()
                }
                else -> {
                    // FIT / FILL / CROP / AUTO — let libVLC keep the native frame.
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
            }
        } catch (_: Exception) {}
    }

    val vlcMediaPlayer: MediaPlayer? get() = mediaPlayer

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
        // LibVLC 3.7.x MediaPlayer exposes no audio-session-id API (only
        // audio *track* ids). Returning the track id here previously made
        // Android AudioEffect consumers attach to a non-existent session.
        // Return the unset sentinel (0 == C.AUDIO_SESSION_ID_UNSET) so the
        // helpers' `if (sid == UNSET) return` guard short-circuits cleanly.
        get() = 0

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        // The polling loop (bounded paused-wait, play↔pause edge detection) is
        // shared via [EnginePositionTicker]. Per-tick readbacks are wrapped in
        // runCatching because currentPositionMs/durationMs touch the native
        // mediaPlayer, which can throw if it is torn down concurrently.
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = _isPlaying,
            isCurrentlyPlaying = { _isPlaying.value },
            onActive = {
                runCatching {
                    trySend(currentPositionMs)
                    _bufferedPositionMs.value = durationMs.coerceAtLeast(0L).let { dur ->
                        if (dur > 0 && _bufferedPositionMs.value <= currentPositionMs) dur
                        else _bufferedPositionMs.value
                    }
                    if (_videoStatsEnabled.value) {
                        updateVideoStats()
                    }
                }
            },
        ).launch()
        awaitClose { ticker.cancel() }
    }.conflate() // only the most-recent position is meaningful; drop stale ticks

    private fun updateVideoStats() {
        val mp = mediaPlayer ?: return
        try {
            val videoTrack = try { mp.currentVideoTrack } catch (_: Exception) { null }
            val newStats = EngineVideoStats(
                videoResolution = videoTrack?.let { vt ->
                    val w = vt.width
                    val h = vt.height
                    if (w > 0 && h > 0) "${w}x${h}" else null
                },
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
            val audioTracks = mp.getAudioTracks()?.filter { it.id != -1 }
            if (audioTracks != null) {
                val currentId = try { mp.audioTrack } catch (_: Exception) { -1 }
                audioTracks.forEachIndexed { index, desc ->
                    val info = TrackLabelInfo(title = desc.name)
                    result.add(
                        MediaTrack(
                            id = "vlc_audio_${desc.id}",
                            index = index,
                            label = TrackLabelFormatter.primary(info).ifBlank { "Audio ${index + 1}" },
                            language = parseLanguageFromLabel(desc.name),
                            isSelected = desc.id == currentId,
                            type = TrackType.AUDIO,
                            badges = TrackLabelFormatter.badges(info),
                        )
                    )
                }
            }
            
            val spuTracks = mp.getSpuTracks()?.filter { it.id != -1 }
            if (spuTracks != null) {
                val currentId = try { mp.spuTrack } catch (_: Exception) { -1 }
                spuTracks.forEachIndexed { index, desc ->
                    val info = TrackLabelInfo(title = desc.name)
                    result.add(
                        MediaTrack(
                            id = "vlc_sub_${desc.id}",
                            index = index,
                            label = TrackLabelFormatter.primary(info).ifBlank { "Subtitle ${index + 1}" },
                            language = parseLanguageFromLabel(desc.name),
                            isSelected = desc.id == currentId,
                            type = TrackType.SUBTITLE,
                            badges = TrackLabelFormatter.badges(info),
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        
        return result
    }

}
