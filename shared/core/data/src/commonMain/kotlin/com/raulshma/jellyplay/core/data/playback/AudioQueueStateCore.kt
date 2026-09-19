package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Deep module for the audio QUEUE-STATE CHASSIS — the ONE owner of the
 * state machine the desktop `DesktopAudioQueueManager` (core:data jvmMain)
 * previously inlined: the eleven playback [MutableStateFlow]s with their
 * Android-identical initial values, the [QueueUndoStack] wiring +
 * [QueueUndoEvent] publication, the advance/retreat/wrap/shuffle/restart
 * selection through [AudioQueuePolicy], and the item-transition
 * choreography (cursor write → [NowPlayingTracker] queue-item publish →
 * stop(prev) report → lyrics fetch → start(next) report → engine prepare
 * through [EngineDispatch]). The manager keeps what is genuinely
 * platform-side: engine lifecycle + observers (feeding [onEnginePlayingChanged]/
 * [onEngineError]/[onEngineEnded]), per-item resolution + next-item
 * prefetch (behind [EngineDispatch.prepare]), the effects push seam, the
 * A-B loop markers, the position ticker, queue persistence and teardown.
 *
 * This is the same fold shape the effects half already has
 * ([AudioEffectsStateCore]: state machine in commonMain, platform halves
 * carrying only their DSP/engine pushes) — COMPOSITION over the
 * [EngineDispatch] port rather than inheritance, because the queue
 * chassis's writes interleave with adapter-owned machinery (prefetch
 * invalidation, focus edges, effects context) that must stay
 * behind lambdas, not template hooks.
 *
 * ## Declared divergence — Android is NOT an adopter (next-slice note)
 *
 * `AudioPlaybackManager` (androidMain) keeps its inlined chassis. Its
 * mutation sites are not port-shaped: each one interleaves
 * playlist-owning media3 writes (`seekTo(next, 0)`, `moveMediaItem`,
 * `clearMediaItems` — the PLAYER owns the playlist there, the list here),
 * `queueLoadingJob` bail-outs during whole-queue MediaItem pre-warms, a
 * real crossfader and Play-On remote routing into the same bodies. Routing
 * those through this core would be a behavior redesign, not a fold — the
 * recorded reason this slice lands desktop-first (CONTEXT.md "audio queue
 * chassis": the Android manager deserves its own verified session). The
 * seams the core exposes (flows by reference, policy decisions, the
 * transition choreography) are the same ones an Android adoption would
 * need; nothing here is desktop-specific.
 *
 * ## Desktop divergences encoded here (manager-KDoc table still applies)
 *
 *  - No `queueLoadingJob` guards — desktop's declared "pre-warm is
 *    next-item-only" divergence retired them; mutations are never blocked
 *    by a queue build.
 *  - [onQueueShapeInvalidated] fires where the desktop cleared its
 *    next-item prefetch; Android has no equivalent (no-op there).
 *  - The play session id is the stdlib multiplatform [Uuid] v4 string —
 *    the exact swap [AudioProgressReporter] made when it promoted to
 *    commonMain (identical session-id shape).
 *
 * Main-thread confined by contract (the [AudioQueueManager] thread
 * contract — same as [QueueUndoStack]); the adapter asserts its platform
 * main thread at every public entry and calls these bodies only from
 * there. Not thread-safe, by the same contract.
 */
@OptIn(ExperimentalUuidApi::class)
class AudioQueueStateCore(
    /** Launch target for the transition choreography's reporting block. */
    private val scope: CoroutineScope,
    /** start(next) reports ride it directly (stop(prev) rides [progressReporter]). */
    private val playbackRepository: PlaybackRepository,
    /** Transition-time lyrics fetch ([fetchLyrics] owns the mapping). */
    private val lyricsManager: AudioLyricsManager,
    /** stop(prev) reporting + synchronous session-id rotation on transitions. */
    private val progressReporter: AudioProgressReporter,
    /** The engine-command port — this core's only engine touch. */
    private val dispatch: EngineDispatch,
    /**
     * Live engine position, or null with no engine — the undo-snapshot and
     * skip-previous-restart reads (`engine?.currentPositionMs ?: published`).
     */
    private val enginePositionMs: () -> Long? = { null },
    /**
     * A queue SHAPE change landed (playQueue / append / remove / clear /
     * move / repeat flip / shuffle / undo restore): the desktop adapter
     * cancels its next-item prefetch here. Android-shaped adapters leave
     * the default.
     */
    private val onQueueShapeInvalidated: () -> Unit = {},
    /**
     * End of queue under RepeatNone — the adapter's end-of-episode hook
     * (desktop: sleep timer; Android: the same hook plus widget pushes).
     */
    private val onQueueExhausted: () -> Unit = {},
    /**
     * The shuffle FLAG flipped (after the write, before the reorder gate) —
     * the desktop adapter re-applies the current track's ReplayGain context
     * under the new order; Android passes fresh context at its apply sites
     * instead and leaves the default.
     */
    private val onShuffleModeChanged: () -> Unit = {},
    /**
     * playQueue reached its start item — the adapter's full user-play path
     * (resolve, detail publish, ticker/reporter start). Split out because
     * the play path is the most platform-divergent surface (desktop's
     * resolver + prefetch vs Android's browser + pre-warm).
     */
    private val onPlayRequested: (itemId: String) -> Unit = {},
) {

    // ── State cells: initial values identical to both twins' managers ──────
    // internal: same-module adopters (the jvmMain manager + jvmTest suites)
    // write through the cells where a choreography STAYS adapter-side
    // (start()'s persistence restore, play()'s detail path, the ticker);
    // everyone else reads the read-only flows below.

    internal val _queue = MutableStateFlow<List<AudioQueueItem>>(emptyList())
    val queue: StateFlow<List<AudioQueueItem>> = _queue.asStateFlow()

    internal val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    internal val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    internal val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    internal val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    internal val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    internal val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    internal val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    internal val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    internal val _isLoadingItem = MutableStateFlow(false)
    val isLoadingItem: StateFlow<Boolean> = _isLoadingItem.asStateFlow()

    internal val _crossfadeDurationMs = MutableStateFlow(0L)
    val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private val _undoEvents = MutableSharedFlow<QueueUndoEvent>(extraBufferCapacity = 4)
    val undoEvents: SharedFlow<QueueUndoEvent> = _undoEvents.asSharedFlow()

    /**
     * Sole writer of the now-playing metadata — re-exposed (by reference,
     * through the adapter) exactly as both managers always did. The
     * transition path publishes via [NowPlayingTracker.publishQueueItem];
     * the adapter's play() detail path owns the one all-six-fields
     * [NowPlayingTracker.publishDetail] site.
     */
    internal val nowPlayingTracker = NowPlayingTracker()

    // ── Session cells (adapter reads/writes through these) ────────────────

    /**
     * The item the session is logically on (null = nothing claimed). Written
     * by the transition choreography and the adapter's play() path; read by
     * the adapter's lyrics apply + the progress reporter's providers.
     */
    internal var currentItemId: String? = null

    /**
     * Jellyfin play session id — rotated SYNCHRONOUSLY by the reporter's
     * stop paths, exactly like both managers' hand-rolled tails did.
     */
    internal var playSessionId: String = Uuid.random().toString()

    /** Skip-previous restart threshold (injectable; [AudioQueuePolicy] default). */
    internal var skipPreviousThresholdMs: Long = AudioQueuePolicy.SKIP_PREVIOUS_RESTART_THRESHOLD_MS

    // ── Private chassis cells ───────────────────────────────────────────────

    private val queueUndoStack = QueueUndoStack()

    private var unshuffledQueue: List<AudioQueueItem> = emptyList()

    // ── Queue mutations (the AudioQueueManager choreography) ───────────────

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int) {
        // A fresh queue invalidates any undo history from the previous queue.
        queueUndoStack.clear()
        onQueueShapeInvalidated()
        _queue.value = items
        _currentIndex.value = startIndex
        val item = items.getOrNull(startIndex) ?: return
        onPlayRequested(item.id)
    }

    fun addToQueue(item: AudioQueueItem) {
        onQueueShapeInvalidated()
        _queue.value = _queue.value + item
        // The engine append is playlist plumbing on Android; on desktop the
        // queue list is the single truth, so there is nothing else to do.
    }

    fun addToQueueAll(items: List<AudioQueueItem>) {
        if (items.isEmpty()) return
        onQueueShapeInvalidated()
        _queue.value = _queue.value + items
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        val removed = q[index]
        pushUndoSnapshot(QueueUndoEvent.ItemRemoved(removed))
        onQueueShapeInvalidated()
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
                dispatch.stop()
                _isPlaying.value = false
            }
        } else if (index < _currentIndex.value) {
            _currentIndex.value -= 1
        }
    }

    fun clearQueue() {
        if (_queue.value.isEmpty()) return
        onQueueShapeInvalidated()
        pushUndoSnapshot(QueueUndoEvent.QueueCleared)
        _queue.value = emptyList()
        _currentIndex.value = -1
        // Android: clearMediaItems parks the player idle; metadata is kept.
        dispatch.stop()
        _isPlaying.value = false
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        // Pure policy: bounds/no-op rejection, the reorder and the cursor
        // remap in one decision (never changes what is playing — pure state).
        val plan = AudioQueuePolicy.planMove(_queue.value, _currentIndex.value, fromIndex, toIndex) ?: return
        pushUndoSnapshot(QueueUndoEvent.ItemMoved(plan.movedItem))
        onQueueShapeInvalidated()
        _queue.value = plan.queue
        _currentIndex.value = plan.currentIndex
    }

    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        // The advance/wrap rule (null = blocked: no undo snapshot, no
        // transition).
        val next = AudioQueuePolicy.nextIndex(_currentIndex.value, q.size, _repeatMode.value) ?: return
        pushUndoSnapshot(QueueUndoEvent.SkippedToNext)
        _currentIndex.value = next
        // Android: seekTo(next, 0) → transition reconciles + plays.
        transitionTo(next, startPositionMs = 0L)
    }

    fun skipToPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (!dispatch.isLive) return
        // Shared restart threshold (strictly >): seek the CURRENT item to
        // zero, no cursor move, no undo snapshot.
        if (AudioQueuePolicy.skipsPreviousRestart(enginePositionMs() ?: 0L, skipPreviousThresholdMs)) {
            seekTo(0L)
            return
        }
        val prev = AudioQueuePolicy.previousIndex(_currentIndex.value, q.size, _repeatMode.value) ?: return
        pushUndoSnapshot(QueueUndoEvent.SkippedToPrevious)
        _currentIndex.value = prev
        transitionTo(prev, startPositionMs = 0L)
    }

    fun toggleShuffle() {
        val wasShuffled = _shuffleMode.value
        _shuffleMode.value = !wasShuffled
        // The ReplayGain ALBUM rule reads the shuffle flag — the adapter
        // re-applies the current track's context so the mode's zeroing
        // tracks the new order (Android passes isShuffled fresh into
        // applyReplayGain at every apply site instead).
        onShuffleModeChanged()
        // Android parity: `val player = exoPlayer ?: return` right after the
        // flag flip — without a live player only the FLAG changes; the queue
        // order and index are untouched until an engine exists.
        if (!dispatch.isLive) return
        onQueueShapeInvalidated()

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
            val currentId = nowPlayingTracker.currentPlayingItemId.value
            val original = unshuffledQueue
            if (original.isNotEmpty()) {
                _queue.value = original
                val restoreIndex = original.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                // Known micro-divergence from Android: when the CURRENT item
                // was appended while shuffled (currentId absent from the
                // original order), legacy rebuilds the player playlist and
                // audibly switches to row 0 of the restored order, while this
                // chassis keeps the appended item playing under a cursor that
                // points at index 0. Index/data parity holds either way.
                _currentIndex.value = restoreIndex
                unshuffledQueue = emptyList()
            }
        }
    }

    fun cycleRepeatMode() {
        setRepeatMode((_repeatMode.value + 1) % 3)
    }

    fun setRepeatMode(mode: Int) {
        _repeatMode.value = mode.coerceIn(0, 2)
        // Repeat mode decides which row auto-advance plays next.
        onQueueShapeInvalidated()
    }

    fun setShuffleMode(enabled: Boolean) {
        if (_shuffleMode.value == enabled) return
        toggleShuffle()
    }

    fun playFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        if (index == _currentIndex.value && dispatch.isLive) {
            // Android: seekTo(current, 0) restarts the same item, no reload.
            seekTo(0L)
            dispatch.play()
            return
        }
        _currentIndex.value = index
        transitionTo(index, startPositionMs = 0L)
    }

    // ── Transport chassis ───────────────────────────────────────────────────

    fun seekTo(positionMs: Long) {
        // Optimistic publish — same rationale as Android (the poll loop would
        // otherwise echo the position back up to 250 ms later).
        val clamped = positionMs.coerceAtLeast(0L)
        _currentPosition.value = clamped
        dispatch.seekTo(clamped)
    }

    fun changePlaybackSpeed(value: Float) {
        _speed.value = value
        dispatch.setPlaybackSpeed(value)
        // No crossfader to inform (desktop declared divergence; Android's
        // twin informs its crossfader adapter-side).
    }

    // ── Undo (QueueSnapshot publication) ───────────────────────────────────

    fun undoLastQueueOperation(): Boolean {
        val snapshot = queueUndoStack.pop() ?: return false
        applyQueueSnapshot(snapshot)
        return true
    }

    private fun pushUndoSnapshot(event: QueueUndoEvent) {
        queueUndoStack.push(
            QueueSnapshot(
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                positionMs = enginePositionMs() ?: _currentPosition.value,
            ),
        )
        _undoEvents.tryEmit(event)
    }

    private fun applyQueueSnapshot(snapshot: QueueSnapshot) {
        onQueueShapeInvalidated()
        _queue.value = snapshot.queue
        _currentIndex.value = snapshot.currentIndex
        // Android: `if (player == null || snapshot.queue.isEmpty()) return` —
        // without a live engine there is NO seek/reconcile pass at all (the IO
        // media-item rebuild never runs either), so the flows land on the
        // snapshot verbatim rather than coercing a snapshotless cursor onto 0.
        if (!dispatch.isLive || snapshot.queue.isEmpty()) return
        // Android: setMediaItems(snapshot.queue, index, positionMs) — the
        // player jumps to the snapshot's item AT the snapshot's position, and
        // the resulting transition reconciles metadata + server reporting.
        val index = snapshot.currentIndex.coerceIn(0, snapshot.queue.lastIndex)
        transitionTo(index, startPositionMs = snapshot.positionMs)
    }

    // ── Engine event callbacks (adapter's observers feed these) ────────────

    /** The engine's isPlaying edge — the chassis mirrors it onto the flow. */
    fun onEnginePlayingChanged(playing: Boolean) {
        _isPlaying.value = playing
    }

    /** The engine's error surface (load + runtime errors share the flow). */
    fun onEngineError(message: String?) {
        _playbackError.value = message
    }

    /**
     * Track end. Android: under repeat ≥ 1 the player never reaches ENDED
     * (ALL wraps, ONE replays); mid-queue advances are ordinary transitions.
     * Desktop: the same outcomes, driven from the single-item engine's ENDED.
     * The advance/wrap decision is [AudioQueuePolicy.nextIndex].
     */
    fun onEngineEnded() {
        val q = _queue.value
        if (_repeatMode.value == AudioQueuePolicy.REPEAT_ONE) {
            // RepeatOne: replay the same item — play() from ENDED seeks back
            // to 0 and unpauses (the keep-open replay path).
            dispatch.play()
            return
        }
        val next = AudioQueuePolicy.nextIndex(_currentIndex.value, q.size, _repeatMode.value)
        if (next != null) {
            transitionTo(next, startPositionMs = 0L)
        } else {
            // End of queue under RepeatNone — Android's STATE_ENDED path:
            // isPlaying off, index stays on the ended item, metadata kept.
            _isPlaying.value = false
            onQueueExhausted()
        }
    }

    // ── Item-transition choreography ───────────────────────────────────────

    /**
     * The `onTrackTransitioned` mirror: reconcile index + metadata from the
     * queue item, report stop(prev) + start(next) to the server, fetch
     * lyrics, then hand the item to the adapter's [EngineDispatch.prepare].
     *
     * With no live engine this only updates the index — the exact Android
     * shape, where a null player means the seek never happens, so no player
     * transition (and therefore no metadata reconciliation) fires either.
     */
    private fun transitionTo(index: Int, startPositionMs: Long = 0L) {
        val item = _queue.value.getOrNull(index) ?: return
        _currentIndex.value = index
        if (!dispatch.isLive) return

        val prevItemId = currentItemId
        val prevSessionId = playSessionId
        val prevPosTicks =
            if (_currentPosition.value > 0) _currentPosition.value * 10_000
            else _duration.value * 10_000

        currentItemId = item.id
        // Queue-item publish shape: five fields from the queue item, artistId
        // deliberately untouched (AudioQueueItem carries none — the tracker's
        // recorded divergence).
        nowPlayingTracker.publishQueueItem(item)

        scope.launch {
            progressReporter.reportStopped(prevItemId, prevSessionId, prevPosTicks)
            fetchLyrics(item)
            playbackRepository.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = item.id,
                    sessionId = playSessionId,
                    mediaSourceId = item.mediaSourceId,
                )
            )
        }
        dispatch.prepare(item, startPositionMs)
    }

    // ── Adapter-facing helpers (same-module choreography stays adapter-side) ─

    /** The row under the cursor, or null (nothing playing / cursor parked). */
    internal fun currentItemOrNull(): AudioQueueItem? = _queue.value.getOrNull(_currentIndex.value)

    /**
     * The out-of-queue play() append: the freshly-played item joins the tail
     * and the cursor jumps to it (the adapter's detail path builds the row).
     */
    internal fun appendPlayedItem(queueItem: AudioQueueItem) {
        _queue.value = _queue.value + queueItem
        _currentIndex.value = _queue.value.lastIndex
    }

    /**
     * Lyrics fetch for a queue item (the adapter's play() path passes the
     * resolved-track duration override; transitions pass none and fall back
     * to the row's duration).
     */
    internal fun fetchLyrics(
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

    /**
     * Teardown display reset (the adapter calls it after releasing the
     * engine, before its lyrics reset): clears the session's item claim and
     * the five tracker display fields — artistId deliberately survives (the
     * tracker's recorded divergence — Android's stop behaved identically
     * pre-extraction) — then parks the playback display flows.
     */
    internal fun onEngineReleased() {
        currentItemId = null
        nowPlayingTracker.clear()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
}
