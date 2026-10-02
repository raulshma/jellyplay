package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Minimum resolved duration (ms) before smart-download auto-cleanup may fire. */
private const val MIN_DURATION_FOR_SMART_DELETE_MS = 5 * 60 * 1000L

/**
 * Owns the episode-continuation cluster extracted from [VideoPlayerViewModel]:
 * the [EpisodeNavigator] stack (season/episode browsing, adjacent discovery,
 * previous/next choreography with the #146 single-flight latch), the
 * "mark watched & skip" / "mark unwatched & quit" overflow orchestration, the
 * autoplay-cancel wiring behind the Up Next overlay, the overlay's
 * next-episode loading flag, and the smart-download cleanup.
 *
 * The [SubtitleStyleController] shape: every fact the moved logic read from
 * the VM arrives as a narrow constructor lambda — NO raw uiState handle
 * crosses (the god-count ratchet stays at its baseline; this class never
 * references [VideoPlayerUiState]). The episode-browsing slice stays a stored
 * slice of the uiState; the navigator remains its single writer through the
 * injected [updateEpisodes] seam, and the mark/advance effects stay the
 * caller-supplied verbs they were before the move.
 *
 * Load-bearing invariants (pinned by [EpisodeContinuationControllerTest]):
 *  - the mark arms mirror the watched-threshold callback exactly — incognito
 *    routes the watched mark to the offline-local record and skips the
 *    unwatched mark entirely ([decideWatchedActions]);
 *  - "mark watched & skip" reuses [playNextEpisode], so the SyncPlay
 *    group-queue routing and the #146 single-flight latch apply unchanged;
 *  - the autoplay cancel flips the decision clock AND the overlay mirror in
 *    that order, so a cancelled countdown can never auto-advance.
 */
