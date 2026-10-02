package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import com.raulshma.jellyplay.core.designsystem.theme.LocalPerformanceMode as DesignSystemPerformanceMode
import com.raulshma.jellyplay.core.designsystem.theme.LocalReducedMotion as DesignSystemReducedMotion
import com.raulshma.jellyplay.core.designsystem.theme.LocalReduceMotionEnabled as DesignSystemReduceMotionEnabled

/**
 * Import-compatibility aliases for the motion/performance flags.
 *
 * The ONE home of these locals is designsystem's `LocalMotionFlags.kt` — moved
 * there so `JellyPlayTheme` can derive its `MotionScheme` from the very same
 * instances (the previous split — scheme picked from raw parameters here,
 * flags surfaced as locals — let the two channels drift). These vals are plain
 * getters, so every read/provide through them targets the designsystem local;
 * there are no copies. Prefer core/ui's `isReducedMotion()` /
 * `performanceAware*` helpers (animation package) at call sites; new code may
 * import the locals from `core.designsystem.theme` directly.
 */

/** Whether the user enabled performance/battery-saver mode. See designsystem's `LocalMotionFlags.kt`. */
val LocalPerformanceMode: ProvidableCompositionLocal<Boolean>
    get() = DesignSystemPerformanceMode

/** Whether the user enabled the reduce-motion accessibility setting. See designsystem's `LocalMotionFlags.kt`. */
val LocalReduceMotionEnabled: ProvidableCompositionLocal<Boolean>
    get() = DesignSystemReduceMotionEnabled

/**
 * The unified "reduce motion" flag: true when either performance mode or
 * reduce motion is on. See designsystem's `LocalMotionFlags.kt`.
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean>
    get() = DesignSystemReducedMotion
