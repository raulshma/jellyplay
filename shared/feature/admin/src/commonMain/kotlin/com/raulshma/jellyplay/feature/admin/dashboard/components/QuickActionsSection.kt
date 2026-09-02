package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.FileText
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Tool
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_devices_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_logs_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_plugins_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_qa_statistics
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_qa_tasks
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_qa_watched
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_quick_access
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stale_media_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_users_title

@Composable
fun QuickActionsSection(
    onScheduledTasks: () -> Unit,
    onDevices: () -> Unit,
    onLogs: () -> Unit,
    onUserStatistics: () -> Unit = {},
    onStaleMedia: () -> Unit = {},
    onWatchedMediaCleanup: () -> Unit = {},
    onPlugins: () -> Unit = {},
    onUsers: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            stringResource(Res.string.admin_quick_access),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionButton(
                icon = Tabler.Outline.Users,
                label = stringResource(Res.string.admin_qa_statistics),
                iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onUserStatistics,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = Tabler.Outline.PlayerPlay,
                label = stringResource(Res.string.admin_qa_tasks),
                iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onScheduledTasks,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = Tabler.Outline.DeviceDesktop,
                label = stringResource(Res.string.admin_devices_title),
                iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onDevices,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionButton(
                icon = Tabler.Outline.FileText,
                label = stringResource(Res.string.admin_logs_title),
                iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onLogs,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = Tabler.Outline.Trash,
                label = stringResource(Res.string.admin_stale_media_title),
                iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onStaleMedia,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = Tabler.Outline.EyeOff,
                label = stringResource(Res.string.admin_qa_watched),
                iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onWatchedMediaCleanup,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionButton(
                icon = Tabler.Outline.Users,
                label = stringResource(Res.string.admin_users_title),
                iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onUsers,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = Tabler.Outline.Tool,
                label = stringResource(Res.string.admin_plugins_title),
                iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onPlugins,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    iconBackgroundColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "quickActionScale",
    )

    Column(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusIndicator(ShapeCache.smooth16)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = iconTint,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
