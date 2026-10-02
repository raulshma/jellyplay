package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Card-rendering preferences surfaced to media-card composables
 * (`PosterCard`, `WideMediaCard`, …) so they can honour the user's
 * appearance settings without each call site having to thread the
 * flags manually.
 */
@Immutable
data class CardDisplayPreferences(
    val showUnwatchedBadge: Boolean = true,
    val showWatchedCheckmark: Boolean = true,
    val hideWatchedItems: Boolean = false,
)

val LocalCardDisplayPreferences: ProvidableCompositionLocal<CardDisplayPreferences> =
    compositionLocalOf { CardDisplayPreferences() }
