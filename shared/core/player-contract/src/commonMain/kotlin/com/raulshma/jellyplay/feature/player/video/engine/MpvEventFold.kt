package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The latched playback booleans both mpv hosts mirror from mpv's property
 * surface. Pure data — the single state the [MpvEventFold] folds events over.
 * Engines store the folded result back ([MpvEventFoldResult.latches]) so the
 * next event sees the accumulated truth; `eofReached`/`pausedForCache` track
 * observed property values, `fileLoaded` is event-driven (START/FILE_LOADED,
 * reset by [MpvPlaybackEvent.StartFile] and [MpvPlaybackEvent.EndFile] STOP).
 *
 * The desktop engine historically carried only `fileLoaded` (its
 * `eofReached`/`pause` checks were live property reads); the Android engine
 * carried none — it wrote `isPlaying` unguarded from the `pause` observer.
 * Both now run this one latch set.
 */
data class MpvPlaybackLatches(
    val fileLoaded: Boolean = false,
    val eofReached: Boolean = false,
    val paused: Boolean = false,
    val pausedForCache: Boolean = false,
)

/**
 * One binding-agnostic mpv event or property change — the raw surface the
 * Android (`MPV.EventObserver` / `mpv_event`) and desktop (JNA `mpv_event`)
 * bindings both hand over, stripped of their native payload types. Numeric
 * cache writes (time-pos, duration, demuxer-cache-*) stay engine-side: they
 * carry no playback-state decision and their payload shapes differ per
 * binding.
 */
sealed interface MpvPlaybackEvent {
    /** `MPV_EVENT_START_FILE` — a new file begins loading. */
    data object StartFile : MpvPlaybackEvent

    /**
     * `MPV_EVENT_FILE_LOADED`. [pausedNow] is the LIVE `pause` property read
     * at event time — when the core auto-plays, `pause` never *changes*, so
     * the pause observer alone would never seed `isPlaying` (and the event
     * may arrive before/after a pause flip, so the latch is not ground truth
     * yet). Engines default the read to `true` (paused) when no handle.
     */
    data class FileLoaded(val pausedNow: Boolean) : MpvPlaybackEvent

    /**
     * `MPV_EVENT_END_FILE` reasons (`mpv_event_end_file.reason`, client.h).
     * [fromCode] maps the wire ints; `null` means the payload was unreadable —
     * never treated as end-of-content (the historical transcoded-stream bug).
     */
    enum class EndFileReason {
        EOF, STOP, QUIT, ERROR, REDIRECT;

        companion object {
            fun fromCode(code: Int): EndFileReason? = when (code) {
                0 -> EOF        // MPV_END_FILE_REASON_EOF
                1 -> STOP       // MPV_END_FILE_REASON_STOP
                2 -> QUIT       // MPV_END_FILE_REASON_QUIT
                3 -> ERROR      // MPV_END_FILE_REASON_ERROR
                4 -> REDIRECT   // MPV_END_FILE_REASON_REDIRECT
                else -> null
            }
        }
    }

    /** [reason] == null when the END_FILE payload was unreadable. */
    data class EndFile(val reason: EndFileReason?) : MpvPlaybackEvent

    /** The `pause` flag property changed. */
    data class PauseChanged(val paused: Boolean) : MpvPlaybackEvent

    /** The `paused-for-cache` flag property changed. */
    data class PausedForCacheChanged(val buffering: Boolean) : MpvPlaybackEvent

    /**
     * The `eof-reached` flag property changed. [pausedNow] is the live `pause`
     * read, needed on the `eof == false` arm: a replay seek-back flips eof
     * without touching `pause`, so its observer will not fire and isPlaying
     * must be re-derived here (the desktop's FILE_LOADED-seed class of fix).
     */
    data class EofReachedChanged(val eof: Boolean, val pausedNow: Boolean) : MpvPlaybackEvent

    /** The `sid`/`aid` selection property changed. */
    enum class TrackKind { SUBTITLE, AUDIO }
    data class TrackSwitch(val kind: TrackKind) : MpvPlaybackEvent

    /**
     * The `sub-text` property changed — the raw line (may be blank; mpv emits
     * "" when the line clears).
     */
    data class SubTextChanged(val text: String) : MpvPlaybackEvent

    /** `MPV_EVENT_IDLE` — desktop-only (the Android binding has no such event). */
    data object CoreIdle : MpvPlaybackEvent
}

