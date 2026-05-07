package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp).tvFocusable(),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
        ),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun PlayerSpeedButton(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp).tvFocusable(),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (speed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
        ),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        androidx.compose.material3.Text(
            if (speed == 1.0f) "1x" else "${speed}x",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
