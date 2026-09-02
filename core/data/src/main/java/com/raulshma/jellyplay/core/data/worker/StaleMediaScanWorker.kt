package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CancellationException
import java.io.IOException

class StaleMediaScanWorker(
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
        } catch (ce: CancellationException) {
            // Honour structured concurrency — never swallow cancellation.
            throw ce
        } catch (e: IOException) {
            // Transient network failure on page N of M: the helper persists
            // progress per batch so a retry resumes cleanly rather than
            // re-scanning from page 0.
            Log.w(TAG, "Stale media scan hit transient IO failure (attempt ${runAttemptCount + 1})", e)
            ScanWorkerHelper.markFailed(scanStateDao, entity)
            Result.retry()
        } catch (e: Exception) {
            Log.w(TAG, "Stale media scan failed permanently", e)
            ScanWorkerHelper.markFailed(scanStateDao, entity)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "StaleMediaScanWorker"
    }
}
