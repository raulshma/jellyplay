package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** androidx material3 ≥ 1.5.0-beta02 names the factory `colors`. */
@Composable
internal actual fun jellyPlayToggleButtonColors(
    containerColor: Color,
    contentColor: Color,
    checkedContainerColor: Color,
    checkedContentColor: Color,
): ToggleButtonColors = ToggleButtonDefaults.colors(
    containerColor = containerColor,
    contentColor = contentColor,
    checkedContainerColor = checkedContainerColor,
    checkedContentColor = checkedContentColor,
)
