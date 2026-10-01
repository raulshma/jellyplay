package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_empty
import org.jetbrains.compose.resources.stringResource

/**
 * The paged PDF outline sheet: an indented tree of [PdfOutlineNode] rows.
 * Nodes whose destination never resolved (null [PdfOutlineNode.pageIndex])
 * render disabled — the title stays readable, the jump does not fire. CBZ
 * and CBR books never open this sheet (they have no TOC story at all).
 * One of the reader's per-sheet files (split from ReaderSheets.kt).
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
