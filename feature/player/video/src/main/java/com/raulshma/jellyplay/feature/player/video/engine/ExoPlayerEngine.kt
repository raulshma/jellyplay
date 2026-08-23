package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
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
import com.raulshma.jellyplay.core.data.playback.isSessionKeyedUrl
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
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleRenderDefaults
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.subtitle.AssSupport
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleMimeMapper
import io.github.peerless2012.ass.media.AssHandler
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
 * Sourced from [SubtitleRenderDefaults.EXOPLAYER_OVERRIDE]
 * — ExoPlayer's documented divergence from the shared 24sp default. See that
 * override's KDoc for why ExoPlayer keeps a distinct size/edge here.
 */
private val DEFAULT_SUBTITLE_SIZE_SP =
    SubtitleRenderDefaults.EXOPLAYER_OVERRIDE.fontSizeSp.toFloat()
private const val TAG = "ExoPlayerEngine"

class ExoPlayerEngine(
    private val context: Context,
    private val streamingOkHttpClient: OkHttpClient,
    bandwidthMeter: DefaultBandwidthMeter? = null,
    private val fontProvider: FontProvider,
    // Nullable + defaulted so non-Hilt constructions (contract tests) compile
    // unchanged; a null cache simply disables byte caching (passthrough).
    private val videoStreamCache: VideoStreamCache? = null,
) : ReloadablePlayerEngine(context) {

    @Volatile
    private var cachedVolume: Float = 1f

    @Volatile
    private var lastAppliedAudioSessionId: Int = -1

    // Mirrors MPV/LibVLC: remembers play state across Activity pause so the
    // engine pauses on lock/home (unless background audio is enabled) and
    // resumes only if it was actually playing. Without this override the
    // inherited PlayerLifecycleCallbacks default is a no-op, so ExoPlayer
    // would keep playing audio silently when the screen locks.
    @Volatile
    private var wasPlayingBeforeActivityPause = false

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
    // SubtitleView/AssSubtitleView visibility toggle in applySubtitleStyle.
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
            // Keep the ASS-render toggle in lockstep with the OBSERVED
            // selection, not just the selectTrack() command: the selector can
            // activate a side-loaded ASS track on its own (preferredTextLanguage
            // match or the stream's default flag) without engine.selectTrack
            // ever running. A stale activeTrackIsAss would then keep the libass
            // overlay hidden and the (blank — its text renderer was replaced)
            // native SubtitleView shown for the whole session: the track plays,
            // nothing renders.
            if (assEnabledForSession) {
                val newlyAss = selectedTextTrackIsAss(tracks)
                if (newlyAss != activeTrackIsAss) {
                    activeTrackIsAss = newlyAss
                    // Diagnostics for the transcode side-load render chain: the
                    // flip plus overlay presence pinpoint where the pipeline
                    // stops when subtitles don't show.
                    Log.d(
                        TAG,
                        "ASS render toggle: activeTrackIsAss=$newlyAss, overlayView=${assOverlayView != null}",
                    )
                    applySubtitleStyle(currentConfig.subtitleStyle)
                }
            }
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

    /**
     * Construction-relevant inputs of the last player build. When a new
     * [load] carries an identical set, the existing player is reused with a
     * bare [androidx.media3.common.Player.setMediaItem] + prepare — the
     * common binge-watch/autoplay case where renderers factory (decoder mode,
     * fallback, audio-processor chain), LoadControl buffers, DRM hook, auth
     * and the ASS session shape are all unchanged. Any delta takes the full
     * teardown/rebuild path, so behavior stays identical to a fresh engine.
     */
    private data class LoadRebuildInputs(
        val decoderMode: com.raulshma.jellyplay.core.model.DecoderMode,
        val exoCfg: ExoPlayerEngineConfig,
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val serverUrl: String?,
        val authToken: String?,
        val headers: Map<String, String>,
        val assSession: Boolean,
        val pauseOnAudioFocusLoss: Boolean,
        // The provider itself (not its product): providers have no equals,
        // so data-class equality degrades to identity — same instance means
        // same DRM hook, different instance forces the rebuild path.
        val drmProvider: EngineDrmSessionManagerProvider?,
        // Whether the data source chain gets the byte-level [VideoStreamCache]
        // wrapper. Part of the equality set because the wrapper is baked into
        // the player's MediaSourceFactory: a reused player keeps its existing
        // chain, so an eligibility flip between items (direct play → transcode,
        // clear → DRM) MUST take the teardown/rebuild path instead of leaking
        // the cached chain onto a non-cacheable item (or vice versa).
        val streamCacheEligible: Boolean,
    )

    private var lastRebuildInputs: LoadRebuildInputs? = null

    override fun load(request: PlaybackRequest) {
        ensurePlayerThread("load")
        recreateEngineScopeIfInactive()

        currentNormalizationGain = request.normalizationGain

        val exoCfg = (currentConfig.engineSpecific as? ExoPlayerEngineConfig) ?: ExoPlayerEngineConfig()
        val assForRequest = AssSupport.hasAssSubtitles(request)
        val streamCacheEligible = isStreamCacheEligible(request)
        val inputs = LoadRebuildInputs(
            decoderMode = currentConfig.decoderMode,
            exoCfg = exoCfg,
            minBufferMs = request.minBufferMs,
            maxBufferMs = request.maxBufferMs,
            serverUrl = request.serverUrl,
            authToken = request.authToken,
            headers = request.headers,
            assSession = assForRequest,
            pauseOnAudioFocusLoss = currentConfig.pauseOnAudioFocusLoss,
            drmProvider = currentConfig.drmSessionManagerProvider,
            streamCacheEligible = streamCacheEligible,
        )

        val existingPlayer = player
        if (existingPlayer != null && inputs == lastRebuildInputs) {
            reusePlayerForRequest(existingPlayer, request, exoCfg, assForRequest)
            return
        }

        release()
        // release() cancels engineScope, and positionFlow's EnginePositionTicker
        // launches on that scope when the flow is first collected — a dead scope
        // means the ticker loop never runs and the seek bar freezes at its seed
        // position. Revive it here so load() always returns with a live scope.
        recreateEngineScopeIfInactive()
        lastRebuildInputs = inputs

        val selector = DefaultTrackSelector(context)
        applyRequestTrackSelection(selector, request, exoCfg)
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
        assEnabledForSession = assForRequest
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

        val dataSourceFactory = createAuthenticatedDataSourceFactory(
            serverUrl = request.serverUrl,
            token = request.authToken,
            headers = request.headers,
            enableStreamCache = streamCacheEligible,
        )

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
            // Own clock-pump factory instead of ass-media's AssRenderersFactory:
            // the library's appended AssRenderer derives the render clock from
            // the renderer position minus a hardcoded 10¹² µs base, and the
            // player's own position cannot be queried from the playback thread —
            // see [AssClockPumpRenderersFactory] for the delta-integration
            // approach used instead.
            val renderers = AssClockPumpRenderersFactory(
                assHandler!!,
                baseRenderersFactory,
                startMediaTimeUs = request.startPositionMs * 1000L,
            )
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
        // directory path. FontProvider's cache serves the bytes — the startup
        // pre-warm loads them on IO once per process, so this Main-thread path
        // (which runs on every load()/track change) does no disk I/O.
        assHandler?.let { handler ->
            handler.init(exo)
            runCatching {
                fontProvider.cachedFontBytes().forEach { (name, bytes) ->
                    runCatching { handler.addFont(name, bytes) }
                }
            }
        }

        // Build media item
        val subtitleConfigs = buildSubtitleConfigurations(request)
        val mediaItem = buildRequestMediaItem(request, subtitleConfigs)
        attachMediaItemAndPrepare(exo, mediaItem, subtitleConfigs, request)

        applyAudioEffects()
    }

    /**
     * Fast path for an unchanged-config reload on a live player (the
     * binge-watch/autoplay case): swap the media item and prepare instead of
     * tearing down and rebuilding the whole ExoPlayer — no DefaultRenderers
     * reflection scan, no re-created LoadControl/media-source factories, and
     * the player keeps its state (buffers may partially survive where the
     * timeline allows). Rendered behavior matches a fresh engine; only
     * construction latency and the inter-item rebuffer disappear.
     */
    private fun reusePlayerForRequest(
        exo: ExoPlayer,
        request: PlaybackRequest,
        exoCfg: ExoPlayerEngineConfig,
        assForRequest: Boolean,
    ) {
        assEnabledForSession = assForRequest
        // Reset the active-track ASS flag: the reused engine must not carry
        // over stale activeTrackIsAss=true from a prior ASS track.
        activeTrackIsAss = false
        // Mirror the per-item state resets of release()+rebuild for the fields
        // the fresh path re-establishes.
        resetItemScopedState()

        trackSelector?.let { applyRequestTrackSelection(it, request, exoCfg) }

        val subtitleConfigs = buildSubtitleConfigurations(request)
        val mediaItem = buildRequestMediaItem(request, subtitleConfigs)
        attachMediaItemAndPrepare(exo, mediaItem, subtitleConfigs, request)

        applyAudioEffects()
    }

    /**
     * The per-item state both [release] and [reusePlayerForRequest] must
     * clear: stale cues from the previous item would linger into the new
     * one's loading window, and the previous episode's buffered position,
     * decoder counters, and "Stats for Nerds" snapshot would surface briefly
     * on the new item.
     */
    private fun resetItemScopedState() {
        _currentCues.value = emptyList()
        _availableTracks.value = emptyList()
        lastSelectedTextTrackId = null
        subtitleTrackAutoDisabled = false
        resetStatsGuard()
        videoDecoderCounters = null
        wasPlayingBeforeActivityPause = false
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
    }

    /**
     * Common tail of the rebuild ([load]) and reuse ([reusePlayerForRequest])
     * paths: record the item-scoped state, then start playback with the resume
     * position folded into a single prepare. With a non-zero subtitle delay
     * configured at boot (typically a persisted per-item correction), the
     * onConfigChanged → refreshSubtitlesForOffsetChange reload never ran —
     * updateConfig fires before load() creates the player, so the reload
     * no-ops and the saved delay only took effect once re-adjusted
     * mid-playback. Reload once at the requested start position so Media3
     * re-parses cues through the OffsettingSubtitleParserFactory with the
     * delay applied — the same proven path the live slider uses.
     * setMediaItem(item, startPosMs) folds the seek into the reload so there
     * is a single prepare (no double-buffer) and the resume position is
     * preserved; the no-delay path seeks after prepare instead.
     */
    private fun attachMediaItemAndPrepare(
        exo: ExoPlayer,
        mediaItem: MediaItem,
        subtitleConfigs: List<MediaItem.SubtitleConfiguration>,
        request: PlaybackRequest,
    ) {
        currentMediaItem = mediaItem
        serverDurationMs = request.serverDurationMs
        currentSubtitleConfigs.clear()
        currentSubtitleConfigs.addAll(subtitleConfigs)
        if (currentConfig.subtitleDelayMs != 0L) {
            exo.setMediaItem(mediaItem, request.startPositionMs)
            exo.prepare()
            exo.play()
        } else {
            exo.setMediaItem(mediaItem)
            exo.prepare()
            if (request.startPositionMs > 0) {
                exo.seekTo(request.startPositionMs)
            }
            exo.play()
        }
    }

    /**
     * Request-scoped track-selection parameters. Built from bare defaults —
     * not `buildUponParameters()` — so a reused selector never carries
     * preferred languages, a bitrate cap, or overrides from the previous
     * item; a fresh selector in the rebuild path gets the same base.
     */
    private fun applyRequestTrackSelection(
        selector: DefaultTrackSelector,
        request: PlaybackRequest,
        exoCfg: ExoPlayerEngineConfig,
    ) {
        val params = DefaultTrackSelector.Parameters.Builder(context)
        if (request.preferredAudioLanguage != null) {
            params.setPreferredAudioLanguage(request.preferredAudioLanguage)
        }
        if (request.preferredSubtitleLanguage != null) {
            params.setPreferredTextLanguage(request.preferredSubtitleLanguage)
        }
        if (request.maxVideoBitrate != null) {
            // Local val captures the non-null value: maxVideoBitrate now lives
            // in :feature:player:core (different module), so Kotlin can no
            // longer smart-cast the cross-module public property. We are inside
            // the null-check branch, so !! is provably safe.
            params.setMaxVideoBitrate(request.maxVideoBitrate!!)
        }
        if (exoCfg.preferredVideoMimeTypes.isNotEmpty()) {
            params.setPreferredVideoMimeTypes(*exoCfg.preferredVideoMimeTypes.toTypedArray())
        }
        if (exoCfg.audioOffloadMode != com.raulshma.jellyplay.core.model.ExoAudioOffloadMode.DISABLED) {
            // Media3 surfaces audio offload through the track selector, not the
            // ExoPlayer.Builder. Map the pref onto AudioOffloadPreferences and
            // push it into the parameters so the selector prefers offload-decodable
            // tracks when the user has enabled (or required) the mode.
            params.setAudioOffloadPreferences(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(exoCfg.audioOffloadMode.value)
                    .build(),
            )
        }
        selector.setParameters(params)
    }

    private fun buildSubtitleConfigurations(
        request: PlaybackRequest,
    ): List<MediaItem.SubtitleConfiguration> =
        request.externalSubtitles.mapNotNull { sub ->
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

    private fun buildRequestMediaItem(
        request: PlaybackRequest,
        subtitleConfigs: List<MediaItem.SubtitleConfiguration>,
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder().setTitle(request.title)
        if (request.artworkUri != null) {
            metadataBuilder.setArtworkUri(Uri.parse(request.artworkUri))
        }

        return MediaItem.Builder()
            .setUri(request.uri)
            .apply {
                // MIME-type hint so DefaultMediaSourceFactory selects the right
                // MediaSource (HlsMediaSource vs progressive extractor):
                //  - Caller-provided hint wins (offline container sniffing).
                //  - Transcoded streams are served as Jellyfin HLS master
                // playlists (master.m3u8) — without the hint ExoPlayer
                // relies on extension detection, which is fragile when the
                // query string trails the .m3u8 path. The official Jellyfin
                // Android client pins APPLICATION_M3U8 the same way. This is
                // also what makes native HLS seeking (segment + EXTINF
                // resolution) reliable on a transcode.
                val inferredMime = when {
                    request.mimeType != null -> request.mimeType
                    isHlsRequest(request) -> MimeTypes.APPLICATION_M3U8
                    else -> null
                }
                inferredMime?.let { setMimeType(it) }
            }
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * Builds the media [DataSource.Factory] chain:
     * `[VideoStreamCache] → auth ResolvingDataSource → OkHttp/Default`.
     *
     * Route media streams through the shared app OkHttp stack rather than a
     * standalone HttpURLConnection: the injected client carries the shared
     * connection pool, the BandwidthInterceptor that feeds adaptive bitrate
     * selection, and HTTP/2 multiplexing — so the highest-bandwidth traffic
     * reuses the same wiring as every other request. OkHttp follows
     * cross-protocol redirects by default, and the "streaming" qualifier
     * already sets a >=30s read timeout. The ResolvingDataSource auth wrapper
     * composes unchanged.
     *
     * Constraint: the shared client's OkHttp disk Cache does NOT cover media
     * bytes — ExoPlayer's progressive source issues `206 Partial Content`
     * range requests, which OkHttp's Cache never stores (it only caches
     * complete 200 responses). Backward seeks and re-opened segments would
     * therefore re-fetch from the network. [enableStreamCache] (see
     * [isStreamCacheEligible]) adds the [VideoStreamCache] CacheDataSource
     * layer around the HTTP base factory for content-stable URLs only, which
     * serves and fills a byte-range cache with volatile-param-stripped keys.
     * The layer sits below [DefaultDataSource]'s scheme routing (and the auth
     * resolver above it), so side-loaded local/content subtitle URIs bypass
     * the cache entirely — only media bytes are ever pinned.
     */
    private fun createAuthenticatedDataSourceFactory(
        serverUrl: String?,
        token: String?,
        headers: Map<String, String>,
        enableStreamCache: Boolean,
    ): DataSource.Factory {
        val httpDataSourceFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent("JellyPlay")
            .setDefaultRequestProperties(headers)

        // Cache the HTTP base only: DefaultDataSource routes file/content/asset
        // URIs through its own non-base sources, keeping them out of the cache.
        // Passthrough (returns the upstream unchanged) when the cache
        // directory cannot be opened — playback never breaks.
        val baseFactory: DataSource.Factory = if (enableStreamCache && videoStreamCache != null) {
            videoStreamCache.getCacheDataSourceFactory(httpDataSourceFactory)
        } else {
            httpDataSourceFactory
        }

        var factory: DataSource.Factory = DefaultDataSource.Factory(context, baseFactory)

        val authority = serverUrl?.let { Uri.parse(it).authority }
        if (authority != null && token != null) {
            factory = ResolvingDataSource.Factory(factory) { dataSpec ->
                if (dataSpec.uri.authority.equals(authority, ignoreCase = true)) {
                    dataSpec.withRequestHeaders(
                        mapOf("X-Emby-Token" to token) + dataSpec.httpRequestHeaders
                    )
                } else {
                    dataSpec
                }
            }
        }

        return factory
    }

    /**
     * Scopes the video byte cache to content-stable, cacheable requests only:
     *
     *  - **DRM**: a configured [EngineDrmSessionManagerProvider] skips the
     *    cache entirely — DRM content must not have ciphertext (or license
     *    boundaries) pinned in a shared byte cache, and offline license
     *    semantics are out of scope here.
     *  - **Direct Play / Direct Stream only**: transcode URLs are
     *    session-keyed (a fresh PlaybackInfo re-POST mints a new one), so
     *    caching them would churn the LRU for single-use entries. The same
     *    rejection applies to DIRECT_STREAM URLs resolved from the server's
     *    transcode endpoint: they carry `PlaySessionId`/`TranscodingJobId`
     *    even when the play method says direct stream, while the client-built
     *    `?static=true` direct URLs never do.
     *  - **No HLS**: Jellyfin serves transcodes/remuxes as `.m3u8` master
     *    playlists (mirrors the MIME inference in [buildRequestMediaItem]);
     *    their segment URLs churn the cache the same way.
     *  - **No live streams**: `LiveStreamId` direct streams grow at the live
     *    edge — cached spans would never complete and go stale.
     *  - **HTTP(S) only**: local/offline files must not copy bytes into the
     *    cache directory.
     *
     * A `null` [PlaybackRequest.playMethod] (no server resolution) is treated
     * as non-cacheable. Offline/local files carry a `DIRECT_PLAY` default
     * (the PlayerSessionManager offline path) and are excluded by the
     * HTTP(S) check above.
     */
    private fun isStreamCacheEligible(request: PlaybackRequest): Boolean {
        if (currentConfig.drmSessionManagerProvider != null) return false
        val uri = request.uri
        if (!uri.startsWith("http", ignoreCase = true)) return false
        val playMethod = request.playMethod ?: return false
        if (playMethod != PlayMethod.DIRECT_PLAY && playMethod != PlayMethod.DIRECT_STREAM) return false
        if (isHlsRequest(request)) return false
        // Session-scoped params (PlaySessionId/TranscodingJobId/LiveStreamId)
        // are defined once in SESSION_SCOPED_QUERY_PARAMS and rejected here —
        // they are never key-normalized: VideoStreamCache's key factory keeps
        // them intact as the backstop.
        if (isSessionKeyedUrl(uri)) return false
        return true
    }

    /**
     * True when the request points at an HLS playlist: an explicit
     * `application/vnd.apple.mpegurl` MIME hint or a `.m3u8` path (the query
     * string can trail the extension, defeating extension detection). Single
     * source for the MIME inference in [buildRequestMediaItem] and the
     * stream-cache rejection in [isStreamCacheEligible].
     */
    private fun isHlsRequest(request: PlaybackRequest): Boolean =
        request.mimeType == MimeTypes.APPLICATION_M3U8 ||
            request.uri.contains(".m3u8", ignoreCase = true)

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
        releaseAudioEffects()
        cachedVolume = 1f
        lastUnmuteVolume = 1f
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        resetItemScopedState()

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

    // Activity lifecycle bridge: unlike MPV/LibVLC, ExoPlayer does not detach
    // views here (PlayerView handles the surface lifecycle). Pausing keeps the
    // seek position; onActivityResume restores play only if it was active.
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

    override fun stop() = runOnPlayerThread { player?.stop() }
    override fun seekTo(positionMs: Long) = runOnPlayerThread { player?.seekTo(positionMs) }
    override fun setPlaybackSpeed(speed: Float) = runOnPlayerThread { player?.setPlaybackSpeed(speed) }

    override val volume: Float get() = cachedVolume

    override fun setVolume(value: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val clamped = clamp01(value)
        rememberUnmuteVolumeIfAudible(clamped)
        p.volume = clamped
        MediaStreamVolume.setNormalized(context, clamped)
    }

    override fun increaseVolume(delta: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val next = (p.volume + delta).coerceAtMost(1f)
        rememberUnmuteVolumeIfAudible(next)
        p.volume = next
        MediaStreamVolume.setNormalized(context, next)
    }

    override fun decreaseVolume(delta: Float) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        val next = (p.volume - delta).coerceAtLeast(0f)
        rememberUnmuteVolumeIfAudible(next)
        p.volume = next
        MediaStreamVolume.setNormalized(context, next)
    }

    override fun setMuted(muted: Boolean) = runOnPlayerThread {
        val p = player ?: return@runOnPlayerThread
        if (muted) {
            snapshotSystemVolumeForMute()
            p.volume = 0f
            MediaStreamVolume.setNormalized(context, 0f)
        } else {
            val target = unmuteTarget()
            p.volume = 1f
            MediaStreamVolume.setNormalized(context, target)
        }
    }

    override fun snapshotIsPlaying(): Boolean = player?.isPlaying ?: super.snapshotIsPlaying()

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        // decoderMode change: decoding changes require a reload, which is
        // handled by the upper layer recreating the player — nothing to do here.
        //
        // subtitleDelayMs change: the OffsettingSubtitleParserFactory's
        // wrapper reads currentConfig.subtitleDelayMs on each parse() call. A
        // delay adjustment is applied live by refreshSubtitlesForOffsetChange(),
        // which reloads the current MediaItem so Media3 re-parses the subtitles
        // through the offset wrapper with the new value. mpv and libVLC
        // re-evaluate the delay continuously (sub-delay / setSpuDelay), so no
        // reload is needed there.

        if (oldConfig.audioEffects != newConfig.audioEffects) {
            applyAudioEffects()
            if (requiresAudioPipelineReconfiguration(oldConfig.audioEffects, newConfig.audioEffects)) {
                reconfigureAudioPipeline()
            }
        }

        if (oldConfig.subtitleStyle != newConfig.subtitleStyle) {
            applySubtitleStyle(newConfig.subtitleStyle)
            // The offset lives on SubtitleStyle.offsetMs, so a delay change also
            // flows through here. Media3 caches parsed cues for the active text
            // track and will not re-invoke the OffsettingSubtitleParserFactory
            // for the cached sample — so the overlay would look like it does
            // nothing until the next reload/seek. Rebuild the media item so the
            // subtitle is parsed through the offset wrapper with the new value.
            if (oldConfig.subtitleStyle.offsetMs != newConfig.subtitleStyle.offsetMs) {
                refreshSubtitlesForOffsetChange()
            }
        }

        if (oldConfig.pauseOnAudioFocusLoss != newConfig.pauseOnAudioFocusLoss) {
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player?.setAudioAttributes(audioAttrs, newConfig.pauseOnAudioFocusLoss)
        }
    }

    /**
     * Reloads the current MediaItem so Media3 re-parses the subtitles through
     * the [OffsettingSubtitleParserFactory] with the new offset.
     *
     * Media3 caches parsed cues for the active text track: neither a track
     * reselection nor a same-position seek reliably re-invokes the parser for
     * the already-parsed sample (the two-phase disable/re-enable collapses into
     * a net-zero parameter diff before the renderer re-evaluates; a small seek
     * reuses the cached sample). The only reliable nudge is a media-period
     * reset, which tears down the text renderer stream and rebuilds it from
     * scratch — re-running the offset wrapper.
     *
     * Mirrors [reconfigureAudioPipeline] / [addExternalSubtitle]: preserve the
     * position and play state, then [setMediaItem] + [prepare] + resume. The
     * [SubtitleDelayOverlay]'s 250 ms flush debounce coalesces rapid taps into
     * fewer [VideoPlayerViewModel.setSubtitleDelay] calls, and that method
     * further debounces the engine apply (~500 ms) so a whole fine-tune burst
     * triggers a single reload / rebuffer.
     */
    private fun refreshSubtitlesForOffsetChange() {
        val exo = player ?: return
        val mediaItem = currentMediaItem ?: return
        runOnPlayerThread {
            withPreservedPlayback { snap ->
                exo.setMediaItem(mediaItem, snap.positionMs)
                exo.prepare()
            }
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
        // an ASS/SSA track and flip the SubtitleView/AssSubtitleView visibility
        // accordingly. Shares the mime predicate with [selectedTextTrackIsAss]
        // via [isAssFormat] so both stay in lockstep.
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
                    groups[groupIndex].getTrackFormat(0).let(::isAssFormat)
                } else {
                    false
                }
            }
            if (newlyAss != activeTrackIsAss) {
                activeTrackIsAss = newlyAss
                applySubtitleStyle(currentConfig.subtitleStyle)
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
                // Hidden until an ASS track is selected (see applySubtitleStyle /
                // selectTrack toggle). Non-ASS tracks keep using the native SubtitleView.
                visibility = if (activeTrackIsAss) View.VISIBLE else View.GONE
            }
            assOverlayView = assView
            // Parent the ASS overlay via the unified reparent path — which
            // pins it to the content frame (the letterboxed video rectangle)
            // so libass's video-space coordinates map 1:1 onto the surface,
            // regardless of any screen-pinned host. Idempotent, so the initial
            // reparent above plus this one collapse to a single add.
            pv.post { reparentSubtitleViews(pv) }
        }

        applySubtitleStyle(currentConfig.subtitleStyle)
        return pv
    }

    /**
     * Parents the native [SubtitleView] and the libass [AssSubtitleView]
     * (when present) into their respective subtitle targets — which are NOT
     * necessarily the same view:
     *
     * - The native SubtitleView follows the *active* target: PlayerView's
     *   `exo_content_frame` (the letterboxed video rectangle) by default —
     *   while a direct child of the PlayerView its layout fractions (bottom
     *   padding, fractional text size) compute against the whole screen
     *   height, so in portrait the captions land in the bottom black bar
     *   instead of on the video — or the screen-pinned [externalSubtitleHost]
     *   when one is attached, so its relative-positioned cues stay on screen
     *   under zoom/crop.
     * - The libass AssSubtitleView ALWAYS stays in the content frame. ASS
     *   coordinates are absolute in the video's frame space and
     *   [AssHandler]'s frame size is the video surface size, so the overlay
     *   must cover exactly the letterboxed video rectangle. The screen-pinned
     *   host spans the full screen (e.g. 2280 px wide against a 1920-px
     *   16:9 video): hosting the overlay there draws every glyph shifted left
     *   by the letterbox amount. Under zoom the content frame scales inside
     *   the video's graphicsLayer, which is geometrically correct for
     *   video-space coordinates — the overlay stays glued to the video.
     *
     * Idempotent: a view already parented to its target is left alone, so
     * repeated layout-pass calls are a cheap no-op (see `lastFrameW/H` guard
     * in [createSurfaceView]).
     */
    private fun reparentSubtitleViews(pv: PlayerView) {
        val contentFrame: android.view.ViewGroup =
            pv.findViewById(androidx.media3.ui.R.id.exo_content_frame) ?: return
        val nativeTarget: android.view.ViewGroup = externalSubtitleHost ?: contentFrame
        reparentInto(pv.subtitleView, nativeTarget)
        reparentInto(assOverlayView, contentFrame)
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

    override fun applySubtitleStyle(style: SubtitleStyle) {
        val pv = playerView ?: return
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
                        when (SubtitleRenderDefaults.EXOPLAYER_OVERRIDE.edgeType) {
                            SubtitleEdgeType.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                            SubtitleEdgeType.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
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
                Log.w(TAG, "setFontScale on AssRender failed", e)
            }
        }
        // The overlay renders ASS; its visibility is driven by [activeTrackIsAss]
        // (set by selectTrack and by onTracksChanged's selection observation),
        // mirrored by the native SubtitleView toggling above so exactly one of
        // the two is shown.
        assOverlayView?.setVisibility(if (activeTrackIsAss) View.VISIBLE else View.GONE)
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        // Map the engine-neutral enum to the media3 resize mode here, inside the
        // only adapter that uses media3's AspectRatioFrameLayout, so no media3
        // constant crosses the engine seam.
        val resizeMode = when (ratio) {
            AspectRatio.FIT, AspectRatio.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3, AspectRatio.RATIO_21_9 ->
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        }
        playerView?.setResizeMode(resizeMode)
        val aspectValue = ratio.ratio
        if (aspectValue != null && aspectValue > 0f) {
            (playerView as? AspectRatioFrameLayout)?.setAspectRatio(aspectValue)
        } else {
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
        }
        p.addListener(posListener)
        trySend(p.currentPosition)

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
    }.conflate()

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

        publishStatsIfChanged(newStats)
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
        withPreservedPlayback { snap ->
            exo.setMediaItem(mediaItem, snap.positionMs)
            exo.prepare()
        }
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
        Log.w(TAG, "Disabling subtitle track: malformed cue batch detected (${">"}$MAX_INCOMING_CUES_PER_BATCH simultaneous cues)")
        subtitleTrackAutoDisabled = true
        _currentCues.value = emptyList()
        try {
            val params = selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            selector.setParameters(params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable malformed subtitle track", e)
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

        val newItem = item.buildUpon()
            .setSubtitleConfigurations(currentSubtitleConfigs.toList())
            .build()
        currentMediaItem = newItem

        withPreservedPlayback { snap ->
            exo.setMediaItem(newItem, snap.positionMs)
            exo.prepare()
        }
    }

}

