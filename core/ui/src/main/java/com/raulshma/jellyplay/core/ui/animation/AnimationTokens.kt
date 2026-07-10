package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
