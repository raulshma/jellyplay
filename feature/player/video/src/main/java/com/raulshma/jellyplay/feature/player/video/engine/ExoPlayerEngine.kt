package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
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
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.ReverbHelper
import com.raulshma.jellyplay.core.data.playback.VirtualizerHelper
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
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

class ExoPlayerEngine(
    private val context: Context,
) : MediaEngine {

    private var engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val capabilities = EngineCapabilities(
        supportsPip = true,
        supportsMiniMode = true,
        supportsOcr = true,
        supportsCues = true,
        supportsAudioDelay = false,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = false,
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

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var currentMediaItem: MediaItem? = null
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private val currentSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

    override val underlyingPlayer: androidx.media3.common.Player? get() = player
    
    private var currentConfig = EngineConfig()

    private val dialogueBoost = DialogueBoostHelper()
    private val nightMode = NightModeHelper()
    private val equalizerHelper = EqualizerHelper()
    private val audioNormalizationHelper = AudioNormalizationHelper()
    private val channelMixHelper = ChannelMixHelper()
    private val bassBoostHelper = BassBoostHelper()
    private val virtualizerHelper = VirtualizerHelper()
    private val reverbHelper = ReverbHelper()

    private var lastVideoStats: EngineVideoStats? = null
    private var audioEffectsAttached = false
    private var lastAudioEffectsConfig: AudioEffectsConfig? = null

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
            applyAudioEffects()
        }
    }

    override fun load(request: PlaybackRequest) {
        release()
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        trackSelector = selector

        val rendererMode = when (currentConfig.decoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(request.minBufferMs, request.maxBufferMs, 1_000, 3_000)
            .setTargetBufferBytes(-1)
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

        val audioAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(msf)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setBandwidthMeter(bandwidthMeter)
            .build()

        exo.addListener(listener)
        player = exo
        
        // Build media item
        val metadataBuilder = MediaMetadata.Builder().setTitle(request.title)
        if (request.artworkUri != null) {
            metadataBuilder.setArtworkUri(Uri.parse(request.artworkUri))
        }

        val subtitleConfigs = request.externalSubtitles.mapNotNull { sub ->
            val mimeType = sub.mimeType ?: mapSubtitleCodecToMime(sub.codec ?: sub.label) ?: return@mapNotNull null
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
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        trackSelector = null
        currentMediaItem = null
        currentSubtitleConfigs.clear()
        lastVideoStats = null
        releaseAudioEffects()
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
    }

    override fun play() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
        }
        p.play()
    }
    override fun pause() { player?.pause() }
    override fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }
    override fun setPlaybackSpeed(speed: Float) { player?.setPlaybackSpeed(speed) }

    override fun updateConfig(config: EngineConfig) {
        val oldConfig = currentConfig
        currentConfig = config

        if (oldConfig.decoderMode != config.decoderMode) {
            // Decoding changes require reload, usually handled by upper layer recreating player
        }

        if (oldConfig.subtitleDelayMs != config.subtitleDelayMs) {
            val p = player
            if (p != null) {
                val currentPosition = p.currentPosition
                val currentMediaItem = p.currentMediaItem
                if (currentMediaItem != null) {
                    val wasPlaying = p.playWhenReady
                    p.setMediaItem(currentMediaItem)
                    p.seekTo(currentPosition)
                    p.prepare()
                    p.playWhenReady = wasPlaying
                }
            }
        }
        
        if (oldConfig.audioEffects != config.audioEffects) {
            applyAudioEffects()
        }
        
        playerView?.let { pv -> applySubtitleStyleToView(pv, config.subtitleStyle) }
    }

    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?) {
        val selector = trackSelector ?: return
        val p = player ?: return
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
                    return
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

    override fun setMaxVideoBitrate(bps: Int?) {
        val selector = trackSelector ?: return
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

    override fun captureViewBitmap(): Bitmap? {
        val pv = playerView ?: return null
        if (pv.width <= 0 || pv.height <= 0) return null
        return try {
            Bitmap.createBitmap(pv.width, pv.height, Bitmap.Config.RGB_565).also {
                pv.draw(Canvas(it))
            }
        } catch (_: Exception) { null }
    }

    override val currentPositionMs: Long get() = player?.currentPosition ?: 0L
    override val durationMs: Long get() = player?.duration?.coerceAtLeast(0L) ?: 0L
    override val playbackSpeed: Float get() = player?.playbackParameters?.speed ?: 1f
    override val audioSessionId: Int get() = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

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
                delay(500)
                runCatching {
                    val currentlyPlaying = p.isPlaying
                    if (currentlyPlaying) {
                        trySend(p.currentPosition)
                        _bufferedPositionMs.value = p.bufferedPosition.coerceAtLeast(0L)
                        updateVideoStats()
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
        val videoFormat = p.videoFormat
        val audioFormat = p.audioFormat
        val bufferedPos = p.bufferedPosition.coerceAtLeast(0L)

        val bandwidthEstimate = bandwidthMeter.bitrateEstimate

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
            audioCodec = audioFormat?.sampleMimeType?.let { codecFromMime(it) },
            audioSampleRate = audioFormat?.sampleRate?.let { if (it > 0) it else null },
            audioChannels = audioFormat?.channelCount?.let { if (it > 0) it else null },
            audioBitrate = audioFormat?.bitrate?.let { if (it > 0) it else null },
            bufferedPositionMs = bufferedPos,
            estimatedBandwidthBps = bandwidthEstimate,
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
            audioEffectsAttached = true
        }
        
        dialogueBoost.setStrength(config.dialogueBoostStrength)
        dialogueBoost.setEnabled(config.dialogueBoostEnabled)
        
        nightMode.setStrength(config.nightModeStrength)
        nightMode.setEnabled(config.nightModeEnabled)
        
        equalizerHelper.setSettings(config.equalizerSettings)
        equalizerHelper.setEnabled(config.equalizerEnabled)

        audioNormalizationHelper.setMode(config.audioNormalizationMode)
        audioNormalizationHelper.setEnabled(config.audioNormalizationEnabled)

        channelMixHelper.setMode(config.channelMixMode)
        channelMixHelper.setEnabled(config.channelMixEnabled)

        bassBoostHelper.setStrength(config.bassBoostStrength)
        bassBoostHelper.setEnabled(config.bassBoostEnabled)

        virtualizerHelper.setStrength(config.virtualizerStrength)
        virtualizerHelper.setEnabled(config.virtualizerEnabled)

        if (config.reverbPreset != com.raulshma.jellyplay.core.model.ReverbPreset.NONE) {
            reverbHelper.detach()
            reverbHelper.attach(sid)
            reverbHelper.setPreset(config.reverbPreset)
        } else {
            reverbHelper.setEnabled(false)
        }
        
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
        audioEffectsAttached = false
        lastAudioEffectsConfig = null
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

    override fun addExternalSubtitle(source: SubtitleSource) {
        val exo = player ?: return
        val item = currentMediaItem ?: return
        val mimeType = source.mimeType ?: mapSubtitleCodecToMime(source.codec ?: source.label) ?: return

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

    private fun mapSubtitleCodecToMime(codecOrLabel: String?): String? {
        if (codecOrLabel == null) return null
        return when (codecOrLabel.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "pgs" -> MimeTypes.APPLICATION_PGS
            "mov_text" -> MimeTypes.APPLICATION_TTML
            else -> null
        }
    }
}
