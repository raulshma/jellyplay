package com.raulshma.jellyplay.core.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * TV-focused player control button used by both the VOD and live players.
 * Promoted from `:feature:player:video` so the live player can reuse it
 * without taking a dependency on the video module.
 */
@Composable
fun PlayerIconButton(
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
            containerColor = playerOnScrim().copy(alpha = 0.1f),
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
