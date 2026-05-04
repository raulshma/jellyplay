package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeMode

@Composable
fun ModeSwitch(
    currentMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMusic = currentMode == HomeMode.MUSIC

    Box(
        modifier = modifier.padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = isMusic,
            onCheckedChange = { onModeChange(if (it) HomeMode.MUSIC else HomeMode.VIDEO) },
            thumbContent = {
                Icon(
                    imageVector = if (isMusic) Icons.Default.MusicNote else Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}
