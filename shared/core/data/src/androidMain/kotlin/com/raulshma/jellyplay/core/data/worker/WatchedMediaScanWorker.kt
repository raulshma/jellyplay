package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException

class WatchedMediaScanWorker(
    appContext: Context,
    workerParams: WorkerParameters,
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
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            Log.w(TAG, "Watched media scan hit transient IO failure (attempt ${runAttemptCount + 1})", e)
            ScanWorkerHelper.markFailed(scanStateDao, entity)
            Result.retry()
        } catch (e: Exception) {
            Log.w(TAG, "Watched media scan failed permanently", e)
            ScanWorkerHelper.markFailed(scanStateDao, entity)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "WatchedMediaScanWorker"
    }
}
