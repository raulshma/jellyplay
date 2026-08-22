package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync.ComputeResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

// C4p2 note: the [PlayedStateSync] interface moved to :shared:core:data
// jvmShared; this impl stays in the legacy module — its constructor takes the
// Android-coupled `OfflineModeManager`, so it is not Koin-constructible until
// the OfflineModeManager/connectivity seam lands.

@Singleton
class PlayedStateSyncImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val offlineRepository: OfflineRepository,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val offlineModeManager: OfflineModeManager,
    private val mediaRepository: dagger.Lazy<MediaRepository>,
    /**
     * Auto-delete-after-watch: reads the download-lifetime pref.
     * Injected (not constructed) and lazy-deferred so the download stack
     * cannot form a construction cycle with this module.
     */
    private val downloadsStore: dagger.Lazy<DownloadsStore>,
    /**
     * Same concern as [downloadsStore]: looks up + deletes a finished download
     * row when a watched flip lands. Lazy for the same cycle-safety reason
     * (`DownloadRepositoryImpl` references only the [PlayedStateSync] companion
     * helper, never the impl, so this edge is acyclic — Lazy keeps it defensive).
     */
    private val downloadRepository: dagger.Lazy<DownloadRepository>,
) : PlayedStateSync {

    override suspend fun flip(itemId: String, played: Boolean): Result<Unit> {
        // Offline: apply locally for immediate UI feedback and stage the flip
        // in the outbox so PlaybackSyncWorker delivers it on reconnect.
        if (offlineModeManager.isOffline) {
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatching { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
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
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            // Auto-delete-after-watch: item was just marked played — if the
            // user opted in and a finished download exists for it, remove it
            // now. The flip already succeeded, so a cleanup failure must never
            // bubble up to the caller.
            if (played) maybeAutoDeleteAfterWatch(itemId)
        } else {
            // Online but the call failed (transient 5xx, auth drop). Don't lose
            // the user's intent: apply locally and enqueue for retry.
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatching { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
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
            runCatching { offlineRepository.applyFavoriteState(itemId, target) }
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
        val current = runCatching { offlineRepository.getOfflineItem(itemId)?.isFavorite }.getOrNull() ?: false
        val target = !current
        runCatching { offlineRepository.applyFavoriteState(itemId, target) }
        runCatching { playbackOutboxRepository.enqueueFavoriteState(itemId, target) }
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
            if (!downloadsStore.get().downloads.first().autoDeleteAfterWatch) return
            val download = downloadRepository.get().getDownloadByMediaItemId(itemId) ?: return
            if (download.status != DownloadStatus.COMPLETED) return
            runCatching { downloadRepository.get().deleteDownload(download.id) }
                .onFailure { Log.w(TAG, "Auto-delete-after-watch failed for $itemId", it) }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-delete-after-watch lookup failed for $itemId", e)
        }
    }

    override suspend fun reconcileOfflineRow(itemId: String): ComputeResult? {
        val offline = offlineRepository.getOfflineItem(itemId) ?: return null
        // Pull a fresh server view (bypass any cached detail so a stale cache
        // cannot mask a newer played/position state).
        val serverItem = mediaRepository.get().getMediaDetail(itemId, force = true).getOrNull()?.item ?: return null

        // Favorite is a user preference shared across devices, so the server is
        // authoritative: if it disagrees with the local row, adopt the server's
        // state. No timestamp tiebreak — a favorite flip made on another device
        // must propagate here regardless of local activity. Best-effort; the
        // played-state reconciliation below runs regardless of outcome.
        if (serverItem.isFavorite != offline.isFavorite) {
            runCatching { offlineRepository.applyFavoriteState(itemId, serverItem.isFavorite) }
        }

        // Server watched (e.g. finished online) always wins — reset the local
        // row so a later offline resume starts the next episode / 0, not a
        // stale half-watched position.
        if (serverItem.isPlayed) {
            offlineRepository.updatePlaybackProgress(
                itemId = itemId,
                positionTicks = 0L,
                percentage = 100.0,
                isPlayed = true,
            )
            return ComputeResult.PLAYED
        }

        // Server unplayed but local played: the user marked the item (or its
        // season/series) unwatched online and that change has not yet reached
        // the local store. Mirror the flip — including the hierarchy cascade —
        // so the offline screen does not show stale watched state.
        if (offline.isPlayed) {
            offlineRepository.applyPlayedState(itemId, isPlayed = false)
            return ComputeResult.UNPLAYED
        }

        // Otherwise the most recent activity wins. Both sides are epoch-millis
        // at heart: the server's `lastPlayedDate` arrives as an ISO string
        // (kotlinx-datetime Instant -> "...Z", or a LocalDateTime when the
        // server omits the offset), and the offline row stores
        // OffsetDateTime.now().toString(). `parseIsoToEpochMillis` accepts
        // both shapes so the comparison is zone-correct regardless of source.
        val serverMillis = parseIsoToEpochMillis(serverItem.lastPlayedDate) ?: return ComputeResult.NOOP
        if (serverMillis > System.currentTimeMillis()) {
            // Sanity guard against future-dated server clocks.
            return ComputeResult.NOOP
        }
        val offlineMillis = offline.lastPlayedDate?.let { parseIsoToEpochMillis(it) } ?: 0L
        if (serverMillis <= offlineMillis) return ComputeResult.NOOP

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
        return ComputeResult.POSITION_UPDATED
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
