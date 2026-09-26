package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The focus-restoring horizontal item strip — the ONE implementation of the
 * plain [LazyRow] + [focusGroup] + [tvFocusRestorer] shell that detail screens
 * hand-rolled per section (Seerr detail's seasons / cast / videos / similar
 * rows were four identical copies). Generic over the row's item projection
 * (the [com.raulshma.jellyplay.feature.home.HomeItemRow] slot pattern): the
 * chassis owns the container, arrangement, padding, keys and the TV focus
 * policy (children form a focus group whose last-focused item is restored on
 * D-pad re-entry); callers supply data + row content.
 *
 * Deliberately NOT the same chassis as [TvFocusableItemRow]: that one manages
 * TV focus at the row level (on-enter grab, focused-index tracking, D-pad
 * cache window, item placement animation). Strips whose items manage their own
 * focus indicator and that only need group + restore semantics use this; the
 * Jellyfin seasons/episodes rows in the details feature stay on
 * [TvFocusableItemRow] because their TV behavior depends on it.
 *
 * @param key stable item identity (required, forwarded to the lazy list).
 * @param contentType forwarded so item recycling stays per-kind.
 */
@Composable
fun <T> FocusRestoringItemRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentType: (item: T) -> Any? = { null },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) {
    LazyRow(
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        modifier = modifier
            .focusGroup()
            .tvFocusRestorer(),
    ) {
        items(
            items = items,
            key = key,
            contentType = contentType,
            itemContent = itemContent,
        )
    }
}
