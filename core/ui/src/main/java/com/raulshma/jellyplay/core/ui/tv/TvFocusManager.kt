package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TvScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isTv = isTvDevice()

    CompositionLocalProvider {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.then(
                if (isTv) Modifier.padding(48.dp) else Modifier
            )
        ) {
            content()
        }
    }
}
