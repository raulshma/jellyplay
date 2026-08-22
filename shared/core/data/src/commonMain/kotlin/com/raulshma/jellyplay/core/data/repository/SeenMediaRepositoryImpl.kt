package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity

class SeenMediaRepositoryImpl constructor(
    private val seenMediaDao: SeenMediaDao,
) : SeenMediaRepository {

    private companion object {
        /** SQLite allows at most 999 bound params per statement; stay safely under. */
        const val SEEN_IDS_QUERY_CHUNK_SIZE = 900
    }

    override suspend fun count(): Int = seenMediaDao.count()

    override suspend fun getSeenIds(itemIds: List<String>): Set<String> {
        // Android SQLite has a hard limit of 999 bound parameters per statement.
        // Chunk the input so a >999-element library does not throw
        // SQLiteBindOrColumnIndexOutOfRangeException.
        if (itemIds.isEmpty()) return emptySet()
        return itemIds.chunked(SEEN_IDS_QUERY_CHUNK_SIZE)
            .flatMap { seenMediaDao.getSeenIds(it) }
            .toSet()
    }

    override suspend fun markAsSeen(
        itemId: String,
        libraryId: String,
        mediaType: String,
        seenAtEpochMs: Long,
    ) {
        seenMediaDao.insertAll(
            listOf(
                SeenMediaEntity(
                    itemId = itemId,
                    libraryId = libraryId,
                    mediaType = mediaType,
                    seenAt = seenAtEpochMs,
                )
            )
        )
    }

    override suspend fun markAsSeen(records: Iterable<SeenMediaRecord>) {
        if (records.any()) {
            seenMediaDao.insertAll(
                records.map { record ->
                    SeenMediaEntity(
                        itemId = record.itemId,
                        libraryId = record.libraryId,
                        mediaType = record.mediaType,
                        seenAt = record.seenAtEpochMs,
                    )
                }
            )
        }
    }

    override suspend fun pruneOlderThan(cutoffEpochMs: Long) {
        seenMediaDao.pruneOlderThan(cutoffEpochMs)
    }

    override suspend fun reconcileAgainstLiveItemIds(liveItemIds: Set<String>): Int {
        // A reconcile against an empty live set would wipe the table; treat that
        // as "no information" rather than "everything deleted" so a transient
        // empty scan (e.g. a folder that failed to load) cannot mass-delete.
        if (liveItemIds.isEmpty()) return 0
        val allSeen = seenMediaDao.getAllSeenItemIds()
        if (allSeen.isEmpty()) return 0
        // Orphan = a tracked id the library no longer returns. Chunked to respect
        // SQLite's 999 bound-parameter ceiling (same ceiling getSeenIds honours).
        val orphans = (allSeen.toSet() - liveItemIds)
        if (orphans.isEmpty()) return 0
        var removed = 0
        for (chunk in orphans.chunked(SEEN_IDS_QUERY_CHUNK_SIZE)) {
            removed += seenMediaDao.deleteByItemIds(chunk)
        }
        return removed
    }
}
