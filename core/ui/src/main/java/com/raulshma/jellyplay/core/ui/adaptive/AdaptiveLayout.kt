package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

@Composable
fun AdaptiveLayout(
    content: @Composable (AdaptiveInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = rememberAdaptiveInfo()
    CompositionLocalProvider(LocalAdaptiveInfo provides adaptiveInfo) {
        content(adaptiveInfo)
    }
}