/**
 * One fold step's output. `null` [isPlaying]/[playbackState] mean "leave the
 * published flow unchanged" — the fold only ever carries genuine decisions,
 * and the engines apply exactly what is declared. Side-effect declarations
 * (cue clears, track refresh, the live-subtitle mirror, the END_FILE error
 * signal) keep every engine-side *write* in adapter code while the *decision*
 * lives here.
 */
data class MpvEventFoldResult(
    /** The latches to store back; feed into the next [MpvEventFold.fold]. */
    val latches: MpvPlaybackLatches,
    /** `null` = leave `_isPlaying` unchanged. */
    val isPlaying: Boolean? = null,
    /** `null` = leave `_playbackState` unchanged. */
    val playbackState: EnginePlaybackState? = null,
    /** Reset the accumulated `currentCues` history (subtitle track switch). */
    val clearCueHistory: Boolean = false,
    /** Clear the live overlay line (`liveSubtitleCue`). */
    val clearLiveCue: Boolean = false,
    /** Re-enumerate the track-list (sid/aid switches). */
    val refreshTracks: Boolean = false,
    /**
     * A non-blank displayed subtitle line to mirror into `liveSubtitleCue` and
     * fold into the cue history. Blank lines arrive as [clearLiveCue] instead,
     * so "no change" and "cleared" are never ambiguous.
     */
    val liveSubtitleText: String? = null,
    /**
     * The END_FILE reason was ERROR — emit the mapped error onto `errorFlow`
     * (the code → [EngineError] mapping is the hosts' MpvErrorTaxonomy edge
     * and stays engine-side: desktop holds an int code, Android a string).
     */
    val emitEndFileError: Boolean = false,
)

/**
 * The ONE mpv event → playback-state machine both mpv engines fold their raw
 * events through (the `MpvErrorTaxonomy`/`MpvConfigMapping` precedent: pure
 * commonMain decision, thin engine adapters). The former hand-mappings had
 * drifted in three user-visible ways, all converged here on the desktop's
 * guarded semantics:
 *
 *  1. **fileLoaded-gated isPlaying** — the Android engine wrote
 *     `_isPlaying = !pause` from the observer with no fileLoaded/eof guard, so
 *     an idle-core pause flip could publish "playing". Neither behavior was
 *     load-bearing (no test pinned the unguarded write; on a loaded file the
 *     guarded expression is identical).
 *  2. **Cue history across track switches** — Android cleared
 *     `currentCues`/`liveSubtitleCue` on a `sid` switch; the desktop observed
 *     no sid at all, so lines from the previous subtitle track bled into the
 *     sync preview when the user switched tracks. Both now clear via
 *     [MpvPlaybackEvent.TrackSwitch].
 *  3. **END_FILE STOP/QUIT** — the desktop parked the state at IDLE, Android
 *     ignored the event (leaving READY after its own stop). IDLE is the honest
 *     post-stop state; on Android the only producer is release()'s stop
 *     command, which resets to IDLE anyway — no user-visible change.
 *
 * Not covered (deliberately engine-side): position/duration/buffered cache
 * writes (no decision, per-binding payload types), the START/FILE_LOADED
 * engine choreography (pending-subtitle adds, delayed track re-polls, config
 * application), and the error-code mapping (MpvErrorTaxonomy).
 */
object MpvEventFold {

