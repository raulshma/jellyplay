package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun MuteButton(
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, IconButtonDefaults.smallRoundShape),
        shape = IconButtonDefaults.smallRoundShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = playerOnScrim().copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = if (isMuted) Tabler.Outline.VolumeOff else Tabler.Outline.Volume,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            modifier = Modifier.size(IconButtonDefaults.smallIconSize),
        )
    }
}
