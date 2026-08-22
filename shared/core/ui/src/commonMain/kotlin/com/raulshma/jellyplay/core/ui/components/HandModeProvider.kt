package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.model.HandMode

@Immutable
data class HandModeState(val mode: HandMode) {
    val isLeft: Boolean get() = mode == HandMode.LEFT
}

val LocalHandMode = staticCompositionLocalOf { HandModeState(HandMode.RIGHT) }

@Composable
fun HandModeProvider(
    mode: HandMode,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHandMode provides HandModeState(mode)) {
        content()
    }
}
