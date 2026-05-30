package com.raulshma.jellyplay.feature.admin.devices

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.Edit
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Device") },
            text = {
                Text("Delete \"${state.selectedDevice?.displayName()}\"? This will log out all associated sessions.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteDevice() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("Cancel") }
            },
        )
    }

    if (state.showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEditNameDialog() },
            title = { Text("Edit Device Name") },
            text = {
                OutlinedTextField(
                    value = state.editCustomName,
                    onValueChange = { viewModel.updateEditCustomName(it) },
                    label = { Text("Custom Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveDeviceName() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditNameDialog() }) { Text("Cancel") }
            },
        )
    }

    JellyPlayScreenScaffold(
        title = "Devices",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Tabler.Outline.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.loadDevices() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                ) {
                    if (state.devices.isEmpty()) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.DeviceDesktop,
                            title = "No devices found",
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(items = state.devices, key = { index, device -> "${index}_${device.id}" }) { _, device ->
                                DeviceItem(
                                    device = device,
                                    onEdit = { viewModel.showEditNameDialog(device) },
                                    onDelete = { viewModel.showDeleteDialog(device) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DeviceInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deviceScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null) {}
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (device.name.contains("TV", ignoreCase = true) ||
                    device.name.contains("Android TV", ignoreCase = true)
                ) Tabler.Outline.DeviceDesktop else Tabler.Outline.DeviceMobile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${device.appName} ${device.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.lastUserName.isNotBlank()) {
                        Text(
                            device.lastUserName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (device.dateLastActivity.isNotBlank()) {
                        Text(
                            formatRelativeTime(device.dateLastActivity),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Tabler.Outline.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun DeviceInfo.displayName(): String = customName?.ifBlank { null } ?: name

private fun formatRelativeTime(dateStr: String): String {
    return try {
        val date = java.time.OffsetDateTime.parse(dateStr)
        val now = java.time.OffsetDateTime.now()
        val duration = java.time.Duration.between(date, now)
        when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> dateStr.take(10)
        }
    } catch (_: Exception) {
        dateStr.take(19)
    }
}
