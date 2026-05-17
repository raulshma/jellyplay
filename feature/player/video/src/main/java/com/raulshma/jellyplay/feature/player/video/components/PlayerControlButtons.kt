package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)
    val effectiveTint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, IconButtonDefaults.smallRoundShape),
        shape = IconButtonDefaults.smallRoundShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = effectiveTint,
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = effectiveTint,
            modifier = Modifier.size(IconButtonDefaults.smallIconSize),
        )
    }
}

@Composable
internal fun PlayerSpeedButton(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)
    val isActive = speed != 1.0f
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, IconButtonDefaults.smallRoundShape),
        shape = IconButtonDefaults.smallRoundShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                Color.White.copy(alpha = 0.1f)
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Text(
            if (speed == 1.0f) "1x" else "${speed}x",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
