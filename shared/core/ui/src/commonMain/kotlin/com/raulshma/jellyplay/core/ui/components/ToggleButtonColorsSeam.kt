package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.ToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Cross-train seam for [androidx.compose.material3.ToggleButtonDefaults]'s
 * checked-toggle color factory.
 *
 * The androidx material3 1.5 line renamed `toggleButtonColors` → `colors`
 * between 1.5.0-alpha22 and 1.5.0-beta02 (the version the app ships — see
 * KmpLibraryComposePlugin's material3 alignment constraint), while the JB
 * compose-multiplatform material3 the desktop target resolves still names it
 * `toggleButtonColors`. commonMain cannot call either name directly without
 * failing the other train's compile; the expect/actual pair below resolves
 * per platform.
 */
@Composable
internal expect fun jellyPlayToggleButtonColors(
    containerColor: Color,
    contentColor: Color,
    checkedContainerColor: Color,
    checkedContentColor: Color,
): ToggleButtonColors