internal class EpisodeContinuationController(
    private val scope: CoroutineScope,
    // ── The navigator stack (the controller owns the EpisodeNavigator) ─────
    private val sessionState: StateFlow<PlayerSessionState>,
    private val sessionEvents: SharedFlow<SessionEvent>,
    private val episodeCatalogue: EpisodeCatalogue,
    /** The resolved media detail of the playing item (the VM's mediaDetail field). */
    private val getDetail: () -> MediaDetail?,
    /** The browsing anchor series (detail's series, else the media mirror's). */
    private val getSeriesId: () -> String?,
    /** The episode-browsing slice write (the navigator stays the slice's single writer). */
    private val updateEpisodes: (((EpisodeBrowserState) -> EpisodeBrowserState) -> Unit),
    /** Starts playback of an item at a tick position (the VM's initialize funnel). */
    private val initializeItem: (itemId: String, startPositionTicks: Long) -> Unit,
    /** Surfaces a failed next-episode resolution to the user. */
    private val reportLoadError: suspend () -> Unit,
    // ── SyncPlay group routing ([routeSyncPlayAdvance]'s reads and sends) ──
    /** Whether a SyncPlay group session is live (the manager's flag, not the uiState mirror). */
    private val isInSyncPlayGroup: () -> Boolean,
    /** The live group snapshot (queue map + currently-playing playlist entry). */
    private val getCurrentGroup: () -> SyncPlayGroup?,
    /** Sends the group's next-item command for the currently-playing queue entry. */
    private val sendNextItem: (currentPlaylistItemId: String) -> Unit,
    /** Sends the group's previous-item command for the currently-playing queue entry. */
    private val sendPreviousItem: (currentPlaylistItemId: String) -> Unit,
    // ── The mark arms (the two paths the watched-threshold callback uses) ──
    /** The incognito gate (routes marks away from the server/outbox). */
    private val isIncognito: () -> Boolean,
    /** Server-side played write ([UserDataMutator.setPlayed] — PlayedStateSync fan-out + outbox). */
    private val markPlayed: suspend (itemId: String) -> Unit,
    /** Local-only offline played mark ([OfflinePlaybackFacade.recordPlayed]). */
    private val recordPlayedOffline: suspend (itemId: String) -> Unit,
    /** Server-side played-flag clear (the "mark unwatched" write). */
    private val markUnwatched: suspend (itemId: String) -> Unit,
    // ── Session + ui reads/writes the orchestration shares ─────────────────
    /** The session's current item id (the mark's target). */
    private val getCurrentItemId: () -> String?,
    /** Whether the episode slice knows a next episode (the advance-vs-close input). */
    private val hasNextEpisode: () -> Boolean,
    /** The uiState's SyncPlay mirror (the mark decision's SyncPlay input). */
    private val isInSyncPlaySession: () -> Boolean,
    /** Closes the player (the no-next exit arm of both overflow actions). */
    private val closePlayer: () -> Unit,
    // ── Autoplay-cancel wiring ─────────────────────────────────────────────
    /** Cancels the autoplay decision clock ([AutoPlayController.cancel]). */
    private val cancelAutoplayDecision: () -> Unit,
    /** Writes the Up Next overlay's `autoplayCancelled` mirror. */
    private val setAutoplayCancelledMirror: (Boolean) -> Unit,
    // ── Smart-download cleanup ─────────────────────────────────────────────
    /** The `smartDownloadsEnabled` preference gate. */
    private val isSmartDownloadsEnabled: () -> Boolean,
    /** The resolved playback duration in ms (the premature-delete gate). */
    private val getDurationMs: () -> Long,
    /** Deletes the item's download ([OfflinePlaybackFacade.deleteDownload]); true when deleted. */
    private val deleteDownload: suspend (itemId: String) -> Boolean,
    /** Surfaces the destructive action to the user instead of deleting invisibly. */
    private val notifySmartDownloadDeleted: () -> Unit,
) {

    /**
     * The navigation module (season/episode browsing state, adjacent-episode
     * discovery, and the previous/next choreography) — constructed here with
     * the seams above; the verbs below are its funnels.
     */
    private val navigator = EpisodeNavigator(
        scope = scope,
        sessionState = sessionState,
        sessionEvents = sessionEvents,
        getDetail = getDetail,
        getSeriesId = getSeriesId,
        episodeCatalogue = episodeCatalogue,
        trySyncPlayNext = { nextItemId ->
            routeSyncPlayAdvance(nextItemId, sendNextItem)
        },
        trySyncPlayPrevious = { previousItemId ->
            routeSyncPlayAdvance(previousItemId, sendPreviousItem)
        },
        onAdvanceFrom = { currentItemId ->
            if (!isIncognito()) {
                runCatchingRethrowingCancellation { markPlayed(currentItemId) }
            }
        },
        reportLoadError = reportLoadError,
        initializeItem = initializeItem,
        updateEpisodes = updateEpisodes,
    )

    /** True while a next-episode advance is in flight and unsettled (#146). */
    val isNextEpisodeLoading: StateFlow<Boolean> get() = navigator.isNextEpisodeLoading

    /**
     * The group-queue check the next/previous advance lambdas share: when the
     * SyncPlay group's queue holds the sibling item, the advance goes through
     * the group command ([send] receives the currently-playing queue entry)
     * and returns true; false falls back to a local reload.
     */
    private fun routeSyncPlayAdvance(
        siblingItemId: String,
        send: (currentPlaylistItemId: String) -> Unit,
    ): Boolean {
        if (!isInSyncPlayGroup()) return false
        val group = getCurrentGroup()
        val currentPlaylistItemId = group?.playingPlaylistItemId
        val siblingInQueue = group?.playlistItemMap?.values?.contains(siblingItemId) == true
        if (currentPlaylistItemId != null && siblingInQueue) {
            send(currentPlaylistItemId)
            return true
        }
        return false
    }

    // ── Navigator funnels (the screen, the PiP transport, the session hooks) ──

    /** Loads one season's episode list (episode sheet season click). */
    fun loadSeason(seasonId: String) = navigator.loadSeason(seasonId)

    /** Starts playback of a picked episode at its saved position. */
    fun playEpisode(episodeId: String, startPositionTicks: Long = 0L) {
        initializeItem(episodeId, startPositionTicks)
    }

    fun playPreviousEpisode() = navigator.previous()

    fun playNextEpisode() = navigator.next()

    /** Writes the adjacent-episode snapshot (session-load + mini-player hydration hook). */
    fun refreshAdjacent(detail: MediaDetail) = navigator.refreshAdjacent(detail)

    /** Loads the series' season list + current season (episode sheet entry point). */
    fun loadSeries(detail: MediaDetail) = navigator.loadSeries(detail)

    /** Adopts a freshly-bound item's season as the browsing selection. */
    fun adoptSeasonOf(detail: MediaDetail) = navigator.adoptSeasonOf(detail)

    /** Resets the browsing slice for an item switch (adjacency + lists are per-item). */
    fun resetForItemSwitch() = navigator.resetForItemSwitch()

    // ── Mark-watched-and-skip / mark-unwatched-and-quit ────────────────────

    /**
     * "Mark watched & skip": marks the current item played through
     * the SAME mutation path the watched-threshold callback uses —
     * `UserDataMutator.setPlayed` (PlayedStateSync fan-out + self-invalidation;
     * an offline session lands in the playback outbox), or the local-only
     * offline mark in incognito ([OfflinePlaybackFacade.recordPlayed], the
     * threshold callback's incognito arm). `SeenMediaRepository` is
     * notification de-dup and is deliberately not consulted. Then advances to
     * the next episode (reusing [playNextEpisode] so the SyncPlay group-queue
     * routing and the #146 single-flight latch apply unchanged), or closes
     * the player when there is no next. The advance itself marks played again
     * (`EpisodeNavigator.onAdvanceFrom`) — idempotent server-side, and it is
     * what covers the episode BEFORE the threshold callback would.
     *
     * NonCancellable: the player may tear down immediately after the advance
     * close, exactly when the mark must survive (same reasoning as the
     * threshold callback's launch).
     */
    fun markWatchedAndSkip() {
        val decision = decideWatchedActions(
            hasNext = hasNextEpisode(),
            incognito = isIncognito(),
            isInSyncPlay = isInSyncPlaySession(),
        )
        val itemId = getCurrentItemId()
        if (itemId != null) {
            scope.launch(NonCancellable) {
                when (decision.watchedMarkPath) {
                    WatchedMarkPath.SERVER ->
                        runCatchingRethrowingCancellation { markPlayed(itemId) }
                    WatchedMarkPath.OFFLINE_LOCAL ->
                        runCatchingRethrowingCancellation { recordPlayedOffline(itemId) }
                }
            }
        }
        if (decision.watchedAdvancesToNext) {
            playNextEpisode()
        } else {
            closePlayer()
        }
    }

    /**
     * "Mark unwatched & exit": clears the played flag through
     * `UserDataMutator.setPlayed` (server/outbox path — incognito is a no-op
     * mark by design, the decision helper says so) and closes the player. The
     * unwatching is the feature's point: the threshold callback or an
     * auto-advance may have marked the item while the user watched something
     * else in it.
     */
    fun markUnwatchedAndQuit() {
        val decision = decideWatchedActions(
            hasNext = hasNextEpisode(),
            incognito = isIncognito(),
            isInSyncPlay = isInSyncPlaySession(),
        )
        val itemId = getCurrentItemId()
        if (decision.unwatchedMarkApplied && itemId != null) {
            scope.launch(NonCancellable) {
                runCatchingRethrowingCancellation { markUnwatched(itemId) }
            }
        }
        closePlayer()
    }

    // ── Autoplay-cancel wiring ─────────────────────────────────────────────

    /** Cancels the pending autoplay countdown (the Up Next overlay's cancel). */
    fun cancelAutoplay() {
        cancelAutoplayDecision()
        setAutoplayCancelledMirror(true)
    }

    // ── Smart-download cleanup ─────────────────────────────────────────────

    /**
     * Auto-removes a finished download when the user crosses the watched
     * threshold, gated by the `smartDownloadsEnabled` preference.
     *
     * Guards against the two risks flagged in the architecture analysis:
     * - *Premature delete on misreported duration*: the reporter derives
     * "95% watched" from `position / duration`. A live stream or a buggy
     * container can report a tiny/growing duration and trip the threshold
     * almost immediately. We require the resolved duration to be at least
     * [MIN_DURATION_FOR_SMART_DELETE_MS] before deleting.
     * - *Silent destructive action*: the deletion is now surfaced to the
     * user instead of happening invisibly.
     */
    fun handleSmartDownloadCleanup(itemId: String) {
        if (!isSmartDownloadsEnabled()) return
        if (getDurationMs() < MIN_DURATION_FOR_SMART_DELETE_MS) return
        scope.launch {
            if (!deleteDownload(itemId)) return@launch
            notifySmartDownloadDeleted()
        }
    }
}
