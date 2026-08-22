package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

public object ScanWorkerHelper {

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
        val stubs = mutableListOf<MediaItemStub>()
        var hasMore = true
        var totalCount = 0

        while (hasMore) {
            if (isStopped()) break
            val result = fetchPage(startIndex, BATCH_SIZE)
            totalCount = result.first
            result.second.mapTo(stubs, mapToStub)
            startIndex += BATCH_SIZE
            hasMore = result.second.size >= BATCH_SIZE && stubs.size < MAX_ITEMS

            if (hasMore) delay(INTER_BATCH_DELAY_MS)

            // `progress` is the "items checked" count surfaced in the UI as
            // "N items checked". Clamp to the server's reported total so the
            // final partial batch — where `startIndex` overshoots `totalCount`
            // (e.g. 200 vs 50) — does not display a count larger than the
            // population that was scanned.
            val scannedSoFar = if (totalCount > 0) minOf(startIndex, totalCount) else startIndex
            scanStateDao.update(
                entity.copy(
                    progress = scannedSoFar,
                    total = totalCount,
                    itemsFound = stubs.size,
                    status = ScanPhase.SCANNING.name,
                )
            )
        }

        // On completion, surface a self-consistent final count. `progress`
        // (items checked) and `total` (server-reported population) must not
        // diverge: clamp progress to total so the UI never shows "checked more
        // than exist". `itemsFound` stays as the post-filter count.
        val finalTotal = if (totalCount > 0) totalCount else stubs.size
        scanStateDao.update(
            entity.copy(
                status = ScanPhase.COMPLETED.name,
                progress = finalTotal,
                total = finalTotal,
                itemsFound = stubs.size,
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

public sealed interface ScanResult {
    data object Success : ScanResult
    data object Failure : ScanResult
}
