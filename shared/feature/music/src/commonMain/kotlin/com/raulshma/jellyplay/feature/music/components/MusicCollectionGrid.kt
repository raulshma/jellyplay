package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import org.jetbrains.compose.resources.stringResource

/**
 * The paged music collections' one grid body: [PagedGrid] with the
 * [MusicCollectionKind] presentation folded in (empty icon/title, error
 * fallback text) and symmetric spacing — the wiring the standalone
 * album/artist screens and the browse pages each hand-assembled. Chrome
 * (scaffold actions vs page header) and geometry stay at the call site: they
 * are per-route-family decisions, not duplication.
 */
@Composable
internal fun <T : Any> MusicCollectionPagedGrid(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    kind: MusicCollectionKind,
    columns: GridCells,
    contentPadding: PaddingValues,
    spacing: Dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T, Modifier) -> Unit,
) {
    PagedGrid(
        items = items,
        itemKey = itemKey,
        modifier = modifier,
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        emptyIcon = kind.emptyIcon,
        emptyTitle = stringResource(kind.emptyTitleRes),
        errorFallbackMessage = stringResource(kind.errorFallbackRes),
        itemContent = itemContent,
    )
}
