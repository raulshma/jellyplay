package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.QueueSnapshot
import com.raulshma.jellyplay.core.data.playback.QueueUndoEvent
import com.raulshma.jellyplay.core.data.playback.QueueUndoStack
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import java.awt.EventQueue
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop audio playback core (wave 9B): real queue + transport over a
 * dedicated audio-only [MpvDesktopEngine] (`vo=null` — audio tracks never open
 * a video output).
 *
 * The Android media3 `AudioPlaybackManager` (legacy `:core:data`) is the
 * SEMANTICS SOURCE OF TRUTH; every observable behavior was mirrored
 * case-by-case and every divergence is declared in the class KDoc below.
 * The manager implements BOTH shared contracts the audio player consumes —
 * [AudioQueueManager] (queue mutations + the five state flows) and
 * [AudioPlayerEngine] (metadata/transport/lyrics/undo/A-B loop) — the same
 * single-object pattern Android uses (its manager implements
 * AudioQueueManager + AudioEffectsManager and the engine seam delegates to
 * it).
 *
 * ## Semantics table (Android → here)
 *
 * | Behavior | Android (media3) | Desktop |
 * |---|---|---|
 * | playQueue(items, start) | clears undo stack, sets queue+index, `play(items[start])`; out-of-bounds start leaves the queue set and returns | identical |
 * | addToQueue/addToQueueAll | appends to queue + player playlist; index unchanged | identical (player playlist concept absent — the queue list IS the truth; next-item resolution happens at advance time) |
 * | removeFromQueue | bounds + undo snapshot; removing current → index coerced + player transitions to the shifted-in item; removing above current → index -1 | identical; removing current reloads the shifted-in item via [transitionTo] (or stops the engine when the queue empties) |
 * | clearQueue | undo snapshot, queue=[], index=-1, player playlist cleared (goes idle, metadata kept) | identical (engine.stop(); metadata kept) |
 * | moveQueueItem | undo snapshot; index remap (from→to / ±1 crossing); player item moved (no transition) | identical (pure state — the playing item never changes) |
 * | skipToNext | index+1, or wrap to 0 under repeat ≥ 1, else no-op; NO undo snapshot on the no-op path | identical |
 * | skipToPrevious | player exists else no-op; position > 3 s → seek 0 only; else index-1 or wrap to last under repeat ≥ 1; undo snapshot only on the index move | identical |
 * | playFromQueue | sets index, seeks player to (index, 0), plays if paused | identical (same-index clicks seek to 0 without a reload; cross-index clicks load via [transitionTo]) |
 * | toggleShuffle ON | current item moves to head, rest reshuffled, index=0, player playlist rebuilt at current position | identical list/index behavior; NO engine reload needed (the current item keeps playing — the playlist rebuild is playlist plumbing, not an observable playback change). Like Android (`val player = exoPlayer ?: return` right after the flag flip), the REORDER is gated on a live engine: toggling shuffle before anything ever played flips only [shuffleMode] |
 * | toggleShuffle OFF | original order restored, index jumps to the current item's original slot, playlist rebuilt at current position | identical (state-only, same reasoning + same engine gate) |
 * | setShuffleMode(b) | no-op when unchanged, else toggleShuffle | identical |
 * | cycleRepeatMode / setRepeatMode | (mode+1)%3 / coerce 0..2, player.repeatMode mapped (0=OFF, 1=ALL, 2=ONE) | identical values; the repeat behavior is applied at track end (below) instead of via a player property |
 * | Auto-advance at track end | ExoPlayer advances mid-queue under OFF; wraps under ALL; replays under ONE; `onMediaItemTransition` reconciles index/metadata and reports stop(prev)+start(next) | engine ENDED → advance/wrap/replay; [transitionTo] performs the same reconciliation and reporting |
 * | End of queue under RepeatNone | STATE_ENDED: isPlaying=false, index stays, sleep-timer end-of-episode hook fires; metadata kept | identical |
 * | play(itemId) | same-item + (READY/BUFFERING) → no-op; reports stop(prev); clears A-B loop; appends to queue when not the current item; resume from server ticks; reports start; fetches lyrics; starts position ticker + 10 s progress reporter | identical (see divergences: no Play-On routing, no queue pre-warm, no crossfade) |
 * | Queue persistence | Room (QueuePersistenceHelper): full-list replace on change, state incl. index/position/repeat/shuffle/speed sampled | identical — the same shared helper over the same Room DAO works on desktop JVM |
 *
 * ## Declared divergences (all deliberate)
 *
 *  - **No crossfade / gapless engine** — `setCrossfadeDurationMs`/
 *    `setGaplessEnabled` keep the observable state flows but there is no
 *    crossfader; track changes are load-file boundaries, so a small gap can
 *    be heard. Crossfade parity needs a second engine instance (later item).
 *  - **No queue pre-warm** — Android builds MediaItems for the whole queue
 *    behind the current item; the desktop resolves each item when it starts
 *    (one detail fetch + URL build per advance). This also retires the
 *    `queueLoadingJob != null` bail-outs that guard Android's
 *    skipToNext/skipToPrevious/playFromQueue/removeFromQueue/undo during a
 *    pre-warm — there is no pre-warm window to guard against, so those
 *    mutations are always live here.
 *  - **No Play-On routing in play()** — Android delegates to a connected
 *    remote session; desktop has no cast stack (the [DesktopAudioPlayerCast]
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
 *    test below pins this end-to-end.
 *  - **Main-thread guard** — Android asserts `Looper.myLooper() == main` on
 *    every mutation; the desktop twin asserts the AWT EDT (the app's
 *     Dispatchers.Main). Injectable so tests can disable it.
 *  - **No audio-effects/ReplayGain application** — the shared
 *    AudioEffectsManager desktop impl is state-only; DSP lands with the mpv
 *    `af` chain work.
 *  - **No media session / now-playing notification / bandwidth sampling** —
 *    Android-only surfaces; the desktop position ticker keeps the A-B loop +
 *    lyrics index duties only.
 */
class DesktopAudioQueueManager(
    private val trackResolver: AudioTrackResolver,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val queuePersistenceHelper: QueuePersistenceHelper,
    private val lyricsManager: AudioLyricsManager,
    private val sleepTimerManager: SleepTimerManager,
    private val scope: CoroutineScope,
    private val engineFactory: () -> MediaEngine = {
        MpvDesktopEngine(extraOptions = mapOf("vo" to "null"))
    },
    /** Android's always-on Looper check, desktop twin = AWT EDT. Tests disable. */
    private val mainThreadGuard: Boolean = true,
    /**
     * Progress-report cadence (Android hard-codes 10 s in
     * AudioProgressReporter). Injectable purely for tests — production wiring
     * leaves the default.
     */
    private val progressReportIntervalMs: Long = PROGRESS_REPORT_INTERVAL_MS,
) : AudioQueueManager, AudioPlayerEngine {

    private companion object {
        // Same cadences as the Android manager/ticker pair.
        private const val POSITION_POLL_INTERVAL_MS = 250L
        private const val POSITION_PAUSED_RECHECK_MS = 2_500L
        private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
    }

    // Position-tracking loop (Android startPositionTracking's exact intervals).
    // internal purely for test tuning; production never touches these.
    internal var positionPollIntervalMs: Long = POSITION_POLL_INTERVAL_MS
    internal var positionPausedRecheckMs: Long = POSITION_PAUSED_RECHECK_MS

    // ── AudioQueueManager state (defaults identical to Android) ────────────

    private val _queue = MutableStateFlow<List<AudioQueueItem>>(emptyList())
    override val queue: StateFlow<List<AudioQueueItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentPlayingItemId = MutableStateFlow<String?>(null)
    override val currentPlayingItemId: StateFlow<String?> = _currentPlayingItemId.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    override val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(0)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // ── AudioPlayerEngine state (defaults identical to Android) ────────────

    private val _title = MutableStateFlow("")
    override val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    override val artist: StateFlow<String> = _artist.asStateFlow()

    private val _artistId = MutableStateFlow<String?>(null)
    override val artistId: StateFlow<String?> = _artistId.asStateFlow()

    private val _album = MutableStateFlow("")
    override val album: StateFlow<String> = _album.asStateFlow()

    private val _albumArtUrl = MutableStateFlow("")
    override val albumArtUrl: StateFlow<String> = _albumArtUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    override val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _isLoadingItem = MutableStateFlow(false)
    override val isLoadingItem: StateFlow<Boolean> = _isLoadingItem.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(0L)
    override val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private val _undoEvents = MutableSharedFlow<QueueUndoEvent>(extraBufferCapacity = 4)
    override val undoEvents: SharedFlow<QueueUndoEvent> = _undoEvents.asSharedFlow()

    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    override val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    override val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    override val lyrics: StateFlow<List<LyricsLine>> get() = lyricsManager.lyrics
    override val currentLyricIndex: StateFlow<Int> get() = lyricsManager.currentLyricIndex
    override val lyricsSource: StateFlow<LyricsSource> get() = lyricsManager.lyricsSource
    override val isFetchingLyrics: StateFlow<Boolean> get() = lyricsManager.isFetchingLyrics
    override val lyricsOffsetMs: StateFlow<Long> get() = lyricsManager.lyricsOffsetMs

    // ── Playback plumbing ──────────────────────────────────────────────────

    private var engine: MediaEngine? = null
    private var engineObserverJobs: List<Job> = emptyList()

    /** Jellyfin play session id — rotated by [reportStoppedCurrent] like Android. */
    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var _isLoadingItemFlag = false
    private var positionJob: Job? = null
    private var progressJob: Job? = null
    private var lastPausedPositionTicks = -1L

    private var gaplessEnabled = true
    private var skipPreviousThresholdMsInternal = 3_000L

    private val queueUndoStack = QueueUndoStack()

    private var unshuffledQueue: List<AudioQueueItem> = emptyList()

    /** App-lifetime kickoff, the desktop twin of Android's `manager.start()`. */
    fun start() {
        lyricsManager.initialize(scope)
        scope.launch {
            val items = queuePersistenceHelper.loadQueue()
            if (items.isNotEmpty()) {
                _queue.value = items
            }
            val savedState = queuePersistenceHelper.loadState()
            savedState?.let { state ->
                _currentIndex.value = state.currentIndex
                _currentPosition.value = state.currentPositionMs
                _repeatMode.value = state.repeatMode.coerceIn(0, 2)
                _shuffleMode.value = state.shuffleEnabled
                _speed.value = state.playbackSpeed
            }
            queuePersistenceHelper.observeQueue(
                scope = scope,
                queue = _queue,
                currentIndex = _currentIndex,
                currentPositionMs = _currentPosition,
                isPlaying = _isPlaying,
                repeatMode = _repeatMode,
                shuffleEnabled = _shuffleMode,
                playbackSpeed = _speed,
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
            engineObserverJobs = listOf(
                scope.launch {
                    created.isPlaying.collect { playing -> _isPlaying.value = playing }
                },
                scope.launch {
                    created.errorFlow.collect { error ->
                        // Android: onPlayerError surfaces the message into the
                        // same playbackError flow the UI shows for load errors.
                        _playbackError.value = error.message
                    }
                },
                scope.launch {
                    created.playbackState.collect { state ->
                        if (state == EnginePlaybackState.ENDED) onEngineEnded()
                    }
                },
            )
        }

    /**
     * Track end. Android: under repeat ≥ 1 the player never reaches ENDED
     * (ALL wraps, ONE replays); mid-queue advances are ordinary transitions.
     * Desktop: the same outcomes, driven from the single-item engine's ENDED.
     */
    private fun onEngineEnded() {
        val q = _queue.value
        if (_repeatMode.value == 2) {
            // RepeatOne: replay the same item — engine.play() from ENDED
            // seeks back to 0 and unpauses (the V2b keep-open replay path).
            engine?.play()
            return
        }
        val idx = _currentIndex.value
        val next = when {
            idx < q.lastIndex -> idx + 1
            _repeatMode.value >= 1 && q.isNotEmpty() -> 0
            else -> -1
        }
        if (next >= 0) {
            transitionTo(next, startPositionMs = 0L)
        } else {
            // End of queue under RepeatNone — Android's STATE_ENDED path:
            // isPlaying off, index stays on the ended item, metadata kept.
            _isPlaying.value = false
            sleepTimerManager.triggerEndOfEpisode()
        }
    }

    /**
     * The `onTrackTransitioned` mirror: reconcile index + metadata from the
     * queue item, report stop(prev) + start(next) to the server, fetch
     * lyrics, then load the item into the engine.
     *
     * With no live engine this only updates the index — the exact Android
     * shape, where a null player means the seek never happens, so no player
     * transition (and therefore no metadata reconciliation) fires either.
     */
    private fun transitionTo(index: Int, startPositionMs: Long = 0L) {
        val item = _queue.value.getOrNull(index) ?: return
        _currentIndex.value = index
        val target = engine ?: return

        val prevItemId = currentItemId
        val prevSessionId = playSessionId
        val prevPosTicks =
            if (_currentPosition.value > 0) _currentPosition.value * 10_000
            else _duration.value * 10_000

        currentItemId = item.id
        _currentPlayingItemId.value = item.id
        _title.value = item.name
        _artist.value = item.artist
        _album.value = item.album ?: ""
        _albumArtUrl.value = item.imageUrl ?: ""

        scope.launch {
            reportStopped(prevItemId, prevSessionId, prevPosTicks)
            fetchLyrics(item)
            playbackRepository.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = item.id,
                    sessionId = playSessionId,
                    mediaSourceId = item.mediaSourceId,
                )
            )
        }
        loadItem(target, item, startPositionMs)
    }

    /** Resolves the item and loads it into the engine. */
    private fun loadItem(target: MediaEngine, item: AudioQueueItem, startPositionMs: Long) {
        scope.launch {
            val track = trackResolver.resolve(item.id, startPositionMs)
            if (track != null) {
                _playbackError.value = null
                target.load(
                    PlaybackRequest(
                        uri = track.uri,
                        title = item.name,
                        startPositionMs = startPositionMs,
                        serverDurationMs = item.durationMs,
                        normalizationGain = item.normalizationGain,
                    ),
                )
            } else {
                // Android: an unresolvable item never enters the prebuilt
                // playlist; the desktop discovers it here instead.
                _playbackError.value = "Failed to load track"
                _isPlaying.value = false
            }
        }
    }

    // ── AudioPlayerEngine: play(itemId) — the Android play() mirror ────────

    override fun play(itemId: String) {
        assertMainThread("play")

        // Divergence (declared): no Play-On/remote-session routing — the
        // desktop cast seam is never connected.

        if (currentItemId == itemId) {
            if (_isLoadingItemFlag) return
            val state = engine?.playbackState?.value
            if (state != null && state != EnginePlaybackState.ENDED && state != EnginePlaybackState.IDLE) {
                return
            }
        }

        reportStoppedCurrent()
        // A→B loop is track-specific; clear it when loading a new item.
        clearAbLoop()
        currentItemId = itemId
        _isLoadingItemFlag = true
        _isLoadingItem.value = true

        val player = getOrCreateEngine()

        scope.launch {
            val track = trackResolver.resolve(itemId, 0L)
            if (track != null) {
                _playbackError.value = null
                _currentPlayingItemId.value = itemId
                _title.value = track.title
                _artist.value = track.artist
                _artistId.value = track.artistId
                _album.value = track.album ?: ""
                _albumArtUrl.value = playbackRepository.getImageUrl(itemId, maxWidth = 600)

                val resumeTicks = track.resumePositionTicks ?: 0L
                val startPositionMs = if (resumeTicks > 0) resumeTicks / 10_000 else 0L

                val q = _queue.value
                val currentIdx = _currentIndex.value
                val isInQueue = currentIdx >= 0 && q.getOrNull(currentIdx)?.id == itemId
                if (!isInQueue) {
                    val queueItem = AudioQueueItem(
                        id = itemId,
                        name = _title.value,
                        artist = _artist.value,
                        album = _album.value,
                        imageUrl = _albumArtUrl.value,
                        mediaSourceId = track.mediaSourceId,
                        durationMs = track.durationMs,
                        normalizationGain = track.normalizationGain,
                    )
                    _queue.value = _queue.value + queueItem
                    _currentIndex.value = _queue.value.lastIndex
                }

                val clickedItem = _queue.value.getOrNull(_currentIndex.value)
                if (clickedItem != null) {
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
                            sessionId = playSessionId,
                            mediaSourceId = track.mediaSourceId,
                            startPositionTicks = if (startPositionMs > 0) startPositionMs * 10_000 else null,
                        )
                    )

                    fetchLyrics(
                        item = clickedItem,
                        durationSecOverride = track.durationMs.takeIf { it > 0 }?.let { it / 1000.0 },
                    )
                    // Divergence (declared): no queue pre-warm — Android
                    // builds MediaItems for the whole queue here; the desktop
                    // resolves per advance.
                    startPositionTracking()
                    startProgressReporting()
                }
            } else {
                _playbackError.value = "Failed to load track"
            }
            _isLoadingItemFlag = false
            _isLoadingItem.value = false
        }
    }

    // ── AudioQueueManager: mutations (Android mirrors) ─────────────────────

    override fun playQueue(items: List<AudioQueueItem>, startIndex: Int) {
        assertMainThread("playQueue")
        // A fresh queue invalidates any undo history from the previous queue.
        queueUndoStack.clear()
        _queue.value = items
        _currentIndex.value = startIndex
        val item = items.getOrNull(startIndex) ?: return
        play(item.id)
    }

    override fun addToQueue(item: AudioQueueItem) {
        assertMainThread("addToQueue")
        _queue.value = _queue.value + item
        // The engine append is playlist plumbing on Android; on desktop the
        // queue list is the single truth, so there is nothing else to do.
    }

    override fun addToQueueAll(items: List<AudioQueueItem>) {
        assertMainThread("addToQueueAll")
        if (items.isEmpty()) return
        _queue.value = _queue.value + items
    }

    override fun removeFromQueue(index: Int) {
        assertMainThread("removeFromQueue")
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        val removed = q[index]
        pushUndoSnapshot(QueueUndoEvent.ItemRemoved(removed))
        val wasPlaying = index == _currentIndex.value
        _queue.value = q.toMutableList().apply { removeAt(index) }
        if (wasPlaying) {
            if (_queue.value.isNotEmpty()) {
                _currentIndex.value = _currentIndex.value.coerceAtMost(_queue.value.lastIndex)
                // Android: removeMediaItem(current) makes the shifted-in item
                // play (player transition reconciles); the desktop loads it.
                transitionTo(_currentIndex.value, startPositionMs = 0L)
            } else {
                _currentIndex.value = -1
                // Android: playlist emptied → player idle, metadata kept.
                engine?.stop()
                _isPlaying.value = false
            }
        } else if (index < _currentIndex.value) {
            _currentIndex.value -= 1
        }
    }

    override fun clearQueue() {
        assertMainThread("clearQueue")
        if (_queue.value.isEmpty()) return
        pushUndoSnapshot(QueueUndoEvent.QueueCleared)
        _queue.value = emptyList()
        _currentIndex.value = -1
        // Android: clearMediaItems parks the player idle; metadata is kept.
        engine?.stop()
        _isPlaying.value = false
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        assertMainThread("moveQueueItem")
        val q = _queue.value
        if (fromIndex < 0 || fromIndex >= q.size) return
        if (toIndex < 0 || toIndex >= q.size) return
        if (fromIndex == toIndex) return
        pushUndoSnapshot(QueueUndoEvent.ItemMoved(q[fromIndex]))
        val mutable = q.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _queue.value = mutable
        val current = _currentIndex.value
        val newIndex = when {
            current == fromIndex -> toIndex
            fromIndex < current && toIndex >= current -> current - 1
            fromIndex > current && toIndex <= current -> current + 1
            else -> current
        }
        _currentIndex.value = newIndex
        // Android's moveMediaItem never changes what is playing — pure state.
    }

    override fun skipToNext() {
        assertMainThread("skipToNext")
        val q = _queue.value
        if (q.isEmpty()) return
        val next = when {
            _currentIndex.value < q.lastIndex -> _currentIndex.value + 1
            _repeatMode.value >= 1 -> 0
            else -> return
        }
        pushUndoSnapshot(QueueUndoEvent.SkippedToNext)
        _currentIndex.value = next
        // Android: seekTo(next, 0) → transition reconciles + plays.
        transitionTo(next, startPositionMs = 0L)
    }

    override fun skipToPrevious() {
        assertMainThread("skipToPrevious")
        val q = _queue.value
        if (q.isEmpty()) return
        val player = engine ?: return
        if (player.currentPositionMs > skipPreviousThresholdMsInternal) {
            seekTo(0L)
            return
        }
        val prev = when {
            _currentIndex.value > 0 -> _currentIndex.value - 1
            _repeatMode.value >= 1 -> q.lastIndex
            else -> return
        }
        pushUndoSnapshot(QueueUndoEvent.SkippedToPrevious)
        _currentIndex.value = prev
        transitionTo(prev, startPositionMs = 0L)
    }

    override fun toggleShuffle() {
        assertMainThread("toggleShuffle")
        val wasShuffled = _shuffleMode.value
        _shuffleMode.value = !wasShuffled
        // Android parity: `val player = exoPlayer ?: return` right after the
        // flag flip — without a live player only the FLAG changes; the queue
        // order and index are untouched until an engine exists.
        val player = engine ?: return

        if (_shuffleMode.value) {
            val q = _queue.value
            val curIdx = _currentIndex.value
            unshuffledQueue = q
            if (q.size <= 1) return
            val current = q.getOrNull(curIdx)
            val others = q.filterIndexed { i, _ -> i != curIdx }.toMutableList()
            others.shuffle()
            _queue.value = if (current != null) listOf(current) + others else others
            _currentIndex.value = 0
            // Android rebuilds the player playlist at the current position;
            // the desktop keeps the same item playing — state-only.
        } else {
            val currentId = _currentPlayingItemId.value
            val original = unshuffledQueue
            if (original.isNotEmpty()) {
                _queue.value = original
                val restoreIndex = original.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                _currentIndex.value = restoreIndex
                unshuffledQueue = emptyList()
            }
        }
    }

    override fun cycleRepeatMode() {
        assertMainThread("cycleRepeatMode")
        setRepeatMode((_repeatMode.value + 1) % 3)
    }

    override fun setRepeatMode(mode: Int) {
        assertMainThread("setRepeatMode")
        _repeatMode.value = mode.coerceIn(0, 2)
    }

    override fun setShuffleMode(enabled: Boolean) {
        assertMainThread("setShuffleMode")
        if (_shuffleMode.value == enabled) return
        toggleShuffle()
    }

    override fun playFromQueue(index: Int) {
        assertMainThread("playFromQueue")
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        if (index == _currentIndex.value && engine != null) {
            // Android: seekTo(current, 0) restarts the same item, no reload.
            seekTo(0L)
            engine?.play()
            return
        }
        _currentIndex.value = index
        transitionTo(index, startPositionMs = 0L)
    }

    // ── AudioPlayerEngine: transport / metadata ────────────────────────────

    override fun seekTo(positionMs: Long) {
        assertMainThread("seekTo")
        // Optimistic publish — same rationale as Android (the poll loop would
        // otherwise echo the position back up to 250 ms later).
        val clamped = positionMs.coerceAtLeast(0L)
        _currentPosition.value = clamped
        engine?.seekTo(clamped)
    }

    override fun togglePlayPause() {
        assertMainThread("togglePlayPause")
        val player = engine ?: return
        if (player.isPlaying.value) player.pause() else player.play()
    }

    override fun pause() {
        assertMainThread("pause")
        engine?.takeIf { it.isPlaying.value }?.pause()
    }

    override fun changePlaybackSpeed(value: Float) {
        assertMainThread("changePlaybackSpeed")
        _speed.value = value
        engine?.setPlaybackSpeed(value)
        // No crossfader to inform (divergence declared above).
    }

    override fun setSkipPreviousThreshold(ms: Long) {
        skipPreviousThresholdMsInternal = ms
    }

    override fun setCrossfadeDurationMs(ms: Long) {
        // State mirror of Android's flag interplay; no audible crossfade on
        // desktop (declared divergence).
        _crossfadeDurationMs.value = ms
        if (ms > 0) {
            gaplessEnabled = false
        } else {
            gaplessEnabled = true
        }
    }

    override fun setGaplessEnabled(enabled: Boolean) {
        gaplessEnabled = enabled
        if (enabled) {
            _crossfadeDurationMs.value = 0L
        }
    }

    override fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    // ── Lyrics passthrough (shared AudioLyricsManager — real on desktop) ───

    private fun fetchLyrics(
        item: AudioQueueItem,
        durationSecOverride: Double? = null,
    ) {
        lyricsManager.fetchLyrics(
            itemId = item.id,
            artistName = item.artist.takeIf { it.isNotBlank() },
            trackName = item.name,
            durationSec = durationSecOverride
                ?: item.durationMs.takeIf { it > 0 }?.let { it / 1000.0 },
        )
    }

    override fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        lyricsManager.searchLyrics(query, callback)
    }

    override fun applyLyrics(lrcLibId: Long) {
        lyricsManager.applyLyrics(lrcLibId, currentItemId)
    }

    override fun setLyricsOffset(offsetMs: Long) {
        lyricsManager.setLyricsOffset(offsetMs)
    }

    // ── Undo (Android mirror) ──────────────────────────────────────────────

    private fun pushUndoSnapshot(event: QueueUndoEvent) {
        queueUndoStack.push(
            QueueSnapshot(
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                positionMs = engine?.currentPositionMs ?: _currentPosition.value,
            ),
        )
        _undoEvents.tryEmit(event)
    }

    override fun undoLastQueueOperation(): Boolean {
        assertMainThread("undoLastQueueOperation")
        val snapshot = queueUndoStack.pop() ?: return false
        applyQueueSnapshot(snapshot)
        return true
    }

    private fun applyQueueSnapshot(snapshot: QueueSnapshot) {
        _queue.value = snapshot.queue
        _currentIndex.value = snapshot.currentIndex
        // Android: `if (player == null || snapshot.queue.isEmpty()) return` —
        // without a live engine there is NO seek/reconcile pass at all (the IO
        // media-item rebuild never runs either), so the flows land on the
        // snapshot verbatim rather than coercing a snapshotless cursor onto 0.
        if (engine == null || snapshot.queue.isEmpty()) return
        // Android: setMediaItems(snapshot.queue, index, positionMs) — the
        // player jumps to the snapshot's item AT the snapshot's position, and
        // the resulting transition reconciles metadata + server reporting.
        val index = snapshot.currentIndex.coerceIn(0, snapshot.queue.lastIndex)
        transitionTo(index, startPositionMs = snapshot.positionMs)
    }

    // ── A→B loop (Android mirror; only cycleAbLoop is on the seam) ────────

    private fun setAbLoopStart() {
        val pos = engine?.currentPositionMs ?: _currentPosition.value
        _abLoopStartMs.value = pos
        val end = _abLoopEndMs.value
        if (end != null && end <= pos) _abLoopEndMs.value = null
    }

    private fun setAbLoopEnd() {
        val start = _abLoopStartMs.value ?: return
        val pos = engine?.currentPositionMs ?: _currentPosition.value
        if (pos <= start) return
        _abLoopEndMs.value = pos
    }

    private fun clearAbLoop() {
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
    }

    override fun cycleAbLoop() {
        assertMainThread("cycleAbLoop")
        when {
            _abLoopStartMs.value == null -> setAbLoopStart()
            _abLoopEndMs.value == null -> setAbLoopEnd()
            else -> clearAbLoop()
        }
    }

    // ── Position ticker (Android startPositionTracking mirror) ─────────────

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var lastPosition = 0L
            var lastDuration = 0L
            while (true) {
                val e = engine
                if (e == null) {
                    delay(positionPollIntervalMs)
                    continue
                }
                if (!e.isPlaying.value) {
                    delay(positionPausedRecheckMs)
                    continue
                }
                val pos = e.currentPositionMs
                val dur = e.durationMs.coerceAtLeast(0L)
                val abEnd = _abLoopEndMs.value
                val abStart = _abLoopStartMs.value
                if (abEnd != null && abStart != null && pos >= abEnd) {
                    e.seekTo(abStart)
                }
                if (pos != lastPosition) {
                    _currentPosition.value = pos
                    lastPosition = pos
                }
                if (dur != lastDuration) {
                    _duration.value = dur
                    lastDuration = dur
                }
                if (lyricsManager.lyrics.value.isNotEmpty()) {
                    lyricsManager.updateCurrentLyricIndex(_currentPosition.value)
                }
                delay(positionPollIntervalMs)
            }
        }
    }

    // ── Progress reporting (AudioProgressReporter mirror) ──────────────────

    private fun startProgressReporting() {
        progressJob?.cancel()
        lastPausedPositionTicks = -1L
        progressJob = scope.launch {
            while (true) {
                delay(progressReportIntervalMs)
                val e = engine ?: continue
                val itemId = currentItemId ?: continue
                val positionTicks = e.currentPositionMs * 10_000
                val isPaused = !e.isPlaying.value
                if (isPaused && positionTicks == lastPausedPositionTicks) continue
                if (isPaused) lastPausedPositionTicks = positionTicks else lastPausedPositionTicks = -1L
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                    )
                )
            }
        }
    }

    /**
     * Report stop for an explicit previous item/session (transition path).
     *
     * Ordering parity with Android's `AudioProgressReporter.reportStopped`:
     * the stop call is LAUNCHED (never awaited) and the session id rotates
     * SYNCHRONOUSLY, so the start-report that follows a transition always
     * carries the fresh id — a deferred rotation could let it race onto the
     * session the stop just used.
     */
    private fun reportStopped(itemId: String?, sessionId: String, positionTicks: Long) {
        val finalItemId = itemId ?: return
        if (positionTicks > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(finalItemId, sessionId, positionTicks)
            }
        }
        playSessionId = UUID.randomUUID().toString()
    }

    /** Report stop for the CURRENT item, rotating the session id (play path). */
    private fun reportStoppedCurrent() {
        val itemId = currentItemId
        val posTicks = (engine?.currentPositionMs ?: 0L) * 10_000
        reportStopped(itemId, playSessionId, posTicks)
    }

    // ── Teardown (Android stopAndRelease mirror) ───────────────────────────

    override fun stopAndRelease() {
        assertMainThread("stopAndRelease")

        val player = engine
        val itemId = currentItemId
        val sid = playSessionId
        val pos = (player?.currentPositionMs ?: 0L) * 10_000

        positionJob?.cancel()
        progressJob?.cancel()
        engineObserverJobs.forEach { it.cancel() }
        engineObserverJobs = emptyList()
        player?.release()
        engine = null

        currentItemId = null
        _currentPlayingItemId.value = null
        _isPlaying.value = false
        _title.value = ""
        _artist.value = ""
        _artistId.value = null
        _album.value = ""
        _albumArtUrl.value = ""
        _currentPosition.value = 0L
        _duration.value = 0L
        lyricsManager.reset()
        playSessionId = UUID.randomUUID().toString()

        if (player != null && itemId != null && pos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sid, pos)
            }
        }
    }
}
