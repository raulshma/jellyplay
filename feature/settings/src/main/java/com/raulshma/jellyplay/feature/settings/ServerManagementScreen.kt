package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerManagementScreen(
    onAddServer: () -> Unit,
    onBack: () -> Unit,
    onServerSwitched: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: ServerManagementViewModel = hiltViewModel(),
) {
    val servers = viewModel.servers
    val activeServerId = viewModel.activeServerId
    val isSwitching = viewModel.isSwitching
    val isAddressOpInProgress = viewModel.isAddressOperationInProgress

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    val userMessageBus = LocalUserMessageBus.current

    var expandedServerId by remember { mutableStateOf<String?>(null) }
    var showAddAddressFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.addressOperationMessage) {
        viewModel.addressOperationMessage?.let { msg ->
            userMessageBus.info(msg)
            viewModel.clearAddressOperationMessage()
        }
    }

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "server_management_init",
    )

    JellyPlayScreenScaffold(
        title = "Server Management",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            IconButton(onClick = onAddServer) {
                Icon(Tabler.Outline.Plus, "Add Server")
            }
        },
    ) {
        if (servers.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.Server,
                title = "No servers configured",
                actionLabel = "Add Server",
                onAction = onAddServer,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(focusRequester),
                contentPadding = PaddingValues(contentPad),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                item {
                    Text(
                        "Tap a server to switch. Active server is highlighted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(servers, key = { _, it -> it.id }, contentType = { _, _ -> "server" }) { index, server ->
                    val visible = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible.value = true }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                        ) + slideInVertically(
                            initialOffsetY = { it / 10 },
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                    ) {
                        ServerCard(
                            server = server,
                            isActive = server.id == activeServerId,
                            isSwitching = isSwitching,
                            isExpanded = expandedServerId == server.id,
                            isAddressOpInProgress = isAddressOpInProgress,
                            onSwitch = {
                                viewModel.switchServer(server.id) {
                                    onServerSwitched()
                                }
                            },
                            onDelete = { viewModel.removeServer(server.id) },
                            onToggleExpand = {
                                expandedServerId = if (expandedServerId == server.id) null else server.id
                            },
                            onAddAddress = { showAddAddressFor = server.id },
                            onRemoveAddress = { address ->
                                viewModel.removeServerAddress(server.id, address)
                            },
                            onSwitchAddress = { address ->
                                viewModel.switchServerAddress(server.id, address)
                            },
                        )
                    }
                }
            }
        }
    }

    showAddAddressFor?.let { serverId ->
        AddAddressSheet(
            onDismiss = { showAddAddressFor = null },
            onAdd = { address ->
                viewModel.addServerAddress(serverId, address)
                showAddAddressFor = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(
    server: ServerInfo,
    isActive: Boolean,
    isSwitching: Boolean,
    isExpanded: Boolean,
    isAddressOpInProgress: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onToggleExpand: () -> Unit,
    onAddAddress: () -> Unit,
    onRemoveAddress: (String) -> Unit,
    onSwitchAddress: (String) -> Unit,
) {
    Card(
        onClick = { if (!isActive && !isSwitching) onSwitch() },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        enabled = !isSwitching,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Tabler.Outline.Server,
                        contentDescription = null,
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = server.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (server.userId != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.User,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Authenticated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        IconButton(onClick = onToggleExpand) {
                            Icon(
                                if (isExpanded) Tabler.Outline.ChevronUp else Tabler.Outline.ChevronDown,
                                contentDescription = if (isExpanded) "Collapse" else "Manage addresses",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Icon(
                            Tabler.Outline.Check,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else if (isSwitching) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Tabler.Outline.Trash,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isActive && isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.height(12.dp))

                    AddressRow(
                        address = server.address,
                        isPrimary = true,
                        onSwitch = null,
                        onRemove = null,
                    )

                    server.alternateAddresses.forEach { altAddress ->
                        AddressRow(
                            address = altAddress,
                            isPrimary = false,
                            onSwitch = if (!isAddressOpInProgress) {
                                { onSwitchAddress(altAddress) }
                            } else null,
                            onRemove = {
                                onRemoveAddress(altAddress)
                            },
                        )
                    }

                    val addAddressFocus = rememberTvFocusState(focusedScale = 1.02f)
                    val addShape = ShapeCache.smoothPill
                    Surface(
                        onClick = onAddAddress,
                        shape = addShape,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .then(addAddressFocus.focusModifier)
                            .tvFocusIndicator(addAddressFocus, addShape),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Tabler.Outline.Plus,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Add Address",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressRow(
    address: String,
    isPrimary: Boolean,
    onSwitch: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                if (isPrimary) Tabler.Outline.Star else Tabler.Outline.Link,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isPrimary) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "Primary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                )
            }
        }
        if (onSwitch != null) {
            val switchFocus = rememberTvFocusState(focusedScale = 1.05f)
            val btnShape = ShapeCache.smoothPill
            Surface(
                onClick = onSwitch,
                shape = btnShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .then(switchFocus.focusModifier)
                    .tvFocusIndicator(switchFocus, btnShape),
            ) {
                Text(
                    "Switch",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Tabler.Outline.X,
                    contentDescription = "Remove address",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAddressSheet(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var address by remember { mutableStateOf("") }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Add Server Address",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter an alternate URL for this server (e.g., a local IP address).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Server address") },
                placeholder = { Text("https://192.168.1.100:8096") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onAdd(address.trim()) },
                    enabled = address.isNotBlank(),
                    shape = ShapeCache.smoothPill,
                ) { Text("Add") }
            }
        }
    }
}
