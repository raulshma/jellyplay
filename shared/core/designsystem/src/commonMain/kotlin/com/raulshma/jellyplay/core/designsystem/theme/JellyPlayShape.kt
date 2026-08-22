package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Smooth-corner shape primitives owned by the design system.
 *
 * These wrappers exist so consumers outside `core:designsystem` don't import
 * the third-party `smooth-corner-rect` library directly — the dependency is
 * `implementation`-scoped in `core:designsystem/build.gradle.kts`, which
 * keeps the library off every consumer's API surface while still letting
 * feature code request smooth-corner shapes.
 *
 * Default [smoothnessAsPercent] of 60 matches the Material 3 Expressive
 * motion used throughout the rest of the design system.
 */

/** Uniform smooth-corner shape. */
fun smoothCornerShape(
    cornerRadius: Dp,
    smoothnessAsPercent: Int = DEFAULT_SMOOTHNESS,
): Shape = AbsoluteSmoothCornerShape(cornerRadius, smoothnessAsPercent)

/**
 * Per-corner smooth-corner shape for asymmetric surfaces (e.g. the home hero,
 * which uses different top/bottom radii to merge with the next section).
 */
fun smoothCornerShape(
    cornerRadiusTL: Dp,
    cornerRadiusTR: Dp,
    cornerRadiusBL: Dp,
    cornerRadiusBR: Dp,
    smoothnessAsPercent: Int = DEFAULT_SMOOTHNESS,
): Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = cornerRadiusTL,
    cornerRadiusTR = cornerRadiusTR,
    cornerRadiusBL = cornerRadiusBL,
    cornerRadiusBR = cornerRadiusBR,
    smoothnessAsPercentTL = smoothnessAsPercent,
    smoothnessAsPercentTR = smoothnessAsPercent,
    smoothnessAsPercentBL = smoothnessAsPercent,
    smoothnessAsPercentBR = smoothnessAsPercent,
)

private const val DEFAULT_SMOOTHNESS = 60
