package com.raulshma.jellyplay.core.notification.worker

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeriesNotificationReads
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.SeriesNotificationConfig
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Calendar
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate

class NewMediaCheckWorker(
    appContext: Context,
    private val workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository,
    /** D12: the series-notification family seam (the per-series Next Up diff read). */
    private val seriesNotificationReads: SeriesNotificationReads,
    private val seenMediaRepository: SeenMediaRepository,
    private val notificationStore: NotificationStore,
    private val dispatcher: NotificationDispatcher,
    private val scheduler: NotificationScheduler,
    /** Clock seam (D3) for the seen-media pruning window + seenAt stamps. */
    private val timeSource: TimeSource,
    /**
     * D12's *arr air-time accelerator reads the existing calendar ingestion
     * (the home "Coming Soon" source) through this seam. Best-effort: every
     * accelerator failure is swallowed — the periodic poller stays the safety
     * net.
     */
    private val arrRepository: ArrRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = notificationStore.notification.first().notificationPreferences
        if (!prefs.enabled) {
            scheduler.cancel()
            return Result.success()
        }

        if (isInQuietHours(prefs)) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return Result.success()
        }

        return try {
            checkForNewMedia(prefs)
            // D12's second pass: per-series new-episode diffs + the *arr
            // accelerator re-schedule. Shares the master gate's failure
            // mapping (IO → retry, the one-shot cap applied below).
            checkSeriesNewEpisodes(prefs)
            Result.success()
        } catch (e: SocketTimeoutException) {
            retryOrFail()
        } catch (e: IOException) {
            retryOrFail()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("NewMediaCheckWorker", "New media check failed", e)
            Result.failure()
        }
    }

    /**
     * The retry/fail split: the periodic run keeps the unbounded retry it has
     * always had (the next period retries anyway), while the *arr
     * accelerator's one-shot runs — recognizable by the series tag — cap
     * their retries ([MAX_ONE_SHOT_RETRIES], the TvWatchNextWorker pattern)
     * so a permanently-failing episode check dies instead of backing off
     * forever.
     */
    private fun retryOrFail(): Result =
        if (workerParams.tags.contains(NotificationScheduler.SERIES_CHECK_WORK_TAG) &&
            runAttemptCount >= MAX_ONE_SHOT_RETRIES
        ) {
            Result.failure()
        } else {
            Result.retry()
        }

    private suspend fun checkForNewMedia(prefs: NotificationPreferences) {
        val foldersResult = mediaRepository.getLibraryFolders()
        val folders = foldersResult.getOrDefault(emptyList())
        if (folders.isEmpty()) return

        val enabledFolders = folders.filter { folder ->
            val config = prefs.libraryConfigs[folder.id]
            config == null || config.enabled
        }
        if (enabledFolders.isEmpty()) return

        val isFirstScan = seenMediaRepository.count() == 0

        // Per-folder fetches are independent (each folder's seen-id set is keyed
        // by that folder's items), so they can run concurrently. A small gate
        // keeps peak server load bounded — replaces the prior naive 200 ms
        // per-folder `delay` that serialized every fetch behind N network RTTs.
        val fetchGate = Semaphore(MAX_CONCURRENT_FOLDER_FETCHES)

        // Concurrent-safe accumulator of every item id the live library reported
        // across all enabled folders this run. Used after the fan-out to evict
        // orphan seen_media rows (items deleted server-side) so a future re-add
        // of the same id can re-trigger a notification instead of being muted
        // forever by a stale row. See SeenMediaRepository.reconcileAgainstLiveItemIds.
        val liveItemIds = ConcurrentHashMap.newKeySet<String>()

        // A null from the transform means "worker stopped" (fetchFolderNewItems
        // also returns null for "nothing new") — a caller policy, dropped here
        // via filterNotNull rather than treated as a failure.
        val newItemsByLibrary = fetchGate
            .mapConcurrent(enabledFolders) { folder ->
                if (isStopped) null
                else fetchFolderNewItems(folder, prefs, isFirstScan, liveItemIds)
            }
            .filterNotNull()
            .toMap()

        val thirtyDaysAgo = timeSource.nowEpochMillis() - THIRTY_DAYS_MS
        seenMediaRepository.pruneOlderThan(thirtyDaysAgo)

        // Reconcile seen_media against the live ids gathered this run. Skipped on
        // the first scan (nothing tracked yet) and when no folder returned items
        // (reconcileAgainstLiveItemIds treats an empty live set as "no information"
        // to avoid a mass-delete on a transiently empty scan).
        if (!isFirstScan && liveItemIds.isNotEmpty()) {
            seenMediaRepository.reconcileAgainstLiveItemIds(liveItemIds)
        }

        if (newItemsByLibrary.isNotEmpty()) {
            dispatcher.dispatch(newItemsByLibrary, prefs)
        }
    }

    // ── D12: the per-series second pass ─────────────────────────────────

    /**
     * The opted-in series' new-episode pass: for each series whose
     * [com.raulshma.jellyplay.core.model.SeriesNotificationConfig] is
     * enabled, fetch the series' next-up episode (`getSeriesNextUpEpisode` —
     * the spec's `getNextUp(seriesId, enableResumable=false)` read; a
     * half-watched episode is not "new"), diff against the config's baseline,
     * notify on the shared new-episodes channel, and write the baseline back.
     * Then re-schedule the *arr accelerator one-shots for the same series.
     *
     * [com.raulshma.jellyplay.core.model.NotificationPreferences.newEpisodesEnabled]
     * is the family's master switch — OFF suppresses ALL series work (the
     * diff pass and the accelerator scheduling alike), mirroring how the
     * first pass sits behind the global `enabled` gate. Quiet hours and the
     * POST_NOTIFICATIONS gate are inherited: [doWork] returns before this
     * pass when either applies.
     */
    private suspend fun checkSeriesNewEpisodes(prefs: NotificationPreferences) {
        if (!prefs.newEpisodesEnabled) return
        val optedIn = prefs.seriesConfigs.filterValues { it.enabled }
        if (optedIn.isEmpty()) return

        // The *arr calendar window is series-independent: refresh + read it
        // once for the whole pass instead of per series. Best-effort — any
        // failure leaves the accelerator off this run; the periodic poller
        // stays the safety net. Cancellation passes through.
        val calendarRows = runCatchingRethrowingCancellation {
            val today = timeSource.today(ZoneId.systemDefault()).toKotlinLocalDate()
            val end = today.plus(SERIES_ACCELERATOR_WINDOW_DAYS, DateTimeUnit.DAY)
            arrRepository.refreshCalendar(today, end)
            arrRepository.calendar(today, end).first()
        }.getOrNull().orEmpty()

        optedIn.forEach { (seriesId, config) ->
            if (isStopped) return
            checkSeriesNewEpisode(seriesId, config, prefs)
            scheduleSeriesAccelerators(seriesId, calendarRows)
        }
    }

    /**
     * One series' newest-episode check + the baseline write-back. The diff
     * decision is the pure [decideSeriesEpisode]:
     *  - [SeriesEpisodeDecision.BASELINE] — first check after opt-in (no
     *    baseline yet): record the newest id WITHOUT notifying (the
     *    worker's first-scan anti-spam rule at series granularity);
     *  - [SeriesEpisodeDecision.NOTIFY] — a genuinely newer episode:
     *    dispatch, then write the new baseline;
     *  - [SeriesEpisodeDecision.NONE] — nothing new (or no episode read):
     *    only the check stamp moves.
     */
    private suspend fun checkSeriesNewEpisode(
        seriesId: String,
        config: SeriesNotificationConfig,
        prefs: NotificationPreferences,
    ) {
        val newest = seriesNotificationReads.getSeriesNextUpEpisode(seriesId)
            .getOrNull()
            ?: return
        val now = timeSource.nowEpochMillis()
        val updated = when (decideSeriesEpisode(config.lastNotifiedEpisodeId, newest.id)) {
            SeriesEpisodeDecision.NONE ->
                config.copy(lastCheckAt = now)
            SeriesEpisodeDecision.BASELINE ->
                config.copy(lastNotifiedEpisodeId = newest.id, lastCheckAt = now)
            SeriesEpisodeDecision.NOTIFY -> {
                dispatcher.dispatchSeriesNewEpisode(seriesId, newest, prefs)
                config.copy(lastNotifiedEpisodeId = newest.id, lastCheckAt = now)
            }
        }
        notificationStore.updateSeriesNotificationConfigs { configs ->
            configs + (seriesId to updated)
        }
    }

    /**
     * The *arr air-time accelerator (best-effort — every failure swallowed;
     * the poller above stays the safety net): resolve the series' tvdb id
     * (the detail screen's `arrServerResolve` enrichment's providerIds
     * precedent), filter the pass-level [calendarRows] (refreshed once in
     * [checkSeriesNewEpisodes]) to this series, and schedule a KEEP-idempotent
     * one-shot check per upcoming Sonarr episode row at airTime + buffer. A
     * later pass re-scheduling the same (series, episode) pair is a
     * WorkManager KEEP no-op.
     */
    private suspend fun scheduleSeriesAccelerators(
        seriesId: String,
        calendarRows: List<ArrCalendarItem>,
    ) {
        if (calendarRows.isEmpty()) return
        val tvdbId = mediaRepository.getMediaDetail(seriesId)
            .getOrNull()
            ?.providerIds?.get("tvdb")?.toIntOrNull()
            ?: return
        val now = timeSource.nowEpochMillis()
        calendarRows.asSequence()
            .filter { it.mediaType == ArrMediaType.SERIES && it.tvdbId == tvdbId }
            .forEach { row ->
                val delayMs = acceleratorDelayMillis(
                    airDateUtc = row.airDateUtc,
                    bufferMs = SERIES_NOTIFY_BUFFER_MS,
                    nowEpochMs = now,
                ) ?: return@forEach
                scheduler.enqueueSeriesEpisodeCheck(
                    seriesId = seriesId,
                    episodeKey = acceleratorEpisodeKey(row.tvdbId, row.airDateUtc),
                    initialDelay = java.time.Duration.ofMillis(delayMs),
                )
            }
    }

    /**
     * Fetches the latest media for a single library folder and records any
     * never-seen-before items. Returns the folder → new-items pair (or null if
     * nothing new was found, the worker was cancelled, or this is the first
     * scan where notifications would be noise).
     *
     * Each folder's seen-id lookups are keyed only by items returned for that
     * same folder, so concurrent invocations across distinct folders do not
     * race on shared state.
     */
    private suspend fun fetchFolderNewItems(
        folder: LibraryFolder,
        prefs: NotificationPreferences,
        isFirstScan: Boolean,
        liveItemIds: MutableSet<String>,
    ): Pair<LibraryFolder, List<com.raulshma.jellyplay.core.model.MediaItem>>? {
        if (isStopped) return null

        val latestResult = mediaRepository.getLatestMedia(
            parentId = folder.id,
            limit = prefs.maxPerCheck,
        )
        val latest = latestResult.getOrDefault(emptyList())
        if (latest.isEmpty()) return null

        val config = prefs.libraryConfigs[folder.id]
        val filtered = if (config != null && config.mediaTypes.isNotEmpty()) {
            latest.filter { it.mediaType.name in config.mediaTypes }
        } else {
            latest
        }
        if (filtered.isEmpty()) return null

        // Record the live ids this folder returned so the caller can reconcile
        // seen_media against them. Done before the new-item filter so the
        // reconciliation sees the full recent set, not just newly-discovered ids.
        liveItemIds.addAll(filtered.map { it.id })

        val itemIds = filtered.map { it.id }
        val seenIds = seenMediaRepository.getSeenIds(itemIds)
        val newItems = filtered.filter { it.id !in seenIds }

        if (newItems.isEmpty()) return null

        seenMediaRepository.markAsSeen(
            newItems.map { item ->
                SeenMediaRecord(
                    itemId = item.id,
                    libraryId = folder.id,
                    mediaType = item.mediaType.name,
                    seenAtEpochMs = timeSource.nowEpochMillis(),
                )
            }
        )
        // On the first scan everything is "new" — suppress notifications to
        // avoid spamming the user with the entire library catalog.
        return if (isFirstScan) null else folder to newItems
    }

    private fun isInQuietHours(prefs: NotificationPreferences): Boolean =
        isInQuietHours(
            currentMinutes = currentMinutesOfDay(),
            quietHoursEnabled = prefs.quietHoursEnabled,
            start = prefs.quietHoursStart,
            end = prefs.quietHoursEnd,
        )

    private fun currentMinutesOfDay(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    companion object {
        const val WORK_TAG = "notification"
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        /** Bounds concurrent per-folder network fetches so a server with many
         *  enabled libraries is not hit with N simultaneous requests. */
        private const val MAX_CONCURRENT_FOLDER_FETCHES = 4

        // ── D12 series-pass constants ───────────────────────────────────

        /** The *arr calendar window the accelerator reads (days ahead). */
        private const val SERIES_ACCELERATOR_WINDOW_DAYS = 7

        /**
         * Fired past the air time so the Sonarr download + the Jellyfin
         * library scan have a chance to land before the check looks.
         */
        private const val SERIES_NOTIFY_BUFFER_MS = 30L * 60 * 1000

        /** The one-shot retry cap (the TvWatchNextWorker pattern). */
        private const val MAX_ONE_SHOT_RETRIES = 3

        /**
         * The pure diff decision behind [checkSeriesNewEpisode]. A null
         * baseline (first check after opt-in) yields [SeriesEpisodeDecision.BASELINE]
         * — the newest id is recorded but nothing notifies; a different id
         * yields NOTIFY; the same id (or an empty newest read) yields NONE.
         */
        internal fun decideSeriesEpisode(
            lastNotifiedEpisodeId: String?,
            newestEpisodeId: String?,
        ): SeriesEpisodeDecision = when {
            newestEpisodeId.isNullOrEmpty() -> SeriesEpisodeDecision.NONE
            lastNotifiedEpisodeId == null -> SeriesEpisodeDecision.BASELINE
            newestEpisodeId == lastNotifiedEpisodeId -> SeriesEpisodeDecision.NONE
            else -> SeriesEpisodeDecision.NOTIFY
        }

        /**
         * The accelerator's per-episode identity: the calendar row carries no
         * Jellyfin episode id, so (tvdb id, air-date stamp) — unique per
         * airing — keys the idempotent one-shot name.
         */
        internal fun acceleratorEpisodeKey(tvdbId: Int?, airDateUtc: String?): String =
            "${tvdbId ?: 0}:$airDateUtc"

        /**
         * The one-shot initial delay for an air time: null when the stamp is
         * unparseable (never schedule on garbage) or already fired (the
         * poller covers aired episodes); otherwise airTime + buffer − now.
         */
        internal fun acceleratorDelayMillis(
            airDateUtc: String?,
            bufferMs: Long,
            nowEpochMs: Long,
        ): Long? {
            val airMs = airDateUtc?.let { stamp ->
                runCatching { java.time.Instant.parse(stamp).toEpochMilli() }.getOrNull()
            } ?: return null
            val fireAt = airMs + bufferMs
            if (fireAt <= nowEpochMs) return null
            return fireAt - nowEpochMs
        }

        /**
         * Pure helper that decides whether [currentMinutes] falls inside a quiet-hours window
         * defined by [start], [end] (both in minutes-of-day, 0..1439). Handles overnight
         * wraparound (e.g. 22:00 → 07:00). Extracted from `isInQuietHours(NotificationPreferences)`
         * so it can be unit tested without `Calendar`.
         */
        internal fun isInQuietHours(
            currentMinutes: Int,
            quietHoursEnabled: Boolean,
            start: Int,
            end: Int,
        ): Boolean {
            if (!quietHoursEnabled) return false
            return if (start > end) {
                // Overnight window wraps past midnight: e.g. 22:00 → 07:00.
                currentMinutes >= start || currentMinutes < end
            } else {
                // Same-day window: e.g. 13:00 → 14:00.
                currentMinutes in start until end
            }
        }
    }

    /** The diff outcome [decideSeriesEpisode] reports for one series check. */
    internal enum class SeriesEpisodeDecision { NONE, BASELINE, NOTIFY }
}
