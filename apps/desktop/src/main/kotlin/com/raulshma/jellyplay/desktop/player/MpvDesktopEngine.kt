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
import com.raulshma.jellyplay.feature.player.video.engine.MpvTlsOptions
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvBinding
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvCore
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvEndFileError
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeHost
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeValue
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvProperties
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsProjection
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsReads
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleSideLoadPlan
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

    // Read side of the ownership gate (the snapshot lives on [core] — the
    // shared MpvCore ownership choreography): the subtitle-style UI renders
    // its custom-config notice from this snapshot. No dropped-keys half — the
    // desktop runs config=no, so there is no on-disk mpv.conf whose values
    // mpv's parser could destroy; ownership comes from the in-app
    // extra-config text alone (option API, `#`-safe).
    override val subtitleStyleOwnership: MpvSubtitleOwnership
        get() = core.ownershipSnapshot

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

    /** Server-reported runtime fallback while the demuxer hasn't resolved one — see [core]. */
    override val currentPositionMs: Long get() = core.cachedPositionMs

    /**
     * The shared engine→server fallback ladder ([MpvCore.effectiveDurationMs], fed from
     * [PlaybackRequest.serverDurationMs] at [load] — Android mpv parity): the
     * demuxer's duration whenever it resolved positive, else the server's
     * runTimeTicks (the only accurate total for HLS/transcoded streams).
     * Replaces the desktop's former private two-line re-implementation.
     */
    override val durationMs: Long
        get() = core.effectiveDurationMs

    override val playbackSpeed: Float get() = core.cachedSpeed

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
     * The desktop [MpvBinding] over the JNA context — the platform seam of
     * the shared [MpvCore]. JNA property writes return an error code instead
     * of throwing, so "absorb failures" here means ignoring the boolean
     * result — exactly what the former inline calls and `DesktopMpvSurface`
     * did. Reads keep the shipped desktop failure semantics
     * (`propFlag`'s failed-read → `false`, null-on-failure scalars).
     */
    private val binding = DesktopMpvBinding()

    /**
     * The host residue: the callbacks only THIS engine implements (the live
     * audio-chain rebuild on a channel-layout move, the FILE_LOADED per-item
     * resets + config full-apply, the raw-URL `sub-add` transport spelling,
     * the LIVE `sub-start` cue-start read — the declared observed-staleness
     * divergence from Android's event-cached pairing). The shared
     * choreography invokes them exactly where the former hand-mirrored body
     * ran its platform extras.
     */
    private val coreHosts = object : MpvCore.Hosts() {
        override fun onChannelLayoutChanged(count: Int) {
            // The layout changed (new item / channel-mix edit): the
            // stereo-gated balance stage must be rebuilt against the
            // new layout.
            applyAudioEffects(currentConfig)
        }

        override fun onBeforeFileLoaded() {
            core.cachedPositionMs = 0L
            // New file → the display-target probe starts over (the target
            // of the previous item must not leak into this one's stats).
            hdrProbePending = true
            hdrTargetIsHdr = false
            hdrStatsActive = false
            hdrStatsNotice = null
            hdrStatsType = null
            core.flushPendingSubtitles()
            core.refreshTracks("file-loaded")
            applyConfigToMpv(currentConfig)
        }

        override fun emitSubAdd(add: MpvSubtitleSideLoadPlan.SubAdd): Boolean {
            val context = aliveCtx() ?: return false
            // sub-add <url> [flags [title [lang]]] — title doubles as the
            // stable label the track-list echoes back, matching how the
            // Android engine keys side-loaded tracks. Desktop transport
            // spelling: raw URL, empty-string lang when the source has none
            // (Android omits the arg instead).
            return MpvLib.command(
                context,
                MpvProperties.CMD_SUB_ADD,
                add.source.url,
                add.flags,
                add.label,
                add.source.language ?: "",
            )
        }

        override fun subtitleDelayMs(): Long = currentConfig.subtitleDelayMs

        // No bundled fallback font on desktop — libass's fontconfig resolves
        // `sans-serif`.

        override fun subStartSeconds(): Double? =
            binding.readDouble(MpvProperties.SUB_START)?.takeIf { it >= 0 }
    }

    /**
     * The shared mpv choreography ([MpvCore], player-video commonMain) over
     * this engine's [binding] and [coreHosts]: the property-intake funnel +
     * cached playback scalars (`positionMs`/`durationValue`/`speedValue`),
     * the fold wiring, the track/side-load machinery (coalescer, registry,
     * before-FILE_LOADED queue gate), the transport + subtitle-style + aspect
     * choreography and the ownership snapshot. `by lazy` because the
     * [MpvCore.Hosts] callbacks close over `core` (and [MpvCore] takes the
     * hosts) — lazy breaks the reference cycle; the first access happens on
     * the event thread's first dispatched event, after every field
     * initializer has run (see the late init block at the bottom).
     */
    private val core: MpvCore by lazy {
        MpvCore(
            host = MpvIntakeHost.DESKTOP,
            binding = binding,
            isPlayingFlow = _isPlaying,
            playbackStateFlow = _playbackState,
            currentCuesSink = _currentCues,
            liveSubtitleCueSink = _liveSubtitleCue,
            bufferedRangesSink = _bufferedRanges,
            availableTracksSink = _availableTracks,
            errorSink = { error -> _errorFlow.tryEmit(error) },
            // The desktop publishes the buffered scalar at OBSERVER cadence
            // (the shipped `_bufferedPositionMs.value =` intake landing).
            bufferedSink = _bufferedPositionMs,
            scopeProvider = { engineScope },
            hosts = coreHosts,
            // The shipped desktop seek wrote the position cache eagerly (its
            // position surface shows the seek target immediately).
            eagerSeekPositionCache = true,
            // The shipped desktop speed getter reads the cache.
            cacheSpeedPropertyWrites = true,
            // The shipped desktop runtime-add gate: sources arriving before
            // FILE_LOADED queue for the flush.
            queueSubtitlesBeforeFileLoaded = true,
        )
    }

    // The former desktop field twins (`positionMs`, `durationValue`,
    // `speedValue`, `serverDurationMs`, `pendingSubtitles`,
    // `observedChannelCount`, `sideLoadedSubtitleIds`, `userOwnedSubtitleKeys`,
    // `foldApplier`, `trackRefresh`) live on [MpvCore] now — the intake
    // landing sites are byte-identical, the field names were the only drift.

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
            ctx?.let { MpvLib.setPropertyString(it, MpvProperties.TARGET_COLORSPACE_HINT, "yes") }
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
            yield(MpvProperties.CONFIG to "no")            // never read user mpv.conf
            yield(MpvProperties.IDLE to "yes")             // survive empty playlist
            yield(MpvProperties.KEEP_OPEN to "yes")        // EOF pauses on last frame; no END_FILE
            yield(MpvProperties.INPUT_DEFAULT_BINDINGS to "no")
            yield(MpvProperties.INPUT_VO_KEYBOARD to "no")
            yield(MpvProperties.OSC to "no")
        }.forEach { (k, v) -> MpvLib.mpv.mpv_set_option_string(context, k, v) }
        if (windowHandle != null) {
            MpvLib.mpv.mpv_set_option_string(context, MpvProperties.WID, windowHandle.toString())
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
        observe(MpvProperties.PAUSE, FORMAT_FLAG)
        observe(MpvProperties.SPEED, FORMAT_DOUBLE)
        observe(MpvProperties.PAUSED_FOR_CACHE, FORMAT_FLAG)
        observe(MpvProperties.EOF_REACHED, FORMAT_FLAG)
        // time-pos MUST be DOUBLE: as INT64 mpv emits only whole-second steps.
        observe(MpvProperties.TIME_POS, FORMAT_DOUBLE)
        observe(MpvProperties.DURATION, FORMAT_DOUBLE)
        observe(MpvProperties.DEMUXER_CACHE_TIME, FORMAT_INT64)
        // Range-level buffered surface. NODE observation arrives as a
        // raw mpv_node pointer in the event; like the track-list observer the
        // core re-reads the property through [MpvLib.readNode] (parsed
        // Kotlin tree) instead of decoding the event's node memory here.
        observe(MpvProperties.DEMUXER_CACHE_STATE, FORMAT_NODE)
        observe(MpvProperties.SUB_TEXT, FORMAT_STRING)
        // Track-selection changes: the fold clears the cue history/live line on
        // a sid switch (the former desktop gap — lines from the previous
        // subtitle track bled into the sync preview) and re-enumerates tracks
        // on either switch (the track-list observer alone does not reliably
        // fire for every switch shape; the Android engine has always re-polled).
        observe(MpvProperties.SID, FORMAT_STRING)
        observe(MpvProperties.AID, FORMAT_STRING)
        observe(MpvProperties.TRACK_LIST, FORMAT_NODE)
        // Live output channel layout — the balance (`pan`) af stage must know
        // it because `pan` pins the output layout (see DesktopAudioEffectChain).
        observe(MpvProperties.AUDIO_PARAMS_CHANNEL_COUNT, FORMAT_INT64)
    }

    // ── Event dispatch ──────────────────────────────────────────────────────

    // The fold-application body ([MpvFoldApplier] over the shared chassis
    // flows), the pending-subtitle queue, the side-load registry and the
    // observed channel-count cache all live on [MpvCore] — the former
    // engine-field wiring was byte-identical to the Android engine's.

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

    // The user-owned `sub-*` ownership snapshot lives on [MpvCore]
    // ([MpvCore.ownershipSnapshot]); this engine's refresh feeds it from the
    // in-app extra-config text alone.

    /**
     * Feeds the shared ownership refresh: the desktop mpv runs with
     * `config=no` (no on-disk mpv.conf), so the extra-config text is the sole
     * ownership source (the conf-text parameter stays null — no parser-drop
     * detection applies). [applySubtitleStyle] skips owned keys so the user's
     * value wins for the session (issue #165, Android parity).
     */
    private fun refreshUserOwnedSubtitleKeys(config: EngineConfig) {
        val mpvCfg = config.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
        core.refreshUserOwnedSubtitleKeys(null, mpvCfg.mpvExtraConfig)
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
            EVENT_START_FILE -> core.onStartFile()
            EVENT_FILE_LOADED -> core.onFileLoaded()
            EVENT_END_FILE -> {
                val payload = event.data?.let { MpvEventEndFile(it).also { it.read() } }
                // The int-code hand-off is this engine's kept divergence: the
                // core's END_FILE choreography (fold → declared error
                // emission → state application) is shared; classification
                // stays [MpvErrorTaxonomy.fromCode] with the
                // `mpv_error_string(code)` diagnostic detail.
                core.onEndFile(
                    payload?.reason,
                    MpvEndFileError.IntCode(payload?.error ?: 0, MpvLib.mpv.mpv_error_string(payload?.error ?: 0)),
                )
            }
            EVENT_IDLE -> core.onCoreIdle()
            EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
            EVENT_SHUTDOWN -> running = false
            else -> Unit
        }
    }

    /**
     * The desktop reader half of the shared [MpvPropertyIntake] intake
     * table: this when-block is EXTRACTION only — this JNA binding's
     * per-format decode of the raw `mpv_event_property` payload — and every
     * DECISION is a table row applied by [MpvCore.onPropertyChange]. The
     * extraction quirks that stay here (declared, not drift): the
     * null-payload → `false` flag coercion, the `FORMAT_STRING` char**
     * dereference, the sid/aid payload shipped unread (the fold keys on the
     * property name alone), the eof arm's live `pause` read, and the NODE
     * observations left payload-free (the core re-reads through
     * [MpvLib.readNode] instead of decoding the event's node memory).
     */
    private fun handlePropertyChange(event: MpvEvent) {
        val prop = event.data?.let { MpvEventProperty(it).also { it.read() } } ?: return
        val name = prop.name?.getString(0) ?: return
        val data = prop.data
        when (name) {
            MpvProperties.PAUSE, MpvProperties.PAUSED_FOR_CACHE ->
                core.onPropertyChange(name, MpvIntakeValue.Flag(data != null && data.getInt(0) != 0))
            MpvProperties.EOF_REACHED -> core.onPropertyChange(
                name,
                MpvIntakeValue.Flag(data != null && data.getInt(0) != 0),
                // eof flipped false (replay seek-back): the fold re-derives
                // isPlaying from the live pause — `pause` itself didn't
                // change, so its observer won't fire (same class as the
                // FILE_LOADED seed).
                livePaused = aliveCtx()?.let { propFlag(it, MpvProperties.PAUSE) } ?: true,
            )
            MpvProperties.SID, MpvProperties.AID -> core.onPropertyChange(name, MpvIntakeValue.Unread)
            MpvProperties.SUB_TEXT ->
                // Contract: null when no line is active — mpv emits "" on
                // clear. FORMAT_STRING event data is a char** (client.h hands
                // the value behind one pointer) — reading the bytes AT data
                // yielded pointer garbage; dereference first.
                core.onPropertyChange(name, MpvIntakeValue.Text(data?.getPointer(0)?.getString(0).orEmpty()))
            MpvProperties.TIME_POS, MpvProperties.DURATION, MpvProperties.SPEED ->
                data?.let { core.onPropertyChange(name, MpvIntakeValue.Decimal(it.getDouble(0))) }
            MpvProperties.DEMUXER_CACHE_TIME, MpvProperties.AUDIO_PARAMS_CHANNEL_COUNT ->
                data?.let { core.onPropertyChange(name, MpvIntakeValue.Whole(it.getLong(0))) }
            MpvProperties.TRACK_LIST, MpvProperties.DEMUXER_CACHE_STATE ->
                // NODE observation arrives as a raw mpv_node pointer in the
                // event; the core re-reads the property through
                // [MpvLib.readNode] (parsed Kotlin tree) at the sink instead
                // of decoding the event's node memory here.
                core.onPropertyChange(name, MpvIntakeValue.Node)
        }
    }

    // ── MediaEngine: source loading & teardown ──────────────────────────────

    override fun load(request: PlaybackRequest) {
        val context = ctx ?: run {
            _errorFlow.tryEmit(EngineError.Render(IllegalStateException("mpv not initialized")))
            return
        }
        // The shared per-item resets (pending batch, fold latches, side-load
        // registry, server duration rung, cue/live-cue flows).
        core.beginLoad(request)
        // Per-item published resets: the previous item's duration/buffer must
        // not leak into this item's BUFFERING window (Android resets both —
        // and the played-range cue history too, which belongs to the previous
        // item; [MpvCore.beginLoad] cleared it).
        _availableTracks.value = emptyList()
        _videoStats.value = EngineVideoStats()
        core.cachedDurationMs = 0L
        _bufferedPositionMs.value = 0L
        _bufferedRanges.value = emptyList()

        // Per-request options. http-header-fields is a list option that
        // PERSISTS on the context — reset it first or the previous item's
        // credentials (X-Emby-Authorization) are sent to this item's server.
        // The -append suffix is then the only way to set entries containing
        // commas (X-Emby-Authorization does), one call per header.
        MpvLib.mpv.mpv_set_option_string(context, MpvProperties.HTTP_HEADER_FIELDS, "")
        request.headers.forEach { (k, v) ->
            MpvLib.mpv.mpv_set_option_string(context, MpvProperties.HTTP_HEADER_FIELDS_APPEND, "$k: $v")
        }
        // mTLS: the tls-* file-path options persist the same way —
        // write the reset trio when no certificate is active so the previous
        // item's certificate/key is never presented to this item's server.
        MpvTlsOptions.from(request.requestSpecific?.tls).forEach { (option, value) ->
            MpvLib.mpv.mpv_set_option_string(context, option, value)
        }
        request.preferredAudioLanguage?.let {
            MpvLib.mpv.mpv_set_option_string(context, MpvProperties.ALANG, it)
        }
        request.preferredSubtitleLanguage?.let {
            MpvLib.mpv.mpv_set_option_string(context, MpvProperties.SLANG, it)
        }
        MpvLib.mpv.mpv_set_option_string(
            context,
            MpvProperties.DEMUXER_READAHEAD_SECS,
            (request.maxBufferMs / 1000).toString(),
        )

        val loadOptions = if (request.startPositionMs > 0) {
            "${MpvProperties.START}=+${request.startPositionMs / 1000.0}"
        } else {
            null
        }
        // loadfile <url> <flags> <index> <options> — index is ignored with
        // replace, but must be present to reach the options slot.
        val loaded = if (loadOptions == null) {
            MpvLib.command(context, MpvProperties.CMD_LOADFILE, request.uri, "replace")
        } else {
            MpvLib.command(context, MpvProperties.CMD_LOADFILE, request.uri, "replace", "0", loadOptions)
        }
        if (!loaded) {
            _errorFlow.tryEmit(EngineError.Source(httpStatus = null, cause = null))
        }
    }

    // The side-loaded-subtitle id registry + the START_FILE flush moved to
    // [MpvCore] (registry, pending queue, [MpvCore.flushPendingSubtitles]);
    // the transport spelling lives in [MpvCore.Hosts.emitSubAdd].

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        // Mirrored into the shared core so its observer-side entries (the
        // coalesced refresh body, the intake funnel) bail with the engine.
        core.released = true
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
        core.play()
    }

    override fun pause() {
        core.pause()
    }

    override fun stop() {
        val context = aliveCtx() ?: return
        binding.command(MpvProperties.CMD_STOP)
        core.resetFoldLatches()
        core.cachedPositionMs = 0L
        core.cachedDurationMs = 0L
        // The server rung of the duration ladder dies with the item — the
        // parity FIX: the former body reset `durationValue` but left
        // `serverDurationMs` serving the stopped file's runtime forever after
        // (Android mpv resets its twin in load/release; the desktop's stop is
        // its per-item teardown, so the fallback dies here).
        core.serverDurationMs = 0L
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
        core.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        core.setPlaybackSpeed(speed)
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
            aliveCtx()?.let { MpvLib.setPropertyFlag(it, MpvProperties.MUTE, muted) }
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
        MpvLib.setPropertyDouble(context, MpvProperties.VOLUME, volumePercent)
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
        // The Android mpv decision logic — negative index deselects (`aid`
        // back to "auto", `sid` to "no"), positive ids write as INT first with
        // the string form as fallback, and a subtitle selection re-enables
        // `sub-visibility` — is the shared [MpvCore.selectTrack].
        core.selectTrack(type, index)
    }

    override fun setSecondarySubtitleTrack(index: Int) {
        core.setSecondarySubtitleTrack(index)
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        core.addExternalSubtitle(source)
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // No-op for direct playback — bitrate control happens server-side when
        // PlaybackInfo negotiates the stream (the engine plays the URL it is
        // handed), same reasoning as the Android mpv engine.
    }

    override fun setNativeSubtitlesVisible(visible: Boolean) {
        core.setNativeSubtitlesVisible(visible)
    }

    /**
     * Applies [style] through the shared [MpvSubtitleStyleApplier] via the
     * [MpvCore] runtime funnel — the same canonical `sub-*` write
     * choreography the Android mpv engine runs over the same `MpvStyleMapping`
     * tables: ownership gating, the font fallback chain, the reference-pinned
     * `sub-font-size` + multiplicative `sub-scale` discipline, `sub-pos`,
     * `sub-margin-y` and the app-owned `sub-visibility`/`sub-delay`.
     *
     * Deliberate parity FIX over the desktop's former private body, which had
     * drifted from the Android reference: it wrote `sub-font-size` directly
     * from the user's size (absolute libass size — layout shifts with the
     * container) and omitted `sub-font` (a user-picked family was silently
     * ignored), `sub-scale` and `sub-margin-y` entirely. The desktop now runs
     * the Android discipline byte-for-byte; its only divergences are the
     * released-handle guard and `fallbackFontFamily = null` (no bundled
     * font on desktop — libass's fontconfig resolves `sans-serif`).
     */
    override fun applySubtitleStyle(style: SubtitleStyle) {
        core.applySubtitleStyleRuntime(style)
    }

    // ── MediaEngine: aspect ratio ───────────────────────────────────────────

    /**
     * The shared [AspectRatioMapping.mpvPlan] application — the exact
     * enum→mpv decision (and property-application order) Android's mpv engine
     * runs — is [MpvCore.setAspectRatio] (the parity-FIX history lives there:
     * CROP's caption margins, the FILL native-frame stretch, the fraction
     * string override).
     */
    override fun setAspectRatio(ratio: AspectRatio) {
        core.setAspectRatio(ratio)
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
        surface = { if (binding.isAlive()) binding else null },
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
        applySubtitleStyle = { cfg -> core.applySubtitleStyleRuntime(cfg.subtitleStyle) },
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
            MpvLib.setPropertyString(context, MpvProperties.AUDIO_CHANNELS, channels)
            lastAppliedAudioChannels = channels
        }
        val pitch = DesktopAudioEffectChain.pitchRatio(fx.pitchSemitones)
        if (pitch != lastAppliedPitch) {
            MpvLib.setPropertyDouble(context, MpvProperties.PITCH, pitch)
            lastAppliedPitch = pitch
        }
        val chain = DesktopAudioEffectChain.buildAfChain(fx, core.observedChannelCount)
        if (chain != lastAppliedAfChain) {
            if (chain != null) {
                MpvLib.setPropertyString(context, MpvProperties.AF, chain)
            } else {
                MpvLib.command(context, MpvProperties.CMD_AF_CLR, "clr", "")
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
                MpvLib.setPropertyString(context, MpvProperties.VF, chain)
            } else {
                MpvLib.command(context, MpvProperties.CMD_VF_CLR, "clr", "")
            }
            lastAppliedVfChain = chain
        }
        val rotation = MpvVideoEffectChain.rotationDegrees(fx)
        if (rotation != lastAppliedRotationDeg) {
            // STRING, not DOUBLE: this libmpv REJECTS FORMAT_DOUBLE writes on
            // the integer `video-rotate` property (verified live — the write
            // returns MPV_ERROR_INVALID_PARAMETER and nothing sticks; the
            // string form applies and reads back).
            MpvLib.setPropertyString(context, MpvProperties.VIDEO_ROTATE, rotation.toString())
            lastAppliedRotationDeg = rotation
        }
    }

    // ── Cue history (G10, mirrors the Android MPV engine's accumulate path) ─

    // The sub-text cue accumulation is the shared [MpvCore.accumulateSubText]
    // (the fold's onLiveSubtitleLine sink); this engine's declared start-time
    // source — the LIVE `sub-start` read (this libmpv delivers `sub-text`
    // before the matching `sub-start` property update, so a cache would be
    // stale exactly at line transitions) — is the [MpvCore.Hosts.subStartSeconds]
    // override above.

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
        if (!core.fileLoaded) return null
        return try {
            val temp = File.createTempFile(TEMP_SHOT_PREFIX, ".png")
            try {
                val ok = MpvLib.command(context, MpvProperties.CMD_SCREENSHOT_TO_FILE, temp.absolutePath, "subtitles")
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

    // The track refresh machinery (the shared [TrackRefreshCoalescer] over
    // [engineScope], the [MpvTrackCatalog] parse + build, the side-load label
    // extraction) lives on [MpvCore] — the coalesced body publishes through
    // the default plain-assign host (this engine's shipped shape). The
    // FILE_LOADED + sid/aid/track-list-observer refresh burst collapses into
    // one `track-list` read ~80 ms after the burst settles, exactly as the
    // former engine-owned coalescer ran (an in-flight read can never race the
    // destroy: the release path cancels the scope and joins its children
    // before mpv_terminate_destroy).

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
            positionMs = core.cachedPositionMs,
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

    /**
     * The desktop [MpvBinding] over the JNA context: the absorbing write
     * surface (JNA property writes return an error code instead of throwing,
     * so "absorb failures" means ignoring the boolean result — exactly what
     * the former inline calls and `DesktopMpvSurface` did), the command /
     * read seams and the shared int-then-string track-id discipline. The
     * `propFlag` failed-read → `false` semantic is the desktop's kept read
     * default (Android's JNI reads default `true`). Nested `inner` by
     * declared symmetry with Android's `AndroidMpvBinding`: the binding is
     * this engine's read/write dialect over its own handle, not a
     * standalone component — "platform code shrinks to a binding" is about
     * the [MpvCore] seam, not file layout.
     */
    private inner class DesktopMpvBinding : MpvBinding {
        override fun isAlive(): Boolean = aliveCtx() != null

        override fun setOptionString(name: String, value: String) {
            aliveCtx()?.let { MpvLib.mpv.mpv_set_option_string(it, name, value) }
        }

        override fun setPropertyString(name: String, value: String) {
            aliveCtx()?.let { MpvLib.setPropertyString(it, name, value) }
        }

        override fun setPropertyDouble(name: String, value: Double) {
            aliveCtx()?.let { MpvLib.setPropertyDouble(it, name, value) }
        }

        override fun setPropertyInt(name: String, value: Int) {
            aliveCtx()?.let { MpvLib.setPropertyInt(it, name, value) }
        }

        override fun setPropertyBoolean(name: String, value: Boolean) {
            aliveCtx()?.let { MpvLib.setPropertyFlag(it, name, value) }
        }

        override fun command(vararg args: String): Boolean =
            aliveCtx()?.let { MpvLib.command(it, *args) } ?: false

        override fun readFlag(name: String): Boolean {
            val context = aliveCtx() ?: return true
            return propFlag(context, name)
        }

        override fun readDouble(name: String): Double? = aliveCtx()?.let { propDouble(it, name) }

        override fun readString(name: String): String? = aliveCtx()?.let { MpvLib.getPropertyString(it, name) }

        override fun readNode(name: String): Any? = aliveCtx()?.let { MpvLib.readNode(it, name) }

        override fun writeIntOrString(name: String, value: Int): Boolean {
            val context = aliveCtx() ?: return false
            val ok = MpvLib.setPropertyInt(context, name, value)
            if (!ok) {
                MpvLib.setPropertyString(context, name, value.toString())
            }
            return ok
        }
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
    // ── Android MpvPlayerEngine) ────────────────────────────────────────────

    // The int-code edge moved into [MpvCore.onEndFile]'s [MpvEndFileError.IntCode]
    // hand-off: classification comes from the shared [MpvErrorTaxonomy.fromCode]
    // with this engine's kept divergence — `mpv_error_string(code)` as the
    // Unknown arm's diagnostic text (Android's string path passes the raw code
    // string).

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
