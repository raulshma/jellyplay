package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.BookTocCacheDao
import com.raulshma.jellyplay.core.database.entity.BookTocCacheEntity
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The decoded view of one `book_toc_cache` row. */
data class BookTocCache(
    val itemId: String,
    val format: BookFormat,
    /** Paged book page count (0 = reflowable/unknown). */
    val pageCount: Int,
    val entries: List<BookTocEntry>,
    val updatedAt: Long,
) {
    /** True when the cache carries anything the detail screen can render. */
    val isEmpty: Boolean get() = entries.isEmpty() && pageCount <= 0
}

/**
 * Local-first book table-of-contents cache (the `book_toc_cache` table).
 * The reader writes it whenever a book's TOC resolves (EPUB `toc` event,
 * PDF outline parse) or its page count becomes known (paged open); the
 * media-detail screen reads it so "Contents" + "Page N of M" render without
 * re-opening the file. Jellyfin has no TOC field, so like bookmarks and
 * annotations (ADR 0003) this is per-install local data.
 */
interface BookTocCacheRepository {

    /** The item's cached TOC (null when never opened / never probed). */
    fun observeToc(itemId: String): Flow<BookTocCache?>

    /** One-shot read — the probe-write path uses it to skip re-parsing. */
    suspend fun getToc(itemId: String): BookTocCache?

    /**
     * Write-through upsert. A decode failure of the stored JSON (corrupt
     * row) degrades to null like every other corrupt-row read in this
     * package — one bad row must not blank the reader or the detail screen.
     */
    suspend fun putToc(
        itemId: String,
        format: BookFormat,
        pageCount: Int,
        entries: List<BookTocEntry>,
    )

    suspend fun deleteToc(itemId: String)
}

/**
 * Null-object implementation: the [DetailViewModel] constructor default and
 * a stand-in for tests. (Every real platform — wasm included — binds the
 * Room-backed repository below.)
 */
class NoopBookTocCacheRepository : BookTocCacheRepository {
    override fun observeToc(itemId: String): Flow<BookTocCache?> =
        kotlinx.coroutines.flow.flowOf(null)
    override suspend fun getToc(itemId: String): BookTocCache? = null
    override suspend fun putToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) = Unit
    override suspend fun deleteToc(itemId: String) = Unit
}

/**
 * Room-backed implementation. commonMain like the reader marks repo — the
 * entity/DAO pair is commonMain Room 3. The entries list rides the row as a
 * JSON document; a corrupt decode degrades to an empty-entries cache rather
 * than throwing out of a Flow (the same stance as
 * [ReaderAnnotationsRepositoryImpl]'s enum parses).
 */
class BookTocCacheRepositoryImpl(
    private val dao: BookTocCacheDao,
    private val timeSource: EpochMillisSource,
    private val json: Json = Json,
) : BookTocCacheRepository {

    override fun observeToc(itemId: String): Flow<BookTocCache?> =
        dao.observeByItemId(itemId).map { it?.toDomain() }

    override suspend fun getToc(itemId: String): BookTocCache? = dao.getByItemId(itemId)?.toDomain()

    override suspend fun putToc(
        itemId: String,
        format: BookFormat,
        pageCount: Int,
        entries: List<BookTocEntry>,
    ) {
        dao.upsert(
            BookTocCacheEntity(
                itemId = itemId,
                format = format.name,
                pageCount = pageCount,
                entriesJson = json.encodeToString(entries),
                updatedAt = timeSource.nowEpochMillis(),
            ),
        )
    }

    override suspend fun deleteToc(itemId: String) = dao.deleteByItemId(itemId)

    private fun BookTocCacheEntity.toDomain(): BookTocCache? {
        val format = BookFormat.entries.firstOrNull { it.name == format } ?: return null
        val entries = runCatching { json.decodeFromString<List<BookTocEntry>>(entriesJson) }
            .getOrDefault(emptyList())
        return BookTocCache(
            itemId = itemId,
            format = format,
            pageCount = pageCount,
            entries = entries,
            updatedAt = updatedAt,
        )
    }
}
