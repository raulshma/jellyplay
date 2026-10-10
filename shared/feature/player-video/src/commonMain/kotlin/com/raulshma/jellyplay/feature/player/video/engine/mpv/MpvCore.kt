package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatioMapping
import com.raulshma.jellyplay.feature.player.video.engine.BufferedRanges
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.MpvErrorTaxonomy
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.feature.player.video.engine.TrackRefreshCoalescer
import com.raulshma.jellyplay.feature.player.video.engine.mergeAccumulatedCues
import com.raulshma.jellyplay.feature.player.video.engine.resolveDurationMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The normalized mpv END_FILE error payload the platform event pumps hand to
 * [MpvCore.onEndFile]. The two hosts surface the same mpv `error` field in
 * different shapes — the Android binding carries the raw STRING (the whole
 * mapping runs through [MpvErrorTaxonomy.fromCodeString]), the desktop
 * binding the numeric code plus its `mpv_error_string(code)` diagnostic —
 * so the taxonomy edge stays one classification table with two declared
 * hand-offs instead of a third hand-mirrored pair.
 */
public sealed interface MpvEndFileError {
    /** The Android binding's string hand-off (`MpvErrorTaxonomy.fromCodeString`). */
    public data class StringCode(val code: String?) : MpvEndFileError

    /** The desktop binding's int hand-off (`MpvErrorTaxonomy.fromCode`). */
    public data class IntCode(val code: Int, val unknownDetail: String) : MpvEndFileError
}

/**
 * The ONE mpv playback choreography shared by Android `MpvPlayerEngine` and
 * desktop `MpvDesktopEngine` — the last hand-mirrored engine twins, extracted
 * behind the [MpvBinding] seam. The core owns ORDERING and decisions; the
 * bindings normalize delivery mechanics; the engines keep only their platform
 * shells (handle creation, render/window setup, the native event pump that
 * TRANSLATES native callbacks into the core's intake calls, and the
 * Android-only audio-session / desktop-only HDR-shader arms).
 *
 * What moved here (each item existed twice, hand-mirrored, with recorded
 * drift):
 *
 *  - **Property intake + cached-field mirroring** — the unified funnel of the
 *    player-contract [MpvPropertyIntake] table both engines ran as private
 *    `applyPropertyIntake` twins over private `cachedPositionMs` vs
 *    `positionMs` field mirrors. The cache sinks land HERE; the buffered
 *    scalar additionally lands in [bufferedSink] when the host publishes it
 *    at observer cadence (desktop) — Android passes `null` and its ticker
 *    keeps publishing from the cache with its clamp.
 *  - **The fold wiring** — one [MpvFoldApplier] over the shared chassis
 *    flows (each engine built an identical one), the START_FILE /
 *    FILE_LOADED / END_FILE / IDLE fold applications with their pre-application
 *    END_FILE error emission ordering, and the cue-history accumulator.
 *  - **Track machinery** — the coalesced refresh ([TrackRefreshCoalescer]
 *    over the host scope), the [MpvTrackCatalog] parse + build over the
 *    binding's `track-list` node, the side-loaded-subtitle id registry, and
 *    the side-load plan executions ([MpvSubtitleSideLoadPlan] batch +
 *    runtime add) with the before-file-loaded queueing gate.
 *  - **Transport + surface choreography** — play (with the keep-open
 *    EOF seek-to-zero), pause, stop command, seek, speed, track selection
 *    (the negative-deselect / int-then-string / sub-visibility re-enable
 *    decisions), secondary-sid, sub-visibility toggle, the shared aspect
 *    plan application, and the [MpvSubtitleStyleApplier] runtime funnels.
 *  - **Ownership + load/reset choreography** — the sub-* ownership snapshot
 *    refresh, `beginLoad`'s per-item resets (latches, registry, pending
 *    queue, server duration rung, cue/live-cue flows) and the
 *    demuxer→server duration ladder.
 *
 * The genuinely per-platform residue (and why it is NOT here): the Android
 * audio-session id + AudioEffect chain, the low-level init option sets, the
 * delayed-track-refresh slot mechanic (an `android.os.Handler` postDelayed —
 * the desktop has no equivalent and no delayed refreshes), the desktop HDR
 * target probe and shader dir, the stats projection seams, and the volume
 * template wiring (the two engines sit on different base-class templates).
 * Each engine passes a [Hosts] object implementing exactly its own residue.
 *
 * Threading: NOT internally synchronized — the same contract the engines
 * always ran. Every fold/intake application happens on the host's serialized
 * event surface (Android: mpv's single observer queue dispatched on the main
 * looper; desktop: the `mpv-desktop-event-loop` thread); the cached scalars
 * are `@Volatile` for the cross-thread ticker/getter reads, exactly like the
 * engines' former field mirrors. [released] is driven by the host's
 * load/release lifecycle and guards the observer-side entries (a callback
 * racing the teardown window bails instead of mutating torn-down state).
 *
 * Public (not internal) because the desktop adapter lives in apps/desktop
 * (the [MpvConfigApplier] precedent). Consumers stay the two engine adapters
 * and the pinning test; not a stable API surface.
 */
