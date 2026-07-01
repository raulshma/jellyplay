package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginStatus

private data class BadgeTint(val label: String, val color: Color)

@Composable
private fun badgeTint(status: PluginStatus): BadgeTint {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        PluginStatus.ACTIVE -> BadgeTint("Active", cs.primary)
        PluginStatus.RESTART -> BadgeTint("Restart", cs.tertiary)
        PluginStatus.MALFUNCTIONED -> BadgeTint("Error", cs.error)
        PluginStatus.DELETED -> BadgeTint("Deleted", cs.outline)
        PluginStatus.DISABLED -> BadgeTint("Disabled", cs.outline)
        PluginStatus.SUPERSEDED -> BadgeTint("Superseded", cs.secondary)
        PluginStatus.NOT_SUPPORTED -> BadgeTint("Unsupported", cs.secondary)
    }
}

@Composable
fun PluginStatusBadge(
    status: PluginStatus,
    modifier: Modifier = Modifier,
) {
    val tint = badgeTint(status)
    Box(
        modifier = modifier
            .clip(ShapeCache.smooth8)
            .background(tint.color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = tint.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
