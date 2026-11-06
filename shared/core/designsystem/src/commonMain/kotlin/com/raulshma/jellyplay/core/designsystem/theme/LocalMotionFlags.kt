package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.raulshma.jellyplay.core.model.ResolvedPerformanceConfig

/**
 * The ONE home of the motion/performance flags. These locals are the single
 * source of truth for the fold: [JellyPlayTheme] derives its
 * `MotionScheme` from them directly (see [motionSchemeFor]), so the scheme and
 * every call-site guard cannot disagree — there is exactly one channel
 * carrying the flags. `JellyPlayPreferenceTheme` (core/ui) provides them once,
 * above [JellyPlayTheme]; everything below reads them.
 *
 * core/ui's `LocalPerformanceMode.kt` re-surfaces these same vals as
 * import-compatibility aliases — the underlying composition-local instances
 * are these, never copies.
 */

/**
 * The fully folded performance posture ([ResolvedPerformanceConfig] — level +
 * overrides + reduce-motion, resolved once by `PerformanceConfigResolver` at
 * the provision seam). This is the ONE channel every behavior gate should
 * read: the historical boolean locals ([LocalPerformanceMode],
 * [LocalReducedMotion]) are compat projections of it, so a gate reading a
 * projection can never disagree with one reading the config. Default is
 * [ResolvedPerformanceConfig.DEFAULT] — the fresh-install posture, so a shell
 * that skips the provider keeps the shipped performance-mode-OFF behavior.
 */
val LocalPerformanceConfig: ProvidableCompositionLocal<ResolvedPerformanceConfig> =
    compositionLocalOf { ResolvedPerformanceConfig.DEFAULT }

/**
 * Whether the user enabled the performance/battery-saver mode.
 * When true, animations and decorative effects should be suppressed to save power.
 *
 * COMPAT projection: provided as the resolved config's `suppressAnimations`
 * (the level's suppression choice with the granular override applied) — at
 * all-AUTO that is exactly the legacy `level == PERFORMANCE` boolean. Prefer
 * reading [LocalPerformanceConfig] at new call sites; this local stays for
 * the unmigrated `LocalReducedMotion` guards.
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
 * scheme wins whenever the resolved posture suppresses animations OR the
 * reduce-motion accessibility flag is on (the same two inputs the resolved
 * config's `reducedMotion` field folds — passed here as the raw pair so the
 * selection cannot double-count the fold). Extracted as a pure function so
 * the selection (not just the scheme contents) is pinned by
 * MotionSchemeSelectionTest.
 */
internal fun motionSchemeFor(
    suppressAnimations: Boolean,
    reduceMotion: Boolean,
): MotionScheme =
    if (suppressAnimations || reduceMotion) ReducedMotionScheme else ExpressiveMotionScheme
