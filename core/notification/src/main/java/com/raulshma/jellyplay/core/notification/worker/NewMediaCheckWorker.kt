package com.raulshma.jellyplay.core.notification.worker

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class NewMediaCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val seenMediaRepository: SeenMediaRepository,
    private val notificationStore: NotificationStore,
    private val dispatcher: NotificationDispatcher,
    private val scheduler: NotificationScheduler,
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
            Result.success()
        } catch (e: SocketTimeoutException) {
            Result.retry()
        } catch (e: IOException) {
            Result.retry()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("NewMediaCheckWorker", "New media check failed", e)
            Result.failure()
        }
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

        val newItemsByLibrary = coroutineScope {
            enabledFolders.map { folder ->
                async {
                    if (isStopped) return@async null

                    fetchGate.withPermit {
                        fetchFolderNewItems(folder, prefs, isFirstScan, liveItemIds)
                    }
                }
            }.awaitAll()
        }.filterNotNull().toMap()

        val thirtyDaysAgo = System.currentTimeMillis() - THIRTY_DAYS_MS
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
                    seenAtEpochMs = System.currentTimeMillis(),
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
}
