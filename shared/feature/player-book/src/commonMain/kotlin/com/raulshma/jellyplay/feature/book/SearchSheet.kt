package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_hint
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_no_results
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_searching
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The search sheet's result set, token-filtered by the caller. */
internal sealed interface ReaderSearchState {
    data object Idle : ReaderSearchState

    data object Searching : ReaderSearchState

    data class Results(val rows: List<EpubSearchResult>) : ReaderSearchState
}

/**
 * The in-book search sheet (EPUB only): a debounced query field over the
 * WebView host's full-text scan. The 400 ms debounce lives HERE; the token
 * and the result state machine are [ReaderSearchSession]'s (the parent
 * supplies `onSearch` → the session's launchSearch, results arriving back
 * through `state`). One of the reader's per-sheet files (split from
 * ReaderSheets.kt, carrying its [ReaderSearchState] with it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(
    state: ReaderSearchState,
    onSearch: (query: String) -> Unit,
    onResultTap: (EpubSearchResult) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_search))
        var query by remember { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.book_reader_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        // Debounce: only a query that survives 400 ms fires a scan. The
        // token stamping (and the late-result drop it buys) is the session's,
        // mirrored host-side in reader.js.
        LaunchedEffect(query) {
            if (query.isBlank()) return@LaunchedEffect
            delay(SEARCH_DEBOUNCE_MS)
            onSearch(query)
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

internal val SEARCH_DEBOUNCE_MS = 400L
