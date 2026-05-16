package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Cached instances of frequently-used [AbsoluteSmoothCornerShape].
 *
 * [AbsoluteSmoothCornerShape] is significantly more expensive than [RoundedCornerShape][androidx.compose.foundation.shape.RoundedCornerShape]
 * because it computes cubic Bézier curves analytically. In LazyColumn items (cards, list items, etc.)
 * each item pays this cost on first composition. By reusing singleton instances we avoid
 * repeated Path construction for the most common radii.
 *
 * Usage:
 * ```kotlin
 * Modifier.clip(ShapeCache.smooth12)
 * Modifier.background(color, ShapeCache.smooth16)
 * ```
 */
object ShapeCache {
    /** 4dp smooth corners — tiny badges, micro surfaces */
    val smooth4 = AbsoluteSmoothCornerShape(cornerRadius = 4.dp, smoothnessAsPercent = 60)

    /** 8dp smooth corners — compact chips, small surfaces */
    val smooth8 = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)

    /** 10dp smooth corners */
    val smooth10 = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)

    /** 12dp smooth corners — song list items, small cards */
    val smooth12 = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)

    /** 14dp smooth corners */
    val smooth14 = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)

    /** 16dp smooth corners — album cards, playlist items */
    val smooth16 = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)

    /** 20dp smooth corners — larger cards, buttons */
    val smooth20 = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)

    /** 24dp smooth corners — dialog surfaces, settings items */
    val smooth24 = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)

    /** 28dp smooth corners — bottom sheets, floating panels */
    val smooth28 = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)

    /** 32dp smooth corners — full-width cards, hero surfaces */
    val smooth32 = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)

    /** 36dp smooth corners — extra-large surfaces */
    val smooth36 = AbsoluteSmoothCornerShape(cornerRadius = 36.dp, smoothnessAsPercent = 60)

    /** Fully smooth (pill) — 50dp, used for buttons and chips */
    val smoothPill = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)
}

/**
 * Returns a context-aware smooth corner shape for grouped list items.
 * Outer corners (first/last items) get large radii, inner corners get small radii,
 * producing a cohesive grouped appearance.
 *
 * @param index The item's position in the group (0-based).
 * @param count Total number of items in the group.
 * @param outerRadius Corner radius for the outer edges (default 22dp).
 * @param innerRadius Corner radius for the inner edges (default 8dp).
 */
fun expressiveListShape(
    index: Int,
    count: Int,
    outerRadius: androidx.compose.ui.unit.Dp = 22.dp,
    innerRadius: androidx.compose.ui.unit.Dp = 8.dp,
): AbsoluteSmoothCornerShape {
    val outer = outerRadius
    val inner = innerRadius
    return when {
        count <= 1 -> AbsoluteSmoothCornerShape(outer, 60)
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = outer,
            cornerRadiusTR = outer,
            cornerRadiusBL = inner,
            cornerRadiusBR = inner,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = inner,
            cornerRadiusTR = inner,
            cornerRadiusBL = outer,
            cornerRadiusBR = outer,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        else -> AbsoluteSmoothCornerShape(inner, 60)
    }
}
