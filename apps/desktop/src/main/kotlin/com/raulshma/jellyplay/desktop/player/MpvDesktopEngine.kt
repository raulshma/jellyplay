package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.END_FILE_REASON_EOF
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.END_FILE_REASON_ERROR
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_END_FILE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_FILE_LOADED
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_IDLE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_PROPERTY_CHANGE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_SHUTDOWN
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.EVENT_START_FILE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.FORMAT_DOUBLE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.FORMAT_FLAG
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.FORMAT_INT64
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.FORMAT_NODE
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.FORMAT_STRING
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.MpvEvent
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.MpvEventEndFile
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.MpvEventProperty
import com.raulshma.jellyplay.feature.player.video.DesktopFrameCaptureEngine
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.BufferedRanges
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.MpvConfigMapping
import com.raulshma.jellyplay.feature.player.video.engine.MpvErrorTaxonomy
import com.raulshma.jellyplay.feature.player.video.engine.MpvStyleMapping
import com.raulshma.jellyplay.feature.player.video.engine.MpvTlsOptions
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackVolumePolicy
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelFormatter
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelInfo
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
import com.raulshma.jellyplay.feature.player.video.engine.mergeAccumulatedCues
import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Desktop playback backend: libmpv over JNA, implementing the common
 * [MediaEngine] contract (. Property/event surface mirrors the
 * Android `MpvPlayerEngine` where semantics are shared — same observed
 * properties, same END_FILE/eof-reached state mapping, same error taxonomy —
 * so the shared player feature behaves identically on both platforms when it
 * migrates (§V3).
 *
 * Video output: mpv renders into a native child window embedded via the `wid`
 * option — the [MpvDesktopEngine] constructor takes the OS window handle (HWND
 * on Windows) from the Compose/Swing layer. Headless setups (tests) pass
 * [extraOptions] with `vo=null`/`ao=null`.
 *
 * The former V2 cuts are closed (the "when the player feature
 * migrates" trigger fired long ago): `EngineConfig.videoEffects` is applied
 * as a live mpv `vf` chain + `video-rotate` property ([DesktopVideoEffectChain]
 * builds the strings — see its shared→mpv parity table), screenshot capture
 * goes through mpv's `screenshot-to-file` ([captureVideoFrame], the desktop
 * seam's COMPOSE engine hook), and [currentCues] accumulates the live-cue
 * history from the observed `sub-text` (with a live `sub-start` read) like
 * the Android MPV engine's `accumulateMpvSubText`.  closed the
 * audio-effects cut before that: `EngineConfig.audioEffects` is applied as a
 * live mpv `af` chain + `audio-channels`/`pitch` properties
 * ([DesktopAudioEffectChain] builds the strings — see its Android→mpv parity
 * table). `PlaybackRequest.normalizationGain` stays unused here: the desktop
 * audio path carries the manager-computed final ReplayGain dB in the config
 * (`replayGainEffectiveDb`), mirroring where Android's `AudioPlaybackManager`
 * applies the gain.
 *
 * Open so [MpvSoftwareRenderEngine] can subclass it for the
 * render-API software-render path with three small hooks ([liveMpvHandle],
 * [onBeforeContextDestroy], [hwdecFor]) instead of duplicating the ~800-line
 * contract implementation.
 */
open class MpvDesktopEngine(
    /** Raw mpv options applied before mpv_initialize (e.g. vo/ao for tests). */
    extraOptions: Map<String, String> = emptyMap(),
    /**
     * Native window handle to embed mpv's video output into (HWND on Windows).
     * Must be supplied at construction — `wid` decides the render target at
     * decoder-init time and is not runtime-settable; the Compose/Swing layer
     * therefore creates the heavyweight child window first, then the engine.
     */
    windowHandle: Long? = null,
    /**
     * The extracted GLSL shader-pack directory: absolute path of the
     * folder the bundled Anime4K chains + user `*.glsl` files live in. `null`
     * (or a not-yet-extracted dir) omits the `glsl-shaders` pair entirely —
     * mpv needs real file paths, so no dir means no packs.
     */
    val shaderDir: String? = null,
    /**
     * Set `target-colorspace-hint=yes` post-init (HDR passthrough):
     * the engine factory flips this only when the user's desktop HDR
     * passthrough setting is on AND the HWND-embed `vo=gpu-next` path was
     * taken (the software-render path renders RGB bitmaps and cannot pass
     * HDR through). mpv then drives an HDR-capable display into its HDR
     * transfer instead of tone mapping.
     */
    private val targetColorspaceHint: Boolean = false,
) : MediaEngine,
    DesktopFrameCaptureEngine {

    override val displayName: String = PlayerType.MPV.displayName

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsPip = false,          // no PiP on desktop; windowing covers it
        supportsMiniMode = false,
        // `sub-text`/`sub-start` now accumulate into currentCues
        // exactly like the Android MPV engine (EngineCapabilityMatrix.MPV).
        supportsCues = true,
        supportsAudioDelay = true,
        supportsSubtitleDelay = true,
        supportsAudioPassthrough = true,
        supportsSubtitleStyle = true,
        supportsSubtitleVerticalPosition = true,
        supportsDialogueBoost = true,
        supportsNightMode = true,
        supportsAudioNormalization = true,
        supportsChannelMixing = true,
        supportsVideoFilters = true,
        supportsLiveQualitySwitch = false,
        supportsBandwidthEstimate = false,
        supportsAssOverride = true,
        supportsAssStyleOverride = true,
        supportsFontFamily = true,
        supportsFreeFormColors = true,
        supportsBorderStyles = true,
        supportsSecondarySubtitles = true,
        supportsScreenshot = true,
        // The `deinterlace` property is runtime-settable here.
        supportsDeinterlace = true,
    )

    override val zoomSafeSubtitleStrategy: ZoomSafeSubtitleStrategy =
        ZoomSafeSubtitleStrategy.COMPOSE_CUE

    // ── State surface (declared BEFORE ctx — createMpv emits into the error
    //    flow during construction, so backing fields must exist first) ──────

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    private val _currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
    override val currentCues: StateFlow<List<TimedCue>> = _currentCues.asStateFlow()

    private val _liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> = _liveSubtitleCue.asStateFlow()

    // replay=1: construction-time failures (libmpv missing/unloadable,
    // render-context create) are emitted BEFORE the EngineEventCoordinator
    // subscribes — PlayerSessionManager publishes the engine into its
    // StateFlow and the collector attaches a beat later, so with replay=0
    // those emissions hit zero subscribers and vanish. That is exactly how
    // a missing libmpv used to become a silent black player screen; the
    // replay hands the last error to the late subscriber instead.
    private val _errorFlow = MutableSharedFlow<EngineError>(replay = 1, extraBufferCapacity = 8)
    override val errorFlow: Flow<EngineError> = _errorFlow.asSharedFlow()

    private val _subtitleEvents = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 8)
    override val subtitleEvents: Flow<SubtitleEvent> = _subtitleEvents.asSharedFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    /**
     * Multi-band buffered surface, maintained from the observed
     * `demuxer-cache-state` node alongside the scalar demuxer-cache-time
     * observer — see [refreshBufferedRanges].
     */
    private val _bufferedRanges = MutableStateFlow<List<LongRange>>(emptyList())
    override val bufferedRanges: StateFlow<List<LongRange>> = _bufferedRanges.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(DEFAULT_POLLING_INTERVAL_MS)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()
    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }

    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val audioSessionId: Int = -1   // no Android audio session on desktop

    @Volatile private var positionMs: Long = 0L
    @Volatile private var durationValue: Long = 0L
    /** Server-reported runtime fallback while the demuxer hasn't resolved one. */
    @Volatile private var serverDurationMs: Long = 0L
    override val currentPositionMs: Long get() = positionMs
    override val durationMs: Long get() = if (durationValue > 0) durationValue else serverDurationMs

    @Volatile private var speedValue: Float = 1f
    override val playbackSpeed: Float get() = speedValue

    @Volatile private var volumePercent: Double = 100.0
    override val volume: Float get() = (volumePercent / 100.0).toFloat()

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = isPlaying,
            isCurrentlyPlaying = { isPlaying.value },
            onActive = { trySend(currentPositionMs) },
        ).launch()
        awaitClose { ticker.cancel() }
    }.conflate()

    // ── mpv context + event pump ────────────────────────────────────────────

    private val ctx: Pointer? = createMpv(extraOptions, windowHandle)

    @Volatile private var running = ctx != null
    private val released = AtomicBoolean(false)

    /**
     * Optional release notification: invoked EXACTLY
     * ONCE from [release] — right after the released CAS wins, before any
     * teardown — so instrumentation attached to the constructed engine (the
     * session harness's EngineActivityRecorder, wired by the factory) can stop
     * observing instead of sampling a released handle forever. Null for every
     * engine nobody wired (the audio queue manager's engine, tests). Assigned
     * by the factory AFTER construction (it observes the constructed engine);
     * @Volatile because release() can be invoked from any thread. Pure
     * notification — callbacks must not touch the engine back.
     */
    @Volatile var onReleased: (() -> Unit)? = null

    private val eventThread = thread(
        name = "mpv-desktop-event-loop",
        isDaemon = true,
        start = false,
    ) {
        val context = aliveCtx() ?: return@thread
        while (running) {
            // Blocks until an event arrives or wakeup() fires (release path).
            val event: MpvEvent = MpvLib.mpv.mpv_wait_event(context, -1.0) ?: break
            handleEvent(event)
        }
    }

    init {
        ctx?.let(::registerObservers)
        if (ctx != null) eventThread.start()

        // HDR passthrough — mpv switches an HDR-capable display to its
        // HDR transfer on the first HDR frame when the hint is set. Runtime
        // property write (post-init): the option is meaningful per-output and
        // this libmpv accepts the string write after mpv_initialize.
        if (targetColorspaceHint) {
            ctx?.let { MpvLib.setPropertyString(it, "target-colorspace-hint", "yes") }
        }

        // Stats projector: polls while enabled, mirroring the Android engines'
        // videoStatsEnabled gating — high-churn properties are only read while
        // the stats overlay is open.
        engineScope.launch {
            while (isActive) {
                if (videoStatsEnabled.value) projectVideoStats()
                delay(VIDEO_STATS_POLL_MS)
            }
        }
    }

    private fun createMpv(extraOptions: Map<String, String>, windowHandle: Long?): Pointer? = try {
        val context = MpvLib.mpv.mpv_create() ?: run {
            _errorFlow.tryEmit(EngineError.Render(IllegalStateException("mpv_create failed")))
            return null
        }
        // Base options mirroring the Android engine's postInitOptions.
        sequence {
            yield("config" to "no")            // never read user mpv.conf
            yield("idle" to "yes")             // survive empty playlist
            yield("keep-open" to "yes")        // EOF pauses on last frame; no END_FILE
            yield("input-default-bindings" to "no")
            yield("input-vo-keyboard" to "no")
            yield("osc" to "no")
        }.forEach { (k, v) -> MpvLib.mpv.mpv_set_option_string(context, k, v) }
        if (windowHandle != null) {
            MpvLib.mpv.mpv_set_option_string(context, "wid", windowHandle.toString())
        }
        extraOptions.forEach { (k, v) -> MpvLib.mpv.mpv_set_option_string(context, k, v) }
        if (MpvLib.mpv.mpv_initialize(context) < 0) {
            MpvLib.mpv.mpv_terminate_destroy(context)
            _errorFlow.tryEmit(EngineError.Render(IllegalStateException("mpv_initialize failed")))
            return null
        }
        context
    } catch (t: Throwable) {
        // No libmpv on the machine (or load-time JNI failure): degrade through
        // errorFlow instead of crashing the caller — Koin laziness plus this
        // catch means the app boots and the failure surfaces at playback.
        _errorFlow.tryEmit(EngineError.Render(t))
        null
    }

    // ── Property observation (same set as Android MpvPlayerEngine) ─────────

    private fun registerObservers(context: Pointer) {
        var userdata = 0L
        fun observe(name: String, format: Int) {
            MpvLib.mpv.mpv_observe_property(context, userdata++, name, format)
        }
        observe("pause", FORMAT_FLAG)
        observe("speed", FORMAT_DOUBLE)
        observe("paused-for-cache", FORMAT_FLAG)
        observe("eof-reached", FORMAT_FLAG)
        // time-pos MUST be DOUBLE: as INT64 mpv emits only whole-second steps.
        observe("time-pos", FORMAT_DOUBLE)
        observe("duration", FORMAT_DOUBLE)
        observe("demuxer-cache-time", FORMAT_INT64)
        // Range-level buffered surface. NODE observation arrives as a
        // raw mpv_node pointer in the event; like the track-list observer the
        // engine re-reads the property through [MpvLib.readNode] (parsed
        // Kotlin tree) instead of decoding the event's node memory here.
        observe("demuxer-cache-state", FORMAT_NODE)
        observe("sub-text", FORMAT_STRING)
        observe("track-list", FORMAT_NODE)
        // Live output channel layout — the balance (`pan`) af stage must know
        // it because `pan` pins the output layout (see DesktopAudioEffectChain).
        observe("audio-params/channel-count", FORMAT_INT64)
    }

    // ── Event dispatch ──────────────────────────────────────────────────────

    @Volatile private var fileLoaded = false
    @Volatile private var pendingSubtitles: List<SubtitleSource> = emptyList()
    @Volatile private var currentConfig: EngineConfig = EngineConfig()
    /** Last observed `audio-params/channel-count`; null until mpv reports one. */
    @Volatile private var observedChannelCount: Int? = null

    // Last-applied audio-effect property values. mpv re-inits its audio chain
    // when `af`/`audio-channels`/`pitch` are written — re-writing the SAME
    // value at every FILE_LOADED breaks the ao=null/real-ao pacing clock
    // (observed: 3 s fixture ended before the manager's first 2.5 s ticker
    // wake). Only actual CHANGES may hit mpv. The caches start at mpv's own
    // defaults (auto-safe ≈ auto, pitch 1.0) so an all-defaults config
    // performs ZERO writes at load — the pre-effects pacing behavior.
    @Volatile private var lastAppliedAudioChannels: String? = AUTO_CHANNELS
    @Volatile private var lastAppliedPitch: Double? = 1.0
    @Volatile private var lastAppliedAfChain: String? = null

    // Video twin of the same discipline: `vf` writes re-init the
    // video pipeline, so only actual CHANGES are pushed, and an all-defaults
    // config performs zero writes. `video-rotate` starts at mpv's own 0.
    @Volatile private var lastAppliedVfChain: String? = null
    @Volatile private var lastAppliedRotationDeg: Int = 0

    // The structured [MpvEngineConfig] diff cache (shared MpvConfigMapping —
    // the groundwork that makes the desktop engine read the config Android's
    // mpv engine has always consumed): scale/deband/interpolation(+video-sync)/
    // framedrop/skiploopfilter/demuxer budgets/audio-device/audio-exclusive/
    // audio-spdif/extras. Starts empty so the first FILE_LOADED application
    // writes every owned key once; subsequent applies (every FILE_LOADED, plus
    // live `engineSpecific` changes) write only actual CHANGES. scaler/deband
    // changes reconfigure the vo pipeline, `audio-device` re-opens the ao —
    // both only ever written on a real change.
    @Volatile private var lastAppliedEngineConfigProps: Map<String, String> = emptyMap()

    // ── HDR passthrough state ───────────────────────────────────────
    // The display-target probe: pending from FILE_LOADED until the first
    // stats tick where mpv's `video-target-params/gamma` resolves (the target
    // exists only after the first frame is rendered), then cached for the
    // file. `hdrTargetIsHdr` gates the tone-mapping suppression in
    // applyEngineConfig; the three hdrStats* fields feed the stats overlay's
    // badge/notice rows and survive the per-tick stats rebuild.
    @Volatile private var hdrProbePending = false
    @Volatile private var hdrTargetIsHdr = false
    @Volatile private var hdrStatsActive = false
    @Volatile private var hdrStatsNotice: String? = null
    @Volatile private var hdrStatsType: String? = null

    /**
     * The live mpv handle for member calls, or null after [release] — every
     * public member routes through this so a post-release call degrades to a
     * no-op instead of JNA-calling a destroyed context.
     */
    private fun aliveCtx(): Pointer? = if (released.get()) null else ctx

    /**
     * The live mpv handle: for subclasses that attach auxiliary contexts tied
     * to it ([MpvSoftwareRenderEngine]'s render-API context is created on
     * this handle at construction) and for desktop tests that assert on raw
     * mpv properties — it is a desktop-native accessor, deliberately NOT part
     * of the commonMain engine contract (the type-erased
     * `underlyingPlayer` escape hatch was retired from it). Returns null
     * post-[release] like [aliveCtx].
     */
    fun liveMpvHandle(): Pointer? = aliveCtx()

    /**
     * Engine-variant hook: emits into the engine's [errorFlow]
     * during construction (e.g. sw render-context creation failure) —
     * subclasses cannot touch the private backing flow directly.
     */
    protected fun tryEmitError(error: EngineError) {
        _errorFlow.tryEmit(error)
    }

    private fun handleEvent(event: MpvEvent) {
        when (event.event_id) {
            EVENT_START_FILE -> {
                fileLoaded = false
                _playbackState.value = EnginePlaybackState.BUFFERING
            }
            EVENT_FILE_LOADED -> {
                fileLoaded = true
                positionMs = 0L
                // New file → the display-target probe starts over (the target
                // of the previous item must not leak into this one's stats).
                hdrProbePending = true
                hdrTargetIsHdr = false
                hdrStatsActive = false
                hdrStatsNotice = null
                hdrStatsType = null
                applyPendingSubtitles()
                refreshTracks()
                applyConfigToMpv(currentConfig)
                // Seed isPlaying from the live core state: when the core
                // auto-plays (default), `pause` never *changes*, so the
                // property-change handler below alone would never fire.
                val paused = ctx?.let { propFlag(it, "pause") } ?: true
                _isPlaying.value = !paused && !eofReached()
                if (!eofReached()) _playbackState.value = EnginePlaybackState.READY
            }
            EVENT_END_FILE -> {
                val payload = event.data?.let { MpvEventEndFile(it).also { it.read() } }
                when (payload?.reason) {
                    END_FILE_REASON_ERROR -> {
                        _errorFlow.tryEmit(mapMpvError(payload.error))
                        _playbackState.value = EnginePlaybackState.ERROR
                    }
                    // Natural EOF shouldn't arrive (keep-open=yes parks at the
                    // last frame), but if an option override disabled it, map
                    // defensively like the Android engine.
                    END_FILE_REASON_EOF -> {
                        _playbackState.value = EnginePlaybackState.ENDED
                        _isPlaying.value = false
                    }
                    // REDIRECT is mpv following an HLS/manifest variant switch —
                    // the *next* entry starts immediately; BUFFERING, not IDLE.
                    com.raulshma.jellyplay.desktop.player.mpv.MpvLib.END_FILE_REASON_REDIRECT ->
                        _playbackState.value = EnginePlaybackState.BUFFERING
                    // STOP/QUIT follow explicit user action and keep the engine
                    // usable for the next load.
                    null -> Unit
                    else -> _playbackState.value = EnginePlaybackState.IDLE
                }
            }
            EVENT_IDLE -> {
                if (!fileLoaded) _playbackState.value = EnginePlaybackState.IDLE
            }
            EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
            EVENT_SHUTDOWN -> running = false
            else -> Unit
        }
    }

    private fun handlePropertyChange(event: MpvEvent) {
        val prop = event.data?.let { MpvEventProperty(it).also { it.read() } } ?: return
        val name = prop.name?.getString(0) ?: return
        val data = prop.data
        when (name) {
            "pause" -> {
                val paused = data != null && data.getInt(0) != 0
                _isPlaying.value = !paused && fileLoaded && !eofReached()
            }
            "paused-for-cache" -> {
                val buffering = data != null && data.getInt(0) != 0
                if (fileLoaded && !eofReached()) {
                    _playbackState.value =
                        if (buffering) EnginePlaybackState.BUFFERING else EnginePlaybackState.READY
                }
            }
            "eof-reached" -> {
                val eof = data != null && data.getInt(0) != 0
                if (eof) {
                    _playbackState.value = EnginePlaybackState.ENDED
                    _isPlaying.value = false
                    _liveSubtitleCue.value = null
                } else if (fileLoaded) {
                    _playbackState.value = EnginePlaybackState.READY
                    // eof flipped false (replay seek-back): re-derive isPlaying
                    // from the live pause state — `pause` itself didn't change,
                    // so its observer won't fire (same class as FILE_LOADED seed).
                    val paused = aliveCtx()?.let { propFlag(it, "pause") } ?: true
                    _isPlaying.value = !paused
                }
            }
            "time-pos" -> data?.let {
                positionMs = ((it.getDouble(0) * 1000).toLong()).coerceAtLeast(0L)
            }
            "duration" -> data?.let { durationValue = (it.getDouble(0) * 1000).toLong() }
            "demuxer-cache-time" -> data?.let { _bufferedPositionMs.value = it.getLong(0) * 1000 }
            "demuxer-cache-state" -> refreshBufferedRanges()
            // Contract: null when no line is active — mpv emits "" on clear.
            // Non-blank lines also fold into the currentCues history (G10,
            // same pairing as Android's accumulateMpvSubText).  fix:
            // FORMAT_STRING event data is a char** (client.h hands the value
            // behind one pointer) — reading the bytes AT data yielded pointer
            // garbage; dereference first.
            "sub-text" -> {
                val raw = data?.getPointer(0)?.getString(0)
                val text = raw?.takeIf { it.isNotBlank() }
                _liveSubtitleCue.value = text
                if (text != null) accumulateCue(text)
            }
            "speed" -> data?.let { speedValue = it.getDouble(0).toFloat() }
            "track-list" -> refreshTracks()
            "audio-params/channel-count" -> {
                val count = data?.getLong(0)?.toInt()
                if (count != null && count != observedChannelCount) {
                    observedChannelCount = count
                    // The layout changed (new item / channel-mix edit): the
                    // stereo-gated balance stage must be rebuilt against the
                    // new layout.
                    applyAudioEffects(currentConfig)
                }
            }
        }
    }

    private fun eofReached(): Boolean = aliveCtx()?.let { propFlag(it, "eof-reached") } ?: false

    /**
     * Re-reads the `demuxer-cache-state` node and folds it into
     * [_bufferedRanges] via the shared pure derivation. Per-range detail
     * (`seekable-ranges`, libmpv >= 0.35) is taken directly; older libmpv
     * derives the contiguous `[demuxer-start-time, cache-end]` window clamped
     * to `[0, duration]`. Numbers arrive as Double/Long from [MpvLib.readNode]
     * — doubles coerce through Number so either shape parses.
     */
    private fun refreshBufferedRanges() {
        val context = aliveCtx() ?: return
        val state = MpvLib.readNode(context, "demuxer-cache-state") as? Map<*, *> ?: return
        fun double(key: String): Double? = (state[key] as? Number)?.toDouble()
        val seekableRanges = (state["seekable-ranges"] as? List<*>)?.mapNotNull { entry ->
            val range = entry as? Map<*, *> ?: return@mapNotNull null
            val start = (range["start"] as? Number)?.toDouble()
            val end = (range["end"] as? Number)?.toDouble()
            if (start != null && end != null) start to end else null
        }
        _bufferedRanges.value = BufferedRanges.fromDemuxerCacheState(
            demuxerStartTimeSec = double("demuxer-start-time"),
            cacheEndSec = double("cache-end"),
            seekableRangesSec = seekableRanges,
            durationMs = durationMs,
        )
    }

    // ── MediaEngine: source loading & teardown ──────────────────────────────

    override fun load(request: PlaybackRequest) {
        val context = ctx ?: run {
            _errorFlow.tryEmit(EngineError.Render(IllegalStateException("mpv not initialized")))
            return
        }
        pendingSubtitles = request.externalSubtitles
        _liveSubtitleCue.value = null
        _availableTracks.value = emptyList()
        _videoStats.value = EngineVideoStats()
        // Reset per-item derived state: the previous item's duration/buffer
        // must not leak into this item's BUFFERING window (Android resets both
        // — and the played-range cue history too, which belongs to the
        // previous item).
        durationValue = 0L
        serverDurationMs = request.serverDurationMs
        _bufferedPositionMs.value = 0L
        _bufferedRanges.value = emptyList()
        _currentCues.value = emptyList()

        // Per-request options. http-header-fields is a list option that
        // PERSISTS on the context — reset it first or the previous item's
        // credentials (X-Emby-Authorization) are sent to this item's server.
        // The -append suffix is then the only way to set entries containing
        // commas (X-Emby-Authorization does), one call per header.
        MpvLib.mpv.mpv_set_option_string(context, "http-header-fields", "")
        request.headers.forEach { (k, v) ->
            MpvLib.mpv.mpv_set_option_string(context, "http-header-fields-append", "$k: $v")
        }
        // mTLS: the tls-* file-path options persist the same way —
        // write the reset trio when no certificate is active so the previous
        // item's certificate/key is never presented to this item's server.
        MpvTlsOptions.from(request.tls).forEach { (option, value) ->
            MpvLib.mpv.mpv_set_option_string(context, option, value)
        }
        request.preferredAudioLanguage?.let {
            MpvLib.mpv.mpv_set_option_string(context, "alang", it)
        }
        request.preferredSubtitleLanguage?.let {
            MpvLib.mpv.mpv_set_option_string(context, "slang", it)
        }
        MpvLib.mpv.mpv_set_option_string(
            context,
            "demuxer-readahead-secs",
            (request.maxBufferMs / 1000).toString(),
        )

        val loadOptions = if (request.startPositionMs > 0) {
            "start=+${request.startPositionMs / 1000.0}"
        } else {
            null
        }
        // loadfile <url> <flags> <index> <options> — index is ignored with
        // replace, but must be present to reach the options slot.
        val loaded = if (loadOptions == null) {
            MpvLib.command(context, "loadfile", request.uri, "replace")
        } else {
            MpvLib.command(context, "loadfile", request.uri, "replace", "0", loadOptions)
        }
        if (!loaded) {
            _errorFlow.tryEmit(EngineError.Source(httpStatus = null, cause = null))
        }
    }

    private fun applyPendingSubtitles() {
        val context = aliveCtx() ?: return
        val pending = pendingSubtitles
        pendingSubtitles = emptyList()
        pending.forEach { sub ->
            // sub-add <url> [flags [title [lang]]] — title doubles as the
            // stable label the track-list echoes back, matching how the
            // Android engine keys side-loaded tracks.
            MpvLib.command(context, "sub-add", sub.url, "auto", sub.label, sub.language ?: "")
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        // First thing after the CAS: stop external observers (the recorder's
        // sampler — see onReleased) BEFORE teardown, so their last reads saw
        // a live engine and no sample lands against a destroyed handle.
        // Guarded: the CAS has already won, so a throwing callback here would
        // abort teardown with released==true and leak the mpv handle forever.
        runCatching { onReleased?.invoke() }
        running = false
        val context = ctx ?: return
        repeat(RELEASE_JOIN_ATTEMPTS) {
            MpvLib.mpv.mpv_wakeup(context)
            runCatching { eventThread.join(RELEASE_JOIN_TIMEOUT_MS / RELEASE_JOIN_ATTEMPTS) }
            if (!eventThread.isAlive) return@repeat
        }
        if (eventThread.isAlive) {
            // Event thread wedged inside a native call. Destroying the context
            // under it risks a use-after-free in handleEvent; leak the engine
            // (daemon thread + mpv handle) instead of crashing the process.
            return
        }
        onBeforeContextDestroy()
        // Stop the stats poller and wait for in-flight native reads to finish
        // BEFORE destroying the context — engineScope reads are the other
        // use-after-destroy window besides the event thread.
        val scopeJob = engineScope.coroutineContext[Job]
        scopeJob?.cancel()
        runCatching {
            kotlinx.coroutines.runBlocking {
                scopeJob?.children?.toList().orEmpty().forEach { it.join() }
            }
        }
        // Blocks until the core's internal threads exit; afterwards the
        // handle is invalid and must never be touched again.
        MpvLib.mpv.mpv_terminate_destroy(context)
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
    }

    /**
     * Engine-variant hook: invoked exactly once during [release],
     * after the event thread has drained/joined (or the leak path bailed) but
     * BEFORE [MpvLib.mpv_terminate_destroy] — the last point where auxiliary
     * native contexts tied to [ctx] can be torn down against a live core, as
     * render.h L122-123 requires of mpv_render_context_free().
     */
    protected open fun onBeforeContextDestroy() {}


    // ── MediaEngine: transport control ──────────────────────────────────────

    override fun play() {
        val context = aliveCtx() ?: return
        if (_playbackState.value == EnginePlaybackState.ENDED) {
            // keep-open holds EOF via an internal pause at the last frame —
            // unpausing alone replays nothing. Seek back first (Android does
            // the same); the eof-reached flip re-derives isPlaying/READY.
            MpvLib.command(context, "seek", "0", "absolute")
        }
        MpvLib.setPropertyFlag(context, "pause", false)
    }

    override fun pause() {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyFlag(context, "pause", true)
    }

    override fun stop() {
        val context = aliveCtx() ?: return
        MpvLib.command(context, "stop")
        fileLoaded = false
        positionMs = 0L
        durationValue = 0L
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _liveSubtitleCue.value = null
        _currentCues.value = emptyList()
        // The stopped file's buffer ranges die with it.
        _bufferedRanges.value = emptyList()
        // The HDR verdict belongs to the (now stopped) file — clear it with
        // the rest of the per-item derived state.
        hdrProbePending = false
        hdrTargetIsHdr = false
        hdrStatsActive = false
        hdrStatsNotice = null
        hdrStatsType = null
    }

    override fun seekTo(positionMs: Long) {
        val context = aliveCtx() ?: return
        MpvLib.command(context, "seek", (positionMs / 1000.0).toString(), "absolute")
        this.positionMs = positionMs.coerceAtLeast(0L)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyDouble(context, "speed", speed.toDouble())
        speedValue = speed
    }

    // ── MediaEngine: volume/mute (RemotePlayableEngine, 0f..1f → mpv %) ────
    //
    // Choreography comes from the commonMain [PlaybackVolumePolicy] — the same
    // pure decisions the Android reloadable engines apply via
    // ReloadablePlayerEngine's final templates: clamp to the boost ceiling
    // (MAX_BOOST_NOMINAL), remember the last audible level, snapshot it on
    // mute, restore it on unmute. The desktop previously hand-rolled
    // `coerceIn(0f,1f) * 100` + a bare mute flag, so unmuting never restored
    // the remembered level. Desktop-owned is the APPLY step only: the policy
    // works in normalized units (1.0 == nominal) and [applyVolumeLevel] is the
    // single 0..1 → 0..100 conversion at the mpv `volume` property write.
    // There is no Android system music stream to sync on desktop — the mpv
    // volume property is the only volume surface — so the unmute plan writes
    // its REMEMBERED_LEVEL restore straight into mpv (Android's mpv adapter
    // instead pairs LEAVE_UNCHANGED with the system-stream sync).

    /** Last audible normalized level; the restore target for unmute. */
    @Volatile private var lastUnmuteVolume: Float = 1f

    /**
     * Per-content-type volume-memory capture (the
     * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] hook).
     * Invoked from the volume apply path ONLY for user-initiated changes
     * ([PlaybackVolumePolicy.LevelPlan.isUserChange]) — the session host
     * assigns it with the active item's bucket, so a remembered level
     * reflects what the user chose, never a sleep-timer fade. Same
     * assignment-safety shape as [onReleased]: assigned by the host after
     * construction.
     */
    @Volatile override var onUserVolumeChange: ((level: Float) -> Unit)? = null

    /** Same seam as ReloadablePlayerEngine's — remember only audible levels. */
    private fun rememberUnmuteVolumeIfAudible(volume01: Float) {
        if (volume01 > 0f) lastUnmuteVolume = volume01
    }

    override fun setVolume(value: Float, isUserChange: Boolean) {
        val context = aliveCtx() ?: return
        applyVolumePlan(context, value, isUserChange)
    }

    override fun increaseVolume(delta: Float) {
        val context = aliveCtx() ?: return
        // The cached percent IS the native level — this engine is the only
        // writer of mpv's `volume`, so the mirror cannot drift (Android reads
        // the property live because its handle can be touched elsewhere).
        // Key/remote nudges are user-shaped for memory capture.
        applyVolumePlan(context, (volumePercent / 100.0).toFloat() + delta, isUserChange = true)
    }

    override fun decreaseVolume(delta: Float) {
        val context = aliveCtx() ?: return
        applyVolumePlan(context, (volumePercent / 100.0).toFloat() - delta, isUserChange = true)
    }

    /** plan + remember + capture + native write — the ReloadablePlayerEngine template body. */
    private fun applyVolumePlan(context: Pointer, raw: Float, isUserChange: Boolean) {
        val plan = PlaybackVolumePolicy.planLevel(raw, PlaybackVolumePolicy.MAX_BOOST_NOMINAL, isUserChange)
        rememberUnmuteVolumeIfAudible(plan.normalized)
        if (plan.isUserChange) onUserVolumeChange?.invoke(plan.normalized)
        applyVolumeLevel(context, plan.normalized)
    }

    /** The one normalized→percent boundary conversion for the mpv `volume` write. */
    private fun applyVolumeLevel(context: Pointer, normalized: Float) {
        volumePercent = normalized * 100.0
        MpvLib.setPropertyDouble(context, "volume", volumePercent)
    }

    override fun setMuted(muted: Boolean) {
        val context = aliveCtx() ?: return
        // mpv owns a real mute flag — the flag silences; the volume property is
        // not the mute mechanism (Android template writes the flag first too).
        MpvLib.setPropertyFlag(context, "mute", muted)
        if (muted) {
            // LEAVE_UNCHANGED is mpv's mute vocabulary (plan.nativeVolume is
            // null — the native level stays put). Remember the pre-mute level
            // BEFORE any further write, mirroring the template's
            // snapshot-before-apply ordering.
            val plan = PlaybackVolumePolicy.planMute(
                PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED,
            )
            rememberUnmuteVolumeIfAudible((volumePercent / 100.0).toFloat())
            plan.nativeVolume?.let { applyVolumeLevel(context, it) }
        } else {
            // Desktop has no system stream, so the remembered level is restored
            // into the mpv volume property itself — the level the user hears is
            // whatever `volume` carries once the flag lifts (coerced by the
            // policy into the audible 0.05..1 window).
            val plan = PlaybackVolumePolicy.planUnmute(
                lastUnmuteVolume,
                PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL,
            )
            plan.nativeVolume?.let { applyVolumeLevel(context, it) }
        }
    }

    // ── MediaEngine: tracks & subtitles ─────────────────────────────────────

    override fun selectTrack(type: TrackType, index: Int) {
        val context = aliveCtx() ?: return
        when (type) {
            TrackType.AUDIO -> MpvLib.setPropertyString(context, "aid", index.toString())
            TrackType.SUBTITLE -> MpvLib.setPropertyString(context, "sid", index.toString())
        }
    }

    override fun setSecondarySubtitleTrack(index: Int) {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyString(
            context,
            "secondary-sid",
            if (index < 0) "no" else index.toString(),
        )
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val context = aliveCtx() ?: return
        if (!fileLoaded) {
            pendingSubtitles = pendingSubtitles + source
            return
        }
        MpvLib.command(context, "sub-add", source.url, "auto", source.label, source.language ?: "")
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // No-op for direct playback — bitrate control happens server-side when
        // PlaybackInfo negotiates the stream (the engine plays the URL it is
        // handed), same reasoning as the Android mpv engine.
    }

    override fun setNativeSubtitlesVisible(visible: Boolean) {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyString(context, "sub-visibility", if (visible) "yes" else "no")
    }

    /**
     * Applies [style] through the commonMain [MpvStyleMapping] — the same
     * canonical `sub-*` key/value table the Android mpv engine applies
     * (resolver-aware colors as mpv `#AARRGGBB`, the shared per-edge-type
     * border/shadow table, `sub-ass-override` driven by
     * [SubtitleStyle.assOverride]). The desktop's former private mirror
     * (`argbCss` + an inline edge-type table + hardcoded override strings) is
     * deleted. Engine-owned per platform, exactly as on Android: `sub-pos`
     * (the mapping pins marginY=0 and leaves vertical placement to the
     * engine's `sub-pos` write) and the font-size delivery — the desktop
     * writes `sub-font-size` directly from the mapping's coerced value
     * instead of Android's `sub-scale` indirection over a bundled font.
     */
    override fun applySubtitleStyle(style: SubtitleStyle) {
        val context = aliveCtx() ?: return
        if (!style.applyCustomStyle) {
            // Reset to mpv/libass native defaults — the tested reset pairs and
            // magnitudes from the mapping's DEFAULTS table, so a custom→default
            // switch mid-session cannot leave stale colors, edge sizes, or
            // typeface toggles behind. The desktop previously flipped only
            // sub-ass-override to "scale"; the canonical reset is "no"
            // (embedded ASS styling fully honored), matching Android's
            // default branch.
            MpvStyleMapping.defaultEntries().forEach { (k, v) ->
                MpvLib.setPropertyString(context, k, v)
            }
            MpvLib.setPropertyDouble(context, "sub-border-size", MpvStyleMapping.defaultBorderSize)
            MpvLib.setPropertyDouble(context, "sub-shadow-offset", MpvStyleMapping.defaultShadowOffset)
            return
        }
        // Custom branch: string-typed pairs straight from the mapping, then the
        // engine-applied numeric magnitudes (Android splits the same way
        // between customStyleEntries and typed setters).
        MpvStyleMapping.customStyleEntries(style).forEach { (k, v) ->
            MpvLib.setPropertyString(context, k, v)
        }
        val values = MpvStyleMapping.computeValues(style)
        MpvLib.setPropertyDouble(context, "sub-font-size", values.fontSize.toDouble())
        MpvLib.setPropertyDouble(context, "sub-border-size", values.outlineSize)
        MpvLib.setPropertyDouble(context, "sub-shadow-offset", values.shadowOffset)
        // sub-pos is measured bottom-up in percent; the app's verticalPosition
        // is top-down (0 = top edge).
        MpvLib.setPropertyDouble(
            context,
            "sub-pos",
            (100.0 - style.verticalPosition * 100.0).coerceIn(0.0, 100.0),
        )
    }

    // ── MediaEngine: aspect ratio ───────────────────────────────────────────

    override fun setAspectRatio(ratio: AspectRatio) {
        val context = aliveCtx() ?: return
        when (ratio) {
            AspectRatio.AUTO, AspectRatio.FIT -> {
                MpvLib.setPropertyString(context, "video-aspect-override", "-1")
                MpvLib.setPropertyDouble(context, "panscan", 0.0)
            }
            // FILL/CROP both stretch to the window: crop via panscan (cut
            // overflow), fill via aspect-override to the window's own ratio —
            // panscan alone keeps the source aspect, so both use the window
            // ratio through aspect-override=-1 + panscan=1 for CROP and defer
            // true FILL to the Swing surface resizing at V3.
            AspectRatio.CROP, AspectRatio.FILL -> {
                MpvLib.setPropertyString(context, "video-aspect-override", "-1")
                MpvLib.setPropertyDouble(context, "panscan", 1.0)
            }
            else -> ratio.ratio?.let {
                MpvLib.setPropertyDouble(context, "video-aspect-override", it.toDouble())
                MpvLib.setPropertyDouble(context, "panscan", 0.0)
            }
        }
    }

    // ── MediaEngine: config ─────────────────────────────────────────────────

    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        val old = currentConfig
        currentConfig = config
        onConfigChanged(old, config)
    }

    private fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        if (oldConfig.audioDelayMs != newConfig.audioDelayMs) {
            ctx?.let { MpvLib.setPropertyDouble(it, "audio-delay", newConfig.audioDelayMs / 1000.0) }
        }
        if (oldConfig.subtitleDelayMs != newConfig.subtitleDelayMs) {
            ctx?.let { MpvLib.setPropertyDouble(it, "sub-delay", newConfig.subtitleDelayMs / 1000.0) }
        }
        if (oldConfig.decoderMode != newConfig.decoderMode) {
            ctx?.let { MpvLib.setPropertyString(it, "hwdec", hwdecFor(newConfig.decoderMode)) }
        }
        if (oldConfig.subtitleStyle != newConfig.subtitleStyle) {
            applySubtitleStyle(newConfig.subtitleStyle)
        }
        if (oldConfig.engineSpecific != newConfig.engineSpecific && newConfig.engineSpecific != null) {
            applyConfigToMpv(newConfig)
        } else if (oldConfig.audioPassthrough != newConfig.audioPassthrough ||
            // The session-scoped deinterlace cycle rides the base
            // config — it must re-run the pair diff even when the
            // engine-specific slice didn't move.
            oldConfig.deinterlace != newConfig.deinterlace ||
            // An HDR-source change flips the tone-mapping suppression
            // gate, so the shared pairs must be re-diffed for the new item.
            oldConfig.hdrSource != newConfig.hdrSource
        ) {
            // Boolean-only change: the spdif list rides the SHARED
            // EngineConfig.audioPassthrough on both platforms (the mapper's
            // AUTO mode composes it), so it must reach the pair diff even
            // when the engine-specific slice didn't move. (Previously the
            // desktop never applied passthrough toggles at all.)
            applyEngineConfig(newConfig)
        }
        if (oldConfig.audioEffects != newConfig.audioEffects) {
            // Live re-apply — mpv re-inits the af chain / audio-channels /
            // pitch on property writes (verified against the bundled libmpv).
            applyAudioEffects(newConfig)
        }
        if (oldConfig.videoEffects != newConfig.videoEffects) {
            // Video twin: mpv re-inits the video pipeline on `vf` writes
            // (same class of live re-apply as the af chain above).
            applyVideoEffects(newConfig)
        }
    }

    private fun applyConfigToMpv(config: EngineConfig) {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyDouble(context, "audio-delay", config.audioDelayMs / 1000.0)
        MpvLib.setPropertyDouble(context, "sub-delay", config.subtitleDelayMs / 1000.0)
        MpvLib.setPropertyString(context, "hwdec", hwdecFor(config.decoderMode))
        applySubtitleStyle(config.subtitleStyle)
        applyEngineConfig(config)
        applyAudioEffects(config)
        applyVideoEffects(config)
    }

    /**
     * Applies the structured [MpvEngineConfig] through the shared
     * [MpvConfigMapping] with the diff-then-write discipline above — the
     * desktop half of the engine-config parity groundwork. Runs at every
     * FILE_LOADED (fresh loads re-write nothing thanks to the cache) and on
     * live `engineSpecific` changes, so the settings-surface knobs (scaler,
     * deband, interpolation, framedrop, skip-loop-filter, demuxer budgets,
     * audio device/exclusive/passthrough, shader pack, tone mapping, render
     * quality, `mpvExtraConfig`) all reach the desktop mpv exactly as they
     * reach Android's.
     */
    private fun applyEngineConfig(config: EngineConfig) {
        val context = aliveCtx() ?: return
        val mpvCfg = config.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
        val pairs = MpvConfigMapping.configPairs(
            config = mpvCfg,
            audioPassthrough = config.audioPassthrough,
            // No low-RAM axis on desktop: the AUTO demuxer budget takes the
            // normal pair (the mapper's device-dependent branch stays Android's).
            lowRamDevice = false,
            deinterlace = config.deinterlace,
            shaderDir = shaderDir,
            // While HDR passthrough is ACTIVE (setting on + HDR item +
            // HDR display target) `tone-mapping` falls to mpv's `auto`
            // default — HDR→HDR, the colorspace-hint path owns the output
            // (and a stale preset from an SDR session is explicitly reset).
            // Before the target probe lands the gate is open (the preset is
            // written): the safe fallback for SDR displays.
            toneMappingSuppressed = hdrPassthroughRequested(config) && hdrTargetIsHdr,
        )
        lastAppliedEngineConfigProps = MpvConfigMapping.applyChanged(pairs, lastAppliedEngineConfigProps) { key, value ->
            MpvLib.setPropertyString(context, key, value)
        }
    }

    /**
     * The HDR-passthrough INTENT (not the active state): the user's setting
     * is on AND the current item's streams are HDR (the config builder folds
     * `isHdrFromStreams` into [EngineConfig.hdrSource]). Whether the OUTPUT is
     * actually HDR additionally depends on the display target
     * ([hdrTargetIsHdr], read from `video-target-params` after the first
     * frame).
     */
    private fun hdrPassthroughRequested(config: EngineConfig): Boolean =
        (config.engineSpecific as? MpvEngineConfig)?.hdrPassthrough == true && config.hdrSource

    /**
     * push the audio-effects config onto mpv — the `af` chain
     * ([DesktopAudioEffectChain.buildAfChain]), the channel-mix
     * `audio-channels` property, and the `pitch` property. All three are
     * runtime-settable; mpv rebuilds the audio chain on write — which is why
     * unchanged values are never re-written (see the pacing note on the
     * last-applied fields).
     */
    private fun applyAudioEffects(config: EngineConfig) {
        val context = aliveCtx() ?: return
        val fx = config.audioEffects
        // The output mode's STEREO forced downmix folds into the same
        // audio-channels value — the effects chain stays this property's
        // single writer (MpvConfigMapping.effectiveAudioChannels composes).
        val mpvCfg = config.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
        val channels = MpvConfigMapping.effectiveAudioChannels(
            mpvCfg.audioOutputMode,
            fx.channelMixMode,
            fx.channelMixEnabled,
        )
        if (channels != lastAppliedAudioChannels) {
            MpvLib.setPropertyString(context, "audio-channels", channels)
            lastAppliedAudioChannels = channels
        }
        val pitch = DesktopAudioEffectChain.pitchRatio(fx.pitchSemitones)
        if (pitch != lastAppliedPitch) {
            MpvLib.setPropertyDouble(context, "pitch", pitch)
            lastAppliedPitch = pitch
        }
        val chain = DesktopAudioEffectChain.buildAfChain(fx, observedChannelCount)
        if (chain != lastAppliedAfChain) {
            if (chain != null) {
                MpvLib.setPropertyString(context, "af", chain)
            } else {
                MpvLib.command(context, "af", "clr", "")
            }
            lastAppliedAfChain = chain
        }
    }

    /**
     * push the video-effects config onto mpv — the `vf` chain
     * ([DesktopVideoEffectChain.buildVfChain]) and the rotation via the
     * separate `video-rotate` property (rotation is an output transform, not
     * a filter). Both are runtime-settable; mpv rebuilds the video pipeline
     * on `vf` writes — which is why unchanged values are never re-written
     * (see the pacing note on the last-applied fields above).
     */
    private fun applyVideoEffects(config: EngineConfig) {
        val context = aliveCtx() ?: return
        val fx = config.videoEffects
        val chain = DesktopVideoEffectChain.buildVfChain(fx)
        if (chain != lastAppliedVfChain) {
            if (chain != null) {
                MpvLib.setPropertyString(context, "vf", chain)
            } else {
                MpvLib.command(context, "vf", "clr", "")
            }
            lastAppliedVfChain = chain
        }
        val rotation = DesktopVideoEffectChain.rotationDegrees(fx)
        if (rotation != lastAppliedRotationDeg) {
            // STRING, not DOUBLE: this libmpv REJECTS FORMAT_DOUBLE writes on
            // the integer `video-rotate` property (verified live — the write
            // returns MPV_ERROR_INVALID_PARAMETER and nothing sticks; the
            // string form applies and reads back).
            MpvLib.setPropertyString(context, "video-rotate", rotation.toString())
            lastAppliedRotationDeg = rotation
        }
    }

    // ── Cue history (G10, mirrors the Android MPV engine's accumulate path) ─

    /**
     * Folds a newly-displayed subtitle line into the [currentCues] history so
     * the subtitle-sync preview can render prev/active/next for embedded subs
     * without re-fetching bytes. mpv fires `sub-text` only on a line *change*
     * and skips blank clears here; the end time starts open-ended and is
     * closed when the next line begins. Covers the played range only — the
     * merge itself is player-video's shared [mergeAccumulatedCues] (now
     * public for this adapter), identical to Android's
     * `accumulateMpvSubText` call shape.
     *
     * Micro-divergence from Android: the START time is read live from the
     * `sub-start` property instead of Android's event-cached value — this
     * libmpv delivers the `sub-text` event BEFORE the matching `sub-start`
     * property update (observed live: the second line inherited the first
     * line's cached start), so the cache is stale exactly at line
     * transitions. One property read per line change; the position fallback
     * mirrors Android for the no-line-yet case.
     */
    private fun accumulateCue(text: String) {
        if (text.isBlank()) return
        val startSec = aliveCtx()
            ?.let { propDouble(it, "sub-start") }
            ?.takeIf { it >= 0 }
            ?: (currentPositionMs / 1000.0)
        val incoming = TimedCue((startSec * 1_000_000L).toLong(), Long.MAX_VALUE, text)
        _currentCues.value = mergeAccumulatedCues(_currentCues.value, listOf(incoming))
    }

    // ── Screenshot capture ────────────────────────────────────────

    /**
     * captures the currently-displayed video frame (subtitles
     * composited, like Android's PixelCopy path) via mpv's
     * `screenshot-to-file` into a temp PNG, decodes it into the platform
     * bitmap the desktop capture seam consumes, and deletes the temp file.
     * Returns null when there is nothing to capture (no file loaded, a
     * `vo=null` audio-only engine has no frame) or the command/decode fails —
     * callers degrade to a failure message, never an exception. Engine-tested
     * against the bundled libmpv (sw-render variant); the HWND-embedded
     * production vo is the same mpv code path.
     */
    override fun captureVideoFrame(): BufferedImage? {
        val context = aliveCtx() ?: return null
        if (!fileLoaded) return null
        return try {
            val temp = File.createTempFile(TEMP_SHOT_PREFIX, ".png")
            try {
                val ok = MpvLib.command(context, "screenshot-to-file", temp.absolutePath, "subtitles")
                if (!ok || temp.length() <= 0L) null else ImageIO.read(temp)
            } finally {
                temp.delete()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Open for [MpvSoftwareRenderEngine], which must pin sw decode (no interop in the sw path). */
    protected open fun hwdecFor(mode: DecoderMode): String = when (mode) {
        DecoderMode.SW_ONLY -> "no"
        else -> "auto-safe"   // HW_PREFERRED / HW_ONLY and any future variants
    }

    // ── Tracks ──────────────────────────────────────────────────────────────

    private fun refreshTracks() {
        val context = aliveCtx() ?: return
        val raw = MpvLib.readNode(context, "track-list") as? List<*> ?: run {
            _availableTracks.value = emptyList()
            return
        }
        val tracks = raw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val type = when (map["type"] as? String) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> return@mapNotNull null   // video tracks aren't in the contract
            }
            val id = (map["id"] as? Long)?.toInt() ?: return@mapNotNull null
            val title = map["title"] as? String
            val lang = map["lang"] as? String
            MediaTrack(
                id = "mpv_$id",
                index = id,
                label = title ?: lang ?: "Track $id",
                language = lang,
                isSelected = map["selected"] as? Boolean == true,
                type = type,
                streamIndex = (map["ff-index"] as? Long)?.toInt(),
                badges = TrackLabelFormatter.badges(
                    TrackLabelInfo(
                        title = title,
                        language = lang,
                        isForced = map["forced"] as? Boolean == true,
                        isDefault = map["default"] as? Boolean == true,
                        isHearingImpaired = map["hearing-impaired"] as? Boolean == true,
                    ),
                ),
            )
        }
        _availableTracks.value = tracks
    }

    // ── Stats projection ────────────────────────────────────────────────────

    private fun projectVideoStats() {
        val context = aliveCtx() ?: return
        probeHdrTarget(context)
        _videoStats.value = EngineVideoStats(
            videoCodec = MpvLib.getPropertyString(context, "video-format"),
            videoDecoder = MpvLib.getPropertyString(context, "hwdec-current")?.takeIf { it != "no" },
            videoResolution = resolution(context),
            videoFrameRate = propDouble(context, "container-fps")?.toFloat(),
            videoBitrate = propDouble(context, "video-bitrate")?.toInt(),
            audioCodec = MpvLib.getPropertyString(context, "audio-codec-name"),
            audioSampleRate = propDouble(context, "audio-params/samplerate")?.toInt(),
            audioChannels = propDouble(context, "audio-params/channel-count")?.toInt(),
            audioBitrate = propDouble(context, "audio-bitrate")?.toInt(),
            bufferedPositionMs = bufferedPositionMs.value,
            bufferSizeBytes = propDouble(context, "demuxer-cache-state/total-bytes")?.toLong() ?: 0L,
            droppedFrames = propDouble(context, "decoder-frame-drop-count")?.toLong() ?: 0L,
            avsyncMs = propDouble(context, "total-avsync")?.toFloat(),
            displayFps = propDouble(context, "display-fps")?.toFloat(),
            voFrameDropCount = propDouble(context, "frame-drop-count")?.toLong(),
            // The cached HDR-target verdict (badge + fallback notice).
            videoHdrType = hdrStatsType,
            hdrOutputActive = hdrStatsActive,
            hdrOutputNotice = hdrStatsNotice,
        )
    }

    /**
     * The display-capability probe: reads `video-target-params/gamma`
     * (mpv exposes the ACTIVE output transfer there — pq/HLG on an HDR
     * target) ONCE per file, at the first stats tick where the property
     * resolves after FILE_LOADED, and caches the verdict. Re-reads would be
     * harmless, but the plan's "poll once after first frame" keeps the
     * badge stable against mid-file output switches.
     */
    private fun probeHdrTarget(context: Pointer) {
        if (!hdrProbePending) return
        val gamma = MpvLib.getPropertyString(context, "video-target-params/gamma") ?: return
        hdrProbePending = false
        hdrTargetIsHdr = gamma == TARGET_GAMMA_PQ || gamma == TARGET_GAMMA_HLG
        hdrStatsType = when (gamma) {
            TARGET_GAMMA_PQ -> "HDR10"
            TARGET_GAMMA_HLG -> "HLG"
            else -> null
        }
        val requested = hdrPassthroughRequested(currentConfig)
        hdrStatsActive = requested && hdrTargetIsHdr
        // Passthrough on but the display target stayed SDR: the tone
        // mapping handles the HDR→SDR conversion — say so in one line.
        hdrStatsNotice =
            if (requested && !hdrTargetIsHdr) HDR_SDR_FALLBACK_NOTICE else null
    }

    private fun resolution(context: Pointer): String? {
        val w = propDouble(context, "video-params/w")?.toInt() ?: return null
        val h = propDouble(context, "video-params/h")?.toInt() ?: return null
        return "${w}x${h}"
    }

    private fun propDouble(context: Pointer, name: String): Double? {
        val mem = Memory(8)
        val rc = MpvLib.mpv.mpv_get_property(context, name, FORMAT_DOUBLE, mem)
        return if (rc >= 0) mem.getDouble(0) else null
    }

    private fun propFlag(context: Pointer, name: String): Boolean {
        val mem = Memory(4)
        val rc = MpvLib.mpv.mpv_get_property(context, name, FORMAT_FLAG, mem)
        return rc >= 0 && mem.getInt(0) != 0
    }

    // ── Error taxonomy (shared MpvErrorTaxonomy — identical mapping to the
    // ── Android MpvPlayerEngine; the JNA int-code hand-off stays here) ─────

    /**
     * The int-code edge only: classification comes from the shared
     * [MpvErrorTaxonomy.fromCode]; the desktop's one kept divergence is the
     * Unknown arm's diagnostic text — libmpv's `mpv_error_string(code)`
     * instead of Android's raw code string (see the taxonomy KDoc).
     */
    private fun mapMpvError(errorCode: Int): EngineError =
        MpvErrorTaxonomy.fromCode(errorCode, unknownDetail = MpvLib.mpv.mpv_error_string(errorCode))

    private companion object {
        private const val DEFAULT_POLLING_INTERVAL_MS = 1000L
        private const val VIDEO_STATS_POLL_MS = 1000L
        private const val RELEASE_JOIN_TIMEOUT_MS = 2_000L
        private const val RELEASE_JOIN_ATTEMPTS = 3
        /** mpv's untouched default for `audio-channels`. */
        private const val AUTO_CHANNELS = "auto"

        /** Temp-file prefix for screenshot-to-file captures. */
        private const val TEMP_SHOT_PREFIX = "jellyplay-frame-"

        /** `video-target-params/gamma` values that mean an HDR output. */
        private const val TARGET_GAMMA_PQ = "pq"
        private const val TARGET_GAMMA_HLG = "hlg"

        /** The one-line stats fallback notice when passthrough is on but the target stayed SDR. */
        private const val HDR_SDR_FALLBACK_NOTICE = "SDR display - tone mapping active"
    }
}
