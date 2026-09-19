package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Pencil
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_show_less
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_bookmarks
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_contents_page
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_contents_show_all
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
 * progress card with the local marks counts, and a Contents list that jumps
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
 * The book's Contents section: a vertical, expandable list of TOC entries —
 * nesting renders as indent, PDF destinations show their page — where a tap
 * deep-links into the reader (href for EPUB, page for PDF). Entries without a
 * resolvable destination render inert (the PDF-outline precedent: the title is
 * still readable).
 *
 * Long TOCs collapse to [TOC_PREVIEW_COUNT] rows behind a "Show all N" toggle.
 * The former horizontal chip row forced every label onto one truncated line
 * inside an endless horizontal flip, which stopped working past a few dozen
 * entries (novel TOCs routinely run 50–200, textbooks more).
 */

/** Rows shown before the "Show all" toggle. */
private const val TOC_PREVIEW_COUNT = 8

/** Extra start padding per nesting level (matches the reader TOC sheet). */
private val TOC_LEVEL_INDENT = 16.dp

@Composable
internal fun BookTocSection(
    toc: List<BookTocEntry>,
    contentPadding: androidx.compose.ui.unit.Dp,
    onJump: (BookTocEntry) -> Unit,
) {
    var expanded by remember(toc) { mutableStateOf(false) }
    val visible = if (expanded) toc else toc.take(TOC_PREVIEW_COUNT)
    Column {
        FadingItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(Res.string.detail_section_contents),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The count sets expectations before the scroll: a 3-entry
                // short story and a 200-entry textbook read very differently.
                Text(
                    text = toc.size.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.padding(horizontal = contentPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visible.forEach { entry ->
                BookTocRow(
                    entry = entry,
                    onClick = { onJump(entry) },
                )
            }
            if (toc.size > TOC_PREVIEW_COUNT) {
                BookTocExpandToggle(
                    expanded = expanded,
                    totalEntries = toc.size,
                    onToggle = { expanded = !expanded },
                )
            }
        }
    }
}

/**
 * One contents row: full-width, indented by nesting depth, with the PDF
 * destination page on the trailing edge. The resting surface (5% onSurface,
 * smooth12) mirrors the compact episode rows so every vertical list in the
 * detail body reads as one family.
 */
@Composable
private fun BookTocRow(
    entry: BookTocEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jumpable = entry.isJumpable
    val focusState = rememberTvFocusState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .then(if (jumpable) focusState.focusModifier else Modifier)
            .then(if (jumpable) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12) else Modifier)
            .then(if (jumpable) Modifier.clickable(onClick = onClick) else Modifier)
            // Indent carries the hierarchy; depth is capped so a deep EPUB
            // nav point can't push its own label off-screen.
            .padding(
                start = 16.dp + (TOC_LEVEL_INDENT * entry.level.coerceIn(0, 4)),
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entry.label.ifBlank { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = if (jumpable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 0-based destination page → human 1-based label. PDF only: EPUB
        // positioning has no page to show.
        entry.page?.let { page ->
            Text(
                text = stringResource(Res.string.detail_book_contents_page, page + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Show all N" / "Show less" pill closing the preview — the same pill
 * language as the other detail-screen toggles (primary on a 10% fill,
 * smooth16) plus a rotating chevron so the collapse direction stays obvious.
 */
@Composable
private fun BookTocExpandToggle(
    expanded: Boolean,
    totalEntries: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "tocChevronRotation",
    )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(ShapeCache.smooth16)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .then(focusState.focusModifier)
                .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16))
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (expanded) {
                    stringResource(CoreUiRes.string.core_ui_show_less)
                } else {
                    pluralStringResource(
                        Res.plurals.detail_book_contents_show_all,
                        totalEntries,
                        totalEntries,
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Tabler.Outline.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}
