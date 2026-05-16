package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeMode

/**
 * A minimal mode switch following Material Design 3 expressive guidelines.
 * Uses a compact Switch with animated thumb content showing the current mode icon.
 */
@Composable
fun ModeSwitch(
    currentMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMusic = currentMode == HomeMode.MUSIC

    Switch(
        checked = isMusic,
        onCheckedChange = { onModeChange(if (it) HomeMode.MUSIC else HomeMode.VIDEO) },
        modifier = modifier,
        thumbContent = {
            Icon(
                imageVector = if (isMusic) Icons.Default.MusicNote else Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}
