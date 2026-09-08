package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync.ComputeResult
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync.ReconcileOutcome
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Phase X MediaRepository cluster flip: moved verbatim from the legacy
// :core:data shim (same package/name). Ctor-level transforms only, plus the
// one mechanical body edit they force:
//  - `@Singleton` / `@Inject` stripped (one framework per type — Koin's
//    dataJvmModule constructs this single; the legacy DataModule bridges the
//    remaining Hilt injectors via koin().get()).
//  - `dagger.Lazy<T>` ctor params → kotlin `Lazy<T>` (the module has no
//    dagger dependency; memoizing single-evaluation semantics preserved —
//    the dataJvmModule def feeds `lazy { get() }`). The deferral is the
//    MediaRepositoryImpl ↔ PlayedStateSync construction-cycle breaker.
//    dagger.Lazy's `.get()` call sites became `.value` (kotlin.Lazy's
//    accessor) — the only body-level change, one-for-one mechanical.
//  - `android.util.Log` → the module's Log facade.
class PlayedStateSyncImpl(
    private val apiClient: JellyfinApiClient,
    private val offlineRepository: OfflineRepository,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val offlineModeManager: OfflineModeManager,
    private val mediaRepository: Lazy<MediaRepository>,
    /**
     * Auto-delete-after-watch: reads the download-lifetime pref.
     * Injected (not constructed) and lazy-deferred so the download stack
     * cannot form a construction cycle with this module.
     */
    private val downloadsStore: Lazy<DownloadsStore>,
    /**
     * Same concern as [downloadsStore]: looks up + deletes a finished download
     * row when a watched flip lands. Lazy for the same cycle-safety reason
     * (`DownloadRepositoryImpl` references only the [PlayedStateSync] companion
     * helper, never the impl, so this edge is acyclic — Lazy keeps it defensive).
     */
    private val downloadRepository: Lazy<DownloadRepository>,
    /**
     * Clock seam for reconcile's server-vs-local ladder: the future-dated
     * sanity guard compares the server stamp against NOW, so an injected fake
     * pins the whole ladder (server-newer wins vs local-newer wins) in tests.
     */
    private val timeSource: TimeSource,
) : PlayedStateSync {

    override suspend fun flip(itemId: String, played: Boolean, seriesId: String?): Result<Unit> =
        flip(itemId, played, seriesId, announce = true)

    /**
     * [announce] = false suppresses the confirmed-write announcement for the
     * one caller that is itself followed by a drain-tail announce of the same
     * ids ([pushUnsyncedIntent] inside the outbox drain): announcing from both
     * would double-emit — the drain announces every delivered flip AND every
     * adopted row exactly once, so the inner flip stays silent.
     */
    private suspend fun flip(itemId: String, played: Boolean, seriesId: String?, announce: Boolean): Result<Unit> {
        // Offline: apply locally for immediate UI feedback and stage the flip
        // in the outbox so PlaybackSyncWorker delivers it on reconnect.
        if (offlineModeManager.isOffline) {
            runCatchingRethrowingCancellation { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatchingRethrowingCancellation { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
            // Auto-delete-after-watch: even offline, a watched flip removes the
            // download (cleanup is local-only; nothing to sync). Guarded so a
            // failure never surfaces or crashes playback.
            if (played) maybeAutoDeleteAfterWatch(itemId)
            return Result.success(Unit)
        }
        val result = if (played) apiClient.markPlayed(itemId) else apiClient.markUnplayed(itemId)
        if (result.isSuccess) {
            // Mirror the server-side cascade into the offline store so
            // downloaded items in this hierarchy stay consistent. Best-effort:
            // a failure here must not surface — the server mutation already
            // succeeded and reconciliation will correct any drift.
            runCatchingRethrowingCancellation { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            // Announce the confirmed write on the same flow server WS pushes
            // use, so open screens heal even when the socket is down (the
            // consumer debounce/throttle collapses this with any echo). Season
            // flips carry the seriesId too — id-matching consumers key on the
            // series, never the season. Drain-context flips stay silent
            // (see [announce]).
            if (announce) {
                mediaRepository.value.notifyUserDataChanged(listOfNotNull(itemId, seriesId))
            }
            // Auto-delete-after-watch: item was just marked played — if the
            // user opted in and a finished download exists for it, remove it
            // now. The flip already succeeded, so a cleanup failure must never
            // bubble up to the caller.
            if (played) maybeAutoDeleteAfterWatch(itemId)
        } else {
            // Online but the call failed (transient 5xx, auth drop). Don't lose
            // the user's intent: apply locally and enqueue for retry.
            runCatchingRethrowingCancellation { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatchingRethrowingCancellation { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
            // The played state wasn't confirmed server-side, so don't delete
            // the download yet — wait for a confirmed played flip.
            return Result.success(Unit)
        }
        return result
    }

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> {
        // Offline: resolve the current state from the local row so the toggle is
        // deterministic without a server round-trip, then apply + stage the
        // absolute target in the outbox for delivery on reconnect.
        if (offlineModeManager.isOffline) {
            return Result.success(applyFavoriteLocallyAndEnqueue(itemId))
        }
        // Online: the server reads + flips atomically (currentIsFavorite = null
        // lets it resolve). The returned Boolean is the authoritative new state.
        val result = apiClient.toggleFavorite(itemId, currentIsFavorite = null)
        if (result.isSuccess) {
            val target = result.getOrNull() ?: return result
            // Mirror into the offline store so downloaded items stay consistent;
            // best-effort like the played mirror above.
            runCatchingRethrowingCancellation { offlineRepository.applyFavoriteState(itemId, target) }
            // Same synthetic announcement as the played flip: confirmed write
            // on the user-data-change flow, socket-independent.
            mediaRepository.value.notifyUserDataChanged(listOf(itemId))
        } else {
            // Online but the call failed — don't lose the user's intent: apply
            // locally and enqueue for retry, resolving target from local state.
            return Result.success(applyFavoriteLocallyAndEnqueue(itemId))
        }
        return result
    }

    /**
     * Shared offline / online-failure fallback for [toggleFavorite]: resolve the
     * current favorite state from the local row (deterministic without a server
     * round-trip), flip it, apply locally, and stage the absolute target in the
     * outbox for delivery on reconnect. Returns the resolved target so the
     * caller's optimistic UI flip is correct regardless of path.
     */
    private suspend fun applyFavoriteLocallyAndEnqueue(itemId: String): Boolean {
        val current = runCatchingRethrowingCancellation { offlineRepository.getOfflineItem(itemId)?.isFavorite }.getOrNull() ?: false
        val target = !current
        runCatchingRethrowingCancellation { offlineRepository.applyFavoriteState(itemId, target) }
        runCatchingRethrowingCancellation { playbackOutboxRepository.enqueueFavoriteState(itemId, target) }
        return target
    }

    /**
     * Auto-deleting a finished download on a watched flip is an unrequested,
     * destructive product decision — it is off by default. Kept behind its own
     * pref (`auto_delete_after_watch`) so it only runs when the user opts in.
     * If the behaviour is unwanted it should be removed (pref + this call site
     * + the store key).
     *
     * Behaviour: when the pref is ON and the just-flipped-played [itemId] has a
     * completed download, delete that download (file + DB row + offline
     * metadata). Everything is wrapped so a cleanup error is logged and
     * swallowed — playback must never crash because we couldn't reclaim disk.
     * Only COMPLETED downloads are removed so an in-flight/partial download is
     * never destroyed mid-transfer.
     */
    private suspend fun maybeAutoDeleteAfterWatch(itemId: String) {
        try {
            if (!downloadsStore.value.downloads.first().autoDeleteAfterWatch) return
            val download = downloadRepository.value.getDownloadByMediaItemId(itemId) ?: return
            if (download.status != DownloadStatus.COMPLETED) return
            runCatchingRethrowingCancellation { downloadRepository.value.deleteDownload(download.id) }
                .onFailure { Log.w(TAG, "Auto-delete-after-watch failed for $itemId", it) }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-delete-after-watch lookup failed for $itemId", e)
        }
    }

    override suspend fun reconcileOfflineRow(itemId: String): ReconcileOutcome {
        val offline = offlineRepository.getOfflineItem(itemId) ?: return ReconcileOutcome.NoChange
        // Pull a fresh server view (bypass any cached detail so a stale cache
        // cannot mask a newer played/position state).
        val serverItem = mediaRepository.value.getMediaDetail(itemId, force = true).getOrNull()?.item
            ?: return ReconcileOutcome.NoChange

        // Favorite is a user preference shared across devices, so the server is
        // authoritative: if it disagrees with the local row, adopt the server's
        // state. No timestamp tiebreak — a favorite flip made on another device
        // must propagate here regardless of local activity. Best-effort; the
        // played-state reconciliation below runs regardless of outcome.
        if (serverItem.isFavorite != offline.isFavorite) {
            runCatchingRethrowingCancellation { offlineRepository.applyFavoriteState(itemId, serverItem.isFavorite) }
        }

        // Server watched (e.g. finished online) always wins — reset the local
        // row so a later offline resume starts the next episode / 0, not a
        // stale half-watched position. Symmetric exception (#153): an
        // undelivered UNPLAYED intent means the user marked the item unwatched
        // offline and the server never heard it — the server's watched state
        // is not newer knowledge, so push the local intent instead of
        // adopting it.
        if (serverItem.isPlayed) {
            if (playbackOutboxRepository.hasUnsyncedUnplayedIntent(itemId)) {
                return pushUnsyncedIntent(itemId, played = false)
            }
            // #157 self-heal: /Items/Resume filters on position > 0 only, so a
            // position report that landed AFTER the item was already played (a
            // sub-threshold STOP replayed by an older build's offline sync, a
            // brief re-watch of the finished episode) leaves the server with
            // Played=true + position>0 — watched, yet permanently resumable in
            // every client, because nothing server-side ever resets the
            // position. markPlayedItem's resetPosition zeroes it. The repair
            // goes through the delete-before-push + flip + delivery-probe
            // helper: a failed push stages a PLAYED row and reports
            // UndeliveredIntent so the drain retries promptly instead of
            // stranding the poison until the periodic backstop. The staged-row
            // flip keeps auto-delete-after-watch semantics identical to a
            // user-issued watched flip (pref-gated).
            val healOutcome = if ((serverItem.playbackPositionTicks ?: 0L) > 0L) {
                pushUnsyncedIntent(itemId, played = true)
            } else {
                null
            }
            if (healOutcome is ReconcileOutcome.UndeliveredIntent) return healOutcome
            // Server watched always wins for the LOCAL row too: zero the stale
            // local resume point (the heal above may have already zeroed the
            // server's; the local row must agree either way).
            offlineRepository.updatePlaybackProgress(
                itemId = itemId,
                positionTicks = 0L,
                percentage = 100.0,
                isPlayed = true,
            )
            // A delivered heal already reported Changed(PLAYED); the unhealed
            // path derives the same outcome here.
            return healOutcome ?: ReconcileOutcome.Changed(ComputeResult.PLAYED)
        }

        // Server unplayed but local played: EITHER the user marked the item
        // unwatched online and that change has not yet reached the local store
        // (mirror the flip), OR a local watched intent never reached the
        // server — a pending/dead-lettered PLAYED outbox row, or an offline
        // watch whose flip was lost (the #153 "watched offline, online home
        // shows mostly completed" bug). The unsynced-intent check separates
        // the two: when the server never received the watch, its unplayed
        // state is not newer knowledge — push instead of clearing.
        if (offline.isPlayed) {
            if (playbackOutboxRepository.hasUnsyncedPlayedIntent(itemId)) {
                return pushUnsyncedIntent(itemId, played = true)
            }
            offlineRepository.applyPlayedState(itemId, isPlayed = false)
            return ReconcileOutcome.Changed(ComputeResult.UNPLAYED)
        }

        // Otherwise the most recent activity wins. Both sides are epoch-millis
        // at heart: the server's `lastPlayedDate` arrives as an ISO string
        // (kotlinx-datetime Instant -> "...Z", or a LocalDateTime when the
        // server omits the offset), and the offline row stores
        // OffsetDateTime.now().toString(). `parseIsoToEpochMillis` accepts
        // both shapes so the comparison is zone-correct regardless of source.
        val serverMillis = parseIsoToEpochMillis(serverItem.lastPlayedDate) ?: return ReconcileOutcome.NoChange
        if (serverMillis > timeSource.nowEpochMillis()) {
            // Sanity guard against future-dated server clocks.
            return ReconcileOutcome.NoChange
        }
        val offlineMillis = offline.lastPlayedDate?.let { parseIsoToEpochMillis(it) } ?: 0L
        if (serverMillis <= offlineMillis) return ReconcileOutcome.NoChange

        val runTime = serverItem.runTimeTicks ?: offline.runTimeTicks
        val percentage = PlayedStateSync.computePlayedPercentage(
            positionTicks = serverItem.playbackPositionTicks,
            runTimeTicks = runTime,
            isPlayed = false,
        )
        offlineRepository.updatePlaybackProgress(
            itemId = itemId,
            positionTicks = serverItem.playbackPositionTicks,
            percentage = percentage,
            isPlayed = false,
        )
        return ReconcileOutcome.Changed(ComputeResult.POSITION_UPDATED)
    }

    /**
     * Delete-before-push for an undelivered played-state intent (#153): the
     * row (pending or dead-lettered) is removed BEFORE the push, so a
     * delivered flip leaves nothing behind, and a formerly dead-lettered row
     * returns with a fresh retry budget instead of lingering as a zombie that
     * every later reconcile re-pushes.
     *
     * Delivery is detected via the outbox probe
     * ([PlaybackOutboxRepository.isPlayedStateIntentDelivered] — the surviving
     * row is the real delivery signal): [ReconcileOutcome.UndeliveredIntent]
     * when the push did not land (the re-enqueued row carries it to the next
     * drain).
     */
    private suspend fun pushUnsyncedIntent(itemId: String, played: Boolean): ReconcileOutcome {
        runCatchingRethrowingCancellation { playbackOutboxRepository.deletePlayedStateIntents(itemId) }
        // announce = false: the drain that drives this reconcile announces
        // every delivered flip in its tail — a second emission here would
        // double-announce the same id.
        flip(itemId, played, seriesId = null, announce = false)
        val delivered = runCatchingRethrowingCancellation {
            playbackOutboxRepository.isPlayedStateIntentDelivered(itemId, played)
        }.getOrDefault(false)
        return when {
            !delivered -> ReconcileOutcome.UndeliveredIntent
            played -> ReconcileOutcome.Changed(ComputeResult.PLAYED)
            else -> ReconcileOutcome.Changed(ComputeResult.UNPLAYED)
        }
    }

    companion object {
        private const val TAG = "PlayedStateSync"
        private val ISO_OFFSET_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        private val ISO_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /**
         * Parses an ISO-8601 datetime string to epoch-millis, accepting either an
         * offset-aware form (e.g. Jellyfin SDK's `Instant.toString()` →
         * `2024-01-15T10:30:00Z`, or the offline row's `OffsetDateTime.toString()`)
         * or a bare `LocalDateTime` (server sometimes omits the offset). Offset-
         * aware inputs parse in their own zone; bare inputs parse in the system
         * zone (the zone that produced them on-device).
         */
        internal fun parseIsoToEpochMillis(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            // Offset-aware first — covers `...Z`, `...+00:00`, `...+05:30`.
            return runCatching {
                java.time.OffsetDateTime.parse(value, ISO_OFFSET_PARSER).toInstant().toEpochMilli()
            }.getOrElse {
                // Bare LocalDateTime fallback — interpret in the system zone.
                runCatching {
                    LocalDateTime.parse(value, ISO_PARSER)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
    }
}
