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
    /**
     * The dirty-write flush signal: fired (best-effort, never blocking the
     * local write) after every history mutation so the sync engine's
     * background one-shot can pick the change up (ADR 0011's dirty-write
     * trigger). Null in direct-construction tests.
     */
    private val onDirty: (suspend () -> Unit)? = null,
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
        notifyDirty()
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
        notifyDirty()
    }

    override suspend fun clearAll(userId: String) {
        dao.clearAll(userId)
        notifyDirty()
    }

    /** Best-effort flush signal — a failed trigger never fails the local write. */
    private suspend fun notifyDirty() {
        val signal = onDirty ?: return
        try {
            signal()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }

    private fun SearchHistoryEntity.toItem() = SearchHistoryItem(
        id = id,
        query = query,
        searchedAt = searchedAt,
    )
}
