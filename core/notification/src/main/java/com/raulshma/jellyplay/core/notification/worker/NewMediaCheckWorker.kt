package com.raulshma.jellyplay.core.notification.worker

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Calendar

@HiltWorker
class NewMediaCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val seenMediaRepository: SeenMediaRepository,
    private val preferencesStore: UserPreferencesStore,
    private val dispatcher: NotificationDispatcher,
    private val scheduler: NotificationScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = preferencesStore.notificationPreferences.first()
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
        val newItemsByLibrary = mutableMapOf<LibraryFolder, List<com.raulshma.jellyplay.core.model.MediaItem>>()

        for (folder in enabledFolders) {
            if (isStopped) break
            delay(200)

            val latestResult = mediaRepository.getLatestMedia(
                parentId = folder.id,
                limit = prefs.maxPerCheck,
            )
            val latest = latestResult.getOrDefault(emptyList())
            if (latest.isEmpty()) continue

            val config = prefs.libraryConfigs[folder.id]
            val filtered = if (config != null && config.mediaTypes.isNotEmpty()) {
                latest.filter { it.mediaType.name in config.mediaTypes }
            } else {
                latest
            }
            if (filtered.isEmpty()) continue

            val itemIds = filtered.map { it.id }
            val seenIds = seenMediaRepository.getSeenIds(itemIds)
            val newItems = filtered.filter { it.id !in seenIds }

            if (newItems.isNotEmpty()) {
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
                if (!isFirstScan) {
                    newItemsByLibrary[folder] = newItems
                }
            }
        }

        val thirtyDaysAgo = System.currentTimeMillis() - THIRTY_DAYS_MS
        seenMediaRepository.pruneOlderThan(thirtyDaysAgo)

        if (newItemsByLibrary.isNotEmpty()) {
            dispatcher.dispatch(newItemsByLibrary, prefs)
        }
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
