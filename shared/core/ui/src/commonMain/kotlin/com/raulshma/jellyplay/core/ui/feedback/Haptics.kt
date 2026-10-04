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
 * Shared implementation for the haptic accessors: a lambda that fires [type]
 * via Compose's pure-Compose haptic API (rather than the platform `View` path
 * used in the player screens) so it resolves from `core:ui`, gated by
 * [LocalHapticsEnabled].
 */
@Composable
private fun rememberHaptic(type: HapticFeedbackType): () -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(hapticFeedback, enabled, type) {
        {
            if (enabled) {
                hapticFeedback.performHapticFeedback(type)
            }
        }
    }
}

/**
 * Confirmation haptic for consequential actions: toggles, favorite,
 * mark-watched, play, account pick.
 */
@Composable
fun rememberConfirmHaptic(): () -> Unit = rememberHaptic(HapticFeedbackType.Confirm)

/**
 * Lighter-than-[rememberConfirmHaptic] tick for discrete selection changes:
 * filter chips, tab picks, swipe-action commits. Same gating and the same
 * pure-Compose path; sparse by contract — never on every press.
 */
@Composable
fun rememberSelectionTickHaptic(): () -> Unit = rememberHaptic(HapticFeedbackType.SegmentTick)
