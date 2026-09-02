package com.raulshma.jellyplay.core.data.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun getRecent(userId: String, limit: Int = 50): Flow<List<SearchHistoryItem>>
    suspend fun saveQuery(query: String, userId: String)
    suspend fun deleteById(id: Long)
    suspend fun clearAll(userId: String)
}
