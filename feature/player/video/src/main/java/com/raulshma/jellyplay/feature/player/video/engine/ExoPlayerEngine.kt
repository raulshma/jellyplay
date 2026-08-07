package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.raulshma.jellyplay.core.data.playback.BassBoostHelper
import com.raulshma.jellyplay.core.data.playback.ChannelMixAudioProcessor
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.DynamicsCompressorAudioProcessor
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.HighPassFilterAudioProcessor
import com.raulshma.jellyplay.core.data.playback.LoudnessEnhancerHelper
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.ReplayGainAudioProcessor
import com.raulshma.jellyplay.core.data.playback.ReverbHelper
import com.raulshma.jellyplay.core.data.playback.VirtualizerHelper
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.subtitle.AssSupport
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleMimeMapper
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient

// The position-polling bounded paused-wait now lives in [EnginePositionTicker].

/**
 * Default subtitle text size for the embedded-style path. A fixed SP value keeps
 * captions stable across orientation changes (a height-fraction scales against the
 * view height, which grows dramatically in portrait).
 *
 * Sourced from [com.raulshma.jellyplay.core.model.SubtitleRenderDefaults.EXOPLAYER_OVERRIDE]
 * — ExoPlayer's documented divergence from the shared 24sp default. See that
 * override's KDoc for why ExoPlayer keeps a distinct size/edge here.
 */
private val DEFAULT_SUBTITLE_SIZE_SP =
    com.raulshma.jellyplay.core.model.SubtitleRenderDefaults.EXOPLAYER_OVERRIDE.fontSizeSp.toFloat()
private const val TAG = "ExoPlayerEngine"

