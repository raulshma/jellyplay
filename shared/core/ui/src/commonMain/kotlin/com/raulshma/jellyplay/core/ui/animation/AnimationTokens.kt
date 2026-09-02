package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.IntOffset
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion

object AnimationTokens {
    const val StaggerDelayPerItem = 40
    const val SectionStaggerDelay = 70

    const val CardPressScale = 0.95f
    const val ButtonPressScale = 0.85f
    const val MiniPlayerButtonPressScale = 0.85f
    const val ScaleEntranceInitial = 0.92f

    const val BottomSheetPredictiveBackMinScale = 0.85f
}

/**
 * True when motion should be reduced — either Performance Mode or Reduce Motion
 * is enabled. Prefer this over checking a single flag so both settings are honored.
 *
 * Animations that resolve their spec through `MaterialTheme.motionScheme.*` already
 * honor both flags (the theme switches to `ReducedMotionScheme` when either is on);
 * this is for code that cannot route through the scheme — primarily
 * `rememberInfiniteTransition`/`Animatable` loops and bespoke effects.
 */
@Composable
@ReadOnlyComposable
fun isReducedMotion(): Boolean = LocalReducedMotion.current

@Composable
fun performanceAwareScale(default: Float): Float {
    return if (LocalReducedMotion.current) 1f else default
}

@Composable
fun performanceAwareStaggerDelay(defaultMs: Int): Int {
    return if (LocalReducedMotion.current) 0 else defaultMs
}

/**
 * Placement spec for `LazyItemScope.animateItem`. The no-arg
 * [androidx.compose.foundation.lazy.animateItem] uses a fixed library-default
 * spring that does NOT consult `motionScheme`/`LocalReducedMotion`, so
 * reduced-motion users still see animated item placement. Pass this as the
 * `placementSpec` to honor the reduce-motion contract: spring normally, snap
 * when reduced.
 *
 * Hoist the result into a local val inside the `items { }` lambda and pass it
 * by name to `animateItem(placementSpec = ...)` — this is unambiguous and
 * version-portable (a @Composable default arg is not reliably supported).
 */
@Composable
@ReadOnlyComposable
fun lazyItemPlacementSpec(): FiniteAnimationSpec<IntOffset> =
    if (LocalReducedMotion.current) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    }

