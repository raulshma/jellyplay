package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_page
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks_empty
import org.jetbrains.compose.resources.stringResource

/**
 * The bookmarks sheet: one row per bookmark — chapter label (falling back to
 * the book title) plus the encoded position rendered as "Page N" (paged,
 * null CFI) or a percent (reflowable). Tap jumps + dismisses; the trash
 * removes. One of the reader's per-sheet files (split from ReaderSheets.kt).
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
                        // The whole row jumps — the trailing icon is a
                        // redundant affordance, not the only target.
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(bookmark) }
                            .padding(horizontal = 16.dp),
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
    // The paged-vs-reflowable branch + display math live in the codec (the
    // single decode the writer, matcher and jumper also use); only the
    // localization is local.
    val label = when (val decoded = ReaderBookmarkCodec.decode(bookmark)) {
        is ReaderBookmarkCodec.DecodedPosition.Paged ->
            stringResource(Res.string.book_reader_bookmark_page, decoded.page + 1)
        is ReaderBookmarkCodec.DecodedPosition.Reflowable ->
            stringResource(Res.string.book_reader_bookmark_percent, decoded.percent)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
