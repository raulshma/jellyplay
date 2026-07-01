package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.raulshma.jellyplay.core.data.playback.AudioNormalizationHelper
import com.raulshma.jellyplay.core.data.playback.BassBoostHelper
import com.raulshma.jellyplay.core.data.playback.ChannelMixHelper
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.ReverbHelper
import com.raulshma.jellyplay.core.data.playback.VirtualizerHelper
import com.raulshma.jellyplay.core.data.playback.LoudnessEnhancerHelper
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleMimeMapper
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// Max time the position-polling loop will wait for playback to resume before
// re-checking config (M2). Prevents the ticker from suspending forever while
// paused.
private const val POSITION_PAUSED_RECHECK_MS = 2_500L

class ExoPlayerEngine(
    private val context: Context,
    bandwidthMeter: DefaultBandwidthMeter? = null,
) : MediaEngine {

    private var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedVolume: Float = 1f

    @Volatile
    private var lastUnmuteVolume: Float = 1f

    @Volatile
    private var lastAppliedAudioSessionId: Int = -1

    private inline fun runOnPlayerThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    override val capabilities = EngineCapabilityMatrix.EXO_PLAYER

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

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var currentMediaItem: MediaItem? = null
    private val bandwidthMeter = bandwidthMeter ?: DefaultBandwidthMeter.Builder(context).build()
    private val currentSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

    override val underlyingPlayer: androidx.media3.common.Player? get() = player
    
    private var currentConfig = EngineConfig()

    private val equalizerHelper = EqualizerHelper()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper)
    private val nightMode = NightModeHelper()
    private val audioNormalizationHelper = AudioNormalizationHelper()
    private val channelMixHelper = ChannelMixHelper()
    private val bassBoostHelper = BassBoostHelper()
    private val virtualizerHelper = VirtualizerHelper()
    private val reverbHelper = ReverbHelper()
    private val loudnessEnhancerHelper = LoudnessEnhancerHelper()

    private var lastVideoStats: EngineVideoStats? = null

    /**
     * Live decoder counters captured from the video renderer's
     * [AnalyticsListener.onVideoEnabled] callback. Held by reference so
     * [updateVideoStats] can read the renderer's running dropped/rendered
     * frame tallies (after [DecoderCounters.ensureUpdated]) — without this,
     * the "Stats for Nerds" dropped-frame and total-frame rows are stuck at
     * 0 because ExoPlayer doesn't surface them through the plain
     * [Player.Listener] API.
     */
    @Volatile
    private var videoDecoderCounters: DecoderCounters? = null
    private var audioEffectsAttached = false
    private var lastAudioEffectsConfig: AudioEffectsConfig? = null
    private var lastAppliedReverbPreset: com.raulshma.jellyplay.core.model.ReverbPreset? = null

    private val _pollingIntervalMs = MutableStateFlow(1000L)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()
    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = when (state) {
                Player.STATE_IDLE -> EnginePlaybackState.IDLE
                Player.STATE_BUFFERING -> EnginePlaybackState.BUFFERING
                Player.STATE_READY -> EnginePlaybackState.READY
                Player.STATE_ENDED -> EnginePlaybackState.ENDED
                else -> EnginePlaybackState.IDLE
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            _availableTracks.value = buildTracks()
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = EnginePlaybackState.ERROR
            _errorFlow.tryEmit(error.message ?: "Unknown playback error")
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            _currentCues.value = cueGroup.cues.mapNotNull { it.text?.toString() }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != lastAppliedAudioSessionId) {
                lastAppliedAudioSessionId = audioSessionId
                // The audio session id changed mid-playback (e.g. track
                // switch). AudioEffect handles are bound to the *old*
                // session id, which is now dead. Detach everything so
                // applyAudioEffects() re-binds to the new session instead
                // of short-circuiting on the cached config.
                if (audioEffectsAttached) {
                    releaseAudioEffects()
                }
                applyAudioEffects()
            }
        }

        override fun onVolumeChanged(volume: Float) {
            cachedVolume = volume
        }
    }

    /**
     * Captures the video renderer's [DecoderCounters] the moment the video
     * stream is enabled, so [updateVideoStats] can read live dropped/rendered
     * frame counts. Reset on disable/release.
     */
    private val decoderCountersListener = object : AnalyticsListener {
        override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
            videoDecoderCounters = decoderCounters
        }

        override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
            videoDecoderCounters = null
        }
    }

    override fun load(request: PlaybackRequest) {
        release()
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val exoCfg = (currentConfig.engineSpecific as? ExoPlayerEngineConfig) ?: ExoPlayerEngineConfig()

        val selector = DefaultTrackSelector(context)
        if (request.preferredAudioLanguage != null) {
            selector.setParameters(
                selector.buildUponParameters().setPreferredAudioLanguage(request.preferredAudioLanguage)
            )
        }
        if (request.preferredSubtitleLanguage != null) {
            selector.setParameters(
                selector.buildUponParameters().setPreferredTextLanguage(request.preferredSubtitleLanguage)
            )
        }
        if (request.maxVideoBitrate != null) {
            selector.setParameters(
                selector.buildUponParameters().setMaxVideoBitrate(request.maxVideoBitrate)
            )
        }
        if (exoCfg.preferredVideoMimeTypes.isNotEmpty()) {
            selector.setParameters(
                selector.buildUponParameters().setPreferredVideoMimeTypes(*exoCfg.preferredVideoMimeTypes.toTypedArray())
            )
        }
        if (exoCfg.audioOffloadMode != com.raulshma.jellyplay.core.model.ExoAudioOffloadMode.DISABLED) {
            // Media3 surfaces audio offload through the track selector, not the
            // ExoPlayer.Builder. Map the pref onto AudioOffloadPreferences and
            // push it into the parameters so the selector prefers offload-decodable
            // tracks when the user has enabled (or required) the mode.
            selector.setParameters(
                selector.buildUponParameters().setAudioOffloadPreferences(
                    androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(exoCfg.audioOffloadMode.value)
                        .build(),
                ),
            )
        }
        trackSelector = selector

        val rendererMode = when (currentConfig.decoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)
            .setEnableDecoderFallback(exoCfg.enableDecoderFallback)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(request.minBufferMs, request.maxBufferMs, 1_000, 3_000)
            .setTargetBufferBytes(-1)
            .setBackBuffer(exoCfg.backBufferDurationMs.coerceAtLeast(0), false)
            .build()

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                    offsetUsProvider = { currentConfig.subtitleDelayMs * 1000L }
                )
            )
        }

        val dataSourceFactory = createAuthenticatedDataSourceFactory(request.serverUrl, request.authToken, request.headers)
        val msf = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // DRM: attach a DrmSessionManager only when the caller supplied one via
        // EngineConfig.drmSessionManagerProvider. This is the single extension
        // point for content protection — the engine never hard-codes Widevine
        // or any scheme, so it stays testable without a DRM framework. A `null`
        // manager (clear content) leaves Media3's default no-DRM path in place.
        currentConfig.drmSessionManagerProvider?.provide()?.let { drmManager ->
            msf.setDrmSessionManagerProvider { drmManager }
        }

        val audioAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(msf)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, currentConfig.pauseOnAudioFocusLoss)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setBandwidthMeter(bandwidthMeter)
            .setVideoScalingMode(exoCfg.videoScalingMode.value)
            .setVideoChangeFrameRateStrategy(exoCfg.frameRateStrategy.value)
            .setSkipSilenceEnabled(exoCfg.skipSilence)
            .build()

        exo.addListener(listener)
        exo.addAnalyticsListener(decoderCountersListener)
        player = exo
        
        // Build media item
        val metadataBuilder = MediaMetadata.Builder().setTitle(request.title)
        if (request.artworkUri != null) {
            metadataBuilder.setArtworkUri(Uri.parse(request.artworkUri))
        }

        val subtitleConfigs = request.externalSubtitles.mapNotNull { sub ->
            val mimeType = sub.mimeType ?: SubtitleMimeMapper.mapCodecToMime(sub.codec ?: sub.label) ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setId(sub.id)
                .setMimeType(mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(
                    (if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0) or
                    (if (sub.isForced) C.SELECTION_FLAG_FORCED else 0)
                )
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(request.uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        exo.setMediaItem(mediaItem)
        currentMediaItem = mediaItem
        currentSubtitleConfigs.clear()
        currentSubtitleConfigs.addAll(subtitleConfigs)
        exo.prepare()
        if (request.startPositionMs > 0) {
            exo.seekTo(request.startPositionMs)
        }
        exo.play()
        
        applyAudioEffects()
    }

    private fun createAuthenticatedDataSourceFactory(
        serverUrl: String?, 
        token: String?, 
        headers: Map<String, String>
    ): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("JellyPlay")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
            
        val baseFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val authority = serverUrl?.let { Uri.parse(it).authority }
        if (authority != null && token != null) {
            return ResolvingDataSource.Factory(baseFactory) { dataSpec ->
                if (dataSpec.uri.authority.equals(authority, ignoreCase = true)) {
                    dataSpec.withRequestHeaders(
                        mapOf("X-Emby-Token" to token) + dataSpec.httpRequestHeaders
                    )
                } else {
                    dataSpec
                }
            }
        }
        return baseFactory
    }

    override fun release() {
        engineScope.cancel()
        player?.removeListener(listener)
        player?.removeAnalyticsListener(decoderCountersListener)
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        trackSelector = null
        currentMediaItem = null
        currentSubtitleConfigs.clear()
        lastVideoStats = null
        videoDecoderCounters = null
        releaseAudioEffects()
        cachedVolume = 1f
        lastUnmuteVolume = 1f
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
    }

    override fun play() = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
        }
        p.play()
    }
    override fun pause() = runOnPlayerThread { player?.pause() }
    override fun stop() = runOnPlayerThread { player?.stop() }
    override fun seekTo(positionMs: Long) = runOnPlayerThread { player?.seekTo(positionMs) }
    override fun setPlaybackSpeed(speed: Float) = runOnPlayerThread { player?.setPlaybackSpeed(speed) }

    override val volume: Float get() = cachedVolume

    override fun setVolume(value: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val clamped = value.coerceIn(0f, 1f)
        if (clamped > 0f) lastUnmuteVolume = clamped
        p.volume = clamped
        MediaStreamVolume.setNormalized(context, clamped)
    }

    override fun increaseVolume(delta: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val next = (p.volume + delta).coerceAtMost(1f)
        if (next > 0f) lastUnmuteVolume = next
        p.volume = next
        MediaStreamVolume.setNormalized(context, next)
    }

    override fun decreaseVolume(delta: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val next = (p.volume - delta).coerceAtLeast(0f)
        if (next > 0f) lastUnmuteVolume = next
        p.volume = next
        MediaStreamVolume.setNormalized(context, next)
    }

    override fun setMuted(muted: Boolean) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val target = if (muted) 0f else lastUnmuteVolume.coerceIn(0.05f, 1f)
        p.volume = target
        MediaStreamVolume.setNormalized(context, target)
    }

    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        val oldConfig = currentConfig
        currentConfig = config

        // decoderMode change: decoding changes require a reload, which is
        // handled by the upper layer recreating the player — nothing to do here.
        //
        // subtitleDelayMs change (M17): the OffsettingSubtitleParserFactory's
        // wrapper reads currentConfig.subtitleDelayMs on each parse() call, so
        // a delay adjustment takes effect for subsequent cues without a media
        // reload. (Previously the offset was snapshotted at prepare() time and
        // the delay slider appeared broken for side-loaded subtitles.)

        if (oldConfig.audioEffects != config.audioEffects) {
            applyAudioEffects()
        }

        if (oldConfig.subtitleStyle != config.subtitleStyle) {
            playerView?.let { pv -> applySubtitleStyleToView(pv, config.subtitleStyle) }
        }

        if (oldConfig.pauseOnAudioFocusLoss != config.pauseOnAudioFocusLoss) {
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player?.setAudioAttributes(audioAttrs, config.pauseOnAudioFocusLoss)
        }
    }

    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?) = runOnPlayerThread {
        val selector = trackSelector ?: return@runOnPlayerThread
        val p = player ?: return@runOnPlayerThread
        val params = selector.buildUponParameters()
        val exoType = if (type == TrackType.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

        if (index < 0) {
            if (type == TrackType.SUBTITLE) {
                params.setTrackTypeDisabled(exoType, true)
            }
            params.clearOverridesOfType(exoType)
        } else {
            if (type == TrackType.SUBTITLE) {
                params.setTrackTypeDisabled(exoType, false)
            }
            val group = trackGroup as? TrackGroup
            if (group != null) {
                 params.setOverrideForType(
                    TrackSelectionOverride(group, (0 until group.length).toList())
                )
            } else {
                val groups = p.currentTracks.groups.filter { it.type == exoType }
                if (groups.isEmpty()) {
                    selector.setParameters(params)
                    return@runOnPlayerThread
                }
                val groupIndex = index.coerceIn(groups.indices)
                if (groupIndex in groups.indices) {
                    val fallbackGroup = groups[groupIndex].mediaTrackGroup
                    params.setOverrideForType(
                        TrackSelectionOverride(fallbackGroup, (0 until fallbackGroup.length).toList())
                    )
                }
            }
        }
        selector.setParameters(params)
    }

    override fun setMaxVideoBitrate(bps: Int?) = runOnPlayerThread {
        val selector = trackSelector ?: return@runOnPlayerThread
        val params = selector.buildUponParameters()
        if (bps != null) {
            params.setMaxVideoBitrate(bps)
        } else {
            params.setMaxVideoBitrate(Int.MAX_VALUE)
        }
        selector.setParameters(params)
    }

    override fun createSurfaceView(context: Context): View {
        val pv = PlayerView(context).apply {
            this.player = this@ExoPlayerEngine.player
            useController = false
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        pv.post {
            val subtitleView = pv.subtitleView ?: return@post
            val parent = subtitleView.parent as? android.view.ViewGroup ?: return@post
            if (parent !== pv) {
                parent.removeView(subtitleView)
                pv.addView(
                    subtitleView,
                    android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }
        playerView = pv
        applySubtitleStyleToView(pv, currentConfig.subtitleStyle)
        return pv
    }

    override fun applySubtitleStyleToView(view: View, style: SubtitleStyle) {
        val pv = (view as? PlayerView) ?: playerView ?: return
        val bgAlpha = (style.backgroundOpacity * 255).toInt()
        val bgColorWithAlpha = (bgAlpha shl 24) or (style.backgroundColor.value and 0x00FFFFFF)
        pv.subtitleView?.let { sv ->
            if (style.applyCustomStyle) {
                sv.setApplyEmbeddedStyles(false)
                sv.setStyle(
                    CaptionStyleCompat(
                        style.fontColor.value,
                        bgColorWithAlpha,
                        Color.TRANSPARENT,
                        when (style.edgeType) {
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                            else -> CaptionStyleCompat.EDGE_TYPE_NONE
                        },
                        style.edgeColor.value,
                        null,
                    )
                )
                sv.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, style.fontSize.toFloat())
            } else {
                sv.setApplyEmbeddedStyles(true)
                sv.setStyle(
                    CaptionStyleCompat(
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        Color.BLACK,
                        android.graphics.Typeface.SANS_SERIF
                    )
                )
                sv.setFractionalTextSize(0.0533f, false)
            }
            sv.setBottomPaddingFraction(style.verticalPosition)
        }
    }

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        playerView?.setResizeMode(mode)
        if (ratio != null && ratio > 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(ratio)
        } else if (ratio == null || ratio == 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(0f)
        }
    }

    override val currentPositionMs: Long get() = player?.currentPosition ?: 0L
    override val durationMs: Long get() = player?.duration?.coerceAtLeast(0L) ?: 0L
    override val playbackSpeed: Float get() = player?.playbackParameters?.speed ?: 1f
    override val audioSessionId: Int get() = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    override val positionFlow: Flow<Long> = callbackFlow {
        val p = player ?: run { close(); return@callbackFlow }
        val posListener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                runCatching { trySend(p.currentPosition) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                runCatching { trySend(p.currentPosition) }
            }
        }
        p.addListener(posListener)
        trySend(p.currentPosition)

        var lastPlayingState = p.isPlaying
        val ticker = engineScope.launch {
            while (isActive) {
                if (!p.isPlaying) {
                    // Bounded wait (M2): a previous `_isPlaying.first { it }`
                    // suspended forever while paused, so polling-interval and
                    // video-stats-config changes were ignored until playback
                    // resumed and buffer/stats froze. Re-check every few
                    // seconds so config is honoured even while paused.
                    withTimeoutOrNull(POSITION_PAUSED_RECHECK_MS) {
                        _isPlaying.first { it }
                    }
                }
                delay(_pollingIntervalMs.value)
                runCatching {
                    val currentlyPlaying = p.isPlaying
                    if (currentlyPlaying) {
                        trySend(p.currentPosition)
                        _bufferedPositionMs.value = p.bufferedPosition.coerceAtLeast(0L)
                        if (_videoStatsEnabled.value) {
                            updateVideoStats()
                        }
                    } else if (currentlyPlaying != lastPlayingState) {
                        trySend(p.currentPosition)
                        _bufferedPositionMs.value = p.bufferedPosition.coerceAtLeast(0L)
                    }
                    lastPlayingState = currentlyPlaying
                }
            }
        }

        awaitClose {
            ticker.cancel()
            try { p.removeListener(posListener) } catch (_: Exception) {}
        }
    }

    private fun updateVideoStats() {
        val p = player ?: return
        val bufferedPos = p.bufferedPosition.coerceAtLeast(0L)
        val bandwidthEstimate = bandwidthMeter.bitrateEstimate

        val counters = videoDecoderCounters
        counters?.ensureUpdated()
        val dropped = (counters?.droppedInputBufferCount ?: 0)
            .coerceAtLeast(counters?.droppedToKeyframeCount ?: 0)
            .toLong()
        val rendered = (counters?.renderedOutputBufferCount ?: 0).toLong()

        val last = lastVideoStats
        if (last != null && last.bufferedPositionMs == bufferedPos &&
            last.estimatedBandwidthBps == bandwidthEstimate &&
            last.droppedFrames == dropped && last.totalVideoFrames == rendered
        ) {
            return
        }

        val videoFormat = p.videoFormat
        val audioFormat = p.audioFormat

        val combinedBitrate = (videoFormat?.bitrate ?: 0) + (audioFormat?.bitrate ?: 0)
        val bufferHealthMs = (bufferedPos - p.currentPosition).coerceAtLeast(0L)
        // Approximate buffered bytes from buffer health and the active stream
        // bitrate (bytes = bits/8 * seconds). Falls back to 0 when the formats
        // don't expose a bitrate.
        val bufferSizeBytes = if (combinedBitrate > 0) combinedBitrate * bufferHealthMs / 8000 else 0L

        val newStats = EngineVideoStats(
            videoCodec = videoFormat?.sampleMimeType?.let { codecFromMime(it) },
            videoDecoder = videoFormat?.codecs,
            videoResolution = videoFormat?.let { f ->
                val w = f.width
                val h = f.height
                if (w > 0 && h > 0) "${w}x${h}" else null
            },
            videoFrameRate = videoFormat?.frameRate?.let { if (it > 0f) it else null },
            videoBitrate = videoFormat?.bitrate?.let { if (it > 0) it else null },
            videoColorRange = videoFormat?.colorInfo?.let { ci ->
                when (ci.colorRange) {
                    androidx.media3.common.C.COLOR_RANGE_LIMITED -> "Limited"
                    androidx.media3.common.C.COLOR_RANGE_FULL -> "Full"
                    else -> null
                }
            },
            videoHdrType = videoFormat?.colorInfo?.let { ci ->
                when {
                    ci.hdrStaticInfo != null -> "HDR10"
                    ci.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
                    ci.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "Dolby Vision"
                    ci.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_SDR -> null
                    else -> null
                }
            },
            videoColorDepth = videoFormat?.colorInfo?.let { ci ->
                val depth = ci.lumaBitdepth
                if (depth > 0 && depth != androidx.media3.common.Format.NO_VALUE) "$depth-bit" else null
            },
            audioCodec = audioFormat?.sampleMimeType?.let { codecFromMime(it) },
            audioSampleRate = audioFormat?.sampleRate?.let { if (it > 0) it else null },
            audioChannels = audioFormat?.channelCount?.let { if (it > 0) it else null },
            audioBitrate = audioFormat?.bitrate?.let { if (it > 0) it else null },
            estimatedBandwidthBps = bandwidthEstimate,
            droppedFrames = dropped,
            totalVideoFrames = rendered,
            bufferedPositionMs = bufferedPos,
            bufferSizeBytes = bufferSizeBytes,
        )

        val currentStats = lastVideoStats
        if (newStats != currentStats) {
            lastVideoStats = newStats
            _videoStats.value = newStats
        }
    }

    private fun codecFromMime(mime: String): String = when {
        mime.startsWith("video/") -> mime.removePrefix("video/")
        mime.startsWith("audio/") -> mime.removePrefix("audio/")
        else -> mime
    }

    private fun applyAudioEffects() {
        val sid = audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        
        val config = currentConfig.audioEffects
        if (lastAudioEffectsConfig == config && audioEffectsAttached) return
        
        if (!audioEffectsAttached) {
            dialogueBoost.attach(sid)
            nightMode.attach(sid)
            equalizerHelper.attach(sid)
            audioNormalizationHelper.attach(sid)
            channelMixHelper.attach(sid)
            bassBoostHelper.attach(sid)
            virtualizerHelper.attach(sid)
            reverbHelper.attach(sid)
            loudnessEnhancerHelper.attach(sid)
            audioEffectsAttached = true
        }
        
        dialogueBoost.setStrength(config.dialogueBoostStrength)
        dialogueBoost.setEnabled(config.dialogueBoostEnabled)
        
        nightMode.setStrength(config.nightModeStrength)
        nightMode.setEnabled(config.nightModeEnabled)
        
        // equalizerHelper owns the single priority-0 Equalizer for this
        // session and DialogueBoostHelper overlays its vocal-band gains
        // on top via setBandOffsets (see EqualizerHelper kdoc). The
        // underlying effect must stay enabled while EITHER is on; if
        // only the user's EQ is off but boost is on, disabling here
        // would silently kill the boost overlay.
        equalizerHelper.setSettings(config.equalizerSettings)
        equalizerHelper.setEnabled(config.equalizerEnabled || config.dialogueBoostEnabled)

        audioNormalizationHelper.setMode(config.audioNormalizationMode)
        audioNormalizationHelper.setEnabled(config.audioNormalizationEnabled)

        channelMixHelper.setMode(config.channelMixMode)
        channelMixHelper.setEnabled(config.channelMixEnabled)

        bassBoostHelper.setStrength(config.bassBoostStrength)
        bassBoostHelper.setEnabled(config.bassBoostEnabled)

        virtualizerHelper.setStrength(config.virtualizerStrength)
        virtualizerHelper.setEnabled(config.virtualizerEnabled)

        loudnessEnhancerHelper.setGain(config.volumeBoostGain)
        loudnessEnhancerHelper.setEnabled(config.volumeBoostEnabled)

        if (config.reverbPreset != com.raulshma.jellyplay.core.model.ReverbPreset.NONE) {
            if (lastAppliedReverbPreset != config.reverbPreset) {
                reverbHelper.detach()
                reverbHelper.attach(sid)
            }
            reverbHelper.setPreset(config.reverbPreset)
        } else {
            reverbHelper.setEnabled(false)
        }
        lastAppliedReverbPreset = config.reverbPreset
        
        lastAudioEffectsConfig = config
    }

    private fun releaseAudioEffects() {
        dialogueBoost.detach()
        nightMode.detach()
        equalizerHelper.detach()
        audioNormalizationHelper.detach()
        channelMixHelper.detach()
        bassBoostHelper.detach()
        virtualizerHelper.detach()
        reverbHelper.detach()
        loudnessEnhancerHelper.detach()
        audioEffectsAttached = false
        lastAudioEffectsConfig = null
        lastAppliedReverbPreset = null
    }

    private fun buildTracks(): List<MediaTrack> {
        val p = player ?: return emptyList()
        val tracks = p.currentTracks
        val result = mutableListOf<MediaTrack>()
        
        fun processType(exoType: Int, trackType: TrackType) {
            val groupCount = tracks.groups.size
            var groupIndex = 0
            for (i in 0 until groupCount) {
                val group = tracks.groups[i]
                if (group.type != exoType) continue
                val isSelected = (0 until group.length).any { group.isTrackSelected(it) }
                val format = group.getTrackFormat(0)
                result.add(
                    MediaTrack(
                        id = "${trackType.name}_${groupIndex}",
                        index = groupIndex,
                        label = buildTrackLabel(format),
                        language = format.language,
                        isSelected = isSelected,
                        type = trackType,
                        trackGroup = group.mediaTrackGroup,
                    )
                )
                groupIndex++
            }
        }
        
        processType(C.TRACK_TYPE_AUDIO, TrackType.AUDIO)
        processType(C.TRACK_TYPE_TEXT, TrackType.SUBTITLE)
        
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

    override fun addExternalSubtitle(source: SubtitleSource) = runOnPlayerThread {
        val exo = player ?: return@runOnPlayerThread
        val item = currentMediaItem ?: return@runOnPlayerThread
        val mimeType = source.mimeType ?: SubtitleMimeMapper.mapCodecToMime(source.codec ?: source.label) ?: return@runOnPlayerThread

        val newSubConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(source.url))
            .setId(source.id)
            .setMimeType(mimeType)
            .setLanguage(source.language)
            .setLabel(source.label)
            .setSelectionFlags(
                (if (source.isDefault) C.SELECTION_FLAG_DEFAULT else 0) or
                (if (source.isForced) C.SELECTION_FLAG_FORCED else 0)
            )
            .build()

        currentSubtitleConfigs.add(newSubConfig)

        val currentPos = exo.currentPosition
        val wasPlaying = exo.isPlaying

        val newItem = item.buildUpon()
            .setSubtitleConfigurations(currentSubtitleConfigs.toList())
            .build()
        currentMediaItem = newItem

        exo.setMediaItem(newItem, currentPos)
        exo.prepare()
        if (wasPlaying) exo.play()
    }

}
