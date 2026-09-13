package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_page
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks_empty
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_ltr
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_rtl
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_size
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_hint
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_no_results
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_searching
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_settings
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_dark
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_light
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_sepia
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_empty
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's bottom sheets: per-format reading settings, the TOC (EPUB nav
 * document / PDF outline tree), the bookmarks list and the in-book search.
 * All state is caller-owned; every sheet is pure presentation + callbacks,
 * and every list sheet keeps its own dismiss request at the caller's elbow
 * (tap-through must jump AND dismiss in one gesture).
 */

/**
 * The settings sheet for PAGED books: the per-book reading direction chips,
 * plus the TOC entry when the format has one (PDF outlines only — CBZ/CBR
 * books have no TOC story, so the row is absent rather than disabled).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagedSettingsSheet(
    direction: ReadingDirection,
    tocAvailable: Boolean,
    onSetDirection: (ReadingDirection) -> Unit,
    onOpenToc: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_settings))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = direction == ReadingDirection.LTR,
                onClick = { onSetDirection(ReadingDirection.LTR) },
                label = { Text(stringResource(Res.string.book_reader_direction_ltr)) },
            )
            FilterChip(
                selected = direction == ReadingDirection.RTL,
                onClick = { onSetDirection(ReadingDirection.RTL) },
                label = { Text(stringResource(Res.string.book_reader_direction_rtl)) },
            )
        }
        if (tocAvailable) {
            TextButton(
                onClick = onOpenToc,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = Tabler.Outline.List, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(Res.string.book_reader_toc))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The settings sheet for REFLOWABLE books: theme, font size, and the TOC /
 * search entries. The TOC entry stays here (v1 behavior) — the search entry
 * rides the TOC sheet to keep the top bar uncluttered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReflowableSettingsSheet(
    theme: ReaderTheme,
    fontSizePx: Int,
    onSetTheme: (ReaderTheme) -> Unit,
    onAdjustFontSize: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_settings))
        Text(
            text = stringResource(Res.string.book_reader_theme),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = theme == ReaderTheme.DARK,
                onClick = { onSetTheme(ReaderTheme.DARK) },
                label = { Text(stringResource(Res.string.book_reader_theme_dark)) },
            )
            FilterChip(
                selected = theme == ReaderTheme.SEPIA,
                onClick = { onSetTheme(ReaderTheme.SEPIA) },
                label = { Text(stringResource(Res.string.book_reader_theme_sepia)) },
            )
            FilterChip(
                selected = theme == ReaderTheme.LIGHT,
                onClick = { onSetTheme(ReaderTheme.LIGHT) },
                label = { Text(stringResource(Res.string.book_reader_theme_light)) },
            )
        }
        Text(
            text = stringResource(Res.string.book_reader_font_size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 20.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp),
        ) {
            IconButton(onClick = { onAdjustFontSize(-1) }) {
                Icon(imageVector = Tabler.Outline.Minus, contentDescription = null)
            }
            Text(
                text = "$fontSizePx px",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onAdjustFontSize(+1) }) {
                Icon(imageVector = Tabler.Outline.Plus, contentDescription = null)
            }
        }
        TextButton(
            onClick = onOpenToc,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(imageVector = Tabler.Outline.List, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(Res.string.book_reader_toc))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The EPUB TOC sheet (flattened nav document) with the in-book search entry
 * pinned above the list — one sheet owns "where am I / find something".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubTocSheet(
    tocItems: List<EpubTocItem>,
    onJump: (href: String) -> Unit,
    onOpenSearch: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_toc))
        TextButton(
            onClick = onOpenSearch,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(imageVector = Tabler.Outline.Search, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(Res.string.book_reader_search))
        }
        if (tocItems.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_toc_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                items(tocItems) { item ->
                    TextButton(
                        onClick = { onJump(item.href) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = item.label.ifBlank { item.href },
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The paged PDF outline sheet: an indented tree of [PdfOutlineNode] rows.
 * Nodes whose destination never resolved (null [PdfOutlineNode.pageIndex])
 * render disabled — the title stays readable, the jump does not fire. CBZ
 * and CBR books never open this sheet (they have no TOC story at all).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfOutlineSheet(
    nodes: List<PdfOutlineNode>,
    onJump: (pageIndex: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_toc))
        if (nodes.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_toc_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                outlineRows(nodes, depth = 0) { page ->
                    onJump(page)
                }
            }
        }
    }
}

/** Flattens the outline tree into indented, jumpable rows. */
private fun androidx.compose.foundation.lazy.LazyListScope.outlineRows(
    nodes: List<PdfOutlineNode>,
    depth: Int,
    onJump: (Int) -> Unit,
) {
    nodes.forEach { node ->
        item(key = "outline-${depth}-${node.title}-${node.pageIndex}") {
            val page = node.pageIndex
            TextButton(
                onClick = { page?.let(onJump) },
                enabled = page != null,
                modifier = Modifier.fillMaxWidth().padding(start = (16 + depth * 16).dp),
            ) {
                Text(text = node.title, maxLines = 2)
            }
        }
        if (node.children.isNotEmpty()) {
            outlineRows(node.children, depth + 1, onJump)
        }
    }
}

