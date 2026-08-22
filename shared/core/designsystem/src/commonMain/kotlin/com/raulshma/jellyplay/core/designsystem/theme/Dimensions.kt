package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralised design-system dimensions for values that recur across multiple
 * screens. Defined here so a single edit propagates everywhere and so call
 * sites read intent (`Dimensions.floatingNavHeight`) rather than magic
 * numbers (`64.dp`).
 *
 * Keep this file deliberately small — only add a constant when the same value
 * (with the same meaning) appears in three or more unrelated files.
 */
object Dimensions {
    /**
     * The height of the bottom floating navigation bar (and the amount the
     * mini-player is allowed to slide behind it before being fully hidden).
     * Read as px via `with(LocalDensity.current) { Dimensions.floatingNavHeight.toPx() }`.
     */
    val floatingNavHeight: Dp = 56.dp
}
