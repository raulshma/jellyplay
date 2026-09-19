package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the reader bookmark table
 * (docs/adr/0003-local-first-reader-marks.md). Follows the Flow-for-observe /
 * suspend-for-write convention used by the rest of this package (see
 * [SearchHistoryDao]); toggle-on-tap logic stays in the callers — the DAO
 * only persists and observes.
 */
@Dao
interface BookBookmarkDao {

    @Upsert
    suspend fun upsert(bookmark: BookBookmarkEntity)

    @Update
    suspend fun update(bookmark: BookBookmarkEntity)

    @Query("SELECT * FROM book_bookmarks WHERE itemId = :itemId ORDER BY positionTicks ASC, createdAt ASC")
    fun observeByItemId(itemId: String): Flow<List<BookBookmarkEntity>>

    @Query("DELETE FROM book_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM book_bookmarks WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("SELECT COUNT(*) FROM book_bookmarks WHERE itemId = :itemId")
    suspend fun countByItemId(itemId: String): Int
}
