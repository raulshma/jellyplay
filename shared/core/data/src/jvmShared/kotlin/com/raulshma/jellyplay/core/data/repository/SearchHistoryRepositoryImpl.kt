package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.SearchHistoryDao
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchHistoryRepositoryImpl constructor(
    private val dao: SearchHistoryDao,
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
                searchedAt = System.currentTimeMillis(),
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
