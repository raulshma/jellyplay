package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Drains the [PlaybackOutboxRepository] — replaying START/PROGRESS/STOP events
 * captured while the device was offline — and reconciles the local offline
 * store against the server so resume positions stay consistent.
 *
 * Triggered:
 *   - On the Offline→Online network transition (immediate, via
 *     [PlaybackSyncReconnectListener]).
 *   - Periodically (backstop) via [PlaybackSyncScheduler].
 *
 * The drain replays outbox entries directly through [JellyfinApiClient] rather
 * than the repository, so a retry does not recurse back into the outbox.
 *
 * Latest-wins reconciliation: for each item that has a downloaded offline row,
 * the server's `MediaItem` is fetched and compared. If the server's
 * `lastPlayedDate` is newer than the local `recordedAt`, or the server reports
 * `isPlayed`, the offline row is overwritten from the server — fixing the
 * "watched half offline → finished online → back offline shows stale 50%"
 * case.
 */
@HiltWorker
class PlaybackSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outbox: PlaybackOutboxRepository,
    private val apiClient: JellyfinApiClient,
    private val offlineRepository: OfflineRepository,
    private val mediaRepository: MediaRepository,
    private val offlineModeManager: OfflineModeManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (offlineModeManager.isOffline) {
            // The constraints guarantee CONNECTED, but the user may have
            // toggled manual offline; defer until genuinely online.
            return Result.retry()
        }
        val pending = outbox.drain()
        if (pending.isEmpty()) return Result.success()

        val reconciledItems = mutableSetOf<String>()
        var anyFailure = false
        for (entry in pending) {
            val ok = runCatching { replay(entry) }.getOrElse { false }
            if (ok) {
                outbox.delete(entry.id)
                reconciledItems.add(entry.itemId)
            } else {
                anyFailure = true
            }
        }
        // Only reconcile after the push so the server's view reflects the
        // locally-recorded progress for these items.
        for (itemId in reconciledItems) {
            runCatching { reconcileOfflineRow(itemId) }
        }

        return when {
            !anyFailure -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> {
                Log.w(TAG, "PlaybackSync exhausted $MAX_RETRIES retries; ${outbox.count()} entries remain")
                Result.failure()
            }
        }
    }

    private suspend fun replay(entry: PlaybackOutboxEntry): Boolean =
        when (entry.eventType) {
            PlaybackOutboxEventType.START ->
                apiClient.reportPlaybackStart(
                    entry.itemId,
                    entry.sessionId,
                    entry.playMethod,
                ).isSuccess
            PlaybackOutboxEventType.PROGRESS ->
                apiClient.reportPlaybackProgress(
                    entry.itemId,
                    entry.sessionId,
                    entry.positionTicks,
                    entry.isPaused,
                    entry.playMethod,
                ).isSuccess
            PlaybackOutboxEventType.STOP ->
                apiClient.reportPlaybackStopped(
                    entry.itemId,
                    entry.sessionId,
                    entry.positionTicks,
                ).isSuccess
        }

    /**
     * Latest-wins reconciliation for downloaded items. Fetches the server's
     * [MediaItem] and, if the server is newer or marks the item played,
     * overwrites the local offline row so resume reads authoritative state.
     */
    private suspend fun reconcileOfflineRow(itemId: String) {
        val offline = offlineRepository.getOfflineItem(itemId) ?: return
        // Pull a fresh server view (bypass any cached detail so a stale cache
        // cannot mask a newer played/position state).
        mediaRepository.invalidateDetailCache(itemId)
        val serverItem = mediaRepository.getMediaDetail(itemId).getOrNull()?.item ?: return

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
            return
        }

        // Otherwise the most recent activity wins. The local `recordedAt` is
        // epoch-millis; the server `lastPlayedDate` is a bare ISO local
        // datetime string. Compare by parsing the server side in the system
        // zone (the same zone used to populate it — see JellyfinDtoMappers).
        val serverMillis = parseLocalToEpochMillis(serverItem.lastPlayedDate) ?: return
        if (serverMillis > System.currentTimeMillis()) {
            // Sanity guard against future-dated server clocks.
            return
        }
        val offlineMillis = offline.lastPlayedDate?.let { parseIsoToEpochMillis(it) } ?: 0L
        if (serverMillis <= offlineMillis) return

        val runTime = serverItem.runTimeTicks ?: offline.runTimeTicks
        val percentage = computePlayedPercentage(serverItem.playbackPositionTicks, runTime, isPlayed = false)
        offlineRepository.updatePlaybackProgress(
            itemId = itemId,
            positionTicks = serverItem.playbackPositionTicks,
            percentage = percentage,
            isPlayed = false,
        )
    }

    private fun computePlayedPercentage(positionTicks: Long?, runTimeTicks: Long?, isPlayed: Boolean): Double =
        when {
            isPlayed -> 100.0
            positionTicks == null || positionTicks <= 0L -> 0.0
            runTimeTicks == null || runTimeTicks <= 0L -> 0.0
            else -> ((positionTicks.toDouble() / runTimeTicks.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        }

    private fun parseLocalToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            // The mapper produces a bare LocalDateTime (no offset). Interpret
            // it in the system zone — the same zone the SDK used to produce it.
            LocalDateTime.parse(value, ISO_PARSER)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun parseIsoToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        // Offline rows are stamped via OffsetDateTime.now().toString() in
        // OfflineRepositoryImpl, so prefer an offset-aware parse and fall back
        // to the bare local form.
        return runCatching {
            java.time.OffsetDateTime.parse(value, ISO_OFFSET_PARSER).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(value, ISO_PARSER)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.playback_sync_periodic"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.playback_sync_now"
        const val WORK_TAG = "playback_sync"

        private const val TAG = "PlaybackSyncWorker"
        private const val MAX_RETRIES = 3
        private val ISO_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val ISO_OFFSET_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
