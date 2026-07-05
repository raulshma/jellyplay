package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenMediaRepositoryImpl @Inject constructor(
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
}
