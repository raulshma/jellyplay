package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Minimum resolved duration (ms) before smart-download auto-cleanup may fire. */
private const val MIN_DURATION_FOR_SMART_DELETE_MS = 5 * 60 * 1000L

/** Debug tag for the queue-first advance — one grep-able trace across the player. */
private const val QUEUE_ADVANCE_LOG_TAG = "UpNextQueue"

/**
 * The Up Next card projection: what the overlay renders as "up next", resolved
 * ONCE with the same precedence the advance applies. The queue head wins over
 * the episode sibling — a queued movie is the headline case (it has no
 * sibling, yet the old display read `episode?.name ?: queued?.title`, which
 * shows nothing for it and shows the SIBLING's name when both exist while the
 * advance plays the head).
 *
 * Display fields only ([VideoQueueItem]'s shape): playback always goes through
 * the item id. `seasonNumber`/`episodeNumber` ride along so the episode card
 * keeps its "S1 · E2" subtitle line; `imageUrl` carries the queue row's own
 * artwork (an episode card's artwork is resolved from [itemId] by the screen,
 * which owns the image-url seam).
 */
@Immutable
data class UpNextCard(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val source: Source,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    /** Where the card's item came from — mirrors the advance's precedence ladder. */
    enum class Source {
        /** The manual up-next queue's head (wins over the episode sibling). */
        QUEUE,

        /** The navigator's adjacent-episode sibling. */
        EPISODE,
    }
}

/**
 * The Up Next module extracted from [VideoPlayerViewModel] (glossary:
 * "Up Next" — the whole what-plays-next surface): the [EpisodeNavigator] stack
 * (season/episode browsing, adjacent discovery, previous/next choreography
 * with the #146 single-flight latch), the QUEUE-FIRST advance, the manual
 * queue-row activation, the card + has-queued-next projections, the
 * "mark watched & skip" / "mark unwatched & quit" overflow orchestration, the
 * autoplay-cancel wiring behind the Up Next overlay, the overlay's
 * next-episode loading flag, the smart-download cleanup, and the binge
 * controls (the autoplay episode limit — the unattended streak, its two auto
 * arms and manual reset arms, the reactive limit verdict — plus the #17
 * after-finish keep gate that holds the card up instead of closing).
 *
 * The [SubtitleStyleController] shape: every fact the moved logic read from
 * the VM arrives as a narrow constructor lambda — NO raw uiState handle
 * crosses (the god-count guard stays at its baseline; this class never
 * references [VideoPlayerUiState]). The episode-browsing slice stays a stored
 * slice of the uiState; the navigator remains its single writer through the
 * injected [updateEpisodes] seam, and the mark/advance effects stay the
 * caller-supplied verbs they were before the move.
 *
 * ONE consume protocol (the invariant the queue sheet's old
 * consume-then-initialize ordering violated — a failed "play now" silently
 * deleted the row): every queue consumption happens ONLY after the session
 * settles on the item, guarded so the row is removed only while it is still
 * the row that was started ([consumeIfHead] for the head arm, the settle-time
 * re-peek guard in [playQueueItem] for the mid-list arm). A load that errors,
 * times out, or is superseded leaves the queue intact.
 *
 * Load-bearing invariants (pinned by [UpNextControllerTest]):
 *  - the mark arms mirror the watched-threshold callback exactly — incognito
 *    routes the watched mark to the offline-local record and skips the
 *    unwatched mark entirely ([decideWatchedActions]);
 *  - "mark watched & skip" reuses [playNextEpisode], so the SyncPlay
 *    group-queue routing and the #146 single-flight latch apply unchanged;
 *  - the autoplay cancel flips the decision clock AND the overlay mirror in
 *    that order, so a cancelled countdown can never auto-advance;
 *  - the card's precedence matches the advance's: queue head over episode
 *    sibling, and null under SyncPlay ownership of order.
 */
