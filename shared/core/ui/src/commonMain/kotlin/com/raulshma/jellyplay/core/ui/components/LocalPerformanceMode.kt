package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the user enabled the performance/battery-saver mode.
 * When true, animations and decorative effects should be suppressed to save power.
 *
 * Prefer checking [LocalReducedMotion] at call sites instead — it is true when
 * *either* performance mode *or* reduce motion is on, so a single guard honors both.
 */
val LocalPerformanceMode: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/**
 * Whether the user enabled the reduce-motion accessibility setting.
 * When true, motion should be reduced or eliminated for accessibility.
 *
 * Prefer checking [LocalReducedMotion] at call sites instead — it is true when
 * *either* performance mode *or* reduce motion is on, so a single guard honors both.
 */
val LocalReduceMotionEnabled: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/**
 * The unified "reduce motion" flag: `true` when *either* [LocalPerformanceMode]
 * or [LocalReduceMotionEnabled] is on. This is what call sites should read to
 * decide whether to suppress an animation/effect, so that a single guard honors
 * both the performance setting and the reduce-motion accessibility setting.
 *
 * Note: animations that resolve their `AnimationSpec` through
 * `MaterialTheme.motionScheme.*` already honor both flags (the theme switches to
 * `ReducedMotionScheme` when either is on) and don't need a manual guard. This
 * local is for code that cannot route through the motion scheme — primarily
 * `rememberInfiniteTransition`/`Animatable` loops and bespoke effects.
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }
