package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
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

    @Upsert
    suspend fun upsertAll(annotations: List<BookAnnotationEntity>)

    @Update
    suspend fun update(annotation: BookAnnotationEntity)

    @Query("SELECT * FROM book_annotations WHERE itemId = :itemId ORDER BY createdAt ASC")
    fun observeByItemId(itemId: String): Flow<List<BookAnnotationEntity>>

    /**
     * Every stored annotation across all items, ordered for stable grouping —
     * the sync adapter's snapshot read (JellyPlayReaderSyncAdapter groups by
     * `itemId` into one wire value per book). Suspend (one read, no
     * observation) like the rest of the write-side queries here.
     */
    @Query("SELECT * FROM book_annotations ORDER BY itemId ASC, createdAt ASC")
    suspend fun getAll(): List<BookAnnotationEntity>

    @Query("SELECT * FROM book_annotations WHERE id = :id")
    suspend fun getById(id: Long): BookAnnotationEntity?

    @Query("DELETE FROM book_annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM book_annotations WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM book_annotations WHERE itemId IN (:itemIds)")
    suspend fun deleteByItemIds(itemIds: Collection<String>)

    @Query("SELECT COUNT(*) FROM book_annotations WHERE itemId = :itemId")
    suspend fun countByItemId(itemId: String): Int

    /**
     * The sync adopt batch (JellyPlayReaderSyncAdapter.applyRemote): the
     * whole pulled batch in ONE transaction — every touched book's rows go,
     * then the pulled lists land. Keys are one per book, so the batch
     * end-state is exactly the per-key delete/upsert pairs'.
     */
    @Transaction
    suspend fun adoptAnnotations(
        deletedItemIds: Collection<String>,
        upserts: List<BookAnnotationEntity>,
    ) {
        if (deletedItemIds.isNotEmpty()) deleteByItemIds(deletedItemIds)
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }
}
