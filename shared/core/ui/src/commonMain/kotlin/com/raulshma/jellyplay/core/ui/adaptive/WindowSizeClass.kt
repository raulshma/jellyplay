package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

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
    // Common window metrics: the container size tracks resize/split/fold on
    // Android and the window frame on desktop, replacing Configuration dp.
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = with(density) { containerSize.width.toDp() }
    val heightDp = with(density) { containerSize.height.toDp() }
    val windowSizeClass = when {
        widthDp.value >= 840 -> WindowSizeClass.Expanded
        widthDp.value >= 600 -> WindowSizeClass.Medium
        else -> WindowSizeClass.Compact
    }
    return AdaptiveInfo(
        windowSizeClass = windowSizeClass,
        isLandscape = widthDp.value > heightDp.value,
    )
}
