package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme

/**
 * Reusable expressive filter chip used by Library and Search filter sheets.
 * Provides consistent press animations and full D-pad focus support (border +
 * breathing glow) via [ExpressiveChipContainer]; highlights with the primary
 * color when [selected].
 */
@Composable
fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val bgColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    }
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    ExpressiveChipContainer(
        onClick = onClick,
        modifier = modifier,
        containerColor = bgColor,
        forceActive = selected,
    ) {
        if (selected) {
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
