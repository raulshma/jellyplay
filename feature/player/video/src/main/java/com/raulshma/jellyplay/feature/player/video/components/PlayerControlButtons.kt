package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

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
                playerOnScrim().copy(alpha = 0.1f)
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

@Composable
internal fun PlayerQualityButton(
    quality: StreamingQuality,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)
    val isActive = quality != StreamingQuality.AUTO
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
                playerOnScrim().copy(alpha = 0.1f)
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Text(
            quality.displayName,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
