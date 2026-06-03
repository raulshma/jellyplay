package com.raulshma.jellyplay.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json

@HiltWorker
class StaleMediaScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: JellyfinApiClient,
    private val scanStateDao: ScanStateDao,
) : CoroutineWorker(appContext, workerParams) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val scanId = inputData.getString("scanId") ?: return Result.failure()
        val entity = scanStateDao.getById(scanId) ?: return Result.failure()

        return try {
            val config = json.decodeFromString<MediaCleanupConfig>(entity.configJson)
            val batchSize = 200
            var startIndex = 0
            val allItems = mutableListOf<com.raulshma.jellyplay.core.model.StaleMediaItem>()
            var hasMore = true

            while (hasMore) {
                val result = apiClient.getStaleItems(
                    daysThreshold = config.daysThreshold,
                    includeNeverPlayed = config.includeNeverPlayed,
                    includeItemTypes = config.includeItemTypes.toList(),
                    parentId = config.libraryIds.firstOrNull(),
                    startIndex = startIndex,
                    limit = batchSize,
                ).getOrDefault(Pair(0, emptyList()))

                allItems.addAll(result.second)
                startIndex += batchSize
                hasMore = result.second.size >= batchSize

                scanStateDao.update(
                    entity.copy(
                        progress = startIndex,
                        total = result.first,
                        itemsFound = allItems.size,
                        status = ScanPhase.SCANNING.name,
                    )
                )
            }

            val stubs = allItems.map { item ->
                com.raulshma.jellyplay.core.model.MediaItemStub(
                    itemId = item.itemId,
                    name = item.name,
                    type = item.type,
                    sizeText = item.sizeText,
                    detail = if (item.daysSincePlay > 0) "${item.daysSincePlay} days ago" else "Never played",
                )
            }

            scanStateDao.update(
                entity.copy(
                    status = ScanPhase.COMPLETED.name,
                    progress = startIndex,
                    total = allItems.size,
                    itemsFound = allItems.size,
                    resultJson = json.encodeToString(
                        kotlinx.serialization.serializer<List<com.raulshma.jellyplay.core.model.MediaItemStub>>(),
                        stubs,
                    ),
                )
            )

            Result.success()
        } catch (e: Exception) {
            scanStateDao.update(entity.copy(status = ScanPhase.FAILED.name))
            Result.failure()
        }
    }
}
