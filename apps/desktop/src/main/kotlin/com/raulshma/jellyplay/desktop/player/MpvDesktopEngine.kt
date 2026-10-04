package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
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
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatioMapping
import com.raulshma.jellyplay.feature.player.video.engine.BufferedRanges
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilityMatrix
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.feature.player.video.engine.EngineStateChassis
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MpvConfigApplier
import com.raulshma.jellyplay.feature.player.video.engine.MpvConfigMapping
import com.raulshma.jellyplay.feature.player.video.engine.MpvErrorTaxonomy
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvFoldApplier
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeHost
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeValue
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPlaybackEvent
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertyIntake
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertyIntakeResult
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertySurface
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsProjection
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsReads
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleSideLoadPlan
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleStyleApplier
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleStylePhase
import com.raulshma.jellyplay.feature.player.video.engine.MpvTlsOptions
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvTrackCatalog
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvUserSubtitleKeys
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvVideoEffectChain
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackVolumePolicy
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TrackRefreshCoalescer
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.feature.player.video.engine.VolumeCommandTemplates
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
import com.raulshma.jellyplay.feature.player.video.engine.mergeAccumulatedCues
import com.raulshma.jellyplay.feature.player.video.engine.resolveDurationMs
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Desktop playback backend: libmpv over JNA, implementing the common
 * MediaEngine contract through the shared [EngineStateChassis] supertype
 * (the published-state chassis androidMain's BasePlayerEngine also extends —
 * the desktop cannot extend that androidMain class directly, so its former
 * twelve-field re-declaration rides the common chassis instead).
 * Property/event surface mirrors the
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
 * as a live mpv `vf` chain + `video-rotate` property ([MpvVideoEffectChain]
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
) : EngineStateChassis(
    // The chassis's parameterized SharedFlow capacities — this engine's
    // deliberate divergences from the Android base's 0/1/1 defaults:
    //
    // replay=1 on the error flow: construction-time failures (libmpv
    // missing/unloadable, render-context create) are emitted BEFORE the
    // EngineEventCoordinator subscribes — PlayerSessionManager publishes the
    // engine into its StateFlow and the collector attaches a beat later, so
    // with replay=0 those emissions hit zero subscribers and vanish. That is
    // exactly how a missing libmpv used to become a silent black player
    // screen; the replay hands the last error to the late subscriber instead.
    // The 8-slot extra buffers ride along with it (the former twin's values).
    errorReplay = 1,
    errorExtraBufferCapacity = 8,
    subtitleExtraBufferCapacity = 8,
),
    DesktopFrameCaptureEngine {

    override val displayName: String = PlayerType.MPV.displayName

    /**
     * Derived from [EngineCapabilityMatrix.MPV] (the matrix's KDoc mandates
     * engines derive from it, so the engine runtime cannot diverge from the
     * matrix by construction — the former hand-typed copy had already dropped
     * `supportsImageSubtitles`, silently disabling offline .sup side-loading
     * on desktop). The declared desktop divergences are the two windowing
     * flags only.
     */
    override val capabilities: EngineCapabilities = EngineCapabilityMatrix.MPV.copy(
        supportsPip = false,          // no PiP on desktop; windowing covers it
        supportsMiniMode = false,     // also the matrix's MPV row; pinned as the desktop's declared value
    )

    override val zoomSafeSubtitleStrategy: ZoomSafeSubtitleStrategy =
        ZoomSafeSubtitleStrategy.COMPOSE_CUE

    // Read side of the ownership gate below ([userOwnedSubtitleKeys]): the
    // subtitle-style UI renders its custom-config notice from this snapshot.
    // No dropped-keys half — the desktop runs config=no, so there is no
    // on-disk mpv.conf whose values mpv's parser could destroy; ownership
    // comes from the in-app extra-config text alone (option API, `#`-safe).
    override val subtitleStyleOwnership: MpvSubtitleOwnership
        get() = MpvSubtitleOwnership(userOwnedSubtitleKeys)

    // ── State surface (player-contract EngineStateChassis) ──────────────────
    //
    // The twelve flow backing fields + exposures (playbackState/isPlaying/
    // tracks/cues/live cue/errors/subtitle events/buffered scalar+ranges/
    // stats/polling/stats-enabled) and the setPollingIntervalMs/
    // setVideoStatsEnabled finals used to be re-declared here verbatim (the
    // androidMain BasePlayerEngine block's desktop twin). They now come from
    // the shared [EngineStateChassis] supertype — the capacities this engine
    // needs are the constructor parameters at the top of the class, and the
    // chassis fields initialize in the super constructor, still BEFORE the
    // `ctx` initializer below (createMpv emits into the error flow during
    // construction, so the backing flow must exist first — that ordering
    // invariant is what this section's former comment pinned).
    //
    // `currentConfig` comes from the chassis too (@Volatile there — the mpv
    // event thread and stats poller read it while the UI thread writes).

    // Scope law (the local twin of androidMain BasePlayerEngine's
    // self-healing engineScope): created ONCE and cancelled EXACTLY ONCE —
    // in the terminal [release], before mpv_terminate_destroy. This engine
    // never internally releases (no release()-as-reset path) and is
    // single-use: the released CAS in [release] is irreversible, so no load
    // can run after the scope dies and no recreate is needed.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val audioSessionId: Int = -1   // no Android audio session on desktop

    @Volatile private var positionMs: Long = 0L
    @Volatile private var durationValue: Long = 0L
    /** Server-reported runtime fallback while the demuxer hasn't resolved one. */
    @Volatile private var serverDurationMs: Long = 0L
    override val currentPositionMs: Long get() = positionMs

    /**
     * The shared engine→server fallback ladder ([resolveDurationMs], fed from
     * [PlaybackRequest.serverDurationMs] at [load] — Android mpv parity): the
     * demuxer's duration whenever it resolved positive, else the server's
     * runTimeTicks (the only accurate total for HLS/transcoded streams).
     * Replaces the desktop's former private two-line re-implementation.
     */
    override val durationMs: Long
        get() = resolveDurationMs(durationValue, serverDurationMs)

    @Volatile private var speedValue: Float = 1f
    override val playbackSpeed: Float get() = speedValue

    @Volatile private var volumePercent: Double = 100.0
    override val volume: Float get() = (volumePercent / 100.0).toFloat()

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = EnginePositionTicker(
            scopeProvider = { engineScope },
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
        // Observer registration + the event-thread start live in the LATE init
        // block at the bottom of this class (before the companion): Kotlin
        // executes property initializers and init blocks in DECLARATION
        // ORDER, and mpv queues a backlog the moment observers register (one
        // initial event per observed property, plus CoreIdle from `idle=yes`)
        // — starting the loop any earlier folded events over this class's
        // still-uninitialized state (observed live: a fold over the not-yet-
        // initialized latch state intrinsic-checked, killed the event thread,
        // and the engine stayed deaf for its whole life — READY never fired).

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
        // Track-selection changes: the fold clears the cue history/live line on
        // a sid switch (the former desktop gap — lines from the previous
        // subtitle track bled into the sync preview) and re-enumerates tracks
        // on either switch (the track-list observer alone does not reliably
        // fire for every switch shape; the Android engine has always re-polled).
        observe("sid", FORMAT_STRING)
        observe("aid", FORMAT_STRING)
        observe("track-list", FORMAT_NODE)
        // Live output channel layout — the balance (`pan`) af stage must know
        // it because `pan` pins the output layout (see DesktopAudioEffectChain).
        observe("audio-params/channel-count", FORMAT_INT64)
    }

    // ── Event dispatch ──────────────────────────────────────────────────────

    /**
     * The shared fold-application body ([MpvFoldApplier], player-contract):
     * folds each event through [MpvEventFold] and applies the declared
     * decisions to the chassis flows plus this engine's two genuine sinks —
     * the coalesced [refreshTracks] (the reason label is accepted for
     * fold-parity and ignored; Android's publish log is its declared use) and
     * the live-`sub-start` cue accumulator. The engine keeps no local latch
     * twin; the applier owns [MpvFoldApplier.latches]. All fold applications
     * run on the single mpv event thread, so read-modify-write is race-free;
     * reset per item in [load] and in [stop]. The former standalone
     * `fileLoaded` field was the applier's `fileLoaded` latch.
     */
    private val foldApplier = MpvFoldApplier(
        isPlaying = _isPlaying,
        playbackState = _playbackState,
        currentCues = _currentCues,
        liveSubtitleCue = _liveSubtitleCue,
        refreshTracks = { _ -> refreshTracks() },
        onLiveSubtitleLine = ::accumulateCue,
    )
    @Volatile private var pendingSubtitles: List<SubtitleSource> = emptyList()
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

    // The structured [MpvEngineConfig] diff cache lives in the shared
    // [MpvConfigApplier] now (its `lastAppliedConfigProps`, same discipline:
    // starts empty so the first FILE_LOADED application writes every owned
    // key once; subsequent applies — every FILE_LOADED, plus live
    // `engineSpecific` changes — write only actual CHANGES. scaler/deband
    // changes reconfigure the vo pipeline, `audio-device` re-opens the ao —
    // both only ever written on a real change).

    /**
     * The `sub-*` styling keys the user explicitly owns via the in-app
     * Advanced MPV Configuration — the desktop mpv runs with `config=no`
     * (no on-disk mpv.conf), so the extra-config text is the sole ownership
     * source. Refreshed on every config push; [applySubtitleStyle] skips
     * owned keys so the user's value wins for the session (issue #165, desktop parity).
     */
    @Volatile private var userOwnedSubtitleKeys: Set<String> = emptySet()

    private fun refreshUserOwnedSubtitleKeys(config: EngineConfig) {
        val mpvCfg = config.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
        userOwnedSubtitleKeys = MpvUserSubtitleKeys.ownedKeys(mpvConfText = null, extraConfigText = mpvCfg.mpvExtraConfig)
    }

    /**
     * The desktop [MpvPropertySurface] over the JNA binding. JNA property
     * writes return an error code instead of throwing, so "absorb failures"
     * here means ignoring the boolean result — exactly what the former inline
     * calls did. Ownership gating lives in [MpvSubtitleStyleApplier].
     */
    private inner class DesktopMpvSurface(private val context: Pointer) : MpvPropertySurface {
        override fun setOptionString(name: String, value: String) {
            MpvLib.mpv.mpv_set_option_string(context, name, value)
        }

        override fun setPropertyString(name: String, value: String) {
            MpvLib.setPropertyString(context, name, value)
        }

        override fun setPropertyDouble(name: String, value: Double) {
            MpvLib.setPropertyDouble(context, name, value)
        }

        override fun setPropertyInt(name: String, value: Int) {
            MpvLib.setPropertyInt(context, name, value)
        }

        override fun setPropertyBoolean(name: String, value: Boolean) {
            MpvLib.setPropertyFlag(context, name, value)
        }
    }

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
            EVENT_START_FILE -> foldApplier.apply(MpvPlaybackEvent.StartFile)
            EVENT_FILE_LOADED -> {
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
                // Fold seeds READY + isPlaying from the LIVE core pause state:
                // when the core auto-plays (default), `pause` never *changes*,
                // so the property-change handler below alone would never fire.
                foldApplier.apply(
                    MpvPlaybackEvent.FileLoaded(
                        pausedNow = ctx?.let { propFlag(it, "pause") } ?: true,
                    ),
                )
            }
            EVENT_END_FILE -> {
                val payload = event.data?.let { MpvEventEndFile(it).also { it.read() } }
                val result = foldApplier.fold(
                    MpvPlaybackEvent.EndFile(payload?.reason?.let(MpvPlaybackEvent.EndFileReason::fromCode)),
                )
                if (result.emitEndFileError) {
                    _errorFlow.tryEmit(mapMpvError(payload?.error ?: 0))
                }
                foldApplier.applyResult(result)
            }
            EVENT_IDLE -> foldApplier.apply(MpvPlaybackEvent.CoreIdle)
            EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
            EVENT_SHUTDOWN -> running = false
            else -> Unit
        }
    }

    /**
     * The desktop reader half of the shared [MpvPropertyIntake] intake
     * table: this when-block is EXTRACTION only — this JNA binding's
     * per-format decode of the raw `mpv_event_property` payload — and every
     * DECISION is a table row applied by [applyPropertyIntake]. The
     * extraction quirks that stay here (declared, not drift): the
     * null-payload → `false` flag coercion, the `FORMAT_STRING` char**
     * dereference, the sid/aid payload shipped unread (the fold keys on the
     * property name alone), and the NODE observations re-read through
     * [MpvLib.readNode] instead of decoding the event's node memory.
     */
    private fun handlePropertyChange(event: MpvEvent) {
        val prop = event.data?.let { MpvEventProperty(it).also { it.read() } } ?: return
        val name = prop.name?.getString(0) ?: return
        val data = prop.data
        when (name) {
            "pause", "paused-for-cache" ->
                applyPropertyIntake(name, MpvIntakeValue.Flag(data != null && data.getInt(0) != 0))
            "eof-reached" -> applyPropertyIntake(
                name,
                MpvIntakeValue.Flag(data != null && data.getInt(0) != 0),
                // eof flipped false (replay seek-back): the fold re-derives
                // isPlaying from the live pause — `pause` itself didn't
                // change, so its observer won't fire (same class as the
                // FILE_LOADED seed).
                livePaused = aliveCtx()?.let { propFlag(it, "pause") } ?: true,
            )
            "sid", "aid" -> applyPropertyIntake(name, MpvIntakeValue.Unread)
            "sub-text" ->
                // Contract: null when no line is active — mpv emits "" on
                // clear. FORMAT_STRING event data is a char** (client.h hands
                // the value behind one pointer) — reading the bytes AT data
                // yielded pointer garbage; dereference first.
                applyPropertyIntake(name, MpvIntakeValue.Text(data?.getPointer(0)?.getString(0).orEmpty()))
            "time-pos", "duration", "speed" ->
                data?.let { applyPropertyIntake(name, MpvIntakeValue.Decimal(it.getDouble(0))) }
            "demuxer-cache-time", "audio-params/channel-count" ->
                data?.let { applyPropertyIntake(name, MpvIntakeValue.Whole(it.getLong(0))) }
            "track-list", "demuxer-cache-state" ->
                // NODE observation arrives as a raw mpv_node pointer in the
                // event; the engine re-reads the property through
                // [MpvLib.readNode] (parsed Kotlin tree) at the sink instead
                // of decoding the event's node memory here.
                applyPropertyIntake(name, MpvIntakeValue.Node)
        }
    }

    /**
     * The desktop funnel of the shared [MpvPropertyIntake] table (the
     * property-intake twin of Android's `applyPropertyIntake`): events fold
     * through [foldApplier], cache sinks land in this engine's fields/flows
     * exactly where their shipped bodies wrote them, and the two node sinks
     * route into this engine's re-read seams. [livePaused] feeds the
     * eof-reached re-derivation; [previousChannelCount] (fed to
     * [MpvPropertyIntake.dispatch]) is the channel-count row's change guard
     * against [observedChannelCount].
     */
    private fun applyPropertyIntake(
        property: String,
        value: MpvIntakeValue,
        livePaused: Boolean = true,
    ) {
        when (val intake = MpvPropertyIntake.dispatch(
            host = MpvIntakeHost.DESKTOP,
            property = property,
            value = value,
            positionMs = positionMs,
            livePaused = livePaused,
            previousChannelCount = observedChannelCount,
        )) {
            is MpvPropertyIntakeResult.Event -> foldApplier.apply(intake.event)
            is MpvPropertyIntakeResult.CachedPositionMs -> positionMs = intake.ms
            is MpvPropertyIntakeResult.CachedDurationMs -> durationValue = intake.ms
            is MpvPropertyIntakeResult.CachedBufferedMs -> _bufferedPositionMs.value = intake.ms
            is MpvPropertyIntakeResult.CachedSpeed -> speedValue = intake.speed
            is MpvPropertyIntakeResult.ChannelLayoutChanged -> {
                observedChannelCount = intake.count
                // The layout changed (new item / channel-mix edit): the
                // stereo-gated balance stage must be rebuilt against the
                // new layout.
                applyAudioEffects(currentConfig)
            }
            MpvPropertyIntakeResult.RefreshTracks -> refreshTracks()
            MpvPropertyIntakeResult.DecodeBufferedRanges -> refreshBufferedRanges()
            // Android-only rows (sub-start / sub-visibility / the relative
            // buffered formula) and unknown/dropped properties — no desktop
            // intake, exactly as the former silent fall-through.
            else -> {}
        }
    }

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
        // Fresh fold state + fresh side-loaded-subtitle id registry for the new
        // item. [MpvSubtitleSideLoadPlan.planBatch] pre-seeds the registry (raw
        // label → SubtitleSource.id) when the pending batch executes at
        // FILE_LOADED — the offline-restore contract the desktop previously
        // lacked (see [sideLoadedSubtitleIds]).
        foldApplier.resetLatches()
        sideLoadedSubtitleIds = emptyMap()
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
        MpvTlsOptions.from(request.requestSpecific?.tls).forEach { (option, value) ->
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

    /**
     * Side-loaded-subtitle id registry: mpv track `title` → [SubtitleSource.id]
     * (the same shape as Android's mpv engine). mpv's `sub-add` takes no id
     * argument, so without this registry [refreshTracks] would emit the
     * synthetic `"mpv_sub_{id}"` and the caller's stable id — the
     * `offline:{index}` / `external:{index}` / `provider:` handle
     * `TrackSelectionPolicy` resolves side-loaded selections against — was
     * lost, making persisted sidecar selections unresolvable on desktop.
     * Maintained by [MpvSubtitleSideLoadPlan], consumed by [MpvTrackCatalog];
     * reset per item in [load].
     */
    @Volatile private var sideLoadedSubtitleIds: Map<String, String> = emptyMap()

    private fun applyPendingSubtitles() {
        val context = aliveCtx() ?: return
        val pending = pendingSubtitles
        pendingSubtitles = emptyList()
        if (pending.isEmpty()) return
        // The shared application loop — planBatch (dedupe against the live
        // track-list, same-title uniquify, isDefault force-select), the
        // registry store-back BEFORE any add executes, then each planned add
        // through this engine's `sub-add` seam — see
        // [MpvSubtitleSideLoadPlan.applyBatch].
        MpvSubtitleSideLoadPlan.applyBatch(
            pending = pending,
            existingLabels = existingSubLabels(),
            registry = sideLoadedSubtitleIds,
            onRegistry = { sideLoadedSubtitleIds = it },
        ) { add ->
            // sub-add <url> [flags [title [lang]]] — title doubles as the
            // stable label the track-list echoes back, matching how the
            // Android engine keys side-loaded tracks. Desktop transport
            // spelling: raw URL, empty-string lang when the source has none
            // (Android omits the arg instead).
            MpvLib.command(context, "sub-add", add.source.url, add.flags, add.label, add.source.language ?: "")
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
        // Full teardown reset — the chassis's published-state choreography
        // (parity FIX: the desktop's former release only flipped the two
        // transport leaves back — _playbackState → IDLE, _isPlaying → false —
        // leaving the item-scoped leaves (cues / tracks / buffered scalar +
        // ranges / stats / live cue) published from the dead item. The Android
        // engines have always run this full reset via
        // [EngineStateChassis.resetPublishedEngineState]; the desktop now runs
        // the same list. The scope-cancel law above is untouched: the chassis
        // reset never touches the scope, which stays create-once/cancel-once.)
        resetPublishedEngineState()
    }

    /**
     * The mpv residue in the chassis resets — the live subtitle line (the
     * same shape as Android MpvPlayerEngine's override; the cue history/
     * tracks/buffer/stats leaves are chassis-owned and cleared for us).
     */
    override fun onResetItemScopedState() {
        _liveSubtitleCue.value = null
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
        foldApplier.resetLatches()
        positionMs = 0L
        durationValue = 0L
        // The server rung of the duration ladder dies with the item — the
        // parity FIX: the former body reset `durationValue` but left
        // `serverDurationMs` serving the stopped file's runtime forever after
        // (Android mpv resets its twin in load/release; the desktop's stop is
        // its per-item teardown, so the fallback dies here).
        serverDurationMs = 0L
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
    // The four commands are the commonMain [VolumeCommandTemplates] finals
    // over [PlaybackVolumePolicy] — the same plan → remember → capture →
    // native write choreography ReloadablePlayerEngine runs on Android (clamp
    // to the boost ceiling, remember the last audible level, snapshot it on
    // mute, restore it on unmute; remember-before-write). Desktop owns the
    // APPLY boundary only: the policy works in normalized units (1.0 ==
    // nominal) and [applyVolumeLevel] is the single 0..1 → 0..100 conversion
    // at the mpv `volume` property write. There is no Android system music
    // stream to sync on desktop — the mpv volume property is the only volume
    // surface — so the restore vocabulary writes the REMEMBERED_LEVEL straight
    // into the property on unmute, and mute is LEAVE_UNCHANGED for the
    // property (mpv's mute FLAG is the silencing mechanism).

    /** Last audible normalized level; the restore target for unmute. */
    @Volatile private var lastUnmuteVolume: Float = 1f

    /**
     * Per-content-type volume-memory capture (the
     * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] hook).
     * Fired from the template ONLY for user-initiated changes
     * ([PlaybackVolumePolicy.LevelPlan.isUserChange]) — the session host
     * assigns it with the active item's bucket, so a remembered level
     * reflects what the user chose, never a sleep-timer fade. Same
     * assignment-safety shape as [onReleased]: assigned by the host after
     * construction.
     */
    @Volatile override var onUserVolumeChange: ((level: Float) -> Unit)? = null

    /** Same rule as ReloadablePlayerEngine's — remember only audible levels. */
    private fun rememberUnmuteVolumeIfAudible(volume01: Float) {
        if (volume01 > 0f) lastUnmuteVolume = volume01
    }

    private val volumeCommands = object : VolumeCommandTemplates.NativeVolumeSurface {
        override val rememberedUnmuteLevel: Float get() = lastUnmuteVolume

        override fun readNativeVolume(): Float? = aliveCtx()?.let {
            // The cached percent IS the native level — this engine is the only
            // writer of mpv's `volume`, so the mirror cannot drift (Android
            // reads the property live because its handle can be touched
            // elsewhere).
            (volumePercent / 100.0).toFloat()
        }

        override fun applyNativeVolume(normalized: Float) {
            aliveCtx()?.let { applyVolumeLevel(it, normalized) }
        }

        override fun applyNativeMuteFlag(muted: Boolean) {
            // mpv owns a real mute flag — the flag silences; the volume
            // property is not the mute mechanism. Written first, matching the
            // Android template's applyNativeMuteFlag step.
            aliveCtx()?.let { MpvLib.setPropertyFlag(it, "mute", muted) }
        }

        override fun snapshotVolumeForMute() {
            // No system stream: the pre-mute native level itself is what an
            // unmute must restore.
            readNativeVolume()?.let(::rememberUnmuteVolumeIfAudible)
        }

        override fun rememberUnmuteVolume(level: Float) = rememberUnmuteVolumeIfAudible(level)

        override fun onUserVolumeChanged(level: Float) {
            onUserVolumeChange?.invoke(level)
        }

        override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
            if (muted) PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED
            else PlaybackVolumePolicy.NativeVolumeRestore.REMEMBERED_LEVEL
    }

    override fun setVolume(value: Float, isUserChange: Boolean) {
        aliveCtx() ?: return
        VolumeCommandTemplates.setVolume(volumeCommands, value, isUserChange)
    }

    override fun increaseVolume(delta: Float) {
        aliveCtx() ?: return
        // Key/remote nudges are user-shaped for memory capture — the template
        // plans the delta with isUserChange = true, as the former bodies did.
        VolumeCommandTemplates.increaseVolume(volumeCommands, delta)
    }

    override fun decreaseVolume(delta: Float) {
        aliveCtx() ?: return
        VolumeCommandTemplates.decreaseVolume(volumeCommands, delta)
    }

    override fun setMuted(muted: Boolean) {
        aliveCtx() ?: return
        VolumeCommandTemplates.setMuted(volumeCommands, muted)
    }

    /** The one normalized→percent boundary conversion for the mpv `volume` write. */
    private fun applyVolumeLevel(context: Pointer, normalized: Float) {
        volumePercent = normalized * 100.0
        MpvLib.setPropertyDouble(context, "volume", volumePercent)
    }

    // ── MediaEngine: tracks & subtitles ─────────────────────────────────────

    /**
     * Android mpv parity (MpvPlayerEngine.selectTrack's decision logic, over
     * this engine's JNA transport): a NEGATIVE index deselects — `aid` back to
     * mpv's "auto" heuristic, `sid` to "no" (mpv has no numeric deselect id) —
     * while a positive id writes as INT first (the typed FORMAT_INT64 write)
     * with the string form as the fallback (the transport-dependent spelling
     * of Android's int-then-string catch), and a subtitle selection
     * re-enables `sub-visibility` — the app may have hidden native subs for
     * the zoom-safe overlay ([setNativeSubtitlesVisible]), and an explicit
     * user pick means they want them seen. The former desktop body wrote the
     * raw index string for both arms, so `selectTrack(AUDIO, -1)` selected
     * track "-1" (silently ignored by mpv, never the auto heuristic) and
     * `selectTrack(SUBTITLE, -1)` failed to deselect.
     */
    override fun selectTrack(type: TrackType, index: Int) {
        val context = aliveCtx() ?: return
        when (type) {
            TrackType.AUDIO ->
                if (index < 0) {
                    MpvLib.setPropertyString(context, "aid", "auto")
                } else if (!MpvLib.setPropertyInt(context, "aid", index)) {
                    MpvLib.setPropertyString(context, "aid", index.toString())
                }
            TrackType.SUBTITLE ->
                if (index < 0) {
                    MpvLib.setPropertyString(context, "sid", "no")
                } else {
                    if (!MpvLib.setPropertyInt(context, "sid", index)) {
                        MpvLib.setPropertyString(context, "sid", index.toString())
                    }
                    MpvLib.setPropertyFlag(context, "sub-visibility", true)
                }
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
        if (!foldApplier.latches.fileLoaded) {
            pendingSubtitles = pendingSubtitles + source
            return
        }
        // The shared plan skips true re-adds (double-tap) and uniquifies
        // same-label different-source subs — see
        // [MpvSubtitleSideLoadPlan.planRuntimeAdd]. "select" (the plan's
        // runtime flag) matches Android: the user's explicit pick must win
        // over mpv's slang heuristic.
        when (val plan = MpvSubtitleSideLoadPlan.planRuntimeAdd(source, existingSubLabels(), sideLoadedSubtitleIds)) {
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Skip -> Unit   // duplicate re-add
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Add -> {
                sideLoadedSubtitleIds = plan.registry
                MpvLib.command(context, "sub-add", source.url, plan.add.flags, plan.add.label, source.language ?: "")
            }
        }
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
     * Applies [style] through the shared [MpvSubtitleStyleApplier] (player-
     * contract) — the same canonical `sub-*` write choreography the Android
     * mpv engine runs over the same `MpvStyleMapping` tables: ownership
     * gating, the font fallback chain, the reference-pinned `sub-font-size` +
     * multiplicative `sub-scale` discipline, `sub-pos`, `sub-margin-y` and
     * the app-owned `sub-visibility`/`sub-delay`.
     *
     * Deliberate parity FIX over the desktop's former private body, which had
     * drifted from the Android reference: it wrote `sub-font-size` directly
     * from the user's size (absolute libass size — layout shifts with the
     * container) and omitted `sub-font` (a user-picked family was silently
     * ignored), `sub-scale` and `sub-margin-y` entirely. The desktop now runs
     * the Android discipline byte-for-byte; its only divergences are the
     * released-handle guard here and `fallbackFontFamily = null` (no bundled
     * font on desktop — libass's fontconfig resolves `sans-serif`).
     */
    override fun applySubtitleStyle(style: SubtitleStyle) {
        val context = aliveCtx() ?: return
        MpvSubtitleStyleApplier.apply(
            surface = DesktopMpvSurface(context),
            style = style,
            phase = MpvSubtitleStylePhase.RUNTIME,
            ownedKeys = userOwnedSubtitleKeys,
            fallbackFontFamily = null,
            subtitleDelayMs = currentConfig.subtitleDelayMs,
        )
    }

    // ── MediaEngine: aspect ratio ───────────────────────────────────────────

    /**
     * Applies the shared [AspectRatioMapping.mpvPlan] — the exact enum→mpv
     * decision (and property-application order) Android's mpv engine runs:
     * `video-aspect-override` first (the reduced `w:h` fraction, or "-1" to
     * clear), then `panscan` and the subtitle-margin pair `sub-use-margins` /
     * `sub-ass-force-margins` so CROP's captions ride the visible frame
     * instead of the cropped-away canvas. The margin keys are app-owned
     * ([MpvUserSubtitleKeys]' APP_OWNED_ALWAYS) on both platforms, so no
     * user-config gating applies here.
     *
     * Parity FIX over the desktop's former hand-rolled body, which knew only
     * the override+panscan pair: CROP pushed captions off-frame (no margin
     * writes), FILL was mis-applied as a second CROP (`panscan=1` — the shared
     * plan resolves FILL to the cleared override + zero panscan, the
     * native-frame stretch), and numeric ratios were written as a raw DOUBLE
     * instead of the plan's fraction string ("177:100" for 16:9).
     */
    override fun setAspectRatio(ratio: AspectRatio) {
        val context = aliveCtx() ?: return
        val plan = AspectRatioMapping.mpvPlan(ratio)
        MpvLib.setPropertyString(context, "video-aspect-override", plan.aspectOverride)
        MpvLib.setPropertyDouble(context, "panscan", plan.panscan)
        MpvLib.setPropertyString(context, "sub-use-margins", plan.subUseMargins)
        MpvLib.setPropertyString(context, "sub-ass-force-margins", plan.subAssForceMargins)
    }

    // ── MediaEngine: config ─────────────────────────────────────────────────

    // The chassis's final [EngineStateChassis.updateConfig] owns the dedup
    // guard + assignment (the desktop's former copy is gone); the ownership
    // refresh that used to sit between the assignment and the hook now runs
    // at the top of the shared [MpvConfigApplier] dispatch — the same call
    // shape (only on a real diff, after `currentConfig` was assigned).

    /**
     * The shared config-delta dispatcher (player-contract, the
     * [MpvFoldApplier] family): it owns the arm ORDER (ownership refresh →
     * audio-delay → sub-delay → hwdec → shared pairs → subtitle style →
     * audio → video — the ladder both engines' hand-mirrored bodies ran and
     * had already drifted once) and the genuinely-shared arms; this engine
     * contributes only its native surfaces — the live `shaderDir` +
     * HDR-active `toneMappingSuppressed` extras, the mode-derived `hwdec`
     * value and its diff-cached audio trio (channels/pitch/af) inside the
     * audio hook. The FILE_LOADED full apply is [MpvConfigApplier.applyFull]
     * on the same applier.
     */
    private val configApplier = MpvConfigApplier(
        surface = { aliveCtx()?.let(::DesktopMpvSurface) },
        extras = {
            MpvConfigApplier.Extras(
                // No low-RAM axis on desktop: the AUTO demuxer budget takes
                // the normal pair (the mapper's device-dependent branch stays
                // Android's).
                lowRamDevice = false,
                shaderDir = shaderDir,
                // While HDR passthrough is ACTIVE (setting on + HDR item +
                // HDR display target) `tone-mapping` falls to mpv's `auto`
                // default — HDR→HDR, the colorspace-hint path owns the output
                // (and a stale preset from an SDR session is explicitly
                // reset). Before the target probe lands the gate is open
                // (the preset is written): the safe fallback for SDR displays.
                toneMappingSuppressed = hdrPassthroughRequested(currentConfig) && hdrTargetIsHdr,
            )
        },
        refreshOwnedKeys = { refreshUserOwnedSubtitleKeys(currentConfig) },
        hwdecValue = { cfg -> hwdecFor(cfg.decoderMode) },
        applySubtitleStyle = { cfg -> applySubtitleStyle(cfg.subtitleStyle) },
        applyAudioEffects = { _, new, delta, full ->
            if (full || delta.audioEffectsChanged || delta.engineSpecificChanged) {
                // Live re-apply — mpv re-inits the af chain / audio-channels /
                // pitch on property writes (verified against the bundled
                // libmpv). engineSpecific rides along because the output
                // mode's STEREO forced downmix folds into the audio-channels
                // value (MpvConfigMapping.effectiveAudioChannels composes);
                // the diff caches keep an unrelated engineSpecific change
                // write-free.
                applyAudioEffects(new)
            }
        },
        applyVideoEffects = { cfg ->
            // Video twin: mpv re-inits the video pipeline on `vf` writes
            // (same class of live re-apply as the af chain above).
            applyVideoEffects(cfg)
        },
    )

    override protected fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        configApplier.applyDelta(oldConfig, newConfig)
    }

    private fun applyConfigToMpv(config: EngineConfig) {
        configApplier.applyFull(config)
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
            fx.maxAudioChannels,
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
     * ([MpvVideoEffectChain.buildVfChain], the shared contract builder both
     * mpv engines apply) and the rotation via the separate `video-rotate`
     * property (rotation is an output transform, not a filter). Both are
     * runtime-settable; mpv rebuilds the video pipeline on `vf` writes —
     * which is why unchanged values are never re-written (see the pacing
     * note on the last-applied fields above).
     */
    private fun applyVideoEffects(config: EngineConfig) {
        val context = aliveCtx() ?: return
        val fx = config.videoEffects
        val chain = MpvVideoEffectChain.buildVfChain(fx)
        if (chain != lastAppliedVfChain) {
            if (chain != null) {
                MpvLib.setPropertyString(context, "vf", chain)
            } else {
                MpvLib.command(context, "vf", "clr", "")
            }
            lastAppliedVfChain = chain
        }
        val rotation = MpvVideoEffectChain.rotationDegrees(fx)
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
        if (!foldApplier.latches.fileLoaded) return null
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

    /**
     * The desktop twin of Android's coalesced track refresh ([TrackRefreshCoalescer],
     * player-contract): the FILE_LOADED + sid/aid/track-list-observer refresh
     * burst collapses into one `track-list` read ~80 ms after the burst
     * settles, instead of one JNA node read per event. Launches on
     * [engineScope] (Dispatchers.Default — desktop property reads are
     * thread-agnostic; the release path joins the scope's children before
     * mpv_terminate_destroy, so an in-flight read can never race the destroy).
     * Pending refreshes die with the scope on release.
     */
    private val trackRefresh = TrackRefreshCoalescer(
        scopeProvider = { engineScope },
        onRefresh = ::performCoalescedRefresh,
    )

    private fun refreshTracks() {
        trackRefresh.request()
    }

    /** The coalesced refresh body: one shared-parse read + catalog build. */
    private fun performCoalescedRefresh() {
        val context = aliveCtx() ?: return
        val raw = MpvLib.readNode(context, "track-list") as? List<*>
        _availableTracks.value = MpvTrackCatalog.mediaTracks(
            entries = raw?.mapNotNull(MpvTrackCatalog::trackEntry) ?: emptyList(),
            sideLoadedSubtitleIds = sideLoadedSubtitleIds,
        )
    }

    /**
     * Raw mpv `title`s of every subtitle track currently in the track-list —
     * the shared [MpvTrackCatalog.existingSubtitleLabels] extraction over the
     * same [readTrackEntries] rows the catalog consumes. Best-effort: empty on
     * any read failure so the caller proceeds to add.
     */
    private fun existingSubLabels(): Set<String> =
        MpvTrackCatalog.existingSubtitleLabels(readTrackEntries())

    /**
     * This binding's parsed `track-list` rows (the plain Kotlin tree
     * [MpvLib.readNode] produces) through the shared
     * [MpvTrackCatalog.trackEntry] parse — the ONE normalization both mpv
     * hosts run (the former per-engine `toTrackEntry` extraction is gone).
     */
    private fun readTrackEntries(): List<MpvTrackCatalog.MpvTrackEntry> =
        aliveCtx()
            ?.let { (MpvLib.readNode(it, "track-list") as? List<*>)?.mapNotNull(MpvTrackCatalog::trackEntry) }
            ?: emptyList()

    // ── Stats projection ────────────────────────────────────────────────────

    private fun projectVideoStats() {
        val context = aliveCtx() ?: return
        probeHdrTarget(context)
        // The shared property-name table + sanitize fold (player-contract,
        // Android parity — see MpvStatsProjection's KDoc for the unified
        // drifted names); the desktop merges its one declared extra, the
        // cached HDR-target verdict (badge + fallback notice).
        val reads = DesktopStatsReads(context)
        _videoStats.value = MpvStatsProjection.project(
            reads = reads,
            scalars = MpvStatsProjection.readGuardScalars(reads),
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs.value,
        ).copy(
            videoHdrType = hdrStatsType,
            hdrOutputActive = hdrStatsActive,
            hdrOutputNotice = hdrStatsNotice,
        )
    }

    /** The desktop [MpvStatsReads] over the JNA binding's null-on-failure reads. */
    private inner class DesktopStatsReads(private val context: Pointer) : MpvStatsReads {
        override fun readString(name: String): String? = MpvLib.getPropertyString(context, name)
        override fun readDouble(name: String): Double? = propDouble(context, name)
        override fun readLong(name: String): Long? = propDouble(context, name)?.toLong()
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

    // (the former private resolution() helper died with the shared
    // MpvStatsProjection — video-params/w|h reads through the stats seam now)

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

    /**
     * The event pump's start gate — deliberately the LAST init block in the
     * class. Kotlin runs property initializers and init blocks in declaration
     * order, so by the time this runs every field the event thread reads
     * ([foldApplier], [sideLoadedSubtitleIds], the last-applied caches, the
     * ownership set, the HDR state) is initialized, and the observation
     * backlog queued by [registerObservers] (one initial event per observed
     * property, plus CoreIdle from `idle=yes`) dispatches against fully built
     * state. The former start sat in the top-of-class init block, BEFORE the
     * former `mpvLatches` initializer further down — a queued event could fold
     * over the still-null latches and kill the thread (see the early init
     * block's comment).
     */
    init {
        ctx?.let(::registerObservers)
        if (ctx != null) eventThread.start()
    }

    private companion object {
        // DEFAULT_POLLING_INTERVAL_MS lives on the state chassis (protected).
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