class ExoPlayerEngine(
    private val context: Context,
    private val streamingOkHttpClient: OkHttpClient,
    bandwidthMeter: DefaultBandwidthMeter? = null,
    private val fontProvider: FontProvider,
) : BasePlayerEngine() {

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

    /**
     * ExoPlayer requires application-thread access for every mutation. The VM
     * calls [load]/[release] from `viewModelScope` (Default + Main) which is
     * safe, but tests or off-Main callers can violate the precondition and
     * corrupt the player silently. Fail fast with a clear cause instead.
     */
    private fun ensurePlayerThread(op: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ExoPlayerEngine.$op must run on the main thread; was ${Thread.currentThread()}"
        }
    }

    override val capabilities = EngineCapabilityMatrix.EXO_PLAYER
    override val zoomSafeSubtitleStrategy = ZoomSafeSubtitleStrategy.NATIVE_PINNED
    override val displayName: String = PlayerType.EXO_PLAYER.displayName

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var frameSizeListener: android.view.View.OnLayoutChangeListener? = null
    // Optional screen-pinned host. When non-null, [reparentSubtitleViews]
    // parents the SubtitleView/AssSubtitleView here (a sibling of the zoomed
    // video surface, outside the pinch/crop transform) instead of the
    // letterboxed `exo_content_frame`, so captions stay put under zoom/crop.
    // Set via [setExternalSubtitleHost]; cleared on detach/release.
    private var externalSubtitleHost: android.view.ViewGroup? = null

    // --- libass (ass-media) overlay state ---
    // Only one of these is populated per session: when AssSupport detects an
    // ASS/SSA subtitle source, assHandler/assOverlayView are created and the
    // native SubtitleView is hidden in favour of the libass overlay. Non-ASS
    // sessions leave all three null/false and run the unchanged DefaultSubtitle
    // ParserFactory path.
    private var assHandler: AssHandler? = null
    private var assOverlayView: AssSubtitleView? = null
    private var assEnabledForSession: Boolean = false
    // Reflects whether the *currently selected* text track is ASS. Drives the
    // SubtitleView/AssSubtitleView visibility toggle in applySubtitleStyleToView.
    private var activeTrackIsAss: Boolean = false
    // Cached id of the currently selected text track group, so onTracksChanged
    // can detect a *subtitle* track switch (vs an audio-only change) and reset
    // the accumulated cue list rather than mixing cues from two tracks.
    private var lastSelectedTextTrackId: String? = null
    // Latched once a malformed text track has been auto-disabled so further
    // onCues callbacks are ignored until the user selects a different subtitle
    // track (which clears this in onTracksChanged). Prevents a bad track from
    // re-triggering the disable / re-enabling itself on the next render tick.
    private var subtitleTrackAutoDisabled: Boolean = false
    private var lastFrameW = -1
    private var lastFrameH = -1
    @Volatile
    private var currentMediaItem: MediaItem? = null
    private val bandwidthMeter = bandwidthMeter ?: DefaultBandwidthMeter.Builder(context).build()

    /**
     * Server-reported total runtime (ms), captured from [PlaybackRequest.serverDurationMs]
     * in [load]. Used as a fallback in [durationMs] when the ExoPlayer demuxer cannot
     * resolve a duration for HLS/transcoded manifests (where `player.duration` is
     * frequently `C.TIME_UNSET`). Mirrors [MpvPlayerEngine.serverDurationMs].
     */
    @Volatile
    private var serverDurationMs: Long = 0L

    /**
     * Mutated from [load] and [release] (both asserted on the player
     * thread) and from [addExternalSubtitle] via [runOnPlayerThread], but read
     * by [buildMediaItem] which may be invoked on a coroutine. CopyOnWrite
     * guarantees safe iteration without ever blocking the player thread, and
     * sidesteps `ConcurrentModificationException` if a subtitle-add lands
     * during a reload.
     */
    private val currentSubtitleConfigs =
        java.util.concurrent.CopyOnWriteArrayList<MediaItem.SubtitleConfiguration>()

    override val underlyingPlayer: androidx.media3.common.Player? get() = player

    /**
     * Per-track ReplayGain (dB) from the current [PlaybackRequest], used
     * for TRACK/ALBUM loudness normalization via [replayGainProcessor].
     * `null` until [load] is called.
     */
    private var currentNormalizationGain: Float? = null

    private val equalizerHelper = EqualizerHelper()
    private val highPassFilter = HighPassFilterAudioProcessor()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper, highPassFilter)
    private val nightMode = NightModeHelper()
    private val bassBoostHelper = BassBoostHelper()
    private val virtualizerHelper = VirtualizerHelper()
    private val reverbHelper = ReverbHelper()
    private val loudnessEnhancerHelper = LoudnessEnhancerHelper()

    // In-sink AudioProcessors for the video ExoPlayer path — these run
    // real DSP (channel matrixing, dynamic compression, ReplayGain,
    // sub-bass high-pass) that the android.media.audiofx helpers above
    // cannot do. Installed via the custom renderers factory in load().
    private val channelMixProcessor = ChannelMixAudioProcessor()
    private val dynamicsProcessor = DynamicsCompressorAudioProcessor()
    private val replayGainProcessor = ReplayGainAudioProcessor()

    /**
     * Collapses the prior 7-helper + 3-processor sprawl behind a single
     * attach/apply/release surface. Owns the bookkeeping
     * (`audioEffectsAttached`, `lastAudioEffectsConfig`,
     * `lastAppliedReverbPreset`) that used to live on the engine.
     */
    private val audioEffectChain = AudioEffectChain(
        dialogueBoost = dialogueBoost,
        nightMode = nightMode,
        equalizerHelper = equalizerHelper,
        bassBoostHelper = bassBoostHelper,
        virtualizerHelper = virtualizerHelper,
        reverbHelper = reverbHelper,
        loudnessEnhancerHelper = loudnessEnhancerHelper,
        channelMixProcessor = channelMixProcessor,
        dynamicsProcessor = dynamicsProcessor,
        replayGainProcessor = replayGainProcessor,
    )

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
    // audioEffectsAttached / lastAudioEffectsConfig / lastAppliedReverbPreset
    // moved into AudioEffectChain.

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
            // Reset the accumulated cue list when the *selected* subtitle track
            // changes, so cues from a prior track don't bleed into the preview.
            // An audio-only track change leaves the text selection untouched.
            val currentTextId = currentSelectedTextTrackId()
            if (currentTextId != lastSelectedTextTrackId) {
                lastSelectedTextTrackId = currentTextId
                _currentCues.value = emptyList()
                // Clear the malformed-track latch only when a subtitle track
                // becomes *selected* (null → non-null). The auto-disable path
                // sets TRACK_TYPE_TEXT disabled, which itself fires this
                // callback with the text id going non-null → null; clearing on
                // that transition would self-clear the guard it just set. Only
                // a fresh selection (user re-enabling / picking a track, or the
                // first auto-select on load) should clear the latch.
                if (currentTextId != null) {
                    subtitleTrackAutoDisabled = false
                }
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            accumulateCues(cueGroup)
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = EnginePlaybackState.ERROR
            _errorFlow.tryEmit(error.toEngineError())
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != lastAppliedAudioSessionId) {
                lastAppliedAudioSessionId = audioSessionId
                // The audio session id changed mid-playback (e.g. track
                // switch). AudioEffect handles are bound to the *old*
                // session id, which is now dead. Detach everything so the
                // chain re-binds to the new session instead of short-
                // circuiting on the cached config.
                audioEffectChain.release()
                audioEffectChain.apply(audioSessionId, currentConfig.audioEffects, currentNormalizationGain)
            }
        }

        override fun onVolumeChanged(volume: Float) {
            cachedVolume = volume
        }

        // NOTE: onMediaMetadataChanged is intentionally NOT overridden here.
        // Previously a pinned title/artwork re-apply path called
        // `player.replaceMediaItem(0, ...)` whenever Media3 reported an empty
        // metadata — which it does repeatedly for HLS/transcoded manifests
        // (Jellyfin transcode playlists carry no metadata). Mutating the
        // currently-playing MediaItem from inside that callback disrupts
        // ExoPlayer's HLS timeline/seek state and was the root cause of
        // seeks restarting playback from 0 on transcoded media. Metadata
        // pinning for the system MediaSession is now handled externally via
        // a ForwardingPlayer in VideoPlayerViewModel.createVideoMediaSession.
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
        ensurePlayerThread("load")
        release()
        recreateEngineScopeIfInactive()

        currentNormalizationGain = request.normalizationGain

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
            // Local val captures the non-null value: maxVideoBitrate now lives
            // in :feature:player:core (different module), so Kotlin can no
            // longer smart-cast the cross-module public property. We are inside
            // the null-check branch, so !! is provably safe.
            val maxVideoBitrate = request.maxVideoBitrate!!
            selector.setParameters(
                selector.buildUponParameters().setMaxVideoBitrate(maxVideoBitrate)
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
        // Custom renderers factory injects the in-sink AudioProcessor chain
        // (channel mix → dynamics → ReplayGain → high-pass) into the video
        // ExoPlayer path. Kept as the base; the ASS path wraps it below so the
        // AudioProcessor chain is preserved on both paths.
        val baseRenderersFactory = object : DefaultRenderersFactory(context) {
            init {
                setExtensionRendererMode(rendererMode)
                setEnableDecoderFallback(exoCfg.enableDecoderFallback)
            }

            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): DefaultAudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(
                        arrayOf(
                            channelMixProcessor,
                            dynamicsProcessor,
                            replayGainProcessor,
                            highPassFilter,
                        ),
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(request.minBufferMs, request.maxBufferMs, 1_000, 3_000)
            .setTargetBufferBytes(-1)
            .setBackBuffer(exoCfg.backBufferDurationMs.coerceAtLeast(0), false)
            .build()

        // Decide whether this session needs the libass (ass-media) path: only
        // when an ASS/SSA subtitle source is present. Non-ASS sessions keep the
        // existing DefaultSubtitleParserFactory path untouched, so the existing
        // engine tests (which never carry ASS sources) are unaffected.
        assEnabledForSession = AssSupport.hasAssSubtitles(request)
        // Reset the active-track ASS flag: a reused engine (load() called again
        // without release()) must not carry over stale activeTrackIsAss=true from
        // a prior ASS track, which would hide subtitles until the next select.
        activeTrackIsAss = false

        val offsetUsProvider: () -> Long = { currentConfig.subtitleDelayMs * 1000L }

        // Construct the AssHandler FIRST (before the delegate parser factory)
        // so AssSubtitleParserFactory can reference it. On the non-ASS path the
        // handler stays null and the DefaultSubtitleParserFactory is used.
        if (assEnabledForSession) {
            val renderType = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
                AssRenderType.OVERLAY_CANVAS else AssRenderType.OVERLAY_OPEN_GL
            assHandler = AssHandler(renderType)
        }

        // One offset mechanism covers both paths: the OffsettingSubtitleParser
        // Factory wraps whichever delegate is active, so the subtitle-delay slider
        // shifts cues on the ASS path too (libass reads the shifted start times).
        // On the ASS path the delegate is the concrete AssSubtitleParserFactory,
        // which is also handed (un-wrapped) to withAssMkvSupport below — that
        // extension requires the concrete type for embedded-MKV ASS extraction,
        // while the offset wrapper handles side-loaded external ASS subs.
        val assParserFactory: AssSubtitleParserFactory? =
            if (assEnabledForSession) AssSubtitleParserFactory(assHandler!!) else null
        val delegateParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory =
            assParserFactory ?: DefaultSubtitleParserFactory()
        val offsetFactory = OffsettingSubtitleParserFactory(delegateParserFactory, offsetUsProvider)

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setSubtitleParserFactory(offsetFactory)
        }

        val dataSourceFactory = createAuthenticatedDataSourceFactory(request.serverUrl, request.authToken, request.headers)

        // On the ASS path, wrap the extractors with Matroska+ASS support (for
        // ASS embedded in MKV) and the renderers with the libass text renderer;
        // otherwise the plain base factories reproduce the pre-ASS build exactly.
        // withAssMkvSupport takes the extractors factory and the BARE
        // AssSubtitleParserFactory (the concrete type), per its signature.
        //
        // KNOWN LIMITATION: because withAssMkvSupport requires the concrete
        // AssSubtitleParserFactory type, embedded ASS-in-MKV extraction bypasses
        // the OffsettingSubtitleParserFactory — the subtitle-delay slider has no
        // effect on embedded MKV ASS tracks in this engine. This is an ass-media
        // library signature constraint; mpv and libVLC render embedded ASS at the
        // correct offset via sub-delay/setSpuDelay regardless. Deliberately left
        // as-is rather than destabilizing the working ASS path.
        val (finalRenderersFactory, msf) = if (assEnabledForSession) {
            val assExtractors = extractorsFactory.withAssMkvSupport(assParserFactory!!, assHandler!!)
            val renderers = AssRenderersFactory(assHandler!!, baseRenderersFactory)
            val sourceFactory = DefaultMediaSourceFactory(dataSourceFactory, assExtractors)
                .setSubtitleParserFactory(offsetFactory)
            renderers to sourceFactory
        } else {
            // setSubtitleParserFactory on the MediaSourceFactory so that side-
            // loaded (side-car SubtitleConfiguration) text subs are also parsed
            // through the offset wrapper. The extractors-level factory set above
            // only covers embedded text tracks; side-car subs are parsed by the
            // MSF's own factory, which must be the offset factory too.
            baseRenderersFactory to DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .setSubtitleParserFactory(offsetFactory)
        }

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

        // WAKE_MODE_NETWORK additionally acquires a Wi-Fi multicast lock so the
        // CPU/Wi-Fi stay awake during backgrounded HTTP streaming playback (the
        // common JellyPlay case — request carries serverUrl/authToken/headers).
        // WAKE_MODE_LOCAL is intended for local file playback. Using LOCAL for
        // HTTP streams risks buffering/drops when the screen is off on battery-
        // conscious devices. Requires android.permission.WAKE_LOCK to take
        // effect (declared in the manifest).
        val isNetworkStream = request.uri.startsWith("http", ignoreCase = true) ||
            request.uri.startsWith("rtmp", ignoreCase = true)

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(finalRenderersFactory)
            .setMediaSourceFactory(msf)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, currentConfig.pauseOnAudioFocusLoss)
            .setWakeMode(if (isNetworkStream) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setBandwidthMeter(bandwidthMeter)
            .setVideoScalingMode(exoCfg.videoScalingMode.value)
            .setVideoChangeFrameRateStrategy(exoCfg.frameRateStrategy.value)
            .setSkipSilenceEnabled(exoCfg.skipSilence)
            .build()

        exo.addListener(listener)
        exo.addAnalyticsListener(decoderCountersListener)
        player = exo

        // Wire the AssHandler to the player and inject the bundled fallback
        // font (and any user-installed fonts) so libass can resolve families.
        // ass-media's font API is per-file byte[] (AssHandler.addFont), not a
        // directory path, so we enumerate FontProvider's fonts dir and feed each
        // .ttf. Mirrors mpv's sub-fonts-dir pointing at the same directory.
        assHandler?.let { handler ->
            handler.init(exo)
            runCatching {
                val fontsDir = fontProvider.provideFontsDir()
                fontsDir.listFiles { file -> file.extension.equals("ttf", ignoreCase = true) }
                    ?.forEach { ttf ->
                        runCatching {
                            ttf.readBytes().takeIf { it.isNotEmpty() }?.let { bytes ->
                                handler.addFont(ttf.nameWithoutExtension, bytes)
                            }
                        }
                    }
            }
        }

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
            .apply {
                // MIME-type hint so DefaultMediaSourceFactory selects the right
                // MediaSource (HlsMediaSource vs progressive extractor):
                //  - Caller-provided hint wins (offline container sniffing).
                //  - Transcoded streams are served as Jellyfin HLS master
                //    playlists (master.m3u8) — without the hint ExoPlayer
                //    relies on extension detection, which is fragile when the
                //    query string trails the .m3u8 path. The official Jellyfin
                //    Android client pins APPLICATION_M3U8 the same way. This is
                //    also what makes native HLS seeking (segment + EXTINF
                //    resolution) reliable on a transcode.
                val inferredMime = when {
                    request.mimeType != null -> request.mimeType
                    request.uri.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                    else -> null
                }
                inferredMime?.let { setMimeType(it) }
            }
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        exo.setMediaItem(mediaItem)
        currentMediaItem = mediaItem
        serverDurationMs = request.serverDurationMs
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
        // Route media streams through the shared app OkHttp stack rather than a
        // standalone HttpURLConnection. The injected client carries the shared
        // connection pool, the user-sized disk Cache, the BandwidthInterceptor
        // that feeds adaptive bitrate selection, and HTTP/2 multiplexing — so
        // the highest-bandwidth traffic reuses the same wiring as every other
        // request. OkHttp follows cross-protocol redirects by default, and the
        // "streaming" qualifier already sets a >=30s read timeout. The
        // ResolvingDataSource auth wrapper below composes on top unchanged.
        val httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent("JellyPlay")
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
        ensurePlayerThread("release")
        engineScope.cancel()
        player?.removeListener(listener)
        player?.removeAnalyticsListener(decoderCountersListener)
        frameSizeListener?.let { playerView?.removeOnLayoutChangeListener(it) }
        frameSizeListener = null
        // Detach the screen-pinned host first so subtitle views are released from
        // it before the PlayerView is torn down. Without this, reparented views
        // (now living outside the PlayerView subtree) would orphan in a host the
        // engine no longer feeds. The screen's onRelease normally does this, but
        // release() must be safe to call without it.
        externalSubtitleHost = null
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        trackSelector = null
        currentMediaItem = null
        serverDurationMs = 0L
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
        _currentCues.value = emptyList()
        lastSelectedTextTrackId = null
        subtitleTrackAutoDisabled = false

        // Drop the libass overlay + handler so the next load() rebuilds them
        // fresh. The AssSubtitleView is a child of the (now-cleared) subtitle
        // target, which was torn down with playerView above; nulling the
        // references avoids leaks and lets the GC reclaim the native
        // Ass/AssRender handles the handler owns. (ass-media has no explicit
        // release() on AssHandler; it relies on the player release propagating
        // to the renderer it injected.)
        assOverlayView = null
        assHandler = null
        assEnabledForSession = false
        activeTrackIsAss = false
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
        if (muted) {
            // Snapshot the system STREAM_MUSIC level the user actually hears
            // (set via gesture path / hardware keys, which bypass the engine
            // API). Snapshotting p.volume instead is wrong — it stays at its
            // 1f default when volume was adjusted outside the engine, so unmute
            // would restore full volume.
            val sysVol = MediaStreamVolume.getNormalized(context)
            if (sysVol > 0f) lastUnmuteVolume = sysVol
            p.volume = 0f
            MediaStreamVolume.setNormalized(context, 0f)
        } else {
            // Restore the system stream to its pre-mute level and set the
            // engine software gain back to unity (1f). Do NOT also scale
            // p.volume by the system level — that would double-attenuate.
            val target = lastUnmuteVolume.coerceIn(0.05f, 1f)
            p.volume = 1f
            MediaStreamVolume.setNormalized(context, target)
        }
    }

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        // decoderMode change: decoding changes require a reload, which is
        // handled by the upper layer recreating the player — nothing to do here.
        //
        // subtitleDelayMs change: the OffsettingSubtitleParserFactory's
        // wrapper reads currentConfig.subtitleDelayMs on each parse() call, so
        // a delay adjustment takes effect for subsequent cues without a media
        // reload. (Previously the offset was snapshotted at prepare() time and
        // the delay slider appeared broken for side-loaded subtitles.)
        //
        // KNOWN LIMITATION: Media3 parses a progressive side-car subtitle file
        // once and caches the cues; parse() is not re-invoked when the delay
        // changes mid-playback, so cues already loaded keep their original
        // timestamps until the user seeks (which re-invokes the parser). mpv and
        // libVLC re-evaluate the delay continuously, so their offset is truly
        // live. Forcing a re-parse on every delay change risks perf/jank, so the
        // buffered-cue limitation is accepted; seeking refreshes the offset.

        if (oldConfig.audioEffects != newConfig.audioEffects) {
            applyAudioEffects()
            if (requiresAudioPipelineReconfiguration(oldConfig.audioEffects, newConfig.audioEffects)) {
                reconfigureAudioPipeline()
            }
        }

        if (oldConfig.subtitleStyle != newConfig.subtitleStyle) {
            playerView?.let { pv -> applySubtitleStyleToView(pv, newConfig.subtitleStyle) }
        }

        if (oldConfig.pauseOnAudioFocusLoss != newConfig.pauseOnAudioFocusLoss) {
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player?.setAudioAttributes(audioAttrs, newConfig.pauseOnAudioFocusLoss)
        }
    }

    override fun selectTrack(type: TrackType, index: Int) = runOnPlayerThread {
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
            // Resolve the TrackGroup by type-filtered positional index — the same
            // indexing [buildTracks] uses to publish [MediaTrack.index], so the
            // two stay in sync without the engine-agnostic contract having to
            // carry the opaque (ExoPlayer-specific) TrackGroup reference.
            val groups = p.currentTracks.groups.filter { it.type == exoType }
            if (groups.isEmpty()) {
                selector.setParameters(params)
                return@runOnPlayerThread
            }
            val groupIndex = index.coerceIn(groups.indices)
            if (groupIndex in groups.indices) {
                val group = groups[groupIndex].mediaTrackGroup
                params.setOverrideForType(
                    TrackSelectionOverride(group, (0 until group.length).toList())
                )
            }
        }
        selector.setParameters(params)

        // Track-type toggle for ASS vs non-ASS visibility. Only relevant on the
        // ASS-enabled session: detect whether the *selected* subtitle track is
        // an ASS/SSA track (Format.sampleMimeType == TEXT_SSA) and flip the
        // SubtitleView/AssSubtitleView visibility accordingly. On non-ASS
        // sessions activeTrackIsAss stays false and the native view is used.
        if (type == TrackType.SUBTITLE && assEnabledForSession) {
            val newlyAss = if (index < 0) {
                false
            } else {
                val groups = p.currentTracks.groups.filter { it.type == exoType }
                val groupIndex = index.coerceIn(groups.indices)
                if (groupIndex in groups.indices) {
                    // The override targets every track in the group; ASS tracks
                    // are homogeneous within a group, so the first format's mime
                    // is representative.
                    groups[groupIndex].getTrackFormat(0).sampleMimeType == MimeTypes.TEXT_SSA
                } else {
                    false
                }
            }
            if (newlyAss != activeTrackIsAss) {
                activeTrackIsAss = newlyAss
                playerView?.let { applySubtitleStyleToView(it, currentConfig.subtitleStyle) }
            }
        }
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
        // Idempotent teardown. A second call (e.g. recomposition after a
        // surface recycle) used to leak the previous PlayerView — it kept the
        // (possibly released) ExoPlayer and its layout listener alive. Detach
        // both before constructing the replacement.
        playerView?.let { old ->
            old.player = null
            frameSizeListener?.let { old.removeOnLayoutChangeListener(it) }
        }
        frameSizeListener = null

        val pv = PlayerView(context).apply {
            this.player = this@ExoPlayerEngine.player
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        pv.post { reparentSubtitleViews(pv) }
        // Re-parent the subtitle views into the (re-laid-out) target after every
        // layout pass. By default that target is the AspectRatioFrameLayout
        // content frame: in portrait the PlayerView letterboxes the video into
        // it, and the SubtitleView must live inside that frame (not the
        // full-screen PlayerView) so captions sit at the bottom of the *video*,
        // and setBottomPaddingFraction / fractional text sizes compute against
        // the video height. When an [externalSubtitleHost] is attached it
        // replaces the content frame so captions pin to the screen under zoom.
        //
        // Layout passes fire frequently during playback (controls show/hide,
        // seekbar interaction, immersive transitions, video-size callbacks);
        // only reparent on genuine geometry changes to suppress no-op work.
        lastFrameW = -1
        lastFrameH = -1
        val layoutListener = android.view.View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w == lastFrameW && h == lastFrameH) return@OnLayoutChangeListener
            lastFrameW = w
            lastFrameH = h
            pv.post {
                reparentSubtitleViews(pv)
                // ASS coordinates are absolute to the video frame; the overlay
                // is MATCH_PARENT inside the target so it already tracks the
                // letterboxed geometry, but force a re-layout to be safe.
                assOverlayView?.requestLayout()
            }
        }
        frameSizeListener = layoutListener
        pv.addOnLayoutChangeListener(layoutListener)
        playerView = pv

        // libass overlay: only created for ASS-enabled sessions. Inserted into
        // the content frame alongside the native SubtitleView so ASS coordinates
        // map to the letterboxed video rectangle, not the full screen.
        if (assEnabledForSession && assHandler != null) {
            val handler = assHandler!!
            val assView = AssSubtitleView(context, handler).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Hidden until an ASS track is selected (see applySubtitleStyleToView /
                // selectTrack toggle). Non-ASS tracks keep using the native SubtitleView.
                visibility = if (activeTrackIsAss) View.VISIBLE else View.GONE
            }
            assOverlayView = assView
            // Parent the ASS overlay via the unified reparent path so it tracks
            // the active target (content frame, or the screen-pinned host when
            // one is attached). Idempotent, so the initial reparent above plus
            // this one collapse to a single add.
            pv.post { reparentSubtitleViews(pv) }
        }

        applySubtitleStyleToView(pv, currentConfig.subtitleStyle)
        return pv
    }

    /**
     * Parents both the native [SubtitleView] and the libass [AssSubtitleView]
     * (when present) into the active subtitle target.
     *
     * Default target is PlayerView's `exo_content_frame` (the letterboxed video
     * rectangle): while the SubtitleView is a direct child of the PlayerView its
     * layout fractions (bottom padding, fractional text size) compute against the
     * whole screen height, so in portrait — where the video is letterboxed —
     * captions land in the bottom black bar instead of on the video. Inside the
     * content frame they are measured against the video dimensions, keeping them
     * correct and consistent with mpv / VLC across rotation.
     *
     * When an [externalSubtitleHost] is attached it replaces the content frame as
     * the target. That host is a sibling of the zoomed video surface (outside the
     * pinch/crop transform), so captions stay pinned to the screen under zoom and
     * crop. Font sizes are SP (density-independent, unaffected); only
     * `setBottomPaddingFraction` / fractional sizes then resolve against the
     * screen height instead of the video height — the intended screen-pinned
     * behavior. See [setExternalSubtitleHost].
     *
     * Idempotent: a view already parented to the active target is left alone, so
     * repeated layout-pass calls are a cheap no-op (see `lastFrameW/H` guard in
     * [createSurfaceView]).
     */
    private fun reparentSubtitleViews(pv: PlayerView) {
        val target: android.view.ViewGroup = externalSubtitleHost
            ?: pv.findViewById(androidx.media3.ui.R.id.exo_content_frame)
            ?: return
        reparentInto(pv.subtitleView, target)
        reparentInto(assOverlayView, target)
    }

    /** Moves [view] into [target] if it isn't already there; no-op otherwise. */
    private fun reparentInto(view: android.view.View?, target: android.view.ViewGroup) {
        val view = view ?: return
        val currentParent = view.parent as? android.view.ViewGroup
        if (currentParent === target) return
        currentParent?.removeView(view)
        // Append (not index 0): in the content-frame target the video surface is
        // the first child, so a 0-index insert would render captions behind it.
        // For the screen-pinned host the host has no other children, so the index
        // is irrelevant.
        target.addView(
            view,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun setExternalSubtitleHost(host: android.view.ViewGroup?) {
        externalSubtitleHost = host
        // Re-parent into the new target immediately when a surface exists. Called
        // on the main thread by the screen's AndroidView factory/onRelease.
        playerView?.let { pv -> pv.post { reparentSubtitleViews(pv) } }
        // If the host is being detached (host == null) the screen's onRelease is
        // about to tear the host down; reparent back into the content frame so
        // the views don't orphan if the PlayerView outlives the host (it won't in
        // practice — both share key(currentEngine) lifetime — but stay safe).
        if (host == null) {
            playerView?.let { pv -> pv.post { reparentSubtitleViews(pv) } }
        }
    }

    override fun applySubtitleStyleToView(view: View, style: SubtitleStyle) {
        val pv = (view as? PlayerView) ?: playerView ?: return
        val bgAlpha = (style.backgroundOpacity * 255).toInt()
        val bgColorWithAlpha = (bgAlpha shl 24) or
            (com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver.resolveBackgroundColor(style) and 0x00FFFFFF)
        pv.subtitleView?.let { sv ->
            if (style.applyCustomStyle) {
                sv.setApplyEmbeddedStyles(false)
                sv.setStyle(
                    CaptionStyleCompat(
                        com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver.resolveTextColor(style),
                        bgColorWithAlpha,
                        Color.TRANSPARENT,
                        when (style.edgeType) {
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                            else -> CaptionStyleCompat.EDGE_TYPE_NONE
                        },
                        com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleColorResolver.resolveEdgeColor(style),
                        fontProvider.typefaceFor(style),
                    )
                )
                sv.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, style.fontSize.toFloat())
            } else {
                // Keep embedded colors/positioning but force a stable font size.
                sv.setApplyEmbeddedStyles(true)
                // Without this, cues that carry an embedded font size make Media3
                // size text as a fraction of the (full-screen) SubtitleView height.
                // On rotation to portrait that height grows dramatically and the
                // captions become huge, while mpv (libass, sizes against the video
                // frame) stays correct.
                sv.setApplyEmbeddedFontSizes(false)
                // ExoPlayer's default branch uses the engine's documented divergence
                // from the shared table (SubtitleRenderDefaults.EXOPLAYER_OVERRIDE):
                // 18sp + DROP_SHADOW rather than the 24sp + OUTLINE other engines use.
                // The divergence is a conscious Media3-specific choice (see the
                // override's KDoc), not silent drift.
                sv.setStyle(
                    CaptionStyleCompat(
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        when (com.raulshma.jellyplay.core.model.SubtitleRenderDefaults.EXOPLAYER_OVERRIDE.edgeType) {
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                            com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                            else -> CaptionStyleCompat.EDGE_TYPE_NONE
                        },
                        Color.BLACK,
                        android.graphics.Typeface.SANS_SERIF
                    )
                )
                sv.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, DEFAULT_SUBTITLE_SIZE_SP)
            }
            sv.setBottomPaddingFraction(style.verticalPosition)
            // Track-type visibility toggle: when the active subtitle track is
            // ASS, libass owns rendering via [assOverlayView] and the native
            // SubtitleView must be hidden (and vice-versa). Non-ASS sessions and
            // sessions without an ASS handler always show the native view.
            sv.setVisibility(if (activeTrackIsAss && assOverlayView != null) View.GONE else View.VISIBLE)
        }

        // libass (AssSubtitleView) styling. ass-media 0.4.0 renders ASS via
        // libass and exposes NO colour/edge override API, so FORCE (colours,
        // borders, edges) remains DEGRADED to as-authored on this engine — only
        // mpv honours FORCE fully. SCALE (font size) IS now honoured: ass-kt
        // 0.4.0's AssRender.setFontScale is reachable at compile time (direct
        // dependency), and AssHandler.render hands back the live AssRender. The
        // render is created lazily on the first frame (createRenderIfNeeded),
        // so it may be null here until playback starts — guard accordingly.
        // Non-ASS tracks use the native SubtitleView path sized above.
        if (activeTrackIsAss && assEnabledForSession) {
            runCatching {
                assHandler?.render?.setFontScale(style.fontSize / 24f)
            }.onFailure { e ->
                android.util.Log.w(TAG, "setFontScale on AssRender failed", e)
            }
        }
        // The overlay renders ASS; its visibility is driven by [activeTrackIsAss]
        // (set in selectTrack), mirrored by the native SubtitleView toggling above
        // so exactly one of the two is shown.
        assOverlayView?.setVisibility(if (activeTrackIsAss) View.VISIBLE else View.GONE)
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
    override val durationMs: Long
        get() {
            // Prefer the ExoPlayer-resolved duration when available; fall back
            // to the server-reported runTimeTicks, which for HLS/transcoded
            // streams is the only accurate total-runtime source (ExoPlayer's
            // `duration` is `C.TIME_UNSET` until/ unless the manifest advertises
            // a finite VOD duration — Jellyfin transcode manifests often do
            // not, leaving the seek bar and end-detection without a duration).
            // Mirrors [MpvPlayerEngine.durationMs].
            val engine = player?.duration ?: C.TIME_UNSET
            return if (engine != C.TIME_UNSET && engine > 0L) engine else serverDurationMs
        }
    override val playbackSpeed: Float get() = player?.playbackParameters?.speed ?: 1f
    override val audioSessionId: Int get() = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    override val positionFlow: Flow<Long> = callbackFlow {
        val p = player ?: run { close(); return@callbackFlow }
        val posListener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                runCatching { trySend(p.currentPosition) }
            }
            // Note: onPlaybackStateChanged intentionally NOT overridden here.
            // The engine's primary listener and EnginePositionTicker already
            // translate state into _playbackState and emit on the play↔pause
            // edge; the previous redundant override only added trySend traffic
            // (Runnable/continuation allocations) on every state change for no
            // net benefit. onPositionDiscontinuity is retained for seeks.
        }
        p.addListener(posListener)
        trySend(p.currentPosition)

        // The polling loop (bounded paused-wait, play↔pause edge detection) is
        // shared via [EnginePositionTicker]; this engine keeps its own
        // `Player.Listener` above for immediate discontinuity notifications.
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = _isPlaying,
            isCurrentlyPlaying = { p.isPlaying },
            onActive = {
                trySend(p.currentPosition)
                val buffered = p.bufferedPosition.coerceAtLeast(0L)
                if (buffered != _bufferedPositionMs.value) {
                    _bufferedPositionMs.value = buffered
                }
                if (_videoStatsEnabled.value) {
                    updateVideoStats()
                }
            },
        ).launch()

        awaitClose {
            ticker.cancel()
            try { p.removeListener(posListener) } catch (_: Exception) {}
        }
    }.conflate() // only the most-recent position is meaningful; drop stale ticks

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
            videoHdrType = videoFormat?.let { f ->
                val ci = f.colorInfo
                // Dolby Vision is identified by codec/MIME, not transfer
                // function — ST2084/PQ is shared by HDR10 and DV, so the prior
                // mapping mislabeled every HDR10 stream as "Dolby Vision".
                when {
                    f.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
                    ci?.hdrStaticInfo != null -> "HDR10"
                    ci?.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
                    ci?.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "HDR10"
                    ci?.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_SDR -> null
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
        audioEffectChain.apply(audioSessionId, currentConfig.audioEffects, currentNormalizationGain)
    }

    private fun releaseAudioEffects() {
        audioEffectChain.release()
    }

    /**
     * Recreates the active media period without releasing the player so
     * [DefaultAudioSink] re-runs AudioProcessor.configure(). This is required
     * when an effect changes processor activation or channel count (for
     * example, enabling 5.1 -> stereo downmix); merely mutating the processor
     * instance cannot alter Media3's already-configured pipeline.
     */
    private fun reconfigureAudioPipeline() {
        val exo = player ?: return
        val mediaItem = currentMediaItem ?: return
        val positionMs = exo.currentPosition
        val wasPlaying = exo.isPlaying

        exo.setMediaItem(mediaItem, positionMs)
        exo.prepare()
        if (wasPlaying) exo.play()
    }

    /**
     * The id of the currently *selected* text track group, or null when none is
     * selected. Used by [onTracksChanged] to detect a subtitle track switch and
     * reset the accumulated cue list. Mirrors the id logic in [buildTracks].
     */
    private fun currentSelectedTextTrackId(): String? {
        val p = player ?: return null
        return p.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_TEXT && (0 until it.length).any { i -> it.isTrackSelected(i) } }
            ?.let { group ->
                group.getTrackFormat(0).id?.takeIf { it.isNotBlank() }
                    ?: "SUBTITLE_${group.mediaTrackGroup.hashCode()}"
            }
    }

    /**
     * Maps the cues in [cueGroup] to [TimedCue]s and folds them into the
     * accumulated list via [mergeAccumulatedCues]. ExoPlayer surfaces only the
     * *currently displayed* cue(s) per callback, so the preview is built
     * incrementally as subs play — it covers the played range only (no
     * ahead-lookahead for forward offsets).
     */
    private fun accumulateCues(cueGroup: CueGroup) {
        // Malformed-text-track guard: a single onCues batch carrying an
        // implausibly large number of simultaneous cues is the signature of a
        // broken SRT/VTT (e.g. timestamp/index lines parsed as simultaneous
        // cues). Media3 would hand all of them to SubtitleView, which lays
        // them out every frame — the "subtitle wall" that freezes the UI and
        // crashes the app. Disable the text track at source and notify.
        if (isPathologicalCueBatch(cueGroup.cues.size)) {
            disableTextTrackForMalformedCues()
            return
        }
        // Once disabled, ignore further callbacks until the user selects a
        // different subtitle track (onTracksChanged clears the latch).
        if (subtitleTrackAutoDisabled) return
        val posUs = cueGroup.presentationTimeUs
        val mapped = cueGroup.cues.mapNotNull { cue: Cue ->
            val text = cue.text
            if (text.isNullOrBlank()) null else TimedCue(posUs, Long.MAX_VALUE, text)
        }
        if (mapped.isEmpty()) return
        _currentCues.value = mergeAccumulatedCues(_currentCues.value, mapped)
    }

    /**
     * Disables the text renderer (mirroring the `selectTrack(SUBTITLE, -1)`
     * disable path), clears the accumulated cue list, latches the auto-disable
     * guard, and emits a [SubtitleEvent.MalformedTrackDisabled] so the UI can
     * tell the user subs were turned off. Called from [accumulateCues] when a
     * pathological cue batch is detected. Runs on the player thread (onCues).
     */
    private fun disableTextTrackForMalformedCues() {
        val selector = trackSelector ?: return
        android.util.Log.w(TAG, "Disabling subtitle track: malformed cue batch detected (${">"}$MAX_INCOMING_CUES_PER_BATCH simultaneous cues)")
        subtitleTrackAutoDisabled = true
        _currentCues.value = emptyList()
        try {
            val params = selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            selector.setParameters(params)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to disable malformed subtitle track", e)
        }
        _subtitleEvents.tryEmit(SubtitleEvent.MalformedTrackDisabled)
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
                val selFlags = format.selectionFlags
                val info = TrackLabelInfo(
                    title = format.label,
                    language = format.language,
                    codec = format.sampleMimeType,
                    channels = if (trackType == TrackType.AUDIO) format.channelCount else null,
                    // Forced/default are selection flags in Media3 (not role flags).
                    isForced = selFlags and C.SELECTION_FLAG_FORCED != 0,
                    isDefault = selFlags and C.SELECTION_FLAG_DEFAULT != 0,
                    // SDH/closed-caption tracks carry ROLE_FLAG_CAPTION.
                    isHearingImpaired = format.roleFlags and C.ROLE_FLAG_CAPTION != 0,
                )
                result.add(
                    MediaTrack(
                        // For side-loaded subtitles Media3 propagates the
                        // MediaItem.SubtitleConfiguration id (== SubtitleSource.id)
                        // into the track format, so prefer it over the synthetic
                        // group index — the subtitle-sync preview resolves the
                        // active external source by that id.
                        id = format.id?.takeIf { it.isNotBlank() }
                            ?: "${trackType.name}_${groupIndex}",
                        index = groupIndex,
                        label = TrackLabelFormatter.primary(info),
                        language = format.language,
                        isSelected = isSelected,
                        type = trackType,
                        badges = TrackLabelFormatter.badges(info),
                    )
                )
                groupIndex++
            }
        }

        processType(C.TRACK_TYPE_AUDIO, TrackType.AUDIO)
        processType(C.TRACK_TYPE_TEXT, TrackType.SUBTITLE)

        return result
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

/**
 * Maps a Media3 [PlaybackException] onto the [EngineError] taxonomy so the UI
 * can distinguish retryable from fatal failures. Previously every error
 * collapsed to `EngineError.Unknown(raw)` and the retry / switch-engine
 * affordances (gated on `retryable` / specific subtypes) could never fire.
 *
 * The mapping lives in the shared engine-contract module
 * ([toEngineError][com.raulshma.jellyplay.feature.player.video.engine.toEngineError])
 * so any Media3-based engine maps codes through one table.
 */
