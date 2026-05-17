package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
internal fun PipButton(
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
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            Icons.Default.PictureInPictureAlt,
            contentDescription = "Picture in Picture",
            modifier = Modifier.size(IconButtonDefaults.smallIconSize),
        )
    }
}
