package com.raulshma.jellyplay.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WatchedMediaScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: JellyfinApiClient,
    private val scanStateDao: ScanStateDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val scanId = inputData.getString("scanId") ?: return Result.failure()
        val entity = scanStateDao.getById(scanId) ?: return Result.failure()

        return try {
            val config = ScanWorkerHelper.json.decodeFromString<MediaCleanupConfig>(entity.configJson)
            val adminUserId = apiClient.currentUser.first()?.id ?: return Result.failure()

            ScanWorkerHelper.executePaginatedScan(
                scanId = scanId,
                scanStateDao = scanStateDao,
                entity = entity,
                isStopped = { isStopped },
                fetchPage = { startIndex, limit ->
                    apiClient.getWatchedItems(
                        userId = adminUserId,
                        includeItemTypes = config.includeItemTypes.toList(),
                        minDaysSincePlayed = config.minDaysSinceWatched,
                        keepFavorites = config.keepFavorites,
                        parentId = config.libraryIds.firstOrNull(),
                        startIndex = startIndex,
                        limit = limit,
                    ).getOrDefault(Pair(0, emptyList()))
                },
                mapToStub = { item ->
                    com.raulshma.jellyplay.core.model.MediaItemStub(
                        itemId = item.itemId,
                        name = item.name,
                        type = item.type,
                        sizeText = "",
                        detail = "Played ${item.playCount}x",
                    )
                },
            )
            Result.success()
        } catch (e: Exception) {
            ScanWorkerHelper.markFailed(scanStateDao, entity)
            Result.failure()
        }
    }
}
