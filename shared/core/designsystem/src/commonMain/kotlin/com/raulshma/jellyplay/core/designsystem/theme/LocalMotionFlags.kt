package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * The ONE home of the motion/performance flags. These locals are the single
 * source of truth for the fold: [JellyPlayTheme] derives its
 * `MotionScheme` from them directly (see [motionSchemeFor]), so the scheme and
 * every call-site guard can no longer disagree — there is exactly one channel
 * carrying the flags. `JellyPlayPreferenceTheme` (core/ui) provides them once,
 * above [JellyPlayTheme]; everything below reads them.
 *
 * core/ui's `LocalPerformanceMode.kt` re-surfaces these same three vals as
 * import-compatibility aliases — the underlying composition-local instances
 * are these, never copies.
 */

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

/**
 * The ONE scheme-selection fold, applied by [JellyPlayTheme]: the reduced
 * scheme wins whenever EITHER flag is on. Extracted as a pure function so the
 * selection (not just the scheme contents) is pinned by MotionSchemeSelectionTest.
 */
internal fun motionSchemeFor(
    performanceMode: Boolean,
    reduceMotion: Boolean,
): MotionScheme =
    if (performanceMode || reduceMotion) ReducedMotionScheme else ExpressiveMotionScheme
