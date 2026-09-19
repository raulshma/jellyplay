package com.raulshma.jellyplay.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * How a reader annotation renders its anchor. Stored as its [name] in the
 * `book_annotations` table (raw string — see
 * [com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity]).
 * [Serializable] so the JSON export embeds it directly.
 */
@Serializable
enum class ReaderAnnotationStyle {
    HIGHLIGHT,
    UNDERLINE,
}

/**
 * Highlight color palette. Stored as its [name] in the `book_annotations`
 * table (raw string — see
 * [com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity]).
 * [Serializable] so the JSON export embeds it directly.
 */
@Serializable
enum class ReaderAnnotationColor {
    YELLOW,
    GREEN,
    BLUE,
    RED,
}

/**
 * A reader position bookmark (domain view of
 * [com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity]).
 * [positionTicks] reuses the [com.raulshma.jellyplay.core.model.BookProgressPolicy]
 * encodings verbatim: paged books store `pageIndex × 10_000`; EPUB books
 * store `floor(percent × 10_000_000)`. [cfi] is the exact `epubcfi(...)`
 * anchor for EPUB bookmarks, NULL for paged books.
 */
data class ReaderBookmark(
    val id: Long,
    val itemId: String,
    val positionTicks: Long,
    val cfi: String?,
    val chapterLabel: String,
    val createdAt: Long,
)

/**
 * A text-anchored highlight/underline with optional note (domain view of
 * [com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity]).
 * [anchorText] was captured at creation time, so the sheet and exports stay
 * readable even after the stored [cfi] goes stale (book replaced, chapter
 * reflowed).
 */
data class ReaderAnnotation(
    val id: Long,
    val itemId: String,
    val cfi: String,
    val style: ReaderAnnotationStyle,
    val color: ReaderAnnotationColor,
    val anchorText: String,
    val note: String?,
    val chapterLabel: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Reader marks (bookmarks, highlights, notes) — local-first per
 * docs/adr/0003-local-first-reader-marks.md: Jellyfin has no bookmark or
 * annotation surface, so marks live in the local Room database per item and
 * per install, and Markdown / JSON export (not a sync channel) is the
 * portability contract.
 *
 * Toggle-on-tap logic stays in the callers: this surface only persists,
 * observes, and exports. The reading-position ticks protocol to the server
 * is untouched by every operation here.
 */
interface ReaderAnnotationsRepository {

    /** The item's bookmarks ordered by reading position. */
    fun observeBookmarks(itemId: String): Flow<List<ReaderBookmark>>

    /** The item's annotations ordered by creation (edits do not reorder). */
    fun observeAnnotations(itemId: String): Flow<List<ReaderAnnotation>>

    suspend fun addBookmark(itemId: String, positionTicks: Long, cfi: String?, chapterLabel: String)

    suspend fun removeBookmark(id: Long)

    suspend fun addAnnotation(
        itemId: String,
        cfi: String,
        style: ReaderAnnotationStyle,
        color: ReaderAnnotationColor,
        anchorText: String,
        note: String?,
        chapterLabel: String,
    )

    /**
     * Applies a partial edit to one annotation: every NULL argument leaves
     * the stored value unchanged, and an empty-string [note] CLEARS the note
     * (an empty note is persisted as NULL so highlight-only marks stay
     * highlight-only). [updatedAt] is stamped on every call that finds the
     * row; a call for a missing id is a no-op.
     */
    suspend fun updateAnnotation(
        id: Long,
        note: String? = null,
        color: ReaderAnnotationColor? = null,
        style: ReaderAnnotationStyle? = null,
    )

    suspend fun deleteAnnotation(id: Long)

    /** Deletes every bookmark AND annotation of [itemId] (per-item reset path). */
    suspend fun deleteAllForItem(itemId: String)

    /**
     * Renders the given marks as a Markdown document (the human-readable
     * export form): `# {title}`, then a `## Bookmarks` list (`chapter —
     * location`) and a `## Highlights & Notes` section (blockquote anchor
     * text + note line). Empty sections are omitted; a bookmark's location
     * renders as `Page {n}` for paged marks (cfi == null) and `{pct}%` for
     * EPUB marks, both derived through
     * [com.raulshma.jellyplay.core.model.BookProgressPolicy]. Pure function —
     * reads nothing, writes nothing.
     */
    fun exportMarkdown(
        itemId: String? = null,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String

    /**
     * Renders the given marks as self-describing JSON (the machine export
     * form): a top-level `version` field (bumped on breaking shape changes;
     * consumers reject unknown versions rather than guessing), the optional
     * [itemId], and the full bookmark + annotation lists. Pure function.
     */
    fun exportJson(
        itemId: String? = null,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String
}
