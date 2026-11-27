package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** JB material3 1.12 line still names the factory `toggleButtonColors`. */
@Composable
internal actual fun jellyPlayToggleButtonColors(
    containerColor: Color,
    contentColor: Color,
    checkedContainerColor: Color,
    checkedContentColor: Color,
): ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(
    containerColor = containerColor,
    contentColor = contentColor,
    checkedContainerColor = checkedContainerColor,
    checkedContentColor = checkedContentColor,
)
