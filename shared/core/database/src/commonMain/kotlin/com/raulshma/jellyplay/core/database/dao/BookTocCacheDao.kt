package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.raulshma.jellyplay.core.database.entity.BookTocCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the book TOC cache (`book_toc_cache`). Follows the
 * Flow-for-observe / suspend-for-write convention of the other reader DAOs
 * ([BookBookmarkDao]); the repository owns the JSON decode and the
 * write-decision logic — this surface only persists and observes.
 */
@Dao
interface BookTocCacheDao {

    @Upsert
    suspend fun upsert(cache: BookTocCacheEntity)

    @Query("SELECT * FROM book_toc_cache WHERE itemId = :itemId")
    fun observeByItemId(itemId: String): Flow<BookTocCacheEntity?>

    @Query("SELECT * FROM book_toc_cache WHERE itemId = :itemId")
    suspend fun getByItemId(itemId: String): BookTocCacheEntity?

    @Query("DELETE FROM book_toc_cache WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM book_toc_cache")
    suspend fun clear()
}
