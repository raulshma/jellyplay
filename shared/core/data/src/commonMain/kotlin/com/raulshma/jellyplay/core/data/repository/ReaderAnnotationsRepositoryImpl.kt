package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.database.dao.BookAnnotationDao
import com.raulshma.jellyplay.core.database.dao.BookBookmarkDao
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Room-backed [ReaderAnnotationsRepository] (docs/adr/0003-local-first-reader-marks.md).
 * commonMain by design — the DAOs and entities are commonMain Room 3.
 *
 * The persisted `style`/`color` columns are raw strings; they parse back
 * through the repo-wide [toEnumOrNull] seam so a corrupt stored value
 * degrades to the documented default instead of throwing — a throw here
 * would poison both observe flows and blank the sheet on one bad row (same
 * stance as [PlaybackOutboxRepositoryImpl]'s eventType parse).
 */
class ReaderAnnotationsRepositoryImpl constructor(
    private val bookmarkDao: BookBookmarkDao,
    private val annotationDao: BookAnnotationDao,
    /** Clock seam for the persisted createdAt/updatedAt stamps. */
    private val timeSource: EpochMillisSource,
) : ReaderAnnotationsRepository {

    override fun observeBookmarks(itemId: String): Flow<List<ReaderBookmark>> =
        bookmarkDao.observeByItemId(itemId).map { entities -> entities.map { it.toDomain() } }

    override fun observeAnnotations(itemId: String): Flow<List<ReaderAnnotation>> =
        annotationDao.observeByItemId(itemId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addBookmark(itemId: String, positionTicks: Long, cfi: String?, chapterLabel: String) {
        bookmarkDao.upsert(
            BookBookmarkEntity(
                itemId = itemId,
                positionTicks = positionTicks,
                cfi = cfi,
                chapterLabel = chapterLabel,
                createdAt = timeSource.nowEpochMillis(),
            )
        )
    }

    override suspend fun removeBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }

    override suspend fun addAnnotation(
        itemId: String,
        cfi: String,
        style: ReaderAnnotationStyle,
        color: ReaderAnnotationColor,
        anchorText: String,
        note: String?,
        chapterLabel: String,
    ) {
        val now = timeSource.nowEpochMillis()
        annotationDao.upsert(
            BookAnnotationEntity(
                itemId = itemId,
                cfi = cfi,
                style = style.name,
                color = color.name,
                anchorText = anchorText,
                note = note?.takeIf { it.isNotEmpty() },
                chapterLabel = chapterLabel,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun updateAnnotation(
        id: Long,
        note: String?,
        color: ReaderAnnotationColor?,
        style: ReaderAnnotationStyle?,
    ) {
        val stored = annotationDao.getById(id) ?: return
        annotationDao.update(
            stored.copy(
                note = when {
                    note == null -> stored.note
                    note.isEmpty() -> null
                    else -> note
                },
                color = color?.name ?: stored.color,
                style = style?.name ?: stored.style,
                updatedAt = timeSource.nowEpochMillis(),
            )
        )
    }

    override suspend fun deleteAnnotation(id: Long) {
        annotationDao.deleteById(id)
    }

    override suspend fun deleteAllForItem(itemId: String) {
        bookmarkDao.deleteByItemId(itemId)
        annotationDao.deleteByItemId(itemId)
    }

    override fun exportMarkdown(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = buildString {
        append("# ").append(title).append('\n')
        if (bookmarks.isNotEmpty()) {
            append("\n## Bookmarks\n\n")
            bookmarks.joinTo(this, separator = "\n") { mark ->
                "- ${mark.chapterLabel} — ${mark.locationLabel()}"
            }
            append('\n')
        }
        if (annotations.isNotEmpty()) {
            append("\n## Highlights & Notes\n\n")
            annotations.joinTo(this, separator = "\n\n") { annotation ->
                buildString {
                    annotation.anchorText.lines().joinTo(this, separator = "\n") { "> $it" }
                    if (!annotation.note.isNullOrEmpty()) {
                        append("\n\nNote: ").append(annotation.note)
                    }
                }
            }
            append('\n')
        }
    }

    override fun exportJson(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = exportJson.encodeToString(
        ReaderMarksExport(
            version = EXPORT_VERSION,
            itemId = itemId,
            title = title,
            bookmarks = bookmarks.map { it.toExport() },
            annotations = annotations.map { it.toExport() },
        )
    )

    private fun BookBookmarkEntity.toDomain() = ReaderBookmark(
        id = id,
        itemId = itemId,
        positionTicks = positionTicks,
        cfi = cfi,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
    )

    private fun BookAnnotationEntity.toDomain() = ReaderAnnotation(
        id = id,
        itemId = itemId,
        cfi = cfi,
        style = style.toEnumOrNull() ?: ReaderAnnotationStyle.HIGHLIGHT,
        color = color.toEnumOrNull() ?: ReaderAnnotationColor.YELLOW,
        anchorText = anchorText,
        note = note,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ReaderBookmark.toExport() = BookmarkExport(
        itemId = itemId,
        positionTicks = positionTicks,
        cfi = cfi,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
    )

    private fun ReaderAnnotation.toExport() = AnnotationExport(
        itemId = itemId,
        cfi = cfi,
        style = style,
        color = color,
        anchorText = anchorText,
        note = note,
        chapterLabel = chapterLabel,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        /** Shape version of [exportJson]; bump on breaking changes. */
        const val EXPORT_VERSION: Int = 1

        /**
         * Export-only encoder: `encodeDefaults` so the self-describing
         * `version`/`itemId` fields always appear, `prettyPrint` because the
         * payload is a user-shareable file.
         */
        private val exportJson = Json {
            encodeDefaults = true
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}

/**
 * Location label for a bookmark in the Markdown export: paged marks
 * (cfi == null) render a 1-based `Page n`; EPUB marks render the reading
 * percent. Both derive from the stored ticks through [BookProgressPolicy] —
 * the same encodings the reading progress uses, so the export never invents
 * a second position math.
 */
private fun ReaderBookmark.locationLabel(): String =
    if (cfi == null) {
        "Page ${BookProgressPolicy.ticksToPage(positionTicks) + 1}"
    } else {
        "${(BookProgressPolicy.ticksToPercent(positionTicks) * 100).roundToInt()}%"
    }

/** JSON export DTO — mirrors [ReaderBookmark]; ids are install-local and omitted. */
@Serializable
private data class BookmarkExport(
    val itemId: String,
    val positionTicks: Long,
    val cfi: String? = null,
    val chapterLabel: String,
    val createdAt: Long,
)

/** JSON export DTO — mirrors [ReaderAnnotation]; ids are install-local and omitted. */
@Serializable
private data class AnnotationExport(
    val itemId: String,
    val cfi: String,
    val style: ReaderAnnotationStyle,
    val color: ReaderAnnotationColor,
    val anchorText: String,
    val note: String? = null,
    val chapterLabel: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Top-level JSON export document. Self-describing via [version]. */
@Serializable
private data class ReaderMarksExport(
    val version: Int,
    val itemId: String? = null,
    val title: String,
    val bookmarks: List<BookmarkExport> = emptyList(),
    val annotations: List<AnnotationExport> = emptyList(),
)
