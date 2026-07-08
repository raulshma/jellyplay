package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

internal object ScanWorkerHelper {

    private const val BATCH_SIZE = 200
    private const val MAX_ITEMS = 10_000
    private const val INTER_BATCH_DELAY_MS = 500L

    val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun <T> executePaginatedScan(
        scanId: String,
        scanStateDao: ScanStateDao,
        entity: ScanStateEntity,
        isStopped: () -> Boolean,
        fetchPage: suspend (startIndex: Int, limit: Int) -> Pair<Int, List<T>>,
        mapToStub: (T) -> MediaItemStub,
    ): ScanResult {
        var startIndex = 0
        val allItems = mutableListOf<T>()
        var hasMore = true
        var totalCount = 0

        while (hasMore) {
            if (isStopped()) break
            val result = fetchPage(startIndex, BATCH_SIZE)
            totalCount = result.first
            allItems.addAll(result.second)
            startIndex += BATCH_SIZE
            hasMore = result.second.size >= BATCH_SIZE && allItems.size < MAX_ITEMS

            if (hasMore) delay(INTER_BATCH_DELAY_MS)

            scanStateDao.update(
                entity.copy(
                    progress = startIndex,
                    total = totalCount,
                    itemsFound = allItems.size,
                    status = ScanPhase.SCANNING.name,
                )
            )
        }

        val stubs = allItems.map(mapToStub)

        scanStateDao.update(
            entity.copy(
                status = ScanPhase.COMPLETED.name,
                progress = startIndex,
                total = allItems.size,
                itemsFound = allItems.size,
                resultJson = json.encodeToString(serializer<List<MediaItemStub>>(), stubs),
            )
        )

        return ScanResult.Success
    }

    suspend fun markFailed(scanStateDao: ScanStateDao, entity: ScanStateEntity) {
        // Re-read the current row so the in-flight progress/total/itemsFound/
        // resultJson that executePaginatedScan persisted during the scan are
        // preserved. The `entity` passed in was captured before scanning began
        // and would overwrite that progress with zeros if copied directly.
        val current = scanStateDao.getById(entity.scanId) ?: entity
        scanStateDao.update(current.copy(status = ScanPhase.FAILED.name))
    }
}

internal sealed interface ScanResult {
    data object Success : ScanResult
    data object Failure : ScanResult
}