/**
 * True when the currently *selected* text track is an ASS/SSA one — the signal
 * that libass owns rendering via [io.github.peerless2012.ass.media.AssSubtitleView]
 * and the native SubtitleView must hide.
 *
 * Matches the format BOTH on `sampleMimeType` and `codecs`: Media3 re-labels
 * source-parsed text tracks to `application/x-media3-cues` in `sampleMimeType`
 * and carries the original format mime in `codecs` — for a side-loaded ASS that
 * means `codecs == "text/x-ssa"` while `sampleMimeType` is the cues type
 * (observed on a Jellyfin HLS transcode).
 *
 * Derived from the OBSERVED [androidx.media3.common.Tracks] state, not from the
 * [ExoPlayerEngine.selectTrack] command: `DefaultTrackSelector` auto-selects
 * side-loaded text tracks on its own (preferredTextLanguage match or the
 * stream's default flag) without the command path ever running. Top-level so
 * the decision is unit-testable without an engine instance.
 */
internal fun selectedTextTrackIsAss(tracks: androidx.media3.common.Tracks): Boolean =
    tracks.groups.any { group ->
        group.type == C.TRACK_TYPE_TEXT &&
            (0 until group.length).any { group.isTrackSelected(it) } &&
            isAssFormat(group.getTrackFormat(0))
    }

