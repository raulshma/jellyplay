package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.database.dao.SearchHistoryDao
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * promotion from jvmShared: the impl is DAO + clock only, so it crosses to
 * commonMain once the clock edge narrows to the common
 * [EpochMillisSource] seam (JVM [TimeSource] fakes in jvmTest still satisfy
 * it through the supertype).
 */
class SearchHistoryRepositoryImpl constructor(
    private val dao: SearchHistoryDao,
    /** Clock seam for the persisted `searchedAt` stamp. */
    private val timeSource: EpochMillisSource,
) : SearchHistoryRepository {

    override fun getRecent(userId: String, limit: Int): Flow<List<SearchHistoryItem>> =
        dao.getRecent(userId, limit).map { entities ->
            entities.map { it.toItem() }
        }

    override suspend fun saveQuery(query: String, userId: String) {
        if (query.trim().length < 2) return
        dao.insertAndEvict(
            SearchHistoryEntity(
                query = query.trim(),
                userId = userId,
                searchedAt = timeSource.nowEpochMillis(),
            )
        )
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearAll(userId: String) {
        dao.clearAll(userId)
    }

    private fun SearchHistoryEntity.toItem() = SearchHistoryItem(
        id = id,
        query = query,
        searchedAt = searchedAt,
    )
}
