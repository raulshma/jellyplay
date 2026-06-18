package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenMediaRepositoryImpl @Inject constructor(
    private val seenMediaDao: SeenMediaDao,
) : SeenMediaRepository {

    override suspend fun count(): Int = seenMediaDao.count()

    override suspend fun getSeenIds(itemIds: List<String>): Set<String> =
        seenMediaDao.getSeenIds(itemIds).toSet()

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