/**
 * ASS/SSA format predicate shared by the selectTrack visibility toggle and
 * [selectedTextTrackIsAss]. Matches BOTH `sampleMimeType` and `codecs`:
 * Media3 re-labels source-parsed text tracks to
 * `application/x-media3-cues` in `sampleMimeType` and carries the original
 * format mime in `codecs` — checking only one field misses one of the two
 * delivery paths.
 */
internal fun isAssFormat(format: Format): Boolean =
    format.sampleMimeType == MimeTypes.TEXT_SSA || format.codecs == MimeTypes.TEXT_SSA

/**
 * Renderers factory for the libass overlay path: the base renderers plus a
 * no-sample "clock pump" that feeds the playback media time into
 * [AssHandler.videoTime]. This reproduces ass-media's `AssRenderersFactory`
 * with one decisive difference — how the clock is derived.
 *
 * The renderer-position `positionUs` passed to `render()` is NOT the media
 * position: on a Jellyfin HLS/TS transcode it carries a constant +10¹² µs base
 * (observed: `positionUs = 1_000_860_429_000` at an 860 s resume point).
 * ass-media's own `AssRenderer` subtracts exactly 10¹² — correct for that
 * stack, but an undocumented assumption. Querying the player's
 * `currentPosition` instead is not an option either: render() runs on the
 * playback thread and media3 throws "Player is accessed on the wrong thread".
 *
 * The pump therefore ANCHORS at the load's start position (media time, from
 * the PlaybackRequest) and integrates the renderer-position DELTAS — deltas
 * are base-independent by construction, so seeks (either direction), pauses
 * and playback-rate changes all track the media timeline without any
 * assumption about the stream's position base.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AssClockPumpRenderersFactory(
    private val assHandler: AssHandler,
    private val base: RenderersFactory,
    private val startMediaTimeUs: Long,
) : RenderersFactory {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> =
        base.createRenderers(
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        ).let { baseRenderers ->
            baseRenderers.toMutableList().also { it.add(AssClockPumpRenderer(assHandler, startMediaTimeUs)) }.toTypedArray()
        }

    override fun createSecondaryRenderer(
        renderer: Renderer,
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Renderer? =
        base.createSecondaryRenderer(
            renderer,
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        )
}

/**
 * Drives [AssHandler.videoTime] with the anchored + integrated media time each
 * render loop; the handler's change-detection + every-3rd-tick throttle then
 * paces the overlay views' renders.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
private class AssClockPumpRenderer(
    private val assHandler: AssHandler,
    startMediaTimeUs: Long,
) : NoSampleRenderer() {
    private var mediaTimeUs = startMediaTimeUs
    private var lastRendererUs: Long? = null
    private var loggedFirstTick = false

    override fun getName(): String = "AssClockPumpRenderer"

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        // Deltas of the renderer position equal deltas of the media time for
        // any constant position base — see the factory KDoc. Both directions
        // are integrated so backward seeks track too.
        lastRendererUs?.let { mediaTimeUs += positionUs - it }
        lastRendererUs = positionUs
        assHandler.videoTime = mediaTimeUs
        if (!loggedFirstTick) {
            // First tick proves the pump renderer was appended and is driving
            // AssHandler.videoTime — if subtitles still don't render after
            // this line, the remaining suspect is glyph rasterization (fonts).
            loggedFirstTick = true
            Log.d(TAG, "ASS clock pump started: mediaTimeUs=$mediaTimeUs")
        }
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
