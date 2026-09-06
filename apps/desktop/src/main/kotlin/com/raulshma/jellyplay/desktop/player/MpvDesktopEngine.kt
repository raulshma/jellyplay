package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.END_FILE_REASON_EOF
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.END_FILE_REASON_ERROR
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_AO_INIT_FAILED
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_LOADING_FAILED
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_NOTHING_TO_PLAY
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_NOT_IMPLEMENTED
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_UNKNOWN_FORMAT
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_UNSUPPORTED
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib.ERROR_VO_INIT_FAILED
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
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelFormatter
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelInfo
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
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
 * [MediaEngine] contract (plan §Phase V2). Property/event surface mirrors the
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
 * The former V2 cuts are closed (wave 17B, the "when the player feature
 * migrates" trigger fired waves ago): `EngineConfig.videoEffects` is applied
 * as a live mpv `vf` chain + `video-rotate` property ([DesktopVideoEffectChain]
 * builds the strings — see its shared→mpv parity table), screenshot capture
 * goes through mpv's `screenshot-to-file` ([captureVideoFrame], the desktop
 * seam's COMPOSE engine hook), and [currentCues] accumulates the live-cue
 * history from the observed `sub-text` (with a live `sub-start` read) like
 * the Android MPV engine's `accumulateMpvSubText`. Wave 14C closed the
 * audio-effects cut before that: `EngineConfig.audioEffects` is applied as a
 * live mpv `af` chain + `audio-channels`/`pitch` properties
 * ([DesktopAudioEffectChain] builds the strings — see its Android→mpv parity
 * table). `PlaybackRequest.normalizationGain` stays unused here: the desktop
 * audio path carries the manager-computed final ReplayGain dB in the config
 * (`replayGainEffectiveDb`), mirroring where Android's `AudioPlaybackManager`
 * applies the gain.
 *
 * Open (wave 12B) so [MpvSoftwareRenderEngine] can subclass it for the
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
) : MediaEngine,
    DesktopFrameCaptureEngine {

    override val displayName: String = PlayerType.MPV.displayName

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsPip = false,          // no PiP on desktop; windowing covers it
        supportsMiniMode = false,
        // Wave 17B: `sub-text`/`sub-start` now accumulate into currentCues
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

    override val underlyingPlayer: Any? get() = aliveCtx()

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
     * Optional release notification (CONC-1, 2026-09 audit): invoked EXACTLY
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

    // Video twin of the same discipline (wave 17B): `vf` writes re-init the
    // video pipeline, so only actual CHANGES are pushed, and an all-defaults
    // config performs zero writes. `video-rotate` starts at mpv's own 0.
    @Volatile private var lastAppliedVfChain: String? = null
    @Volatile private var lastAppliedRotationDeg: Int = 0

    /**
     * The live mpv handle for member calls, or null after [release] — every
     * public member routes through this so a post-release call degrades to a
     * no-op instead of JNA-calling a destroyed context.
     */
    private fun aliveCtx(): Pointer? = if (released.get()) null else ctx

    /**
     * Engine-variant hook (wave 12B): the live mpv handle for subclasses that
     * attach auxiliary contexts tied to it — [MpvSoftwareRenderEngine]'s
     * render-API context is created on this handle at construction. Returns
     * null post-[release] like [aliveCtx].
     */
    protected fun liveMpvHandle(): Pointer? = aliveCtx()

    /**
     * Engine-variant hook (wave 12B): emits into the engine's [errorFlow]
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
            // Contract: null when no line is active — mpv emits "" on clear.
            // Non-blank lines also fold into the currentCues history (G10,
            // same pairing as Android's accumulateMpvSubText). Wave 17B fix:
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
        onReleased?.invoke()
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
     * Engine-variant hook (wave 12B): invoked exactly once during [release],
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

    override fun setVolume(value: Float) {
        val context = aliveCtx() ?: return
        volumePercent = (value.coerceIn(0f, 1f) * 100.0)
        MpvLib.setPropertyDouble(context, "volume", volumePercent)
    }

    override fun increaseVolume(delta: Float) = setVolume(volume + delta)

    override fun decreaseVolume(delta: Float) = setVolume(volume - delta)

    override fun setMuted(muted: Boolean) {
        val context = aliveCtx() ?: return
        MpvLib.setPropertyFlag(context, "mute", muted)
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

    override fun applySubtitleStyle(style: SubtitleStyle) {
        val context = aliveCtx() ?: return
        if (!style.applyCustomStyle) {
            // Native ASS styling with the Android default override mode
            // ("scale" keeps embedded layout/styling, only resizes).
            MpvLib.setPropertyString(context, "sub-ass-override", "scale")
            return
        }
        MpvLib.setPropertyDouble(context, "sub-font-size", style.fontSize.toDouble())
        MpvLib.setPropertyString(context, "sub-color", argbCss(style.fontColor, 1f))
        MpvLib.setPropertyString(context, "sub-border-color", argbCss(style.edgeColor, 1f))
        MpvLib.setPropertyString(
            context,
            "sub-back-color",
            argbCss(style.backgroundColor, style.backgroundOpacity),
        )
        // sub-pos is measured bottom-up in percent; the app's verticalPosition
        // is top-down (0 = top edge).
        MpvLib.setPropertyDouble(
            context,
            "sub-pos",
            (100.0 - style.verticalPosition * 100.0).coerceIn(0.0, 100.0),
        )
        when (style.edgeType) {
            SubtitleEdgeType.OUTLINE,
            SubtitleEdgeType.RAISED,
            SubtitleEdgeType.DEPRESSED,
            -> {
                MpvLib.setPropertyDouble(context, "sub-border-size", 2.5)
                MpvLib.setPropertyDouble(context, "sub-shadow-offset", 0.0)
            }
            SubtitleEdgeType.DROP_SHADOW -> {
                MpvLib.setPropertyDouble(context, "sub-border-size", 0.0)
                MpvLib.setPropertyDouble(context, "sub-shadow-offset", 1.5)
            }
            SubtitleEdgeType.NONE -> {
                MpvLib.setPropertyDouble(context, "sub-border-size", 0.0)
                MpvLib.setPropertyDouble(context, "sub-shadow-offset", 0.0)
            }
        }
        // FORCE is libass's full-restyle mode — the equivalent of the Android
        // engine's --ass-override=force handling of AssOverrideMode.FORCE.
        MpvLib.setPropertyString(context, "sub-ass-override", "force")
    }

    private fun argbCss(color: SubtitleColor, opacity: Float): String {
        val argb = color.value
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        // mpv's sub-* color props take #RRGGBBAA.
        return stringHex(r, g, b, alpha)
    }

    private fun stringHex(r: Int, g: Int, b: Int, a: Int): String {
        fun h(v: Int) = v.coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
        return "#${h(r)}${h(g)}${h(b)}${h(a)}"
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
        applyAudioEffects(config)
        applyVideoEffects(config)
    }

    /**
     * Wave 14C: push the audio-effects config onto mpv — the `af` chain
     * ([DesktopAudioEffectChain.buildAfChain]), the channel-mix
     * `audio-channels` property, and the `pitch` property. All three are
     * runtime-settable; mpv rebuilds the audio chain on write — which is why
     * unchanged values are never re-written (see the pacing note on the
     * last-applied fields).
     */
    private fun applyAudioEffects(config: EngineConfig) {
        val context = aliveCtx() ?: return
        val fx = config.audioEffects
        val channels = DesktopAudioEffectChain.channelMixToAudioChannels(fx.channelMixMode, fx.channelMixEnabled)
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
     * Wave 17B: push the video-effects config onto mpv — the `vf` chain
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
     * closed when the next line begins. Covers the played range only —
     * identical semantics to Android's `accumulateMpvSubText`/
     * `mergeAccumulatedCues` pair (the merge rules are mirrored privately
     * here because player-video keeps its accumulator module-internal).
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
        val existing = _currentCues.value
        if (existing.isEmpty()) {
            _currentCues.value = listOf(incoming)
            return
        }
        // Close the open-ended span of any cue still "active" at the point
        // the new line begins.
        var changed = false
        val closed = existing.map { cue ->
            if (cue.endTimeUs == Long.MAX_VALUE && cue.startTimeUs < incoming.startTimeUs) {
                changed = true
                cue.copy(endTimeUs = incoming.startTimeUs)
            } else {
                cue
            }
        }
        // mpv re-emits the active line on some track/list transitions — an
        // identical repeat changes nothing (only the closure above applies).
        val lastText = closed.lastOrNull()?.text
        if (lastText != null && incoming.text.toString() == lastText.toString()) {
            if (changed) _currentCues.value = closed
            return
        }
        _currentCues.value = (closed + incoming)
            .sortedBy { it.startTimeUs }
            .takeLast(MAX_ACCUMULATED_CUES)
    }

    // ── Screenshot capture (wave 17B) ────────────────────────────────────────

    /**
     * Wave 17B: captures the currently-displayed video frame (subtitles
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
        )
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

    // ── Error taxonomy (identical mapping to Android MpvPlayerEngine) ──────

    private fun mapMpvError(errorCode: Int): EngineError = when (errorCode) {
        ERROR_LOADING_FAILED -> EngineError.Network(null)
        ERROR_AO_INIT_FAILED,
        ERROR_VO_INIT_FAILED,
        ERROR_NOTHING_TO_PLAY,
        ERROR_UNKNOWN_FORMAT,
        ERROR_UNSUPPORTED,
        ERROR_NOT_IMPLEMENTED,
        -> EngineError.Decoder(codec = null, cause = null)
        else -> EngineError.Unknown(
            "Playback error (mpv): ${MpvLib.mpv.mpv_error_string(errorCode)}",
        )
    }

    private companion object {
        private const val DEFAULT_POLLING_INTERVAL_MS = 1000L
        private const val VIDEO_STATS_POLL_MS = 1000L
        private const val RELEASE_JOIN_TIMEOUT_MS = 2_000L
        private const val RELEASE_JOIN_ATTEMPTS = 3
        /** mpv's untouched default for `audio-channels`. */
        private const val AUTO_CHANNELS = "auto"

        /** Cue-history cap (player-video's CueAccumulator.MAX_ACCUMULATED_CUES). */
        private const val MAX_ACCUMULATED_CUES = 500

        /** Temp-file prefix for screenshot-to-file captures. */
        private const val TEMP_SHOT_PREFIX = "jellyplay-frame-"
    }
}