/**
 * The bookmarks sheet: one row per bookmark — chapter label (falling back to
 * the book title) plus the encoded position rendered as "Page N" (paged,
 * null CFI) or a percent (reflowable). Tap jumps + dismisses; the trash
 * removes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    bookmarks: List<ReaderBookmark>,
    title: String,
    onJump: (ReaderBookmark) -> Unit,
    onDelete: (ReaderBookmark) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_bookmarks))
        if (bookmarks.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_bookmarks_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = bookmark.chapterLabel.ifBlank { title },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            BookmarkLocationText(bookmark = bookmark)
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(
                                imageVector = Tabler.Outline.Trash,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onJump(bookmark) }) {
                            Icon(imageVector = Tabler.Outline.Bookmark, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a bookmark's stored position the way its book encodes it: paged
 * rows (null CFI) decode their ticks to a 1-based page, reflowable rows to
 * a percent — both through the shared [BookProgressPolicy] decoders, so the
 * sheet always matches what the writer stored.
 */
@Composable
private fun BookmarkLocationText(bookmark: ReaderBookmark) {
    val label = if (bookmark.cfi == null) {
        stringResource(
            Res.string.book_reader_bookmark_page,
            BookProgressPolicy.ticksToPage(bookmark.positionTicks) + 1,
        )
    } else {
        stringResource(
            Res.string.book_reader_bookmark_percent,
            (BookProgressPolicy.ticksToPercent(bookmark.positionTicks) * 100).roundToInt().coerceIn(0, 100),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The search sheet's result set, token-filtered by the caller. */
internal sealed interface ReaderSearchState {
    data object Idle : ReaderSearchState

    data object Searching : ReaderSearchState

    data class Results(val rows: List<EpubSearchResult>) : ReaderSearchState
}

/**
 * The in-book search sheet (EPUB only): a debounced query field over the
 * WebView host's full-text scan. The 400 ms debounce, the incrementing token
 * and the result filtering all live HERE so the parent only supplies the
 * raw host plumbing (`onSearch` → `host.search(query, token)`, results
 * arriving back through `state`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(
    state: ReaderSearchState,
    onSearch: (query: String, token: Int) -> Unit,
    onResultTap: (EpubSearchResult) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_search))
        var query by remember { mutableStateOf("") }
        var token by remember { mutableStateOf(0) }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.book_reader_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        // Debounce: only a query that survives 400 ms fires a scan; each
        // fired scan increments the token so late results of older queries
        // are dropped (host-side token guard mirrors this).
        LaunchedEffect(query) {
            if (query.isBlank()) return@LaunchedEffect
            delay(SEARCH_DEBOUNCE_MS)
            token += 1
            onSearch(query, token)
        }
        when (state) {
            is ReaderSearchState.Searching -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    text = stringResource(Res.string.book_reader_search_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            is ReaderSearchState.Results -> if (state.rows.isEmpty()) {
                SheetEmptyText(text = stringResource(Res.string.book_reader_search_no_results))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    items(state.rows, key = { it.cfi }) { row ->
                        TextButton(
                            onClick = { onResultTap(row) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                if (row.chapter.isNotBlank()) {
                                    Text(
                                        text = row.chapter,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = row.excerpt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            ReaderSearchState.Idle -> Unit
        }
    }
}

/** The shared "this sheet has nothing to show" line. */
@Composable
private fun SheetEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

internal val SEARCH_DEBOUNCE_MS = 400L
