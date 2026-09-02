package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    tooltipText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.12f)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, CircleShape),
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}
