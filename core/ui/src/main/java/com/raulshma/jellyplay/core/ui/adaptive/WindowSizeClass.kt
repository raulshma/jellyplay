package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

data class AdaptiveInfo(
    val windowSizeClass: WindowSizeClass,
    val isLandscape: Boolean,
)

fun classifyWindow(widthDp: Int, heightDp: Int): AdaptiveInfo {
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

val LocalAdaptiveInfo = compositionLocalOf {
    AdaptiveInfo(WindowSizeClass.Compact, false)
}
