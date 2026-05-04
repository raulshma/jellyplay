package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeMode

@Composable
fun ModeSwitch(
    currentMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.height(40.dp)) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            selected = currentMode == HomeMode.VIDEO,
            onClick = { onModeChange(HomeMode.VIDEO) },
            icon = {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = "Video",
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                )
            },
            label = { Text("Video", style = MaterialTheme.typography.labelSmall) },
        )
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            selected = currentMode == HomeMode.MUSIC,
            onClick = { onModeChange(HomeMode.MUSIC) },
            icon = {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = "Music",
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                )
            },
            label = { Text("Music", style = MaterialTheme.typography.labelSmall) },
        )
    }
}
