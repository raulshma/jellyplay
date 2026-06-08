package com.raulshma.jellyplay.core.notification.worker

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.core.network.JellyfinApiClient
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
    private val apiClient: JellyfinApiClient,
    private val seenMediaDao: SeenMediaDao,
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
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private suspend fun checkForNewMedia(prefs: NotificationPreferences) {
        val foldersResult = apiClient.getLibraryFolders()
        val folders = foldersResult.getOrDefault(emptyList())
        if (folders.isEmpty()) return

        val enabledFolders = folders.filter { folder ->
            val config = prefs.libraryConfigs[folder.id]
            config == null || config.enabled
        }
        if (enabledFolders.isEmpty()) return

        val isFirstScan = seenMediaDao.count() == 0
        val newItemsByLibrary = mutableMapOf<LibraryFolder, List<com.raulshma.jellyplay.core.model.MediaItem>>()

        for (folder in enabledFolders) {
            if (isStopped) break
            delay(200)

            val latestResult = apiClient.getLatestMedia(
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
            val seenIds = seenMediaDao.getSeenIds(itemIds).toSet()
            val newItems = filtered.filter { it.id !in seenIds }

            if (newItems.isNotEmpty()) {
                seenMediaDao.insertAll(
                    newItems.map { item ->
                        SeenMediaEntity(
                            itemId = item.id,
                            libraryId = folder.id,
                            mediaType = item.mediaType.name,
                            seenAt = System.currentTimeMillis(),
                        )
                    }
                )
                if (!isFirstScan) {
                    newItemsByLibrary[folder] = newItems
                }
            }
        }

        val thirtyDaysAgo = System.currentTimeMillis() - THIRTY_DAYS_MS
        seenMediaDao.pruneOlderThan(thirtyDaysAgo)

        if (newItemsByLibrary.isNotEmpty()) {
            dispatcher.dispatch(newItemsByLibrary, prefs)
        }
    }

    private fun isInQuietHours(prefs: NotificationPreferences): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = prefs.quietHoursStart
        val end = prefs.quietHoursEnd
        return if (start > end) {
            currentMinutes >= start || currentMinutes < end
        } else {
            currentMinutes in start until end
        }
    }

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
