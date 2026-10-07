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

    /** Every bookmark across every item — the sync adapter's snapshot read. */
    @Query("SELECT * FROM book_bookmarks ORDER BY itemId ASC, positionTicks ASC, createdAt ASC")
    suspend fun getAll(): List<BookBookmarkEntity>

    @Query("DELETE FROM book_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM book_bookmarks WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    /** The sync tombstone read: every row at one (itemId, positionTicks) join key. */
    @Query("DELETE FROM book_bookmarks WHERE itemId = :itemId AND positionTicks = :positionTicks")
    suspend fun deleteAtPosition(itemId: String, positionTicks: Long)

    @Query("SELECT COUNT(*) FROM book_bookmarks WHERE itemId = :itemId")
    suspend fun countByItemId(itemId: String): Int
}
