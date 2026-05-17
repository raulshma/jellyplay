package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
    val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(
            Icons.Default.PictureInPictureAlt,
            contentDescription = "Picture in Picture",
            modifier = Modifier.size(22.dp),
        )
    }
}
