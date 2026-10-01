package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_empty
import org.jetbrains.compose.resources.stringResource

/**
 * The EPUB TOC sheet (flattened nav document) with the in-book search entry
 * pinned above the list — one sheet owns "where am I / find something".
 * One of the reader's per-sheet files (split from ReaderSheets.kt).
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
                itemsIndexed(
                    tocItems,
                    key = { index, item -> "${index}_${item.href}" },
                    contentType = { _, _ -> "tocItem" },
                ) { _, item ->
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
