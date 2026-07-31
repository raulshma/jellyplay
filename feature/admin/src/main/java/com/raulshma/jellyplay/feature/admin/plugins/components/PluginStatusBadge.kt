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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginStatus
import com.raulshma.jellyplay.feature.admin.R

private data class BadgeTint(@StringRes val labelRes: Int, val color: Color)

@Composable
private fun badgeTint(status: PluginStatus): BadgeTint {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        PluginStatus.ACTIVE -> BadgeTint(R.string.admin_status_active, cs.primary)
        PluginStatus.RESTART -> BadgeTint(R.string.admin_status_restart, cs.tertiary)
        PluginStatus.MALFUNCTIONED -> BadgeTint(R.string.admin_status_error, cs.error)
        PluginStatus.DELETED -> BadgeTint(R.string.admin_status_deleted, cs.outline)
        PluginStatus.DISABLED -> BadgeTint(R.string.admin_status_disabled, cs.outline)
        PluginStatus.SUPERSEDED -> BadgeTint(R.string.admin_status_superseded, cs.secondary)
        PluginStatus.NOT_SUPPORTED -> BadgeTint(R.string.admin_status_unsupported, cs.secondary)
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
            text = stringResource(tint.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = tint.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
