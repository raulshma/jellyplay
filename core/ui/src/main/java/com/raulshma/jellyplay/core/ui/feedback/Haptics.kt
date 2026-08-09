package com.raulshma.jellyplay.core.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Whether confirmation haptics are enabled. Provided once at the app root from
 * the `hapticsEnabled` appearance preference, so a single setting governs every
 * confirmation haptic in the app (mirrors [com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode]).
 */
val LocalHapticsEnabled: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }

/**
 * Returns a lambda that fires a single confirmation haptic via Compose's
 * [androidx.compose.ui.hapticfeedback.HapticFeedback], gated by [LocalHapticsEnabled].
 * Uses the pure-Compose haptic API (rather than the platform `View` path used in
 * the player screens) so it resolves from `core:ui`, which has no `android.view`
 * symbol access. Toggles, favorite, and mark-watched all share one preference.
 */
@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(hapticFeedback, enabled) {
        {
            if (enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
}