public class MpvCore(
    /** Which host is dispatching — the [MpvPropertyIntake] column selector. */
    public val host: MpvIntakeHost,
    /** The platform seam — writes, commands, reads, track-id writes. */
    public val binding: MpvBinding,
    // ── Chassis sinks (the same protected EngineStateChassis fields the
    // engines' former MpvFoldApplier wiring passed directly). ────────────────
    private val isPlayingFlow: MutableStateFlow<Boolean>,
    private val playbackStateFlow: MutableStateFlow<EnginePlaybackState>,
    private val currentCuesSink: MutableStateFlow<List<TimedCue>>,
    private val liveSubtitleCueSink: MutableStateFlow<CharSequence?>,
    /** The buffered-ranges sink (`demuxer-cache-state` decodes land here). */
    private val bufferedRangesSink: MutableStateFlow<List<LongRange>>,
    /** The published tracks sink — the coalesced refresh's landing. */
    private val availableTracksSink: MutableStateFlow<List<MediaTrack>>,
    /** Engine error emissions (END_FILE taxonomy + load failures). */
    private val errorSink: (EngineError) -> Unit,
    /**
     * The buffered-scalar sink for hosts that publish it at OBSERVER cadence
     * (desktop: every `demuxer-cache-time` event writes the flow). `null`
     * keeps the cache-only landing (Android: its ticker publishes the clamped
     * cache at poll cadence — the declared buffered-surface divergence).
     */
    private val bufferedSink: MutableStateFlow<Long>? = null,
    /** The live scope the coalesced track refresh launches on. */
    scopeProvider: () -> CoroutineScope,
    /** The host's platform-native residue (defaults cover the shared shape). */
    private val hosts: Hosts = Hosts(),
    /**
     * Seek-write-through: update [cachedPositionMs] eagerly on
     * [seekTo] (the desktop's shipped behavior — its position surface shows
     * the seek target immediately; Android waits for the `time-pos` observer).
     */
    private val eagerSeekPositionCache: Boolean = false,
    /**
     * Speed-write-through: cache [setPlaybackSpeed] values locally (the
     * desktop's getter reads the cache; Android's live-reads the property —
     * the declared `speed` divergence of the intake table).
     */
    private val cacheSpeedPropertyWrites: Boolean = false,
    /**
     * Queue `addExternalSubtitle` sources until FILE_LOADED (the desktop's
     * shipped gate); `false` plans the add immediately (Android — its
     * START_FILE flush covers the load-time batch).
     */
    private val queueSubtitlesBeforeFileLoaded: Boolean = false,
) {

    /**
     * The host's platform-native residue — the callbacks only ONE engine
     * implements. Defaults are no-ops so each host overrides exactly its own
     * set; the SHARED choreography below calls them at the position the
     * shipped bodies ran their platform extras.
     */
    public open class Hosts {
        /**
         * A transport command threw (Android-class bindings). `op` labels the
         * shipped log site ("play" / "seekTo" / "stop" / ...). The absorbing
         * desktop bindings never invoke this.
         */
        public open fun onTransportError(op: String, error: Throwable) {}

        /** `audio-params/channel-count` moved — rebuild the audio chain (desktop). */
        public open fun onChannelLayoutChanged(count: Int) {}

        /** `sub-visibility` observed (Android's debug-log-only row). */
        public open fun onSubVisibilityObserved(visible: Boolean) {}

        /**
         * A delayed track refresh slot (Android's `postDelayed` re-polls for
         * late-arriving HLS/transcode track entries). The host owns the
         * scheduling mechanic AND the delayed body (released guard + build +
         * publish), because the slot's cancellation semantics are its
         * platform's.
         */
        public open fun scheduleTrackRefresh(reason: String, delayMs: Long) {}

        /**
         * Post-publish notification for a freshly built track list (Android's
         * debug publish log). The landing itself is the core's —
         * [availableTracksSink] is assigned change-guarded (a no-op set still
         * propagates a comparison, which the shipped Android guard avoided;
         * the flows' own equality dedupe makes the two shapes observationally
         * identical).
         */
        public open fun onTracksPublished(tracks: List<MediaTrack>, reason: String) {}

        /** A coalesced build failed (Android's JNI reads can throw; log only). */
        public open fun onTrackBuildFailed(reason: String, error: Throwable) {}

        /** START_FILE host extras, run BEFORE the fold (Android: the pending-subtitle flush + delayed re-polls). */
        public open fun onStartFile() {}

        /** FILE_LOADED host extras, run BEFORE the fold (desktop: per-item resets + pending flush + config apply). */
        public open fun onBeforeFileLoaded() {}

        /** After a runtime style apply (Android: `sub-reload` + the render-state debug log). */
        public open fun onStyleApplied(style: SubtitleStyle) {}

        /** mpv.conf keys the conf parser destroyed (Android's unquoted-`#` warning). */
        public open fun onUnsalvageableConfKeys(keys: Set<String>) {}

        /**
         * Delivers one planned `sub-add` through the host's transport
         * spelling (Android: `mpvOpenableUrl` + omitted blank lang; desktop:
         * raw URL + `""` lang). Returns success when the transport can
         * report it; false/throw = the add did not land. The open default
         * deliberately FAILS (`false`): a host that never implements the
         * transport gets a loud not-landed, never a silently-claimed
         * success — unlike the notification hooks above, this is a command
         * with a result, so a no-op default would lie.
         */
        public open fun emitSubAdd(add: MpvSubtitleSideLoadPlan.SubAdd): Boolean = false

        /** A runtime `addExternalSubtitle` add landed (Android: delayed track re-poll). */
        public open fun onSubAddOk(source: SubtitleSource, add: MpvSubtitleSideLoadPlan.SubAdd) {}

        /** A runtime add was skipped as a duplicate (Android's skip log). */
        public open fun onSubAddSkipped(reason: String) {}

        /** The START_FILE pending batch finished (Android's render-state debug log). */
        public open fun onPendingSubtitlesFlushed() {}

        /** The config's subtitle delay for the style applier (the host's `currentConfig` read). */
        public open fun subtitleDelayMs(): Long = 0L

        /** The bundled fallback font family (Android's font provider; desktop: null — libass fontconfig). */
        public open fun fallbackFontFamily(): String? = null

        /**
         * The cue-start source for [accumulateSubText]: Android returns its
         * event-cached `sub-start` (>= 0 or null); the desktop reads the
         * `sub-start` property LIVE (its declared observed-staleness
         * divergence — this libmpv delivers `sub-text` before the matching
         * `sub-start` update). Null falls back to the cached position.
         */
        public open fun subStartSeconds(): Double? = null
    }

    // ── Fold + refresh machinery ────────────────────────────────────────────

    /**
     * The shared fold-application body ([MpvFoldApplier], player-contract):
     * folds each event through [MpvEventFold] and applies the declared
     * decisions to the chassis flows, the coalesced track refresh, and the
     * cue accumulator. Read by hosts that need a latch outside the fold
     * (the desktop's `fileLoaded` gate lives here as [fileLoaded]).
     */
    public val foldApplier: MpvFoldApplier = MpvFoldApplier(
        isPlaying = isPlayingFlow,
        playbackState = playbackStateFlow,
        currentCues = currentCuesSink,
        liveSubtitleCue = liveSubtitleCueSink,
        refreshTracks = { reason -> refreshTracks(reason) },
        onLiveSubtitleLine = ::accumulateSubText,
    )

    private val trackRefresh = TrackRefreshCoalescer(
        scopeProvider = scopeProvider,
        onRefresh = ::performCoalescedRefresh,
    )

    // ── Observer-cached state (the former per-engine field mirrors) ─────────

    /**
     * Set `true` by the host's release BEFORE the async native destroy —
     * observer-side entries bail so a callback racing the teardown window
     * cannot touch torn-down state. Reset per item by [beginLoad] (the
     * Android reuse path; the desktop engine is single-use and never re-loads
     * past its release CAS).
     */
    @Volatile
    public var released: Boolean = false

    /** Observer-cached playhead (ms) — the ticker/gutter reads, no JNI on the hot path. */
    @Volatile
    public var cachedPositionMs: Long = 0L

    /** Observer-cached demuxer duration (ms) — the duration ladder's engine rung. */
    @Volatile
    public var cachedDurationMs: Long = 0L

    /** Observer-cached buffered-ahead (ms) — the hosts' buffered surfaces seed from this. */
    @Volatile
    public var cachedBufferedPositionMs: Long = 0L

    /** Most recent sub-start (media-time seconds) pairing the next `sub-text` cue; -1 = none. */
    @Volatile
    public var cachedSubStartSec: Double = -1.0

    /** The observed speed cache (desktop only — its getter reads this). */
    @Volatile
    public var cachedSpeed: Float = 1f

    /** Server-reported total runtime — the duration ladder's fallback rung (see [effectiveDurationMs]). */
    @Volatile
    public var serverDurationMs: Long = 0L

    /** Last observed `audio-params/channel-count` — the desktop row's change guard. */
    @Volatile
    public var observedChannelCount: Int? = null

    /**
     * Side-loaded-subtitle id registry: mpv track `title` → [SubtitleSource.id].
     * Maintained by [MpvSubtitleSideLoadPlan], consumed by [MpvTrackCatalog];
     * reset per item by [beginLoad].
     */
    @Volatile
    public var sideLoadedSubtitleIds: Map<String, String> = emptyMap()
        private set

    /**
     * The user's custom-config subtitle ownership (`sub-*` styling keys the
     * user explicitly owns via mpv.conf / the in-app extra config, plus the
     * conf-only unsalvageable keys) — the issue-#165 gate every subtitle-style
     * write consults. See [refreshUserOwnedSubtitleKeys].
     */
    @Volatile
    public var ownershipSnapshot: MpvSubtitleOwnership = MpvSubtitleOwnership.NONE
        private set

    /** The owned-key set read side, for the engines' init-option gating. */
    public val ownedStyleKeys: Set<String>
        get() = ownershipSnapshot.ownedStyleKeys

    /** The shared engine→server duration ladder (both engines' `durationMs` getter body). */
    public val effectiveDurationMs: Long
        get() = resolveDurationMs(cachedDurationMs, serverDurationMs)

    /** The fold's FILE_LOADED latch — the desktop's runtime-add gate. */
    public val fileLoaded: Boolean
        get() = foldApplier.latches.fileLoaded

    private var pendingSubtitlesQueue: List<SubtitleSource> = emptyList()

    /** The not-yet-flushed side-load batch (Android logs its size at START_FILE). */
    public val pendingSubtitles: List<SubtitleSource>
        get() = pendingSubtitlesQueue

    // ── Property intake ─────────────────────────────────────────────────────

    /**
     * The ONE funnel of the [MpvPropertyIntake] table both engines ran as
     * private `applyPropertyIntake` twins: events fold through
     * [foldApplier], cache sinks land in the fields above (+ [bufferedSink]
     * when the host publishes at observer cadence), and the node sinks route
     * into the track refresh / buffered-ranges decode. [livePaused] feeds the
     * eof-reached re-derivation (the caller's live `pause` read — the
     * transport read stays at the event pump); [nodePayload] carries the
     * event-carried `demuxer-cache-state` tree for bindings that deliver the
     * parsed node (Android) — bindings that re-read (desktop) leave it null.
     */
    public fun onPropertyChange(
        property: String,
        value: MpvIntakeValue,
        livePaused: Boolean = true,
        nodePayload: Any? = null,
    ) {
        if (released) return
        when (val intake = MpvPropertyIntake.dispatch(
            host = host,
            property = property,
            value = value,
            positionMs = cachedPositionMs,
            livePaused = livePaused,
            previousChannelCount = observedChannelCount,
        )) {
            is MpvPropertyIntakeResult.Event ->
                foldApplier.apply(intake.event, refreshReason = "property:$property")
            is MpvPropertyIntakeResult.CachedPositionMs -> cachedPositionMs = intake.ms
            is MpvPropertyIntakeResult.CachedDurationMs -> cachedDurationMs = intake.ms
            is MpvPropertyIntakeResult.CachedBufferedMs -> landBufferedScalar(intake.ms)
            is MpvPropertyIntakeResult.CachedSubStartSec -> cachedSubStartSec = intake.seconds
            is MpvPropertyIntakeResult.CachedSpeed -> cachedSpeed = intake.speed
            is MpvPropertyIntakeResult.ChannelLayoutChanged -> {
                observedChannelCount = intake.count
                // The layout changed (new item / channel-mix edit): the host's
                // af chain must be rebuilt against the new layout (desktop).
                hosts.onChannelLayoutChanged(intake.count)
            }
            MpvPropertyIntakeResult.RefreshTracks -> refreshTracks("property:$property")
            MpvPropertyIntakeResult.DecodeBufferedRanges ->
                decodeBufferedRanges(nodePayload ?: binding.readNode(MpvProperties.DEMUXER_CACHE_STATE))
            is MpvPropertyIntakeResult.SubVisibilityObserved -> hosts.onSubVisibilityObserved(intake.visible)
            // Unknown / unobserved / dropped-for-host — the table's one
            // documented no-op, exactly the former silent fall-throughs.
            null -> {}
        }
    }

    private fun landBufferedScalar(ms: Long) {
        cachedBufferedPositionMs = ms
        bufferedSink?.value = ms
    }

    /**
     * Folds a `demuxer-cache-state` node (the plain Kotlin tree) into the
     * buffered-ranges sink via the shared pure derivation. Per-range detail
     * (`seekable-ranges`, libmpv >= 0.35) is taken directly; older libmpv
     * derives the contiguous `[demuxer-start-time, cache-end]` window clamped
     * to the item bounds. Numbers arrive as Double/Long from either
     * binding's tree — coerced through [Number] so either shape parses.
     */
    public fun decodeBufferedRanges(state: Any?) {
        if (released) return
        val map = state as? Map<*, *> ?: return
        val seekableRanges = (map["seekable-ranges"] as? List<*>)?.mapNotNull { entry ->
            val range = entry as? Map<*, *> ?: return@mapNotNull null
            val start = (range["start"] as? Number)?.toDouble()
            val end = (range["end"] as? Number)?.toDouble()
            if (start != null && end != null) start to end else null
        }
        fun double(key: String): Double? = (map[key] as? Number)?.toDouble()
        bufferedRangesSink.value = BufferedRanges.fromDemuxerCacheState(
            demuxerStartTimeSec = double("demuxer-start-time"),
            cacheEndSec = double("cache-end"),
            seekableRangesSec = seekableRanges,
            durationMs = effectiveDurationMs,
        )
    }

    // ── Playback events (the native event pump's normalized entries) ────────

    /** START_FILE: host extras first, then the fold — the shipped order both engines ran. */
    public fun onStartFile() {
        hosts.onStartFile()
        foldApplier.apply(MpvPlaybackEvent.StartFile)
    }

    /**
     * FILE_LOADED: host extras (desktop: per-item resets + pending-subtitle
     * flush + config full-apply; Android: the track re-polls), then the fold
     * seeds READY + isPlaying from the LIVE core pause state — when the core
     * auto-plays (default), `pause` never *changes*, so the pause observer
     * alone would never fire (shared [MpvEventFold] semantics).
     */
    public fun onFileLoaded() {
        hosts.onBeforeFileLoaded()
        foldApplier.apply(
            MpvPlaybackEvent.FileLoaded(pausedNow = binding.readFlag(MpvProperties.PAUSE)),
        )
    }

    /** IDLE (desktop's `idle=yes` core-idle events). */
    public fun onCoreIdle() {
        foldApplier.apply(MpvPlaybackEvent.CoreIdle)
    }

    /**
     * END_FILE — NOT end-of-content with keep-open=yes: true EOF keeps the
     * file open and flips `eof-reached`; END_FILE means the demuxer closed
     * mid-stream (transcode abort, network drop, redirect, stop). The fold +
     * latch store happen FIRST so the declared error emission can run through
     * the host's taxonomy hand-off ([MpvEndFileError]) BEFORE the state
     * application — the choreography both engines have always run. Only the
     * fold's [MpvEventFoldResult.emitEndFileError] verdict emits, so a plain
     * keep-open EOF never surfaces an error.
     */
    public fun onEndFile(reasonCode: Int?, error: MpvEndFileError) {
        val result = foldApplier.fold(
            MpvPlaybackEvent.EndFile(MpvPlaybackEvent.EndFileReason.fromCode(reasonCode ?: -1)),
        )
        if (result.emitEndFileError) {
            errorSink(
                when (error) {
                    is MpvEndFileError.StringCode -> MpvErrorTaxonomy.fromCodeString(error.code)
                    is MpvEndFileError.IntCode ->
                        MpvErrorTaxonomy.fromCode(error.code, unknownDetail = error.unknownDetail)
                },
            )
        }
        foldApplier.applyResult(result, refreshReason = "end-file")
    }

    // ── Load / reset choreography ───────────────────────────────────────────

    /**
     * The per-item load resets BOTH engines shipped identically: the teardown
     * guard clears (Android's reuse path), the pending-subtitle queue takes
     * the request's batch, fold latches + the side-load registry reset, the
     * server duration rung captures the request, and the cue/live-cue flows
     * clear. The hosts add their platform residue around this (Android: the
     * cache resets + request configure + playFile; desktop: the published
     * tracks/stats/buffer resets + per-request options + loadfile).
     */
    public fun beginLoad(request: PlaybackRequest) {
        released = false
        pendingSubtitlesQueue = request.externalSubtitles
        foldApplier.resetLatches()
        sideLoadedSubtitleIds = emptyMap()
        serverDurationMs = request.serverDurationMs
        currentCuesSink.value = emptyList()
        liveSubtitleCueSink.value = null
    }

    /**
     * The four observer-cached playback scalars reset for a new item; the
     * first time-pos / duration observations repopulate them as the demuxer
     * resolves. [positionMs] seeds the playhead (Android passes the request's
     * start position; the desktop's reset seeds zero).
     */
    public fun resetPlaybackCaches(positionMs: Long = 0L) {
        cachedPositionMs = positionMs
        cachedDurationMs = 0L
        cachedBufferedPositionMs = 0L
        cachedSubStartSec = -1.0
    }

    /** Per-item fold reset (the desktop's `stop` / the hosts' release paths). */
    public fun resetFoldLatches() {
        foldApplier.resetLatches()
    }

    /** Drops any not-yet-flushed side-load batch (the hosts' release paths). */
    public fun clearPendingSubtitles() {
        pendingSubtitlesQueue = emptyList()
    }

    /** Clears the side-loaded-subtitle id registry (the hosts' release paths). */
    public fun resetSideLoadedRegistry() {
        sideLoadedSubtitleIds = emptyMap()
    }

    // ── Transport ───────────────────────────────────────────────────────────

    /**
     * Un-pauses; on a keep-open EOF first seeks back to zero — keep-open
     * holds EOF via an internal pause at the last frame, so unpausing alone
     * replays nothing (both engines' shipped bodies; the eof-reached flip
     * re-derives isPlaying/READY through the fold).
     */
    public fun play() {
        if (!binding.isAlive()) return
        try {
            if (playbackStateFlow.value == EnginePlaybackState.ENDED) {
                binding.command(MpvProperties.CMD_SEEK, "0", MpvProperties.SEEK_ABSOLUTE)
            }
        } catch (e: Exception) {
            hosts.onTransportError("play", e)
            return
        }
        binding.setPropertyBoolean(MpvProperties.PAUSE, false)
    }

    /** Pauses via the `pause` flag write. */
    public fun pause() {
        if (!binding.isAlive()) return
        binding.setPropertyBoolean(MpvProperties.PAUSE, true)
    }

    /** The bare `stop` command (Android's stop; the desktop's per-item teardown keeps its own wider reset). */
    public fun commandStop() {
        if (!binding.isAlive()) return
        try {
            binding.command(MpvProperties.CMD_STOP)
        } catch (e: Exception) {
            hosts.onTransportError("stop", e)
        }
    }

    /**
     * Absolute seek. Fixed-precision seconds with 6 decimals — byte-identical
     * to the Android engine's shipped allocation-free formatting (avoids the
     * Formatter + StringBuilder per seek during scrub). The desktop's former
     * bare `toString` spelling parses to the same double, so normalizing
     * both hosts onto this form is wire-visible but semantically identical.
     */
    public fun seekTo(positionMs: Long) {
        if (!binding.isAlive()) return
        try {
            val secsStr = formatFixed(positionMs / 1000.0, 6)
            binding.command(MpvProperties.CMD_SEEK, secsStr, MpvProperties.SEEK_ABSOLUTE)
        } catch (e: Exception) {
            hosts.onTransportError("seekTo", e)
            return
        }
        if (eagerSeekPositionCache) {
            cachedPositionMs = positionMs.coerceAtLeast(0L)
        }
    }

    /** Speed via the `speed` property; write-through cached when [cacheSpeedPropertyWrites]. */
    public fun setPlaybackSpeed(speed: Float) {
        if (!binding.isAlive()) return
        try {
            binding.setPropertyDouble(MpvProperties.SPEED, speed.toDouble())
        } catch (_: Exception) {
            // Absorbing surfaces never throw; throwing surfaces log via the
            // write seam. The cache only tracks issued values either way.
        }
        if (cacheSpeedPropertyWrites) {
            cachedSpeed = speed
        }
    }

    // ── Track selection ─────────────────────────────────────────────────────

    /**
     * The shared track-selection decisions (Android's reference logic):
     * a NEGATIVE index deselects — `aid` back to mpv's "auto" heuristic,
     * `sid` to "no" — while a positive id writes via
     * [MpvBinding.writeIntOrString], and a subtitle selection re-enables
     * `sub-visibility` (the app may have hidden native subs for the zoom-safe
     * overlay; an explicit user pick means they want them seen). The host
     * adds its refresh/log extras around this.
     */
    public fun selectTrack(type: TrackType, index: Int) {
        if (!binding.isAlive()) return
        try {
            when (type) {
                TrackType.AUDIO ->
                    if (index < 0) {
                        binding.setPropertyString(MpvProperties.AID, MpvProperties.SELECT_AUTO)
                    } else {
                        binding.writeIntOrString(MpvProperties.AID, index)
                    }
                TrackType.SUBTITLE ->
                    if (index < 0) {
                        binding.setPropertyString(MpvProperties.SID, MpvProperties.SELECT_NO)
                    } else {
                        binding.writeIntOrString(MpvProperties.SID, index)
                        binding.setPropertyBoolean(MpvProperties.SUB_VISIBILITY, true)
                    }
            }
        } catch (e: Exception) {
            hosts.onTransportError("select MPV ${type.name.lowercase()} track id=$index", e)
        }
    }

    /**
     * Secondary subtitle track ([MpvProperties.SECONDARY_SID], G4) rendered
     * alongside the primary; index < 0 clears it. The desktop's former
     * string-only positive write normalizes onto the same
     * int-then-string discipline the rest of its track writes already used
     * (mpv parses both to the same id).
     */
    public fun setSecondarySubtitleTrack(index: Int) {
        if (!binding.isAlive()) return
        if (index < 0) {
            binding.setPropertyString(MpvProperties.SECONDARY_SID, MpvProperties.SELECT_NO)
        } else {
            binding.writeIntOrString(MpvProperties.SECONDARY_SID, index)
        }
    }

    /**
     * Toggles mpv's native subtitle rendering via the live `sub-visibility`
     * property — the zoom-safe COMPOSE_CUE strategy hides native subs while
     * the Compose overlay renders from [foldApplier]'s live line, restoring
     * them at zoom 1 (full libass fidelity).
     */
    public fun setNativeSubtitlesVisible(visible: Boolean) {
        if (!binding.isAlive()) return
        binding.setPropertyString(
            MpvProperties.SUB_VISIBILITY,
            if (visible) MpvProperties.SELECT_YES else MpvProperties.SELECT_NO,
        )
    }

    /**
     * Applies the shared [AspectRatioMapping.mpvPlan] — `video-aspect-override`
     * first (the reduced `w:h` fraction, or "-1" to clear), then `panscan` and
     * the subtitle-margin pair so CROP's captions ride the visible frame. The
     * override write stays independent of the trio exactly as the Android
     * engine's two-block try shape pinned.
     */
    public fun setAspectRatio(ratio: AspectRatio) {
        if (!binding.isAlive()) return
        val plan = AspectRatioMapping.mpvPlan(ratio)
        runCatching {
            binding.setPropertyString(MpvProperties.VIDEO_ASPECT_OVERRIDE, plan.aspectOverride)
        }
        runCatching {
            binding.setPropertyDouble(MpvProperties.PANSCAN, plan.panscan)
            binding.setPropertyString(MpvProperties.SUB_USE_MARGINS, plan.subUseMargins)
            binding.setPropertyString(MpvProperties.SUB_ASS_FORCE_MARGINS, plan.subAssForceMargins)
        }
    }

    // ── Subtitle style ──────────────────────────────────────────────────────

    /**
     * One [MpvSubtitleStyleApplier] pass — the canonical `sub-*` write
     * choreography (ownership gating, font fallback chain, reference-pinned
     * `sub-font-size` + multiplicative `sub-scale`, `sub-pos`, margins,
     * `sub-visibility`/`sub-delay`) over this core's ownership snapshot.
     * Does NOT catch — callers own containment where their shipped bodies
     * had it (the Android init phase lets a failure surface into the
     * view-initialization catch).
     */
    public fun applySubtitleStylePhase(style: SubtitleStyle, phase: MpvSubtitleStylePhase) {
        if (!binding.isAlive()) return
        MpvSubtitleStyleApplier.apply(
            surface = binding,
            style = style,
            phase = phase,
            ownedKeys = ownedStyleKeys,
            fallbackFontFamily = hosts.fallbackFontFamily(),
            subtitleDelayMs = hosts.subtitleDelayMs(),
        )
    }

    /**
     * The runtime style funnel: one RUNTIME pass (contained) followed by the
     * host's [Hosts.onStyleApplied] extras (Android's `sub-reload` +
     * render-state log) — the shape [MpvPlayerEngine.applySubtitleStyle] and
     * the desktop adapter both shipped.
     */
    public fun applySubtitleStyleRuntime(style: SubtitleStyle) {
        if (!binding.isAlive()) return
        try {
            applySubtitleStylePhase(style, MpvSubtitleStylePhase.RUNTIME)
        } catch (e: Exception) {
            hosts.onTransportError("apply MPV subtitle style", e)
        }
        hosts.onStyleApplied(style)
    }

    // ── Ownership (issue #165) ──────────────────────────────────────────────

    /**
     * Refreshes the user-owned `sub-*` key snapshot from the host's config
     * sources: the on-disk mpv.conf text (Android only — the desktop runs
     * `config=no`) and the in-app extra-config text. Conf-only keys whose
     * values mpv's parser drops (unquoted `#`) are detected and reported so
     * the user learns why their value never shows. Every subtitle-style
     * write consults the refreshed snapshot, so the user's value wins for
     * the whole session.
     */
    public fun refreshUserOwnedSubtitleKeys(confText: String?, extraConfigText: String?) {
        val ownedKeys = MpvUserSubtitleKeys.ownedKeys(confText, extraConfigText)
        val droppedConfKeys = confText?.let(MpvUserSubtitleKeys::unsalvageableConfKeys) ?: emptySet()
        if (droppedConfKeys.isNotEmpty()) {
            hosts.onUnsalvageableConfKeys(droppedConfKeys)
        }
        ownershipSnapshot = MpvSubtitleOwnership(ownedKeys, droppedConfKeys)
    }

    // ── Tracks ──────────────────────────────────────────────────────────────

    /**
     * The track re-poll entry: immediate refreshes route through the
     * coalescer (a select + sid/aid/track-list observer burst collapses into
     * one `track-list` read); delayed refreshes keep the host's slot so they
     * are not cancelled by an intervening immediate refresh (Android's
     * late-arriving HLS/transcode enumeration).
     */
    public fun refreshTracks(reason: String, delayMs: Long = 0L) {
        if (released) return
        if (delayMs > 0) {
            hosts.scheduleTrackRefresh(reason, delayMs)
        } else {
            trackRefresh.request()
        }
    }

    /** Cancels a pending coalesced refresh (the Android release path). */
    public fun cancelTrackRefresh() {
        trackRefresh.cancel()
    }

    /** The coalesced refresh body: one shared-parse read + catalog build + landing. */
    private fun performCoalescedRefresh() {
        if (released) return
        val tracks = try {
            buildTracks()
        } catch (e: Exception) {
            hosts.onTrackBuildFailed("coalesced", e)
            return
        }
        // The landing is the core's (the flows dedupe equal lists internally);
        // the notification hook carries the hosts' publish-log extras.
        availableTracksSink.value = tracks
        hosts.onTracksPublished(tracks, "coalesced")
    }

    /**
     * The shared [MpvTrackCatalog] build over the binding's parsed
     * `track-list` rows (side-loaded id stamping, labels, badges).
     */
    public fun buildTracks(): List<MediaTrack> = MpvTrackCatalog.mediaTracks(
        entries = readTrackEntries(),
        sideLoadedSubtitleIds = sideLoadedSubtitleIds,
    )

    /**
     * The binding's parsed `track-list` rows through the shared
     * [MpvTrackCatalog.trackEntry] parse — the ONE normalization both mpv
     * hosts run. Best-effort: degrades to empty on any read failure so
     * callers proceed.
     */
    public fun readTrackEntries(): List<MpvTrackCatalog.MpvTrackEntry> {
        val trackList = try {
            binding.readNode(MpvProperties.TRACK_LIST) as? List<*>
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return trackList.mapNotNull(MpvTrackCatalog::trackEntry)
    }

    /**
     * Raw mpv `title`s of every subtitle track currently in the track-list —
     * the side-load dedupe key. Best-effort: empty on any read failure.
     */
    public fun existingSubLabels(): Set<String> =
        MpvTrackCatalog.existingSubtitleLabels(readTrackEntries())

    // ── Side-loaded subtitles ───────────────────────────────────────────────

    /** Queues a source for the next [flushPendingSubtitles] (the before-FILE_LOADED gate). */
    public fun queueSubtitle(source: SubtitleSource) {
        pendingSubtitlesQueue = pendingSubtitlesQueue + source
    }

    /**
     * The shared batch flush: [MpvSubtitleSideLoadPlan.applyBatch] (dedupe
     * against the live track-list, same-title uniquify, isDefault→select),
     * the registry store-back BEFORE any add executes, each planned add
     * through [Hosts.emitSubAdd], then the host's flush extras.
     */
    public fun flushPendingSubtitles() {
        val subtitles = pendingSubtitlesQueue
        pendingSubtitlesQueue = emptyList()
        if (subtitles.isEmpty()) return
        MpvSubtitleSideLoadPlan.applyBatch(
            pending = subtitles,
            existingLabels = existingSubLabels(),
            registry = sideLoadedSubtitleIds,
            onRegistry = { sideLoadedSubtitleIds = it },
        ) { add ->
            hosts.emitSubAdd(add)
        }
        hosts.onPendingSubtitlesFlushed()
    }

    /**
     * User-initiated external subtitle add. Hosts with the
     * before-FILE_LOADED gate (desktop) queue the source until the latch
     * fires; the shared plan then skips true re-adds (double-tap,
     * re-attach after a config reload) and uniquifies same-label
     * different-source subs, storing the registry back before the transport
     * delivers.
     */
    public fun addExternalSubtitle(source: SubtitleSource) {
        if (queueSubtitlesBeforeFileLoaded && !foldApplier.latches.fileLoaded) {
            queueSubtitle(source)
            return
        }
        when (val plan = MpvSubtitleSideLoadPlan.planRuntimeAdd(source, existingSubLabels(), sideLoadedSubtitleIds)) {
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Skip -> hosts.onSubAddSkipped(plan.reason)
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Add -> {
                sideLoadedSubtitleIds = plan.registry
                val delivered = try {
                    hosts.emitSubAdd(plan.add)
                } catch (e: Exception) {
                    hosts.onTransportError("add external subtitle ${source.id}", e)
                    false
                }
                if (delivered) {
                    hosts.onSubAddOk(source, plan.add)
                }
            }
        }
    }

    // ── Cue history (G10) ───────────────────────────────────────────────────

    /**
     * Folds a newly-displayed subtitle line (mpv `sub-text`) into the
     * accumulated cue list so the subtitle-sync preview can render
     * prev/active/next for embedded subs without re-fetching bytes. mpv
     * fires `sub-text` only on a line *change*; blank clears are ignored.
     * The start time comes from [Hosts.subStartSeconds] (the hosts' declared
     * cached-vs-live divergence), falling back to the cached position when
     * no sub-start has been reported. Covers the played range only.
     */
    public fun accumulateSubText(text: String) {
        if (text.isBlank()) return
        val startSec = hosts.subStartSeconds() ?: cachedPositionMs / 1000.0
        val incoming = listOf(TimedCue((startSec * 1_000_000L).toLong(), Long.MAX_VALUE, text))
        currentCuesSink.value = mergeAccumulatedCues(currentCuesSink.value, incoming)
    }
}
