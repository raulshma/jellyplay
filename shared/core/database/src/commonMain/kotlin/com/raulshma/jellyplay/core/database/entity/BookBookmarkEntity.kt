package com.raulshma.jellyplay.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A reader position bookmark for one library item
 * (docs/adr/0003-local-first-reader-marks.md: marks are local-first, per
 * item, per install — Jellyfin has no bookmark field, so portability is the
 * export channel, not a sync channel).
 *
 * [positionTicks] reuses the reading-progress encodings of
 * [com.raulshma.jellyplay.core.model.BookProgressPolicy] verbatim: paged
 * books (CBZ/CBR/PDF) store `pageIndex × 10_000`; reflowable EPUB books
 * store `floor(percent × 10_000_000)`. [cfi] carries the exact `epubcfi(...)`
 * anchor for EPUB bookmarks and is NULL for paged books (page-anchored).
 * The native side never parses a CFI beyond equality — anchoring stays in
 * the reader engine, and a CFI that no longer displays degrades to the
 * position encoding.
 *
 * No unique index: multiple bookmarks per item are expected, and two at the
 * same position are distinct rows (createdAt orders them).
 */
@Entity(
    tableName = "book_bookmarks",
    indices = [
        Index(value = ["itemId"]),
    ],
)
data class BookBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val positionTicks: Long,
    /** Exact `epubcfi(...)` anchor; NULL for paged books. Opaque — equality only. */
    val cfi: String?,
    val chapterLabel: String,
    val createdAt: Long,
)
