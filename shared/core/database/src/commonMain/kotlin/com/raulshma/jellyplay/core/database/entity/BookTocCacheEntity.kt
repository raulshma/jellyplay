package com.raulshma.jellyplay.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The cached table-of-contents of one book, written by the reader when the
 * TOC resolves (EPUB `toc` event, PDF outline parse, comic page count) and
 * read by the media-detail screen so "Contents" shows without re-opening or
 * re-parsing the file. One row per item — an upsert refreshes it on every
 * reader open, so a replaced/repaired book file self-heals on the next read.
 *
 * [entriesJson] is the serialized `List<BookTocEntry>` (core:model) — the
 * shape is shared with the server progress story only in spirit; Jellyfin
 * has no TOC field, so like bookmarks/annotations (ADR 0003) this is a
 * local-first, per-install surface.
 *
 * [pageCount] is the paged book's page count (CBZ/CBR/PDF) — surfaced on the
 * detail screen as "Page N of M". 0 for a reflowable EPUB (no pages) and
 * when unknown.
 */
@Entity(tableName = "book_toc_cache")
data class BookTocCacheEntity(
    @PrimaryKey val itemId: String,
    /** The [com.raulshma.jellyplay.core.model.BookFormat] name the cache was built from. */
    val format: String,
    val pageCount: Int,
    val entriesJson: String,
    val updatedAt: Long,
)
