package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Pencil
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_bookmarks
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_format_comic
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_finished_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_highlights
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_page_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_percent_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_contents
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Book-only detail-screen pieces, factored out of [DetailContentBody] so the
 * media-detail body keeps one shared section flow while BOOK items get their
 * own reading-aware surface: author line, format/progress metadata, a reading
 * progress card with the local marks counts, and a Contents row that jumps
 * straight into the reader (see the TOC cache story in
 * [DetailViewModel.loadBookExtras]).
 */

/**
 * The book's author, from whichever field the server populated: Jellyfin
 * metadata managers write the author either as a person (type/role "Author"/
 * "Writer") or as artist items; null when neither exists (raw files).
 */
internal fun bookAuthorLabel(detail: MediaDetail, item: MediaItem): String? {
    val person = detail.people.firstOrNull { p ->
        p.type.equals("Author", ignoreCase = true) ||
            p.role?.equals("Author", ignoreCase = true) == true ||
            p.role?.equals("Writer", ignoreCase = true) == true
    }
    return person?.name
        ?: item.artistItems.firstOrNull()?.name?.takeIf { it.isNotBlank() }
}

/** Display label for the book's file format. */
internal fun bookFormatLabel(format: BookFormat, comicLabel: String): String = when (format) {
    BookFormat.EPUB -> "EPUB"
    BookFormat.PDF -> "PDF"
    BookFormat.CBZ, BookFormat.CBR -> comicLabel
}

/** True when the entry can actually land somewhere in the reader. */
internal val BookTocEntry.isJumpable: Boolean
    get() = !href.isNullOrBlank() || page != null

/**
 * One book's reading progress, resolved once from the server ticks. The meta
 * chips, the reading card's label, and its progress bar all render from this
 * single decision (finished > not-started > percent > page-of-count) instead
 * of re-deriving the branch shape per site.
 */
internal sealed interface BookReadingProgress {
    data object Finished : BookReadingProgress
    data object NotStarted : BookReadingProgress
    /** [percent] is 0..1 (reflowable positioning). */
    data class Percent(val percent: Float) : BookReadingProgress
    /** [page] is 1-based against [pageCount]. */
    data class Pages(val page: Int, val pageCount: Int) : BookReadingProgress
    /** Paged book whose page count is not known yet. */
    data object Unknown : BookReadingProgress
}

internal fun resolveBookReadingProgress(
    isPlayed: Boolean,
    ticks: Long,
    format: BookFormat,
    pageCount: Int,
): BookReadingProgress = when {
    isPlayed -> BookReadingProgress.Finished
    ticks <= 0L -> BookReadingProgress.NotStarted
    format.isReflowable -> BookReadingProgress.Percent(
        BookProgressPolicy.ticksToPercent(ticks).toFloat(),
    )
    pageCount > 0 -> BookReadingProgress.Pages(
        BookProgressPolicy.ticksToPage(ticks) + 1,
        pageCount,
    )
    else -> BookReadingProgress.Unknown
}

/** Bar fill 0..1; null when nothing measurable is known. */
internal fun BookReadingProgress.barFraction(): Float? = when (this) {
    BookReadingProgress.Finished -> 1f
    BookReadingProgress.NotStarted -> 0f
    is BookReadingProgress.Percent -> percent
    is BookReadingProgress.Pages -> (page.toFloat() / pageCount).coerceIn(0f, 1f)
    BookReadingProgress.Unknown -> null
}

/**
 * The reading progress card: a thin progress bar plus the percent/pages
 * line and the local marks counts. Informational — the Read/Continue action
 * lives in the shared action row above it.
 */
@Composable
internal fun BookReadingCard(
    format: BookFormat,
    progress: BookReadingProgress,
    bookmarkCount: Int,
    highlightCount: Int,
    modifier: Modifier = Modifier,
) {
    val comicLabel = stringResource(Res.string.detail_book_format_comic)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Tabler.Outline.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when (progress) {
                    BookReadingProgress.Finished -> stringResource(Res.string.detail_book_finished_badge)
                    is BookReadingProgress.Percent -> stringResource(
                        Res.string.detail_book_percent_progress,
                        (progress.percent * 100).toInt().coerceIn(0, 100),
                    )
                    is BookReadingProgress.Pages -> stringResource(
                        Res.string.detail_book_page_progress,
                        progress.page.coerceIn(1, progress.pageCount),
                        progress.pageCount,
                    )
                    // An unstarted reflowable book still reads "0%"; an
                    // unstarted paged book has no honest page number (they
                    // are 1-based), so it falls to the format label.
                    BookReadingProgress.NotStarted ->
                        if (format.isReflowable) {
                            stringResource(Res.string.detail_book_percent_progress, 0)
                        } else {
                            bookFormatLabel(format, comicLabel)
                        }
                    BookReadingProgress.Unknown -> bookFormatLabel(format, comicLabel)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Thin progress bar — same fill language as the Read button's backing
        // bar, but always visible (the card exists to show reading state).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(ShapeCache.smooth4)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            val fraction = progress.barFraction() ?: 0f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        if (bookmarkCount > 0 || highlightCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (bookmarkCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = pluralStringResource(
                                Res.plurals.detail_book_bookmarks,
                                bookmarkCount,
                                bookmarkCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (highlightCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Pencil,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = pluralStringResource(
                                Res.plurals.detail_book_highlights,
                                highlightCount,
                                highlightCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The book's Contents row: a horizontally scrollable, focus-aware row of
 * chapter chips (nesting rendered as indent). A tap deep-links into the
 * reader at the entry; entries without a resolvable destination render
 * inert (the PDF-outline precedent: the title is still readable).
 */
@Composable
internal fun BookTocSection(
    toc: List<BookTocEntry>,
    contentPadding: androidx.compose.ui.unit.Dp,
    onJump: (BookTocEntry) -> Unit,
) {
    Column {
        FadingItem {
            Text(
                text = stringResource(Res.string.detail_section_contents),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(horizontal = contentPadding)
                    .semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        // Key pairs the index with the label: EPUB TOCs may repeat an href
        // (duplicate nav entries) and PDF outlines may repeat a page.
        TvFocusableItemRow(
            items = toc.mapIndexed { i, entry -> i to entry },
            key = { (index, entry) -> "book_toc_${index}_${entry.href ?: ""}_${entry.page ?: -1}" },
            contentPadding = PaddingValues(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { _, (_, entry), focusModifier ->
            BookTocChip(
                entry = entry,
                onClick = { onJump(entry) },
                modifier = focusModifier,
            )
        }
    }
}

@Composable
private fun BookTocChip(
    entry: BookTocEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jumpable = entry.isJumpable
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val label = entry.label.ifBlank { " " }
    Row(
        modifier = modifier
            .graphicsLayer {
                // Nested entries dim slightly — the indent alone reads weakly
                // in a fast horizontal flip.
                alpha = 1f - (entry.level.coerceIn(0, 3) * 0.12f)
            }
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .then(if (jumpable) focusState.focusModifier else Modifier)
            .then(if (jumpable) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16) else Modifier)
            .then(if (jumpable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = 14.dp + (10.dp * entry.level.coerceIn(0, 3)),
                end = 14.dp,
                top = 7.dp,
                bottom = 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
