package com.raulshma.jellyplay.feature.library.components

import androidx.compose.runtime.Composable

@Composable
fun ExpressiveGridItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    content()
}
