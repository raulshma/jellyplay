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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginStatus
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_active
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_deleted
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_disabled
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_error
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_restart
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_superseded
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_unsupported

private data class BadgeTint(val labelRes: StringResource, val color: Color)

@Composable
private fun badgeTint(status: PluginStatus): BadgeTint {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        PluginStatus.ACTIVE -> BadgeTint(Res.string.admin_status_active, cs.primary)
        PluginStatus.RESTART -> BadgeTint(Res.string.admin_status_restart, cs.tertiary)
        PluginStatus.MALFUNCTIONED -> BadgeTint(Res.string.admin_status_error, cs.error)
        PluginStatus.DELETED -> BadgeTint(Res.string.admin_status_deleted, cs.outline)
        PluginStatus.DISABLED -> BadgeTint(Res.string.admin_status_disabled, cs.outline)
        PluginStatus.SUPERSEDED -> BadgeTint(Res.string.admin_status_superseded, cs.secondary)
        PluginStatus.NOT_SUPPORTED -> BadgeTint(Res.string.admin_status_unsupported, cs.secondary)
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