    fun fold(latches: MpvPlaybackLatches, event: MpvPlaybackEvent): MpvEventFoldResult {
        val loaded = latches.fileLoaded
        val eof = latches.eofReached
        return when (event) {
            MpvPlaybackEvent.StartFile -> MpvEventFoldResult(
                latches = latches.copy(fileLoaded = false, eofReached = false),
                playbackState = EnginePlaybackState.BUFFERING,
            )

            is MpvPlaybackEvent.FileLoaded -> {
                val next = latches.copy(
                    fileLoaded = true,
                    // A freshly-loaded file cannot be at EOF; a stale true (the
                    // previous keep-open park) must not gate this file's seed.
                    eofReached = false,
                    paused = event.pausedNow,
                )
                MpvEventFoldResult(
                    latches = next,
                    isPlaying = !event.pausedNow && !next.eofReached,
                    playbackState = if (!next.eofReached) EnginePlaybackState.READY else null,
                )
            }

            is MpvPlaybackEvent.EndFile -> when (event.reason) {
                MpvPlaybackEvent.EndFileReason.EOF ->
                    // Defensive: keep-open normally parks BEFORE any END_FILE
                    // (eof-reached handles it); if keep-open was overridden,
                    // EOF is genuine completion.
                    MpvEventFoldResult(
                        latches = latches,
                        playbackState = EnginePlaybackState.ENDED,
                        isPlaying = false,
                    )
                MpvPlaybackEvent.EndFileReason.REDIRECT ->
                    // mpv follows the HLS/manifest variant immediately — the
                    // next entry starts; BUFFERING, not IDLE.
                    MpvEventFoldResult(latches = latches, playbackState = EnginePlaybackState.BUFFERING)
                MpvPlaybackEvent.EndFileReason.ERROR ->
                    MpvEventFoldResult(
                        latches = latches,
                        playbackState = EnginePlaybackState.ERROR,
                        emitEndFileError = true,
                    )
                // STOP/QUIT follow explicit user action and keep the engine
                // usable for the next load — parked at IDLE, not end-of-content.
                MpvPlaybackEvent.EndFileReason.STOP,
                MpvPlaybackEvent.EndFileReason.QUIT,
                -> MpvEventFoldResult(latches = latches, playbackState = EnginePlaybackState.IDLE)
                // Unreadable payload — never treated as end-of-content.
                null -> MpvEventFoldResult(latches = latches)
            }

            is MpvPlaybackEvent.PauseChanged -> {
                val next = latches.copy(paused = event.paused)
                MpvEventFoldResult(
                    latches = next,
                    // Guarded mirror: an idle-core pause flip (no file loaded,
                    // or parked at EOF) must not publish "playing".
                    isPlaying = !event.paused && next.fileLoaded && !next.eofReached,
                )
            }

            is MpvPlaybackEvent.PausedForCacheChanged -> {
                val next = latches.copy(pausedForCache = event.buffering)
                MpvEventFoldResult(
                    latches = next,
                    // Same guard as `pause`: before FILE_LOADED (or at EOF) the
                    // flag carries no READY/BUFFERING decision.
                    playbackState = if (next.fileLoaded && !next.eofReached) {
                        if (event.buffering) EnginePlaybackState.BUFFERING else EnginePlaybackState.READY
                    } else {
                        null
                    },
                )
            }

            is MpvPlaybackEvent.EofReachedChanged -> {
                val next = latches.copy(eofReached = event.eof)
                when {
                    event.eof -> MpvEventFoldResult(
                        latches = next,
                        // keep-open parks at the last frame: this observer is
                        // the authoritative end-of-content signal. The live
                        // caption dies with the file.
                        playbackState = EnginePlaybackState.ENDED,
                        isPlaying = false,
                        clearLiveCue = true,
                    )
                    next.fileLoaded -> MpvEventFoldResult(
                        latches = next,
                        // eof flipped false: a replay seek-back. Re-derive
                        // isPlaying from the live pause (its observer will not
                        // fire — same class as the FILE_LOADED seed).
                        playbackState = EnginePlaybackState.READY,
                        isPlaying = !event.pausedNow,
                    )
                    else -> MpvEventFoldResult(latches = next)
                }
            }

            is MpvPlaybackEvent.TrackSwitch -> when (event.kind) {
                MpvPlaybackEvent.TrackKind.SUBTITLE -> MpvEventFoldResult(
                    latches = latches,
                    // Track switch: lines from the prior track must not bleed
                    // into the sync preview, and a stale caption must not
                    // linger in the zoom-safe overlay.
                    refreshTracks = true,
                    clearCueHistory = true,
                    clearLiveCue = true,
                )
                MpvPlaybackEvent.TrackKind.AUDIO -> MpvEventFoldResult(
                    latches = latches,
                    refreshTracks = true,
                )
            }

            is MpvPlaybackEvent.SubTextChanged ->
                if (event.text.isBlank()) {
                    // mpv emits "" when the line clears — surface the clear.
                    MpvEventFoldResult(latches = latches, clearLiveCue = true)
                } else {
                    MpvEventFoldResult(latches = latches, liveSubtitleText = event.text)
                }

            MpvPlaybackEvent.CoreIdle ->
                if (!loaded) {
                    MpvEventFoldResult(latches = latches, playbackState = EnginePlaybackState.IDLE)
                } else {
                    MpvEventFoldResult(latches = latches)
                }
        }
    }
}
