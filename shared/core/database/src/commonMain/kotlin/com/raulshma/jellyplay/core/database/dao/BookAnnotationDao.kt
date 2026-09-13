package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the reader highlight/underline table
 * (docs/adr/0003-local-first-reader-marks.md). Follows the Flow-for-observe /
 * suspend-for-write convention used by the rest of this package (see
 * [SearchHistoryDao]). Lists order by creation (`createdAt`, not
 * `updatedAt`) so an edited highlight does not jump to the top of the sheet.
 */
@Dao
interface BookAnnotationDao {

    @Upsert
    suspend fun upsert(annotation: BookAnnotationEntity)

    @Update
    suspend fun update(annotation: BookAnnotationEntity)

    @Query("SELECT * FROM book_annotations WHERE itemId = :itemId ORDER BY createdAt ASC")
    fun observeByItemId(itemId: String): Flow<List<BookAnnotationEntity>>

    @Query("SELECT * FROM book_annotations WHERE id = :id")
    suspend fun getById(id: Long): BookAnnotationEntity?

    @Query("DELETE FROM book_annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM book_annotations WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("SELECT COUNT(*) FROM book_annotations WHERE itemId = :itemId")
    suspend fun countByItemId(itemId: String): Int
}
