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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.Edit
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.SearchField
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_custom_name
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_days_ago
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete_device_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete_device_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_devices_search_placeholder
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_devices_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_edit_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_edit_device_name_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_hours_ago
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_just_now
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_min_ago
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_devices
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_retry
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_save
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown_error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    // TV focus-on-launch: focus the first device once the list arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else state.devices.size,
        tag = "devices_init",
    )

    if (state.showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.admin_delete_device_title),
            message = stringResource(Res.string.admin_delete_device_body, state.selectedDevice?.displayName() ?: ""),
            confirmText = stringResource(Res.string.admin_delete),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.deleteDevice() },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    if (state.showEditNameDialog) {
        ImeAlertDialog(
            onDismissRequest = { viewModel.dismissEditNameDialog() },
            title = { Text(stringResource(Res.string.admin_edit_device_name_title)) },
            text = {
                OutlinedTextField(
                    value = state.editCustomName,
                    onValueChange = { viewModel.updateEditCustomName(it) },
                    label = { Text(stringResource(Res.string.admin_custom_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveDeviceName() }) { Text(stringResource(Res.string.admin_save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditNameDialog() }) { Text(stringResource(Res.string.admin_cancel)) }
            },
        )
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.admin_devices_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            val refreshFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.then(refreshFocusState.focusModifier).tvFocusIndicator(refreshFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(Res.string.admin_refresh))
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
                        Text(state.error ?: stringResource(Res.string.admin_unknown_error), color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        val retryFocusState = rememberTvFocusState()
                        FilledTonalButton(
                            onClick = { viewModel.loadDevices() },
                            modifier = Modifier.then(retryFocusState.focusModifier).tvFocusIndicator(retryFocusState, ShapeCache.smooth12),
                        ) { Text(stringResource(Res.string.admin_retry)) }
                    }
                }
            }
            else -> {
                // Search-by-name/user + grouping by last user.
                var searchQuery by remember { mutableStateOf("") }
                val query = searchQuery.trim()
                val filteredDevices = remember(state.devices, query) {
                    if (query.isEmpty()) state.devices
                    else state.devices.filter { device ->
                        query in device.displayName() ||
                            query in device.appName ||
                            query in device.lastUserName
                    }
                }
                // Group by last user name so multi-user servers stay scannable.
                // Empty user names collapse into a single "Unknown" bucket.
                val unknownUserLabel = stringResource(Res.string.admin_unknown)
                val grouped = remember(filteredDevices, unknownUserLabel) {
                    filteredDevices.groupBy { it.lastUserName.ifBlank { unknownUserLabel } }
                        .toList()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    SearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.admin_devices_search_placeholder),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.devices.isEmpty()) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.DeviceDesktop,
                                title = stringResource(Res.string.admin_no_devices),
                            )
                        } else if (filteredDevices.isEmpty()) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.Search,
                                title = query,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .tvFocusRestorer()
                                    .focusRequester(listFocusRequester),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = adaptiveInfo.bottomPadding(isTv),
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                grouped.forEach { (userName, devices) ->
                                    // Section header per user (only when grouping yields >1 group).
                                    if (grouped.size > 1) {
                                        item(key = "header_$userName") {
                                            Text(
                                                userName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                            )
                                        }
                                    }
                                    itemsIndexed(
                                        items = devices,
                                        key = { index, device -> "${userName}_${index}_${device.id}" },
                                    ) { _, device ->
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
    val editFocusState = rememberTvFocusState()
    val deleteFocusState = rememberTvFocusState()

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
                .focusIndicator(ShapeCache.smooth16)
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
                        val relative = remember(device.dateLastActivity) { formatRelativeTime(device.dateLastActivity) }
                        Text(
                            when (relative) {
                                is RelativeTime.Formatted -> relative.value
                                is RelativeTime.Res -> if (relative.arg != null) {
                                    stringResource(relative.resId, relative.arg)
                                } else {
                                    stringResource(relative.resId)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(36.dp)
                    .then(editFocusState.focusModifier)
                    .tvFocusIndicator(editFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Edit,
                    contentDescription = stringResource(Res.string.admin_edit_cd),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .then(deleteFocusState.focusModifier)
                    .tvFocusIndicator(deleteFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.admin_delete_cd),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun DeviceInfo.displayName(): String = customName?.ifBlank { null } ?: name

private sealed interface RelativeTime {
    data class Formatted(val value: String) : RelativeTime
    data class Res(val resId: org.jetbrains.compose.resources.StringResource, val arg: Any? = null) : RelativeTime
}

private fun formatRelativeTime(dateStr: String): RelativeTime {
    return try {
        val date = java.time.OffsetDateTime.parse(dateStr)
        val now = java.time.OffsetDateTime.now()
        val duration = java.time.Duration.between(date, now)
        when {
            duration.toMinutes() < 1 -> RelativeTime.Res(Res.string.admin_just_now)
            duration.toMinutes() < 60 -> RelativeTime.Res(Res.string.admin_min_ago, duration.toMinutes())
            duration.toHours() < 24 -> RelativeTime.Res(Res.string.admin_hours_ago, duration.toHours())
            duration.toDays() < 7 -> RelativeTime.Res(Res.string.admin_days_ago, duration.toDays())
            else -> RelativeTime.Formatted(dateStr.take(10))
        }
    } catch (_: Exception) {
        RelativeTime.Formatted(dateStr.take(19))
    }
}
