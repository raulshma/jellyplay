package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.raulshma.jellyplay.core.model.LayoutMode

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

/**
 * Applies the manual layout override (issue #166) to the measured adaptive
 * info. AUTO passes the measurement through untouched; PHONE clamps the size
 * class to Compact so a tablet renders the single-pane phone shell; TABLET
 * lifts it to Expanded so a phone renders the two-pane shell. The measured
 * `isLandscape` is preserved either way — the override deliberately reshapes
 * only the width-class axis (the landscape two-pane forks still key off the
 * real orientation), so a portrait phone forcing TABLET gets the expanded
 * density without the side-by-side detail body.
 */
fun LayoutMode.applyOverride(info: AdaptiveInfo): AdaptiveInfo = when (this) {
    LayoutMode.AUTO -> info
    LayoutMode.PHONE -> info.copy(windowSizeClass = WindowSizeClass.Compact)
    LayoutMode.TABLET -> info.copy(windowSizeClass = WindowSizeClass.Expanded)
}
