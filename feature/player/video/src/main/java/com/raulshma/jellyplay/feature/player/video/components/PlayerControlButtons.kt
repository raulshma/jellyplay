package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LabeledControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    iconModifier: Modifier = Modifier,
) {
    val isActive = tint != Color.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = iconModifier,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LabeledSpeedButton(
    onClick: () -> Unit,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp),
            ) {
                Text(
                    if (speed == 1.0f) "1x" else "${speed}x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Speed",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
