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

@HiltWorker
class StaleMediaScanWorker @AssistedInject constructor(
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

            ScanWorkerHelper.executePaginatedScan(
                scanId = scanId,
                scanStateDao = scanStateDao,
                entity = entity,
                isStopped = { isStopped },
                fetchPage = { startIndex, limit ->
                    apiClient.getStaleItems(
                        daysThreshold = config.daysThreshold,
                        includeNeverPlayed = config.includeNeverPlayed,
                        includeItemTypes = config.includeItemTypes.toList(),
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
                        sizeText = item.sizeText,
                        detail = if (item.daysSincePlay > 0) "${item.daysSincePlay} days ago" else "Never played",
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
