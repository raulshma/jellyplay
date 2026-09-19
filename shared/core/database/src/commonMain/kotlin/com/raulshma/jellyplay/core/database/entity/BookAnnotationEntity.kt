package com.raulshma.jellyplay.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A text-anchored reader annotation (highlight / underline, with an optional
 * attached note) for one library item
 * (docs/adr/0003-local-first-reader-marks.md: local-first marks; Jellyfin has
 * no annotation field, so export is the portability contract).
 *
 * [cfi] is the exact `epubcfi(...)` range anchor produced by the reader
 * engine — stored opaque, never parsed natively beyond equality (a CFI that
 * no longer displays after a book replacement degrades to the chapter label).
 * [anchorText] is the excerpt captured at creation time, so the export and
 * the annotation sheet stay readable even when the anchor goes stale.
 *
 * [style] and [color] are stored as plain strings (the table's other enum-ish
 * columns — playback_outbox.eventType, downloads.status — follow the same
 * raw-string convention, decoded defensively one layer up): the value sets
 * are fixed by the reader feature —
 *  - style: `"HIGHLIGHT"` or `"UNDERLINE"`
 *  - color: `"YELLOW"`, `"GREEN"`, `"BLUE"` or `"RED"`
 * A value outside those sets (hand-edited DB, older/newer install) decodes to
 * the repository layer's documented default rather than crashing the sheet.
 *
 * [updatedAt] diverges from [createdAt] only after an edit (note / color /
 * style change); the annotation list orders by creation so a re-colored
 * highlight does not jump around.
 */
@Entity(
    tableName = "book_annotations",
    indices = [
        Index(value = ["itemId"]),
    ],
)
data class BookAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    /** Exact `epubcfi(...)` range anchor. Opaque — equality only. */
    val cfi: String,
    /** `"HIGHLIGHT"` or `"UNDERLINE"` (see class KDoc). */
    val style: String,
    /** `"YELLOW"`, `"GREEN"`, `"BLUE"` or `"RED"` (see class KDoc). */
    val color: String,
    /** Excerpt captured at creation time; keeps the mark readable when the CFI goes stale. */
    val anchorText: String,
    /** Optional attached note; NULL = highlight-only mark. */
    val note: String?,
    val chapterLabel: String,
    val createdAt: Long,
    val updatedAt: Long,
)
