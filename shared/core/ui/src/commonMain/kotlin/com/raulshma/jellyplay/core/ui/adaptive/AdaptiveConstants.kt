package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout constants for responsive grid/item sizing across phone, tablet, and TV.
 *
 * Phone (Compact):  single-column or small grid cells
 * Tablet (Medium):  medium grid cells with more columns
 * Tablet/TV (Expanded): large grid cells, two-pane support, TV-optimized spacing
 */

// ── Grid cell sizes (poster/card width) ──────────────────────────────────
object AdaptiveGridSize {
    val Compact: Dp = 150.dp
    val Medium: Dp = 170.dp
    val Expanded: Dp = 190.dp
    val Tv: Dp = 220.dp
}

// ── Grid cell minimum size for LazyVerticalGrid ──────────────────────────
object AdaptiveGridMinSize {
    val Compact: Dp = 140.dp
    val Medium: Dp = 155.dp
    val Expanded: Dp = 170.dp
    val Tv: Dp = 200.dp
}

// ── MediaRow (horizontal lazy row) card width ────────────────────────────
object AdaptiveRowCardWidth {
    val Compact: Dp = 160.dp
    val Medium: Dp = 180.dp
    val Expanded: Dp = 200.dp
    val Tv: Dp = 240.dp
}

// ── Content horizontal padding ───────────────────────────────────────────
object AdaptiveContentPadding {
    val Compact: Dp = 16.dp
    val Medium: Dp = 24.dp
    val Expanded: Dp = 32.dp
    val Tv: Dp = 48.dp
}

// ── Spacing between items ────────────────────────────────────────────────
object AdaptiveItemSpacing {
    val Compact: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Expanded: Dp = 16.dp
    val Tv: Dp = 20.dp
}

// ── Bottom content padding (above nav bar) ───────────────────────────────
object AdaptiveBottomPadding {
    val Compact: Dp = 100.dp
    val Medium: Dp = 90.dp
    val Expanded: Dp = 80.dp
    val Tv: Dp = 80.dp
}

// ── Hero header height ───────────────────────────────────────────────────
object AdaptiveHeroHeight {
    val PortraitCompact: Dp = 520.dp
    val LandscapeMedium: Dp = 320.dp
    val Expanded: Dp = 400.dp
    val Tv: Dp = 420.dp
}

// ── Detail backdrop height ───────────────────────────────────────────────
object AdaptiveBackdropHeight {
    val Portrait: Dp = 540.dp
    val LandscapeExpanded: Dp = 384.dp
    val Expanded: Dp = 480.dp
    val Tv: Dp = 456.dp
}

// ── Detail body max width (centered content on wide screens) ─────────────
object AdaptiveDetailBodyMaxWidth {
    val Compact: Dp = Dp.Infinity
    val Medium: Dp = 680.dp
    val Expanded: Dp = 840.dp
    val Tv: Dp = Dp.Infinity
}

// ── Convenience helpers ──────────────────────────────────────────────────

/**
 * Returns the appropriate grid cell size based on the current window size class and TV mode.
 */
fun AdaptiveInfo.gridCellSize(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveGridSize.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveGridSize.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveGridSize.Medium
    else -> AdaptiveGridSize.Compact
}

/**
 * Returns the appropriate grid cell minimum size for LazyVerticalGrid.
 */
fun AdaptiveInfo.gridMinSize(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveGridMinSize.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveGridMinSize.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveGridMinSize.Medium
    else -> AdaptiveGridMinSize.Compact
}

/**
 * Returns the appropriate row card width for horizontal lazy rows.
 */
fun AdaptiveInfo.rowCardWidth(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveRowCardWidth.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveRowCardWidth.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveRowCardWidth.Medium
    else -> AdaptiveRowCardWidth.Compact
}

/**
 * Returns the appropriate content padding.
 */
fun AdaptiveInfo.contentPadding(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveContentPadding.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveContentPadding.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveContentPadding.Medium
    else -> AdaptiveContentPadding.Compact
}

/**
 * Returns the appropriate item spacing.
 */
fun AdaptiveInfo.itemSpacing(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveItemSpacing.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveItemSpacing.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveItemSpacing.Medium
    else -> AdaptiveItemSpacing.Compact
}

/**
 * Returns the appropriate bottom content padding (above nav bar).
 */
fun AdaptiveInfo.bottomPadding(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveBottomPadding.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveBottomPadding.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveBottomPadding.Medium
    else -> AdaptiveBottomPadding.Compact
}

/**
 * Number of columns for settings-like lists.
 */
fun AdaptiveInfo.settingsColumns(): Int = when (windowSizeClass) {
    WindowSizeClass.Expanded -> 2
    WindowSizeClass.Medium -> if (isLandscape) 2 else 1
    WindowSizeClass.Compact -> 1
}

fun AdaptiveInfo.detailBodyMaxWidth(isTv: Boolean = false): Dp = when {
    isTv -> AdaptiveDetailBodyMaxWidth.Tv
    windowSizeClass == WindowSizeClass.Expanded -> AdaptiveDetailBodyMaxWidth.Expanded
    windowSizeClass == WindowSizeClass.Medium -> AdaptiveDetailBodyMaxWidth.Medium
    else -> AdaptiveDetailBodyMaxWidth.Compact
}