internal class UpNextController(
    private val scope: CoroutineScope,
    // ── The navigator stack (the controller owns the EpisodeNavigator) ─────
    private val sessionState: StateFlow<PlayerSessionState>,
    private val sessionEvents: SharedFlow<SessionEvent>,
    private val episodeCatalogue: EpisodeCatalogue,
    /**
     * The manual up-next queue; the advance is QUEUE-FIRST (head consumed
     * before the episode sibling) but suppressed during a SyncPlay group —
     * the server-side group queue owns the order there. Null only in test
     * graphs that don't exercise the queue.
     */
    val videoQueue: VideoQueueController? = null,
    /**
     * The uiState's SyncPlay-membership mirror as a flow — the REACTIVE gate
     * behind the display projections ([upNextCard] / [hasQueuedNext]). The
     * advance keeps the manager-flag [isInSyncPlayGroup] read (the same split
     * the mark decision already applies): a display projection gating on the
     * mirror stays consistent with the overlay math, which reads the same
     * mirror through [SegmentCalculatorInput].
     */
    private val syncPlaySessionMirror: Flow<Boolean>,
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
    // ── Binge controls (the autoplay episode limit + the after-finish card) ──
    /**
     * The configured consecutive-auto-advance cap, `0` = unlimited — as a
     * FLOW so the reactive [isAutoplayLimitReached] verdict tracks a mid-play
     * settings change without a controller handle poking it (the session maps
     * the aggregate's `autoplayEpisodeLimit` leaf).
     */
    private val autoplayEpisodeLimit: Flow<Int> = flowOf(0),
    /**
     * The `showNextEpisodeDetailsAfterFinish` pref (the end-of-media
     * keep-gate's pref arm; #17).
     */
    private val isShowDetailsAfterFinishEnabled: () -> Boolean = { false },
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
     * The adjacent-episode snapshot, tapped from the navigator's single-writer
     * seam: every slice write the navigator makes crosses
     * [tapAdjacentEpisode], so this mirror tracks
     * [EpisodeBrowserState.nextEpisode] with no second source. Feeds the card
     * projection ([upNextCard]).
     *
     * Declared BEFORE the navigator — the navigator's construction captures
     * the wrapped seam (Kotlin initialises properties in declaration order).
     */
    private val adjacentEpisode = MutableStateFlow<MediaItem?>(null)

    /**
     * The [updateEpisodes] seam wrapped to tap the adjacent-episode snapshot
     * into [adjacentEpisode] on the way through — the navigator's writes stay
     * the ONLY thing that can change it, so the card needs no separate
     * sibling flow and can never disagree with the browsing slice.
     */
    private val tapAdjacentEpisode: (((EpisodeBrowserState) -> EpisodeBrowserState) -> Unit) = { update ->
        updateEpisodes { state ->
            val next = update(state)
            adjacentEpisode.value = next.nextEpisode
            next
        }
    }

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
        updateEpisodes = tapAdjacentEpisode,
    )

    /**
     * The queue flow behind the reactive projections, defaulted to an
     * always-empty flow for the test graphs that pass a null [videoQueue]
     * (never written there).
     */
    private val queueItems: StateFlow<List<VideoQueueItem>> =
        videoQueue?.queue ?: MutableStateFlow(emptyList())

    /**
     * The Up Next card projection — the display truth for "what plays next".
     * Queue head beats episode sibling (matching [playNextEpisode]'s advance
     * precedence); null while a SyncPlay group owns the order. Recomputes on
     * queue writes, adjacent-episode writes (both StateFlow-backed) and
     * SyncPlay-membership flips.
     */
    val upNextCard: StateFlow<UpNextCard?> = combine(
        queueItems,
        adjacentEpisode,
        syncPlaySessionMirror,
    ) { queue, sibling, inSyncPlay ->
        when {
            inSyncPlay -> null
            queue.isNotEmpty() -> queue.first().let { head ->
                UpNextCard(
                    itemId = head.itemId,
                    title = head.title,
                    subtitle = head.subtitle,
                    imageUrl = head.imageUrl,
                    source = UpNextCard.Source.QUEUE,
                )
            }
            sibling != null -> UpNextCard(
                itemId = sibling.id,
                title = sibling.name,
                subtitle = sibling.seriesName,
                source = UpNextCard.Source.EPISODE,
                seasonNumber = sibling.seasonNumber,
                episodeNumber = sibling.episodeNumber,
            )
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The reactive has-queued-next gate — the queue's non-emptiness under the
     * SyncPlay suppression, as a flow. The single source the VM's uiState
     * mirror, the segment projection and the end-of-playback autoplay gate
     * all read (the advance itself peeks the queue live in [playNextEpisode];
     * this surface exists for the decision inputs).
     */
    val hasQueuedNext: StateFlow<Boolean> = combine(
        queueItems,
        syncPlaySessionMirror,
    ) { queue, inSyncPlay ->
        !inSyncPlay && queue.isNotEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Latch for the queue-first advance (the navigator's #146 latch, queue arm). */
    private val queueAdvanceInFlight = AtomicBoolean(false)

    /**
     * Latch for the manual queue-row activation ([playQueueItem]) — separate
     * from [queueAdvanceInFlight] ON PURPOSE: a manual "play now" is a
     * deliberate pick and legitimately preempts an auto-advance that is still
     * settling (the advance's settle guard then sees a different bind and
     * leaves its row queued). Only re-taps of the manual activation itself
     * are single-flighted.
     */
    private val manualQueuePlayInFlight = AtomicBoolean(false)

    /** How a queue consumption's settle wait resolved. */
    private enum class AdvanceSettled { SETTLED, FAILED }

    /** True while a next-episode advance is in flight and unsettled. */
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

    /** Starts playback of a picked episode at its saved position (manual — resets the binge streak). */
    fun playEpisode(episodeId: String, startPositionTicks: Long = 0L) {
        resetAutoAdvanceStreak()
        initializeItem(episodeId, startPositionTicks)
    }

    fun playPreviousEpisode() {
        resetAutoAdvanceStreak()
        navigator.previous()
    }

    /**
     * QUEUE-FIRST advance: when the manual up-next queue holds an item (and no
     * SyncPlay group owns the order), its head plays instead of the episode
     * sibling. Movies in the queue are the point — they have no sibling. The
     * #146 single-flight latch still rides [next] for the episode-sibling path.
     */
    fun playNextEpisode() {
        if (!isInSyncPlayGroup()) {
            videoQueue?.peekHead()?.let { head ->
                playQueuedHead(head)
                return
            }
        }
        navigator.next()
    }

    /**
     * The queue arm of the advance. The head is NOT consumed up front: it is
     * consumed only when the session settles on the item ([consumeIfHead]), so
     * an advance that dies before playback — a failed load, the player closing
     * mid-load, the user backing out — leaves the queue intact instead of
     * silently dropping the item (the consume-then-initialize ordering lost
     * the head exactly that way). A single-flight latch mirrors the
     * navigator's #146 latch: re-taps while an advance is settling are
     * dropped rather than double-initializing the same head.
     */
    private fun playQueuedHead(head: VideoQueueItem) {
        if (!queueAdvanceInFlight.compareAndSet(false, true)) {
            Log.d(QUEUE_ADVANCE_LOG_TAG, "queue advance ignored (in flight): ${head.itemId}")
            return
        }
        Log.d(QUEUE_ADVANCE_LOG_TAG, "queue-first advance -> ${head.itemId}")
        launchSettleWatch(
            itemId = head.itemId,
            latch = queueAdvanceInFlight,
            arm = "queue advance",
        ) {
            videoQueue?.consumeIfHead(head.itemId)
        }
    }

    /**
     * The settle-watch both queue arms share — the ONE consume protocol's
     * engine: start [itemId], then wait until the session either settles on
     * it ([AdvanceSettled.SETTLED]) or reports a failed load
     * ([AdvanceSettled.FAILED]), bounded by NEXT_EPISODE_SETTLE_TIMEOUT_MS
     * (a timeout reads as not-settled). The caller has already CAS'd [latch]
     * and logged its arm line; this releases the latch in the finally and
     * runs [onSettled] — the arm's consume step — only on SETTLED, so a
     * failed load, the player closing mid-load or a superseded bind leaves
     * the queue exactly as it was (the consume-then-initialize ordering
     * lost the head that way; both arms consume AFTER settle for that
     * reason).
     */
    private fun launchSettleWatch(
        itemId: String,
        latch: AtomicBoolean,
        arm: String,
        onSettled: () -> Unit,
    ) {
        initializeItem(itemId, 0L)
        scope.launch {
            try {
                val settled = withTimeoutOrNull(NEXT_EPISODE_SETTLE_TIMEOUT_MS) {
                    merge(
                        sessionState.map { state ->
                            if (state.currentItemId == itemId) AdvanceSettled.SETTLED else null
                        }.filterNotNull(),
                        sessionEvents.map { event ->
                            if (event is SessionEvent.ShowError) AdvanceSettled.FAILED else null
                        }.filterNotNull(),
                    ).first()
                }
                if (settled == AdvanceSettled.SETTLED) {
                    onSettled()
                }
                Log.d(QUEUE_ADVANCE_LOG_TAG, "$arm settled=$settled item=$itemId")
            } finally {
                latch.set(false)
            }
        }
    }

    /**
     * The queue sheet's "play now" for the row at [index] — the MANUAL
     * activation arm, running the SAME settle-then-consume protocol as the
     * head arm (this replaces the old consume-then-initialize ordering that
     * deleted the row on a failed load): PEEK the row at [index], start it,
     * and remove it only after the session settles on it — with the
     * settle-time re-peek guard, because the row may have MOVED (another
     * action re-ordered or drained the queue while the load ran): the row is
     * consumed only while the row at [index] is still the item that was
     * started; otherwise the queue is left intact (deleting an unrelated row
     * for an item that already played would be the lost-head bug in mirror
     * image). A failed load ([SessionEvent.ShowError]), the settle timeout or
     * a superseded bind keeps the row.
     *
     * Single-flighted on [manualQueuePlayInFlight] (re-taps while the load
     * settles are dropped, #146); a queue-head ADVANCE in flight does not
     * block a manual pick — see the latch's KDoc.
     */
    fun playQueueItem(index: Int) {
        val item = queueItems.value.getOrNull(index) ?: return
        // A manual queue-row pick is a deliberate item selection: the binge
        // streak restarts (the autoplay cap's reset arm).
        resetAutoAdvanceStreak()
        if (!manualQueuePlayInFlight.compareAndSet(false, true)) {
            Log.d(QUEUE_ADVANCE_LOG_TAG, "manual queue play ignored (in flight): ${item.itemId}")
            return
        }
        Log.d(QUEUE_ADVANCE_LOG_TAG, "manual queue play[$index] -> ${item.itemId}")
        launchSettleWatch(
            itemId = item.itemId,
            latch = manualQueuePlayInFlight,
            arm = "manual queue play",
        ) {
            // The settle-time re-peek: consume only while the row at [index]
            // is STILL the item that was started.
            val stillTheSameRow = queueItems.value.getOrNull(index)?.itemId == item.itemId
            if (stillTheSameRow) {
                videoQueue?.consumeAt(index)
            }
            Log.d(
                QUEUE_ADVANCE_LOG_TAG,
                "manual queue play consumed=$stillTheSameRow item=${item.itemId}",
            )
        }
    }

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
        // A deliberate user advance — the binge streak restarts.
        resetAutoAdvanceStreak()
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

    // ── Binge controls: the autoplay episode limit + the after-finish card ──

    /**
     * The unattended binge streak: consecutive AUTO-advances this player
     * session. Grows ONLY on [advanceUnattended] (both auto arms — the
     * engine end and the Up Next countdown); every manual item selection
     * resets it —
     * the manual verbs here ([playEpisode] / [playPreviousEpisode] /
     * [playQueueItem] / [markWatchedAndSkip] / [playNextManually]) plus the
     * player-open arm ([resetAutoAdvanceStreak], the VM's Initialize funnel).
     * Deliberately a counter of its own, NOT [AutoPlayController]'s
     * still-watching streak: that one also resets on generic user
     * interaction, while the cap's reset is exactly "manual item selection /
     * player open" — a pause mid-episode must not lift the cap.
     */
    private val autoAdvanceStreak = MutableStateFlow(0)

    /**
     * The autoplay-cap verdict: true when the configured cap is armed
     * (limit > 0) and the streak reached it. While true, the two auto arms
     * are blocked ([canAutoAdvance] — the VM's end-of-playback gate) and the
     * Up Next overlay renders MANUAL ("Watch now / Dismiss", no countdown).
     */
    val isAutoplayLimitReached: StateFlow<Boolean> = combine(
        autoAdvanceStreak,
        autoplayEpisodeLimit,
    ) { streak, limit -> limit > 0 && streak >= limit }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The end-of-playback gate's limit arm: a reached cap blocks the advance. */
    fun canAutoAdvance(): Boolean = !isAutoplayLimitReached.value

    /** Player open / manual item selection: the unattended streak restarts. */
    fun resetAutoAdvanceStreak() {
        autoAdvanceStreak.value = 0
    }

    /**
     * The unattended auto-advance: the binge streak grows, then the advance.
     * Two triggers share it — the engine-end arm (the VM's
     * `handlePlaybackEnded`) and the Up Next countdown's expiry (the
     * overlay's [NextEpisodeOverlay] countdown reaching zero) — in both the
     * user pressed nothing. Blocked upstream by [canAutoAdvance] when the
     * cap is reached, so this only runs while the cap allows it.
     */
    fun advanceUnattended() {
        recordAutoAdvanceForLimit()
        playNextEpisode()
    }

    /**
     * The MANUAL play-next arm — the card's "Watch now" button, the PiP
     * transport's next action, the still-watching Continue: a deliberate
     * user pick resets the unattended streak, then advances.
     */
    fun playNextManually() {
        resetAutoAdvanceStreak()
        playNextEpisode()
    }

    private fun recordAutoAdvanceForLimit() {
        autoAdvanceStreak.value += 1
    }

    /**
     * The end-of-media keep gate (#17): when playback concluded and no
     * auto-advance will happen, should the Up Next card stay up — persistently,
     * as a "next episode" details card — instead of the player closing?
     *
     * Decision table: the pref must be on; the user must NOT have cancelled
     * the countdown (a dismissed card stays dismissed — the close path is the
     * current behavior); a next item must exist (the card projection is the
     * display truth — queue head or episode sibling, null under SyncPlay);
     * and no auto-advance may be pending (autoplay off, or on with the limit
     * reached — an enabled autoplay under the cap would have advanced before
     * this gate ran, so the last arm is defensive).
     */
    fun shouldKeepUpNextCardAfterFinish(
        autoplayEnabled: Boolean,
        autoplayCancelled: Boolean,
    ): Boolean {
        if (!isShowDetailsAfterFinishEnabled()) return false
        if (autoplayCancelled) return false
        if (upNextCard.value == null) return false
        if (autoplayEnabled && !isAutoplayLimitReached.value) return false
        return true
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
