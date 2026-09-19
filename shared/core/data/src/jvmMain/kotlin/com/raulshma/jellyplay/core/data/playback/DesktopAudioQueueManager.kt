package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.playback.focus.FocusOutcome
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import java.awt.EventQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop audio playback core: real queue + transport over a dedicated
 * audio-only mpv engine (`vo=null` — audio tracks never open a video
 * output; the factory is ctor-injected so this module never references the
 * app-side `MpvDesktopEngine`).
 *
 * Lives in core:data jvmMain (relocated from apps/desktop — the recorded
 * "audio queue chassis" first stage): everything it touches was already a
 * core:data collaborator except the engine factory, the effects stack and
 * the AWT main-thread guard, all of which are ctor seams. jvmMain (not
 * jvmShared) because `java.awt.EventQueue` must never reach the Android
 * bootclasspath.
 *
 * The Android media3 `AudioPlaybackManager` (androidMain) is the SEMANTICS
 * SOURCE OF TRUTH; every observable behavior was mirrored case-by-case and
 * every divergence is declared in the class KDoc below. The manager
 * implements BOTH shared contracts the audio player consumes —
 * [AudioQueueManager] (queue mutations + the five state flows) and
 * [AudioPlayerEngine] (metadata/transport/lyrics/undo/A-B loop) — the same
 * single-object pattern Android uses.
 *
 * The QUEUE-STATE CHASSIS — the eleven playback state flows, the
 * [QueueUndoStack] wiring, the advance/retreat/wrap/shuffle/restart
 * selection and the item-transition choreography — is no longer inlined
 * here: it lives once in the shared (commonMain) [AudioQueueStateCore],
 * which this manager drives over a narrow [EngineDispatch] port (the
 * private [engineDispatch] object below — the manager's ONLY engine-command
 * route; lifecycle, observers, effects pushes and release stay direct).
 * Android deliberately does not adopt the core yet — see the core's KDoc
 * for the declared divergence. The pure QUEUE POLICY underneath both —
 * advance/wrap, retreat, the 3 s skip-previous restart threshold, the
 * move-remap, the A→B loop transitions and the position-tick decisions —
 * lives in [AudioQueuePolicy], and the stop-report tail in
 * [AudioProgressReporter.stopAndCancel], as before.
 *
 * The six now-playing metadata flows (item id, title, artist, artist id,
 * album, album art url) are written ONLY through the shared
 * [NowPlayingTracker] — owned by the chassis core and re-exposed by
 * reference (same [StateFlow] instances, the exact Android
 * `AudioPlaybackManager` pattern), so every consumer of the manager's
 * properties is unchanged. The three publish sites map onto the tracker's
 * publish shapes: [NowPlayingTracker.publishDetail] (play's resolve path),
 * [NowPlayingTracker.publishQueueItem] (the core's transition), and
 * [NowPlayingTracker.clear] ([stopAndRelease]).
 *
 * ## Semantics table (shared AudioQueuePolicy / Android → here)
 *
 * | Behavior | Android (media3) | Desktop |
 * |---|---|---|
 * | playQueue(items, start) | clears undo stack, sets queue+index, `play(items[start])`; out-of-bounds start leaves the queue set and returns | identical (core: [AudioQueueStateCore.playQueue] → its play hook) |
 * | addToQueue/addToQueueAll | appends to queue + player playlist; index unchanged | identical (player playlist concept absent — the queue list IS the truth; next-item resolution happens at advance time) |
 * | removeFromQueue | bounds + undo snapshot; removing current → index coerced + player transitions to the shifted-in item; removing above current → index -1 | identical; removing current reloads the shifted-in item via the core's transition (or parks the engine when the queue empties) |
 * | clearQueue | undo snapshot, queue=[], index=-1, player playlist cleared (goes idle, metadata kept) | identical (engine stop; metadata kept) |
 * | moveQueueItem | [AudioQueuePolicy.planMove] — undo snapshot; index remap (from→to / ±1 crossing); player item moved (no transition) | identical (same [AudioQueuePolicy.planMove] call; pure state — the playing item never changes) |
 * | skipToNext | [AudioQueuePolicy.nextIndex] — index+1, or wrap to 0 under repeat ≥ 1, else no-op; NO undo snapshot on the no-op path | identical (same call, in the core) |
 * | skipToPrevious | player exists else no-op; [AudioQueuePolicy.skipsPreviousRestart] (position > 3 s → seek 0 only); else [AudioQueuePolicy.previousIndex] (index-1 or wrap to last under repeat ≥ 1); undo snapshot only on the index move | identical (core, over [EngineDispatch.isLive]) |
 * | playFromQueue | sets index, seeks player to (index, 0), plays if paused | identical (same-index clicks seek to 0 without a reload; cross-index clicks load via the core's transition) |
 * | toggleShuffle ON | current item moves to head, rest reshuffled, index=0, player playlist rebuilt at current position | identical list/index behavior; NO engine reload needed (the current item keeps playing — the playlist rebuild is playlist plumbing, not an observable playback change). Like Android (`val player = exoPlayer ?: return` right after the flag flip), the REORDER is gated on a live engine: toggling shuffle before anything ever played flips only [shuffleMode] |
 * | toggleShuffle OFF | original order restored, index jumps to the current item's original slot, playlist rebuilt at current position | identical (state-only, same reasoning + same engine gate) |
 * | setShuffleMode(b) | no-op when unchanged, else toggleShuffle | identical |
 * | cycleRepeatMode / setRepeatMode | (mode+1)%3 / coerce 0..2, player.repeatMode mapped (0=OFF, 1=ALL, 2=ONE) | identical values; the repeat behavior is applied at track end (below) instead of via a player property |
 * | Auto-advance at track end | ExoPlayer advances mid-queue under OFF; wraps under ALL; replays under ONE; `onMediaItemTransition` reconciles index/metadata and reports stop(prev)+start(next) | engine ENDED → core [AudioQueueStateCore.onEngineEnded]: [AudioQueuePolicy.nextIndex] advance/wrap (or engine replay under ONE); the core's transition performs the same reconciliation and reporting |
 * | A→B loop markers | [AudioQueuePolicy] transitions: cycle nothing→A→B→clear; set-A clears an at/before B; set-B needs strictly-later pos | identical ([AudioQueuePolicy.cycleAbLoop]; the ticker's enforcement is the shared [AudioQueuePolicy.positionTickPlan]) |
 * | End of queue under RepeatNone | STATE_ENDED: isPlaying=false, index stays, sleep-timer end-of-episode hook fires; metadata kept | identical (core [AudioQueueStateCore] exhaustion hook → the sleep timer) |
 * | play(itemId) | same-item + (READY/BUFFERING) → no-op; reports stop(prev); clears A-B loop; appends to queue when not the current item; resume from server ticks; reports start; fetches lyrics; starts position ticker + 10 s progress reporter | identical (see divergences: no Play-On routing, pre-warm is next-item-only, no crossfade/gapless — investigated) |
 * | Queue persistence | Room (QueuePersistenceHelper): full-list replace on change, state incl. index/position/repeat/shuffle/speed sampled | identical — the same shared helper over the same Room DAO works on desktop JVM |
 * | Focus claim edge (ADR-0004) | `Player.Listener.onIsPlayingChanged`: isPlaying=true → `acquire(MUSIC)` (Denied → `pause()`), false → `release(MUSIC)` | identical — the engine isPlaying observer below is this manager's ONE play-edge chokepoint (see [onPlayingEdge]) |
 * | stopAndRelease tail | [AudioProgressReporter.stopAndCancel] (final stop report + sync session-id rotation) after engine/session cleanup; display flows reset, artistId kept | identical (same call; only the engine/session cleanup differs) |
 *
 * ## Declared divergences (all deliberate)
 *
 *  - **No crossfade / gapless engine** — `setCrossfadeDurationMs`/
 *    `setGaplessEnabled` keep the observable state flows but there is no
 *    crossfader; track changes are load-file boundaries, so a small gap can
 *    be heard. Crossfade parity needs a second engine instance (later item).
 *    Gapless (mpv playlist-driven auto-advance) was SPIKED for and
 *    declined on evidence: feeding the next item via `loadfile … append-play`
 *    under the production engine options (`keep-open=yes`,
 *    `gapless-audio=weak`) makes mpv advance itself — END_FILE(EOF) for the
 *    outgoing entry and START_FILE for the appended one fire in the same
 *    instant — which (a) races this manager's ENDED-driven advance (the
 *    engine maps END_FILE(EOF) → ENDED, so both mpv and the manager would
 *    start the next item), and (b) makes current-item identity depend on
 *    mpv's shadow playlist, which every queue mutation below (remove/move/
 *    shuffle/undo/skip) would have to mirror. That re-couples the whole
 *    case-by-case parity surface to playlist plumbing deliberately
 *    replaced ("the queue list IS the truth"); revisit only behind a
 *    dedicated engine contract for playlist identity.
 *  - **Queue pre-warm is next-item-only** — Android builds MediaItems for
 *    the whole queue behind the current item and guards its
 *    skipToNext/skipToPrevious/playFromQueue/removeFromQueue/undo paths with
 *    `queueLoadingJob != null` bail-outs during that build. The desktop
 *    resolves per advance, PLUS a single next-item prefetch ([schedulePrefetchForNext])
 *    scheduled ~2 s behind a successful load: it only fills a one-entry
 *    id-keyed cache that [loadItem] consumes when it loads THAT item at
 *    position 0, and every queue mutation (and explicit `play`) clears it
 *    (the chassis core's shape-invalidation hook). A mutation racing
 *    an in-flight prefetch therefore degrades to a wasted fetch, never a
 *    wrong load — which is why the Android bail-out guards stay retired
 *    here.
 *  - **No Play-On routing in play()** — Android delegates to a connected
 *    remote session; desktop has no cast stack (its `DesktopAudioPlayerCast`
 *    seam is a never-connected no-op).
 *  - **Track-error recovery** — a failed per-item resolution during
 *    auto-advance stops with a playbackError (Android's prebuilt-media-item
 *    playlists skip unresolvable items at build time instead).
 *  - **Resume-restore preference is dead code on Android too** — Android's
 *    `isRestoredCurrentItem` branch can never fire (play() assigns
 *    `currentItemId = itemId` synchronously before the async check), so the
 *    desktop mirrors the live behavior: server ticks only.
 *  - **Playback-error text is the constant** — Android surfaces
 *    `detailResult.exceptionOrNull()?.message ?: "Failed to load track"`;
 *    the desktop's resolution seam folds detail+local into one call, so a
 *    null result reports the constant. Same flow, less precise message.
 *  - **Pause state across a reload follows mpv's `pause` property**, which —
 *    like ExoPlayer's `playWhenReady` — persists across loads: skipping to
 *    the next/previous item while paused keeps it paused, playing keeps it
 *    playing (Android parity). mpv's keep-open EOF parking does NOT set that
 *    property, so an auto-advance after a natural track end resumes playback
 *    exactly like ExoPlayer's post-STATE_ENDED transition. The real-engine
 *    test pins this end-to-end.
 *  - **Main-thread guard** — Android asserts `Looper.myLooper() == main` on
 *    every mutation; the desktop twin asserts the AWT EDT (the app's
 *     Dispatchers.Main). Injectable so tests can disable it.
 *  - **Audio effects are REAL via the engine's mpv `af` chain** — the
 *    shared AudioEffectsStateCore state machine lives in the app-side
 *    `DesktopAudioEffectsManager`, wired through the narrow
 *    [AudioEffectsSession] port: every mutation is folded into
 *    `EngineConfig.audioEffects` and pushed onto the engine (mpv applies
 *    the `af` chain live), and per-track ReplayGain context flows through
 *    [AudioEffectsSession.applyReplayGainForTrack] at the same two sites
 *    Android applies it (explicit play + advance). Filter parity per
 *    effect is documented in the app-side chain builder; the visualizer
 *    taps remain the one absent surface (no in-sink PCM tap on mpv).
 *  - **No media session / now-playing notification / bandwidth sampling** —
 *    Android-only surfaces; the desktop position ticker keeps the A-B loop +
 *    lyrics index duties only.
 *  - **[stopAndRelease] no longer resets `artistId`** — declared delta of the
 *    [NowPlayingTracker] adoption: the tracker's `clear()` returns the five
 *    display fields to their defaults but deliberately keeps the artist id
 *    (Android's stop behaved identically pre-extraction; the desktop's former
 *    hand-rolled reset cleared all six). Harmless surface — the flow is only
 *    read while a now-playing row renders — and it keeps ONE clear shape
 *    shared by both managers.
 */
class DesktopAudioQueueManager(
    private val trackResolver: AudioTrackResolver,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val queuePersistenceHelper: QueuePersistenceHelper,
    private val lyricsManager: AudioLyricsManager,
    private val sleepTimerManager: SleepTimerManager,
    private val scope: CoroutineScope,
    /**
     * Constructs the DEDICATED audio-only engine on first play (production:
     * `MpvDesktopEngine(extraOptions = mapOf("vo" to "null"))` from
     * desktopPlayerModule; tests substitute fakes). Required — no default —
     * since the relocation: the mpv engine type lives app-side and this
     * module cannot reference it.
     */
    private val engineFactory: () -> MediaEngine,
    /** Android's always-on Looper check, desktop twin = AWT EDT. Tests disable. */
    private val mainThreadGuard: Boolean = true,
    /**
     * Progress-report cadence — forwarded to the shared (commonMain)
     * [AudioProgressReporter], which owns the 10 s production constant.
     * Injectable purely for tests — production wiring leaves the default.
     */
    private val progressReportIntervalMs: Long = AudioProgressReporter.PROGRESS_REPORT_INTERVAL_MS,
    /**
     * The desktop effects state machine behind the narrow
     * [AudioEffectsSession] port (the app-side `DesktopAudioEffectsManager`
     * in production). When wired, every mutation is pushed onto the engine
     * as `EngineConfig.audioEffects` (live mpv `af` application) and
     * per-track ReplayGain context flows through
     * [AudioEffectsSession.applyReplayGainForTrack] at the same two sites
     * Android applies it (explicit play + advance). Nullable so plain
     * queue-semantics tests can omit the stack.
     */
    internal val effectsManager: AudioEffectsSession? = null,
    /**
     * The cross-player exclusivity owner (PlaybackFocus, ADR-0004 slice 2) —
     * the twin of the Android manager's ctor seam. Music claims the floor on
     * the is-playing edge (the ONE chokepoint every desktop play path
     * crosses — the engine observer below; see [onPlayingEdge]) and pauses
     * when the matrix commands it (read-aloud took the floor; the app-side
     * `DesktopAudioQueueManagerSurface` forwards that pause back here).
     * MUSIC claims publish state at slices 1-2 and, since the ADR-0004
     * migration slice landed in core:data (osLegClaimants is now
     * READ_ALOUD + MUSIC), they also request the OS seat — which on desktop
     * still degrades to vacuous arbitration (the app-side binding is the
     * in-process `DesktopFocusArbiter`, whose grant is vacuously true and
     * whose listener is never invoked, so no OS seat is actually held and
     * the behavior here is unchanged) while the claim-state PUBLICATION is
     * live: a desktop reader observing claimState sees Held(MUSIC) for
     * real. Defaulted Noop so plain constructions (tests) keep
     * single-player semantics.
     */
    private val playbackFocus: PlaybackFocus = NoopPlaybackFocus,
) : AudioQueueManager, AudioPlayerEngine {

    private companion object {
        // Same cadences as the Android manager/ticker pair.
        private const val POSITION_POLL_INTERVAL_MS = 250L
        /** Next-item prefetch fires this far behind a successful load. */
        private const val PREFETCH_DELAY_MS = 2_000L
    }

    // Seeds the shared EnginePositionTicker's polling-interval flow below
    // (player-contract owns the cadence + the paused re-check constant).
    // internal purely for test tuning; production never touches it.
    internal var positionPollIntervalMs: Long = POSITION_POLL_INTERVAL_MS

    // Next-item prefetch (see the "Queue pre-warm is next-item-only"
    // divergence note). One job + one cached entry; cleared by every queue
    // mutation (the chassis core's shape-invalidation hook) and by explicit
    // play().
    internal var prefetchDelayMs: Long = PREFETCH_DELAY_MS
    private var prefetchJob: Job? = null
    private var prefetchedTrack: Pair<String, ResolvedAudioTrack>? = null

    // ── Engine plumbing ────────────────────────────────────────────────────

    private var engine: MediaEngine? = null
    private var engineObserverJobs: List<Job> = emptyList()

    /**
     * The manager's engine-command port — [AudioQueueStateCore]'s ONLY way
     * to touch the engine, and after the chassis fold this manager's only
     * command route too (lifecycle, observers, effects pushes and release
     * stay direct engine touches). Every member keeps the historical
     * engine-null tolerance of the inlined calls it replaced.
     */
    private val engineDispatch = object : EngineDispatch {
        override val isLive: Boolean get() = engine != null

        override fun prepare(item: AudioQueueItem, startPositionMs: Long) {
            val target = engine ?: return
            loadItem(target, item, startPositionMs)
        }

        override fun play() {
            engine?.play()
        }

        override fun pause() {
            engine?.takeIf { it.isPlaying.value }?.pause()
        }

        override fun stop() {
            engine?.stop()
        }

        override fun seekTo(positionMs: Long) {
            engine?.seekTo(positionMs)
        }

        override fun setPlaybackSpeed(speed: Float) {
            engine?.setPlaybackSpeed(speed)
        }
    }

    /**
     * Server progress reporting — the shared (commonMain)
     * [AudioProgressReporter] with engine-backed provider lambdas. Owns the
     * 10 s cadence, the paused-position dedup and the stop-report ordering
     * (launched never awaited; session id rotated synchronously). The
     * session cells it reads/rotates live in the chassis core.
     */
    private val progressReporter = AudioProgressReporter(
        scope = scope,
        playbackRepository = playbackRepository,
        // Desktop has no remote/cast session (declared divergence: the cast
        // seam is a never-connected no-op) — the remote gate never trips.
        remoteSessionActive = { false },
        positionMsProvider = { engine?.currentPositionMs },
        isPlayingProvider = { engine?.isPlaying?.value == true },
        itemIdProvider = { state.currentItemId },
        playSessionIdProvider = { state.playSessionId },
        playSessionIdSetter = { state.playSessionId = it },
        reportIntervalMs = progressReportIntervalMs,
    )

    // ── The queue-state chassis (commonMain AudioQueueStateCore) ───────────
    // Owns the eleven playback state flows (re-exposed below by reference),
    // the undo stack + events, the advance/shuffle selection and the
    // transition choreography; this manager supplies the engine port, the
    // reporter and the desktop-only reactions (prefetch invalidation,
    // sleep-timer end-of-episode, ReplayGain context re-apply on shuffle,
    // and the full user-play path).

    internal val state: AudioQueueStateCore = AudioQueueStateCore(
        scope = scope,
        playbackRepository = playbackRepository,
        lyricsManager = lyricsManager,
        progressReporter = progressReporter,
        dispatch = engineDispatch,
        enginePositionMs = { engine?.currentPositionMs },
        onQueueShapeInvalidated = { clearPrefetch() },
        onQueueExhausted = { sleepTimerManager.triggerEndOfEpisode() },
        onShuffleModeChanged = {
            state.currentItemOrNull()?.let { current ->
                effectsManager?.applyReplayGainForTrack(current.normalizationGain, state.shuffleMode.value)
            }
        },
        onPlayRequested = { play(it) },
    )

    // ── AudioQueueManager state (re-exposed chassis flows, by reference) ───

    override val queue: StateFlow<List<AudioQueueItem>> get() = state.queue

    override val currentIndex: StateFlow<Int> get() = state.currentIndex

    override val currentPlayingItemId: StateFlow<String?> get() = state.nowPlayingTracker.currentPlayingItemId

    override val shuffleMode: StateFlow<Boolean> get() = state.shuffleMode

    override val repeatMode: StateFlow<Int> get() = state.repeatMode

    // ── AudioPlayerEngine state ────────────────────────────────────────────
    // The now-playing metadata six re-expose the chassis tracker's flows BY
    // REFERENCE (same StateFlow instances — the Android manager's pattern);
    // the manager never writes them directly.

    override val title: StateFlow<String> get() = state.nowPlayingTracker.title

    override val artist: StateFlow<String> get() = state.nowPlayingTracker.artist

    override val artistId: StateFlow<String?> get() = state.nowPlayingTracker.artistId

    override val album: StateFlow<String> get() = state.nowPlayingTracker.album

    override val albumArtUrl: StateFlow<String> get() = state.nowPlayingTracker.albumArtUrl

    override val isPlaying: StateFlow<Boolean> get() = state.isPlaying

    override val currentPosition: StateFlow<Long> get() = state.currentPosition

    override val duration: StateFlow<Long> get() = state.duration

    override val speed: StateFlow<Float> get() = state.speed

    override val playbackError: StateFlow<String?> get() = state.playbackError

    override val isLoadingItem: StateFlow<Boolean> get() = state.isLoadingItem

    override val crossfadeDurationMs: StateFlow<Long> get() = state.crossfadeDurationMs

    override val undoEvents: SharedFlow<QueueUndoEvent> get() = state.undoEvents

    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    override val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    override val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    override val lyrics: StateFlow<List<LyricsLine>> get() = lyricsManager.lyrics
    override val currentLyricIndex: StateFlow<Int> get() = lyricsManager.currentLyricIndex
    override val lyricsSource: StateFlow<LyricsSource> get() = lyricsManager.lyricsSource
    override val isFetchingLyrics: StateFlow<Boolean> get() = lyricsManager.isFetchingLyrics
    override val lyricsOffsetMs: StateFlow<Long> get() = lyricsManager.lyricsOffsetMs

    // ── Playback plumbing that stays manager-side ──────────────────────────

    private var gaplessEnabled = true
    private var _isLoadingItemFlag = false
    private var positionJob: Job? = null

    /** App-lifetime kickoff, the desktop twin of Android's `manager.start()`. */
    fun start() {
        lyricsManager.initialize(scope)
        scope.launch {
            val items = queuePersistenceHelper.loadQueue()
            if (items.isNotEmpty()) {
                state._queue.value = items
            }
            val savedState = queuePersistenceHelper.loadState()
            savedState?.let { s ->
                state._currentIndex.value = s.currentIndex
                state._currentPosition.value = s.currentPositionMs
                state._repeatMode.value = s.repeatMode.coerceIn(0, 2)
                state._shuffleMode.value = s.shuffleEnabled
                state._speed.value = s.playbackSpeed
            }
            queuePersistenceHelper.observeQueue(
                scope = scope,
                queue = state._queue,
                currentIndex = state._currentIndex,
                currentPositionMs = state._currentPosition,
                isPlaying = state._isPlaying,
                repeatMode = state._repeatMode,
                shuffleEnabled = state._shuffleMode,
                playbackSpeed = state._speed,
            )
        }
    }

    // ── Main-thread contract (AudioQueueManager) ───────────────────────────

    private fun assertMainThread(method: String) {
        if (!mainThreadGuard) return
        check(EventQueue.isDispatchThread()) {
            "AudioQueueManager.$method must be called on the main (AWT EDT) " +
                "thread. Wrap the call site in `withContext(Dispatchers.Main) { ... }`."
        }
    }

    // ── Engine lifecycle ───────────────────────────────────────────────────

    private fun getOrCreateEngine(): MediaEngine =
        engine ?: engineFactory().also { created ->
            engine = created
            effectsManager?.let { fx ->
                // Live effect application: every effects mutation re-pushes
                // the snapshot (updateConfig dedupes; the engine rebuilds the
                // mpv af chain on write).
                fx.onEffectsChanged = { pushEffectsSnapshot() }
                pushEffectsSnapshot()
            }
            engineObserverJobs = listOf(
                scope.launch {
                    created.isPlaying.collect { playing ->
                        state.onEnginePlayingChanged(playing)
                        onPlayingEdge(playing)
                    }
                },
                scope.launch {
                    created.errorFlow.collect { error ->
                        // Android: onPlayerError surfaces the message into the
                        // same playbackError flow the UI shows for load errors.
                        state.onEngineError(error.message)
                    }
                },
                scope.launch {
                    created.playbackState.collect { s ->
                        if (s == EnginePlaybackState.ENDED) state.onEngineEnded()
                    }
                },
            )
        }

    /**
     * Focus claim edge (ADR-0004 slice 2) — the desktop twin of the Android
     * manager's Player.Listener.onIsPlayingChanged claim site. Every desktop
     * play path (queue tap, resume, auto-advance, repeat replay) crosses this
     * ONE observer, so no per-entry-point claim sites can drift. Newest user
     * action wins: a true edge publishes Held(MUSIC) — the reader (whose
     * speech loop is NOT a commandable surface) pauses on that state — and a
     * false edge releases. A Denied claim honors the interface contract
     * ("the caller MUST NOT produce audio"): `pause()` mirrors the user's
     * own pause and the resulting isPlaying=false edge releases the attempt
     * on the observer's next pass. Desktop today never produces a denial
     * (the in-process twin grants vacuously and nothing suspends), but the
     * branch is mirrored verbatim so the seam holds the day an authority
     * behind it grows teeth.
     */
    private fun onPlayingEdge(playing: Boolean) {
        if (playing) {
            if (playbackFocus.acquire(PlaybackSurfaceId.MUSIC) is FocusOutcome.Denied) {
                pause()
            }
        } else {
            playbackFocus.release(PlaybackSurfaceId.MUSIC)
        }
    }

    /** Resolves the item and loads it into the engine (the prepare port body). */
    private fun loadItem(target: MediaEngine, item: AudioQueueItem, startPositionMs: Long) {
        scope.launch {
            val track = consumePrefetched(item.id, startPositionMs)
                ?: trackResolver.resolve(item.id, startPositionMs)
            if (track != null) {
                state._playbackError.value = null
                // Android: applyReplayGain(item.normalizationGain, shuffle)
                // before the player transition (AudioPlaybackManager advance).
                effectsManager?.applyReplayGainForTrack(item.normalizationGain, state.shuffleMode.value)
                target.load(
                    PlaybackRequest(
                        uri = track.uri,
                        title = item.name,
                        startPositionMs = startPositionMs,
                        serverDurationMs = item.durationMs,
                        normalizationGain = item.normalizationGain,
                    ),
                )
                schedulePrefetchForNext()
            } else {
                // Android: an unresolvable item never enters the prebuilt
                // playlist; the desktop discovers it here instead.
                state._playbackError.value = "Failed to load track"
                state._isPlaying.value = false
            }
        }
    }

    // ── Effects snapshot plumbing ──────────────────────────────────────────

    private fun pushEffectsSnapshot() {
        val fx = effectsManager ?: return
        val e = engine ?: return
        e.updateConfig(EngineConfig(audioEffects = fx.snapshotConfig()))
    }

    // ── Next-item prefetch (divergence note: "pre-warm is next-item-only") ─

    /** Cancels the in-flight prefetch and drops any cached entry. */
    private fun clearPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedTrack = null
    }

    /**
     * Schedules a background resolve of the item auto-advance would play
     * next (the shared [AudioQueuePolicy.nextIndex] — the same rule the
     * chassis core consults for the actual advances). Resolve-only: the
     * cached entry is consumed by [loadItem] exclusively for a position-0
     * load of that id, so a queue mutation between prefetch and consumption
     * degrades to a wasted fetch — never a wrong load.
     */
    private fun schedulePrefetchForNext() {
        clearPrefetch()
        val q = state._queue.value
        if (q.isEmpty()) return
        val next = AudioQueuePolicy.nextIndex(state._currentIndex.value, q.size, state._repeatMode.value) ?: return
        val nextItem = q.getOrNull(next) ?: return
        prefetchJob = scope.launch {
            delay(prefetchDelayMs)
            val track = trackResolver.resolve(nextItem.id, 0L) ?: return@launch
            prefetchedTrack = nextItem.id to track
        }
    }

    private fun consumePrefetched(itemId: String, startPositionMs: Long): ResolvedAudioTrack? {
        val cached = prefetchedTrack ?: return null
        if (cached.first != itemId || startPositionMs != 0L) return null
        prefetchedTrack = null
        return cached.second
    }

    // ── AudioPlayerEngine: play(itemId) — the Android play() mirror ────────

    override fun play(itemId: String) {
        assertMainThread("play")

        // Divergence (declared): no Play-On/remote-session routing — the
        // desktop cast seam is never connected.

        if (state.currentItemId == itemId) {
            if (_isLoadingItemFlag) return
            val engineState = engine?.playbackState?.value
            if (engineState != null &&
                engineState != EnginePlaybackState.ENDED &&
                engineState != EnginePlaybackState.IDLE
            ) {
                return
            }
        }

        // No-arg shape: current item + engine position from the reporter's
        // providers; session id rotates synchronously inside.
        progressReporter.reportStopped()
        // A→B loop is track-specific; clear it when loading a new item.
        clearAbLoop()        // An explicit play() changes the playback context — any next-item
        // prefetch scheduled for the previous context is stale.
        clearPrefetch()
        state.currentItemId = itemId
        _isLoadingItemFlag = true
        state._isLoadingItem.value = true

        val player = getOrCreateEngine()

        scope.launch {
            val track = trackResolver.resolve(itemId, 0L)
            if (track != null) {
                state._playbackError.value = null
                // Detail publish shape: the ONE site that knows the artist id
                // and the server image url (Android play()'s detail path).
                state.nowPlayingTracker.publishDetail(
                    itemId = itemId,
                    title = track.title,
                    artist = track.artist,
                    artistId = track.artistId,
                    album = track.album ?: "",
                    albumArtUrl = playbackRepository.getImageUrl(itemId, maxWidth = 600),
                )

                val resumeTicks = track.resumePositionTicks ?: 0L
                val startPositionMs = if (resumeTicks > 0) resumeTicks / 10_000 else 0L

                val q = state._queue.value
                val currentIdx = state.currentIndex.value
                val isInQueue = currentIdx >= 0 && q.getOrNull(currentIdx)?.id == itemId
                if (!isInQueue) {
                    val queueItem = AudioQueueItem(
                        id = itemId,
                        name = title.value,
                        artist = artist.value,
                        album = album.value,
                        imageUrl = albumArtUrl.value,
                        mediaSourceId = track.mediaSourceId,
                        durationMs = track.durationMs,
                        normalizationGain = track.normalizationGain,
                    )
                    state.appendPlayedItem(queueItem)
                }

                val clickedItem = state._queue.value.getOrNull(state.currentIndex.value)
                if (clickedItem != null) {
                    // Android play(): effectsProcessor.applyReplayGain(
                    // detail.item.normalizationGain, _shuffleMode.value).
                    effectsManager?.applyReplayGainForTrack(
                        clickedItem.normalizationGain ?: track.normalizationGain,
                        state.shuffleMode.value,
                    )
                    player.load(
                        PlaybackRequest(
                            uri = track.uri,
                            title = clickedItem.name,
                            startPositionMs = startPositionMs,
                            serverDurationMs = clickedItem.durationMs,
                            normalizationGain = clickedItem.normalizationGain,
                        ),
                    )

                    playbackRepository.reportPlaybackStart(
                        PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = state.playSessionId,
                            mediaSourceId = track.mediaSourceId,
                            startPositionTicks = if (startPositionMs > 0) startPositionMs * 10_000 else null,
                        )
                    )

                    state.fetchLyrics(
                        item = clickedItem,
                        durationSecOverride = track.durationMs.takeIf { it > 0 }?.let { it / 1000.0 },
                    )
                    // Divergence (declared): pre-warm is NEXT-ITEM-ONLY —
                    // Android builds MediaItems for the whole queue here.
                    schedulePrefetchForNext()
                    startPositionTracking()
                    progressReporter.start()
                }
            } else {
                state._playbackError.value = "Failed to load track"
            }
            _isLoadingItemFlag = false
            state._isLoadingItem.value = false
        }
    }

    // ── AudioQueueManager: mutations (chassis-core delegation) ─────────────

    override fun playQueue(items: List<AudioQueueItem>, startIndex: Int) {
        assertMainThread("playQueue")
        state.playQueue(items, startIndex)
    }

    override fun addToQueue(item: AudioQueueItem) {
        assertMainThread("addToQueue")
        state.addToQueue(item)
    }

    override fun addToQueueAll(items: List<AudioQueueItem>) {
        assertMainThread("addToQueueAll")
        state.addToQueueAll(items)
    }

    override fun removeFromQueue(index: Int) {
        assertMainThread("removeFromQueue")
        state.removeFromQueue(index)
    }

    override fun clearQueue() {
        assertMainThread("clearQueue")
        state.clearQueue()
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        assertMainThread("moveQueueItem")
        state.moveQueueItem(fromIndex, toIndex)
    }

    override fun skipToNext() {
        assertMainThread("skipToNext")
        state.skipToNext()
    }

    override fun skipToPrevious() {
        assertMainThread("skipToPrevious")
        state.skipToPrevious()
    }

    override fun toggleShuffle() {
        assertMainThread("toggleShuffle")
        state.toggleShuffle()
    }

    override fun cycleRepeatMode() {
        assertMainThread("cycleRepeatMode")
        state.cycleRepeatMode()
    }

    override fun setRepeatMode(mode: Int) {
        assertMainThread("setRepeatMode")
        state.setRepeatMode(mode)
    }

    override fun setShuffleMode(enabled: Boolean) {
        assertMainThread("setShuffleMode")
        state.setShuffleMode(enabled)
    }

    override fun playFromQueue(index: Int) {
        assertMainThread("playFromQueue")
        state.playFromQueue(index)
    }

    // ── AudioPlayerEngine: transport / metadata ────────────────────────────

    override fun seekTo(positionMs: Long) {
        assertMainThread("seekTo")
        state.seekTo(positionMs)
    }

    override fun togglePlayPause() {
        assertMainThread("togglePlayPause")
        if (!engineDispatch.isLive) return
        if (engine?.isPlaying?.value == true) engineDispatch.pause() else engineDispatch.play()
    }

    override fun pause() {
        assertMainThread("pause")
        engineDispatch.pause()
    }

    override fun changePlaybackSpeed(value: Float) {
        assertMainThread("changePlaybackSpeed")
        state.changePlaybackSpeed(value)
        // No crossfader to inform (divergence declared above).
    }

    override fun setSkipPreviousThreshold(ms: Long) {
        state.skipPreviousThresholdMs = ms
    }

    override fun setCrossfadeDurationMs(ms: Long) {
        // State mirror of Android's flag interplay; no audible crossfade on
        // desktop (declared divergence).
        state._crossfadeDurationMs.value = ms
        if (ms > 0) {
            gaplessEnabled = false
        } else {
            gaplessEnabled = true
        }
    }

    override fun setGaplessEnabled(enabled: Boolean) {
        gaplessEnabled = enabled
        if (enabled) {
            state._crossfadeDurationMs.value = 0L
        }
    }

    override fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    // ── Lyrics passthrough (shared AudioLyricsManager — real on desktop) ───

    override fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        lyricsManager.searchLyrics(query, callback)
    }

    override fun applyLyrics(lrcLibId: Long) {
        lyricsManager.applyLyrics(lrcLibId, state.currentItemId)
    }

    override fun setLyricsOffset(offsetMs: Long) {
        lyricsManager.setLyricsOffset(offsetMs)
    }

    // ── Undo (chassis core; only the assert stays here) ────────────────────

    override fun undoLastQueueOperation(): Boolean {
        assertMainThread("undoLastQueueOperation")
        return state.undoLastQueueOperation()
    }

    // ── A→B loop (Android mirror; only cycleAbLoop is on the seam) ────────
    // Marker transition rules live in the shared (commonMain)
    // [AudioQueuePolicy] — the same transitions the Android manager's
    // setAbLoopStart/setAbLoopEnd execute. cycleAbLoop writes the pair the
    // state machine returns directly (writing unchanged markers back would
    // be StateFlow-conflated to a no-op); the former private one-shot
    // setters died with the inline `when`.

    private fun clearAbLoop() {
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
    }

    override fun cycleAbLoop() {
        assertMainThread("cycleAbLoop")
        val next = AudioQueuePolicy.cycleAbLoop(
            positionMs = engine?.currentPositionMs ?: state.currentPosition.value,
            markers = AudioQueuePolicy.AbLoopMarkers(_abLoopStartMs.value, _abLoopEndMs.value),
        )
        _abLoopStartMs.value = next.startMs
        _abLoopEndMs.value = next.endMs
    }

    // ── Position ticker (Android startPositionTracking mirror) ─────────────

    private fun startPositionTracking() {
        positionJob?.cancel()
        var lastPosition = 0L
        var lastDuration = 0L
        // The shared polling loop (player-contract) owns the cadence, the
        // bounded reactive paused-wait and the engine-less exponential
        // backoff; this is only the tick body. Paused ticks still reach
        // [onActive] on a play↔pause edge — the body's own gate keeps them
        // no-ops. (Replaces the former hand-rolled while(true) loop, whose
        // plain `delay(positionPausedRecheckMs)` paused-wait held a resume
        // for up to 2.5 s; the ticker wakes on the isPlaying flow, so a
        // resume is detected immediately.)
        val tickBody: () -> Unit = tickBody@{
            val e = engine ?: return@tickBody
            if (!e.isPlaying.value) return@tickBody

            // Shared tick decisions (commonMain AudioQueuePolicy — the
            // same plan the Android ticker executes; the desktop keeps
            // only these, its declared divergence from Android's
            // crossfade/bandwidth tick duties).
            val plan = AudioQueuePolicy.positionTickPlan(
                positionMs = e.currentPositionMs,
                durationMs = e.durationMs,
                lastPublishedPositionMs = lastPosition,
                lastPublishedDurationMs = lastDuration,
                hasLyrics = lyricsManager.lyrics.value.isNotEmpty(),
                abLoopStartMs = _abLoopStartMs.value,
                abLoopEndMs = _abLoopEndMs.value,
            )
            plan.seekToMs?.let { e.seekTo(it) }
            plan.publishPositionMs?.let {
                state._currentPosition.value = it
                lastPosition = it
            }
            plan.publishDurationMs?.let {
                state._duration.value = it
                lastDuration = it
            }
            if (plan.updateLyricIndex) {
                lyricsManager.updateCurrentLyricIndex(state.currentPosition.value)
            }
        }
        positionJob = EnginePositionTicker(
            scope = scope,
            pollingIntervalMs = MutableStateFlow(positionPollIntervalMs),
            isPlayingFlow = state.isPlaying,
            isCurrentlyPlaying = { engine?.isPlaying?.value == true },
            isReady = { engine != null },
            onActive = { tickBody() },
        ).launch()
        // Prime read: the former hand-rolled loop published the first
        // position/duration on its FIRST iteration — before any delay —
        // while the shared ticker delays before its first tick. Seed the
        // display flows once here (the body's own gates make this a no-op
        // when no engine is live yet), so the transition stop-report
        // fallback — `_duration.value * 10_000` — and the UI's first frame
        // see the loaded item's values immediately, as before. Synchronous
        // on purpose: a launched tick could race the very fallback it feeds.
        tickBody()
    }

    // ── Progress reporting ─────────────────────────────────────────────────
    // The shared (commonMain) AudioProgressReporter above IS the former
    // desktop mirror: start() replaces startProgressReporting(),
    // reportStopped(...) / reportStopped() replace the two report functions
    // (same launched-never-awaited stop, same synchronous session-id
    // rotation — invariants recorded in the reporter's KDoc).

    // ── Teardown (Android stopAndRelease mirror) ───────────────────────────

    override fun stopAndRelease() {
        assertMainThread("stopAndRelease")

        positionJob?.cancel()
        // Teardown entry (BEFORE the engine release below): the reporter
        // snapshots the final item/session/position through its providers —
        // which read the still-live engine — cancels its loop, launches the
        // final stop report and rotates the session id synchronously. This
        // is the former ~35-line hand-rolled tail, now shared with the
        // Android adapter inside AudioProgressReporter.
        progressReporter.stopAndCancel()
        clearPrefetch()
        effectsManager?.onEffectsChanged = null
        engineObserverJobs.forEach { it.cancel() }
        engineObserverJobs = emptyList()
        engine?.release()
        engine = null

        // Display resets + the session's item claim + the tracker clear
        // (artistId deliberately survives — the tracker's recorded
        // divergence, see the declared-divergences note above), in the
        // chassis core.
        state.onEngineReleased()
        lyricsManager.reset()
    }
}
