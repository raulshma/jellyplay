package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object TvOverscan {
    val horizontal = 48.dp
    val vertical = 48.dp
}

val LocalTvMode = compositionLocalOf { false }

val LocalTvTypography = compositionLocalOf<androidx.compose.material3.Typography?> { null }

@Composable
fun TvScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isTv = LocalTvMode.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isTv) Modifier.padding(
                    horizontal = TvOverscan.horizontal,
                    vertical = TvOverscan.vertical,
                ) else Modifier
            ),
    ) {
        if (topBar != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}
