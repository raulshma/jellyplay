package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * One playback session's lifecycle state — the "deep module" shell being
 * extracted from [VideoPlayerViewModel] (Stage B of the video-player
 * refactor).
 *
 * Step B1a moves ONLY the session-scoped latches and bookkeeping fields; the
 * methods that read and write them (initialize, release/performRelease, seek
 * persistence, stop-reporting, the coalesced seek-mirror write) still live on
 * the ViewModel and reach through this class. Later steps (B1b–B4) move
 * those behaviors in; until then this is a pure field move with zero behavior
 * change.
 *
 * Construction contract:
 * - the ViewModel's [CoroutineScope] is INJECTED, never constructed here.
 *   Session-launched coroutines (e.g. the coalesced seek-mirror write tracked
 *   by [pendingSeekProgressJob]) keep launching on that scope — never on
 *   [releaseScope] and never on a session-internal scope cancelled in a
 *   future release(), because the onDispose teardown path joins the pending
 *   seek job and depends on those launch semantics;
 * - [PlayerSessionManager] and [PlaybackProgressReporter] are injected as
 *   already-constructed instances. The reporter keeps being built inside the
 *   ViewModel (its ui-state handle wiring stays VM-side by design) and is
 *   handed over here as an object.
 */
internal class PlaybackSession(
    val scope: CoroutineScope,
    val playerSessionManager: PlayerSessionManager,
    val progressReporter: PlaybackProgressReporter,
) {

    // @Volatile: set in the VM's release()/performRelease() (off Main) and
    // read in initializeInternal's early-bail + decision guards.
    @Volatile
    internal var released: Boolean = false

    /**
     * Dedup guard for Stop reports. Two release paths can fire for the same
     * session — reportCurrentPlaybackStopped (transcode fallback,
     * end-of-item) and the final teardown in performRelease. Without this
     * guard the server receives a duplicate Stop for the same play-session
     * id, which can mark the item more-watched than reality and trigger
     * duplicate resume rows. Keyed by sessionId so a new load (new session)
     * clears the latch.
     */
    @Volatile
    internal var stopReportedForSession: String? = null

    /** Position (ms) of the last explicit seek; feeds getReportPositionMs. */
    internal var lastSeekPositionMs: Long? = null

    /** Wall clock of the last explicit seek; bounds the seek-latch's validity. */
    internal var lastSeekTimestamp: Long = 0L

    /**
     * Last position (ms) written to the process-death persistence; used to
     * throttle writes.
     */
    internal var lastPersistedPositionMs: Long = Long.MIN_VALUE

    /**
     * Locally-allocated UUID play-session id — the fallback used until (and
     * unless) the server issues its own id through the PlaybackInfo endpoint
     * (see [PlayerSessionState.playSessionId] on the session manager).
     */
    internal var playSessionId: String = java.util.UUID.randomUUID().toString()

    /**
     * Single-flight coalescing job for the offline-mirror DB write during
     * seek scrubbing; cancelled + relaunched per seek. Must keep launching on
     * the ViewModel-supplied [scope] — the teardown path joins this job after
     * cancelling the viewModelScope.
     */
    internal var pendingSeekProgressJob: Job? = null

    /**
     * In-flight media-load coroutine, so a new initialize call can cancel the
     * previous one before launching its own — prevents overlapping
     * network/teardown side effects when a SyncPlay load event races a user
     * navigation.
     */
    internal var loadJob: Job? = null

    /**
     * Scope for teardown work that must outlive the viewModelScope on clear()
     * (the final stop-report and the pending-seek join): IO dispatcher +
     * supervisor so one failing write cannot cancel the other. The VM cancels
     * it from onCleared.
     */
    internal val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/**
 * Event surface a [PlaybackSession] will expose to the ViewModel once the
 * behaviors move in (B2+): the VM stays the single forwarder, mapping each
 * event into its existing sinks (the close-player channel, the uiState error
 * fields, the user-message bus, the pass-out event channel). Declared now so
 * later steps emit against a stable shape; nothing emits yet.
 */
sealed interface SessionEvent {
    /** A playback error to surface in the player's error dialog. */
    data class ShowError(val error: String, val retryable: Boolean) : SessionEvent

    /** A transient informational message for the user. */
    data class InformUser(val message: String) : SessionEvent

    /** Media playback reached its end (autoplay/close policy stays VM-side). */
    data object PlaybackEnded : SessionEvent

    /** The session asks the player screen to close. */
    data object ClosePlayerRequested : SessionEvent

    /** Pass-out protection triggered a pause. */
    data object PassOutPause : SessionEvent
}

/**
 * Narrow persistence seam for the session's resume position: the four
 * SavedStateHandle keys (item id, position, play-session id, persisted-at
 * epoch) behind read accessors, so the session can persist and restore a
 * process-death resume position without touching the handle type.
 *
 * B1a declares the type only; the SavedStateHandle-backed implementation and
 * its wiring (seekTo's persist half, persistPlaybackPosition, the
 * process-death restore) move behind it at B3.
 */
interface SessionPositionStore {
    fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long)
    fun savedItemId(): String?
    fun savedPositionMs(): Long?
    fun savedPersistedAtMs(): Long?
    fun savedPlaySessionId(): String?
}
