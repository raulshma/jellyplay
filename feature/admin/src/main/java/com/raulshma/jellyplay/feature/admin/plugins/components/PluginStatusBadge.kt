package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PluginStatus

@Composable
fun PluginStatusBadge(
    status: PluginStatus,
    modifier: Modifier = Modifier,
) {
    val (label, bgColor, contentColor) = when (status) {
        PluginStatus.ACTIVE -> Triple("Active", Color(0xFF4CAF50), Color.White)
        PluginStatus.RESTART -> Triple("Restart", Color(0xFFFF9800), Color.White)
        PluginStatus.DELETED -> Triple("Deleted", Color(0xFF9E9E9E), Color.White)
        PluginStatus.SUPERSEDED -> Triple("Superseded", Color(0xFF607D8B), Color.White)
        PluginStatus.MALFUNCTIONED -> Triple("Error", Color(0xFFF44336), Color.White)
        PluginStatus.NOT_SUPPORTED -> Triple("Unsupported", Color(0xFF9C27B0), Color.White)
        PluginStatus.DISABLED -> Triple("Disabled", Color(0xFF757575), Color.White)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = bgColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
