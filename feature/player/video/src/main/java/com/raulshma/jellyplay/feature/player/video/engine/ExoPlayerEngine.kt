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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ExoPlayerEngine(
    private val context: Context,
) : PlayerEngine {

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var mediaSession: MediaSession? = null
    private var onStateChanged: ((Boolean) -> Unit)? = null
    private var onTracksChanged: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var currentDecoderMode: DecoderMode = DecoderMode.HW_PREFERRED

    private var mediaSourceFactory: DefaultMediaSourceFactory? = null
    private var serverAuthority: String? = null
    private var accessToken: String? = null

    private val dialogueBoost = DialogueBoostHelper()
    private val nightMode = NightModeHelper()
    private val equalizerHelper = EqualizerHelper()
    private var dialogueBoostEnabled: Boolean = false
    private var nightModeEnabled: Boolean = false
    private var equalizerEnabled: Boolean = false
    private var _subtitleDelayMs: Long = 0L

    private var onPlaybackStateChangedCallback: ((Int) -> Unit)? = null
    private var onPlaybackEndedCallback: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onStateChanged?.invoke(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            onTracksChanged?.invoke()
            onPlaybackStateChangedCallback?.invoke(playbackState)
            if (playbackState == Player.STATE_READY) {
                if (dialogueBoostEnabled) applyDialogueBoostInternal(true)
                if (nightModeEnabled) applyNightModeInternal(true)
                if (equalizerEnabled) applyEqualizerInternal(true)
            }
            if (playbackState == Player.STATE_ENDED) {
                onPlaybackEndedCallback?.invoke()
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            this@ExoPlayerEngine.onTracksChanged?.invoke()
        }

        override fun onPlayerError(error: PlaybackException) {
            onError?.invoke(error.message ?: "Unknown playback error")
        }
    }

    override fun setOnError(callback: ((String) -> Unit)?) {
        onError = callback
    }

    override fun setOnPlaybackStateChanged(callback: ((Int) -> Unit)?) {
        onPlaybackStateChangedCallback = callback
    }

    fun setOnPlaybackEnded(callback: (() -> Unit)?) {
        onPlaybackEndedCallback = callback
    }

    fun setAuthInfo(serverUrl: String?, token: String?) {
        serverAuthority = serverUrl?.let { Uri.parse(it).authority }
        accessToken = token
    }

    private fun createAuthenticatedDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("JellyPlay")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val baseFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val authority = serverAuthority
        val token = accessToken
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

    fun configureMedia(
        url: String,
        title: String,
        startPositionMs: Long,
        subtitleConfigs: List<MediaItem.SubtitleConfiguration>,
        artworkUri: String? = null,
    ) {
        val p = player ?: return
        val metadataBuilder = MediaMetadata.Builder().setTitle(title)
        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUri))
        }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadataBuilder.build())
            .build()
        p.setMediaItem(mediaItem)
        p.prepare()
        if (startPositionMs > 0) p.seekTo(startPositionMs)
        p.play()
    }

    fun setPreferredAudioLanguage(language: String?) {
        val selector = trackSelector ?: return
        if (language != null) {
            val params = selector.buildUponParameters()
            params.setPreferredAudioLanguage(language)
            selector.setParameters(params)
        }
    }

    fun setPreferredSubtitleLanguage(language: String?) {
        val selector = trackSelector ?: return
        if (language != null) {
            val params = selector.buildUponParameters()
            params.setPreferredTextLanguage(language)
            selector.setParameters(params)
        }
    }

    fun setMaxVideoBitrate(bitrate: Int?) {
        val selector = trackSelector ?: return
        if (bitrate != null) {
            val params = selector.buildUponParameters()
            params.setMaxVideoBitrate(bitrate)
            selector.setParameters(params)
        }
    }

    fun setMediaSessionCallback(sessionManager: PlaybackSessionManager, playSessionId: String) {
        val p = player ?: return
        val session = MediaSession.Builder(context, p)
            .setId(playSessionId)
            .build()
        mediaSession = session
        sessionManager.setActiveSession(session)
    }

    fun releaseMediaSession(sessionManager: PlaybackSessionManager) {
        mediaSession?.let { sessionManager.clearSession(it) }
        mediaSession?.release()
        mediaSession = null
    }

    fun buildSubtitleConfigurations(
        streams: List<MediaStream>,
        getSubtitleDeliveryUrl: (String) -> String,
        buildSubtitleUrl: ((index: Int, codec: String?) -> String)? = null,
    ): List<MediaItem.SubtitleConfiguration> {
        return streams
            .filter { it.type == StreamType.SUBTITLE }
            .mapNotNull { stream ->
                val url = when {
                    !stream.deliveryUrl.isNullOrBlank() -> getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                    stream.isExternal && buildSubtitleUrl != null -> buildSubtitleUrl(stream.index, stream.codec)
                    else -> return@mapNotNull null
                }

                if (url.isBlank()) return@mapNotNull null

                val mimeType = mapSubtitleCodecToMime(stream.codec) ?: return@mapNotNull null

                MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                    .setId("external:${stream.index}")
                    .setMimeType(mimeType)
                    .setLanguage(stream.language)
                    .setLabel(stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown")
                    .setSelectionFlags(
                        (if (stream.isDefault) C.SELECTION_FLAG_DEFAULT else 0) or
                        (if (stream.isForced) C.SELECTION_FLAG_FORCED else 0)
                    )
                    .build()
            }
    }

    val currentMediaItem: MediaItem? get() = player?.currentMediaItem

    fun createPlayer(
        url: String,
        title: String,
        startPositionMs: Long,
    ) {
        release()

        val selector = DefaultTrackSelector(context)
        trackSelector = selector

        val rendererMode = when (currentDecoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 3_000)
            .setTargetBufferBytes(-1)
            .build()

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                    offsetUsProvider = { _subtitleDelayMs * 1000L }
                )
            )
        }

        val dataSourceFactory = createAuthenticatedDataSourceFactory()
        val msf = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        mediaSourceFactory = msf

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
            .build()

        exo.addListener(listener)
        player = exo
    }

    override fun initialize(url: String, title: String, startPositionMs: Long) {
        release()

        val selector = DefaultTrackSelector(context)
        trackSelector = selector

        val rendererMode = when (currentDecoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 3_000)
            .setTargetBufferBytes(-1)
            .build()

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                    offsetUsProvider = { _subtitleDelayMs * 1000L }
                )
            )
        }

        val dataSourceFactory = createAuthenticatedDataSourceFactory()
        val msf = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        mediaSourceFactory = msf

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
            .build()

        exo.addListener(listener)
        player = exo

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        exo.setMediaItem(mediaItem)
        exo.prepare()
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.play()
    }

    override fun release() {
        player?.removeListener(listener)
        playerView?.player = null
        playerView = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        trackSelector = null
        mediaSourceFactory = null
        releaseAudioEffects()
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }
    override fun seekForward(amountMs: Long) {
        player?.let { it.seekTo((it.currentPosition + amountMs).coerceAtMost(it.duration)) }
    }
    override fun seekBack(amountMs: Long) {
        player?.let { it.seekTo((it.currentPosition - amountMs).coerceAtLeast(0)) }
    }
    override fun setPlaybackSpeed(speed: Float) { player?.setPlaybackSpeed(speed) }

    override fun setAudioDelay(ms: Long) {}

    override fun setSubtitleDelay(ms: Long) {
        if (_subtitleDelayMs == ms) return
        _subtitleDelayMs = ms
        // The OffsettingSubtitleParserFactory picks up the new delay on next parse.
        // A re-prepare is needed for existing subtitles to use the new offset.
        val p = player ?: return
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

    override fun setDecoderMode(mode: DecoderMode) {
        currentDecoderMode = mode
    }

    override fun setAudioPassthrough(enabled: Boolean) {}

    override fun setAspectRatio(mode: Int, ratio: Float?) {
        playerView?.setResizeMode(mode)
        if (ratio != null && ratio > 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(ratio)
        } else if (ratio == null || ratio == 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(0f)
        }
    }

    override val isPlaying: Boolean get() = player?.isPlaying == true
    override val currentPositionMs: Long get() = player?.currentPosition ?: 0L
    override val durationMs: Long get() = player?.duration?.coerceAtLeast(0L) ?: 0L
    override val playbackSpeed: Float get() = player?.playbackParameters?.speed ?: 1f
    override val audioSessionId: Int get() = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
    override val supportsAudioDelay: Boolean get() = false
    override val supportsSubtitleDelay: Boolean get() = true
    override val supportsAudioPassthrough: Boolean get() = false
    override val supportsSubtitleStyle: Boolean get() = true
    override val supportsDialogueBoost: Boolean get() = true
    override val supportsNightMode: Boolean get() = true
    override val supportsOcr: Boolean get() = true
    override val supportsCues: Boolean get() = true

    override val audioTracks: List<PlayerEngine.TrackInfo>
        get() = buildTracks(C.TRACK_TYPE_AUDIO, PlayerEngine.TrackType.AUDIO)

    override val subtitleTracks: List<PlayerEngine.TrackInfo>
        get() = buildTracks(C.TRACK_TYPE_TEXT, PlayerEngine.TrackType.SUBTITLE)

    override fun selectAudioTrack(index: Int) {
        val selector = trackSelector ?: return
        val p = player ?: return
        val params = selector.buildUponParameters()
        if (index < 0) {
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            val groupIndex = index.coerceIn(groups.indices)
            if (groupIndex in groups.indices) {
                val group = groups[groupIndex].mediaTrackGroup
                params.setOverrideForType(
                    TrackSelectionOverride(group, (0 until group.length).toList())
                )
            }
        }
        selector.setParameters(params)
    }

    override fun selectSubtitleTrack(index: Int) {
        val selector = trackSelector ?: return
        val p = player ?: return
        val params = selector.buildUponParameters()
        if (index < 0) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val groupIndex = index.coerceIn(groups.indices)
            if (groupIndex in groups.indices) {
                val group = groups[groupIndex].mediaTrackGroup
                params.setOverrideForType(
                    TrackSelectionOverride(group, (0 until group.length).toList())
                )
            }
        }
        selector.setParameters(params)
    }

    override fun createPlayerView(ctx: Context): View {
        val pv = PlayerView(ctx).apply {
            this.player = this@ExoPlayerEngine.player
            useController = false
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        // Reparent SubtitleView out of AspectRatioFrameLayout so subtitles can reach
        // the bottom of the screen (e.g. into black bars for letterboxed content).
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
        return pv
    }

    override fun setOnStateChanged(callback: ((Boolean) -> Unit)?) {
        onStateChanged = callback
    }

    override fun setOnTracksChanged(callback: (() -> Unit)?) {
        onTracksChanged = callback
    }

    override fun setSubtitleStyle(style: SubtitleStyle, view: View?) {
        val pv = (view as? PlayerView) ?: playerView ?: return
        val bgAlpha = (style.backgroundOpacity * 255).toInt()
        val bgColorWithAlpha = (bgAlpha shl 24) or (style.backgroundColor.value and 0x00FFFFFF)
        pv.subtitleView?.let { sv ->
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

    override fun getCurrentCues(): List<androidx.media3.common.text.Cue> {
        return player?.currentCues?.cues ?: emptyList()
    }

    override fun setDialogueBoostEnabled(enabled: Boolean) {
        dialogueBoostEnabled = enabled
        applyDialogueBoostInternal(enabled)
    }

    override fun setNightModeEnabled(enabled: Boolean, gain: Int) {
        nightModeEnabled = enabled
        nightMode.setTargetGain(gain)
        applyNightModeInternal(enabled)
    }

    override fun setEqualizerEnabled(enabled: Boolean, settings: EqualizerSettings) {
        equalizerEnabled = enabled
        equalizerHelper.setSettings(settings)
        applyEqualizerInternal(enabled)
    }

    override fun captureViewBitmap(): Bitmap? {
        val pv = playerView ?: return null
        if (pv.width <= 0 || pv.height <= 0) return null
        return try {
            Bitmap.createBitmap(pv.width, pv.height, Bitmap.Config.ARGB_8888).also {
                pv.draw(Canvas(it))
            }
        } catch (_: Exception) { null }
    }

    override fun positionFlow(): Flow<Long> = callbackFlow {
        val p = player ?: run { close(); return@callbackFlow }
        val posListener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                trySend(p.currentPosition)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                trySend(p.currentPosition)
            }
        }
        p.addListener(posListener)
        trySend(p.currentPosition)

        val ticker = launch {
            while (isActive) {
                delay(250)
                trySend(p.currentPosition)
            }
        }

        awaitClose {
            ticker.cancel()
            p.removeListener(posListener)
        }
    }

    private fun applyDialogueBoostInternal(enabled: Boolean) {
        val sid = audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        dialogueBoost.attach(sid)
        dialogueBoost.setEnabled(enabled)
    }

    private fun applyNightModeInternal(enabled: Boolean) {
        val sid = audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        nightMode.attach(sid)
        nightMode.setEnabled(enabled)
    }

    private fun applyEqualizerInternal(enabled: Boolean) {
        val sid = audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        equalizerHelper.attach(sid)
        equalizerHelper.setEnabled(enabled)
    }

    private fun releaseAudioEffects() {
        dialogueBoost.detach()
        nightMode.detach()
        equalizerHelper.detach()
    }

    private fun buildTracks(trackType: Int, type: PlayerEngine.TrackType): List<PlayerEngine.TrackInfo> {
        val p = player ?: return emptyList()
        val tracks = p.currentTracks
        val result = mutableListOf<PlayerEngine.TrackInfo>()
        val groups = tracks.groups.filter { it.type == trackType }
        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            val isSelected = (0 until group.length).any { group.isTrackSelected(it) }
            val format = group.getTrackFormat(0)
            result.add(
                PlayerEngine.TrackInfo(
                    index = groupIndex,
                    label = buildTrackLabel(format),
                    language = format.language,
                    isSelected = isSelected,
                    type = type,
                    trackGroup = group.mediaTrackGroup,
                )
            )
        }
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

    private fun mapSubtitleCodecToMime(codec: String?): String? {
        if (codec == null) return null
        return when (codec.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "pgs" -> MimeTypes.APPLICATION_PGS
            "mov_text" -> MimeTypes.APPLICATION_SUBRIP
            else -> null
        }
    }
}
