package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@Composable
fun JellyFocusableRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = LocalJellyPlayUi.current.layout.contentPadding),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(LocalJellyPlayUi.current.layout.itemSpacing),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        modifier = modifier.tvFocusRestorer(),
        content = content,
    )
}
