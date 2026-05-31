package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class AdaptiveInfo(
    val windowSizeClass: WindowSizeClass,
    val isLandscape: Boolean,
)

val LocalAdaptiveInfo = compositionLocalOf {
    AdaptiveInfo(WindowSizeClass.Compact, false)
}

@Composable
fun rememberAdaptiveInfo(): AdaptiveInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val windowSizeClass = when {
        widthDp >= 840 -> WindowSizeClass.Expanded
        widthDp >= 600 -> WindowSizeClass.Medium
        else -> WindowSizeClass.Compact
    }
    return AdaptiveInfo(
        windowSizeClass = windowSizeClass,
        isLandscape = widthDp > heightDp,
    )
}
