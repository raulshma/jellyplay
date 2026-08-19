package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Deep module for the home screen's pending-sync surface: everything the UI
 * shows about offline playback events still queued in the playback outbox.
 * Previously these five concerns lived inline on HomeViewModel as scattered
 * fields and collectors; they are one concern (is there un-synced watch
 * progress, what is it, and when will it flush) and now have one owner.
 *
 * Owns exclusively:
 *  * [pendingSyncCount] — the header badge count (outbox size, WhileSubscribed
 *    so the Room flow is only collected while the header is on screen).
 *  * [pendingSyncEntries] — the reactive row list for the sync details sheet.
 *  * [pendingItemDetails] + [ensurePendingItemDetails] — offline-first
 *    title/poster resolution per outbox id, deduped and pruned to the queued
 *    ids (the sheet calls ensure on every recomposition; resolution is the
 *    data layer's [OfflineFirstItemResolver] policy).
 *  * [syncNow] — the manual drain trigger (a no-op while offline: the drain
 *    worker requires a network connection).
 *  * [awaitOutboxDrained] — the drain gate the offline→online transition
 *    waits on before fetching Continue Watching / Next Up.
 *
 * Placement (core/data, per the OfflineDeleteActions precedent): every
 * collaborator is a core/data type, and the holder holds no UI shaping. Same
 * construction contract as SeerrRequestStateHolder — not a DI bean; built by
 * the consumer with its own scope, which all launches run on.
 */
class SyncStatusStateHolder(
    /** The consumer's scope: resolve/collect jobs must die with it. */
    private val scope: CoroutineScope,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val playbackSyncScheduler: PlaybackSyncScheduler,
    private val offlineFirstItemResolver: OfflineFirstItemResolver,
    private val offlineModeManager: OfflineModeManager,
) {

    companion object {
        /**
         * How long [awaitOutboxDrained] will wait for the playback outbox to
         * drain before giving up. The drain (PlaybackSyncWorker) replays
         * offline marks to the server; if we fetch before it completes, CW can
         * still list items the user just marked unplayed. The drain is usually
         * near-instant on reconnect, so this is a short cap — on timeout we
         * fetch anyway (the next periodic refresh or pull-to-refresh
         * re-syncs).
         */
        private const val OUTBOX_DRAIN_WAIT_MS = 8_000L
    }

    /**
     * Count of playback events queued in the offline outbox. Surfaced to the
     * home header so the user can see that their offline watch progress is
     * pending sync (and that it will flush automatically on reconnect).
     */
    val pendingSyncCount: StateFlow<Int> = playbackOutboxRepository.countFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Reactive snapshot of pending outbox entries (oldest-first), for the
     * sync details sheet. Only collected while the sheet is open, so it does
     * not add steady-state flow cost.
     */
    val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>> =
        playbackOutboxRepository.getAllFlow()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Resolved media metadata (title + poster URL) keyed by outbox `itemId`,
     * for rendering per-row context in the sync details sheet. Resolution is
     * **offline-first** (owned by [OfflineFirstItemResolver] in the data layer)
     * and falls back to a network `getMediaDetail` lookup only when the item
     * was watched but never downloaded. Entries are populated on demand via
     * [ensurePendingItemDetails] and pruned to the currently-queued ids so the
     * map never grows unbounded. `item == null` (with a network-derived URL)
     * marks a resolved-but-not-found id so we don't refetch it every
     * recomposition.
     */
    private val _pendingItemDetails =
        MutableStateFlow<Map<String, ResolvedMediaRef>>(emptyMap())
    val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>> = _pendingItemDetails.asStateFlow()

    /**
     * Item ids currently being resolved, to dedupe concurrent callers.
     * Concurrent set: mutated from [scope.launch] coroutines, so it must not
     * rely on the consumer's scope being confined to a single thread.
     */
    private val pendingResolveInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Ensures the [pendingItemDetails] map holds a resolution for every id in
     * [itemIds], pruning any stale entries that are no longer queued. Cheap to
     * call on every recomposition — already-resolved and in-flight ids are
     * skipped. Safe to call with an empty collection (clears the map). When to
     * resolve is a UI-sheet policy that stays with the consumer; how to
     * resolve is the resolver's (data-layer) policy.
     */
    fun ensurePendingItemDetails(itemIds: Collection<String>) {
        val keep = itemIds.toSet()
        // Drop resolutions for ids that are no longer queued.
        if (_pendingItemDetails.value.keys.any { it !in keep }) {
            _pendingItemDetails.value = _pendingItemDetails.value.filterKeys { it in keep }
        }
        for (id in keep) {
            if (_pendingItemDetails.value.containsKey(id)) continue
            // add() is the atomic check-and-claim: a separate `in` check
            // followed by `+=` lets two concurrent callers both pass and
            // double-resolve the same id.
            if (!pendingResolveInFlight.add(id)) continue
            scope.launch {
                try {
                    val resolved = offlineFirstItemResolver.resolveMediaRef(id)
                    _pendingItemDetails.update { current -> current + (id to resolved) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The resolver absorbs domain failures (not-found, offline
                    // fallbacks); anything thrown here is infrastructure-level.
                    // Swallow and leave the id unresolved so the next ensure
                    // call retries it — rethrowing would crash the consumer's
                    // scope over a title lookup.
                } finally {
                    // Release the dedup slot on every exit path; missing it on
                    // failure would wedge the id as permanently "in-flight".
                    pendingResolveInFlight -= id
                }
            }
        }
    }

    /**
     * Manually drain the playback outbox. The drain worker requires a network
     * connection (NetworkType.CONNECTED constraint), so this is a no-op while
     * offline — the caller surfaces that in the sheet rather than firing a
     * work request that can't run. On reconnect the worker drains anyway.
     */
    fun syncNow() {
        if (offlineModeManager.offlineMode.value != OfflineMode.ONLINE) return
        playbackSyncScheduler.enqueueNow()
    }

    /**
     * Waits for the playback outbox to drain (count reaches 0) so the server
     * has processed offline watched/unwatched marks before a home-section fetch
     * reads Continue Watching / Next Up. Returns immediately when nothing is
     * pending; on [OUTBOX_DRAIN_WAIT_MS] timeout it returns regardless so the
     * fetch proceeds (a later periodic refresh re-syncs). Dead-lettered entries
     * are excluded from the count, so a persistently-undeliverable mark won't
     * stall the wait indefinitely.
     */
    suspend fun awaitOutboxDrained() {
        if (playbackOutboxRepository.count() == 0) return
        withTimeoutOrNull(OUTBOX_DRAIN_WAIT_MS) {
            playbackOutboxRepository.countFlow().first { it == 0 }
        }
    }
}
