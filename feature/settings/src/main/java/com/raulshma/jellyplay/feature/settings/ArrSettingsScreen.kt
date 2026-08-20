package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrDiscoveryError
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

/**
 * Settings surface for the Direct *arr Integration experimental feature.
 *
 * Lists every server JellyPlay will contact (auto-discovered via Seerr first,
 * then manual overrides), lets the user toggle Seerr auto-discovery, and
 * provides add/remove for manual server entries. Discovered servers are
 * read-only — to remove one, edit it in Seerr.
 *
 * The screen assumes the caller has already gated on the
 * `DIRECT_ARR_INTEGRATION` experimental flag; it does not re-check.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArrSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: ArrSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val allServers = remember(servers) { servers.radarrServers + servers.sonarrServers }

    var showAddDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "arr_init",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_integrations_arr)) },
                navigationIcon = {
                    CircleBgBackButton(onClick = onBack)
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshServers() },
                        enabled = !isRefreshing,
                        modifier = Modifier.focusIndicator(CircleShape),
                    ) {
                        Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.settings_refresh_cd))
                    }
                },
            )
        },
    ) { padding ->
        // Center the focused item in the viewport when scrolling reaches the list
        // edges, instead of parking it at the bottom, which is the default
        // BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .imePadding()
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_arr_direct_integration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_arr_direct_integration_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_arr_auto_discover), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_arr_auto_discover_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = preferences.useSeerrDiscovery,
                            onCheckedChange = { viewModel.setUseSeerrDiscovery(it) },
                            modifier = Modifier.focusIndicator(),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_arr_servers),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f),
                    )
                    if (allServers.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.testAllServers() },
                            modifier = Modifier.focusIndicator(),
                        ) {
                            Text(stringResource(R.string.settings_arr_test_all))
                        }
                    }
                }
            }

            if (allServers.isEmpty()) {
                item {
                    val text = when {
                        isRefreshing -> stringResource(R.string.settings_arr_resolving)
                        servers.discoveryError is ArrDiscoveryError.NoAdminPermission ->
                            stringResource(R.string.settings_arr_no_admin_hint)
                        servers.discoveryError is ArrDiscoveryError.Other ->
                            (servers.discoveryError as ArrDiscoveryError.Other).message
                        else -> stringResource(R.string.settings_arr_no_servers_hint)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // A non-empty list with a discovery error means the user added a
                // manual server as a workaround; surface the misconfiguration so
                // they know auto-discover is still broken.
                servers.discoveryError?.let { err ->
                    item {
                        Text(
                            when (err) {
                                is ArrDiscoveryError.NoAdminPermission ->
                                    stringResource(R.string.settings_arr_no_admin_hint)
                                is ArrDiscoveryError.Other -> err.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                items(allServers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        status = serverStatus[server.id]
                            ?: ArrSettingsViewModel.ServerConnectionStatus.Idle,
                        onTest = { viewModel.testServer(server) },
                        onRemove = { viewModel.removeManualServer(server) },
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusIndicator(),
                ) {
                    Icon(Tabler.Outline.Plus, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_arr_add_manual_server))
                }
            }
        }
        }
    }

    if (showAddDialog) {
        AddManualServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, baseUrl, apiKey, kind ->
                viewModel.addManualServer(name, baseUrl, apiKey, kind)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ServerRow(
    server: ArrServerConfig,
    status: ArrSettingsViewModel.ServerConnectionStatus,
    onTest: () -> Unit,
    onRemove: (ArrServerConfig) -> Unit,
) {
    val isTesting = status is ArrSettingsViewModel.ServerConnectionStatus.Testing
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Spacer(Modifier.width(8.dp))
                Text(server.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                val tagText = when {
                    server.isManual -> stringResource(R.string.settings_arr_manual)
                    server.kind == ArrServiceKind.RADARR -> stringResource(R.string.settings_arr_radarr_seerr)
                    else -> stringResource(R.string.settings_arr_sonarr_seerr)
                }
                Text(
                    tagText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                server.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Status label + error detail. Connected is shown only briefly via the
            // green dot; an explicit "Connected" line would crowd every healthy row.
            when (status) {
                is ArrSettingsViewModel.ServerConnectionStatus.Error -> Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is ArrSettingsViewModel.ServerConnectionStatus.Testing -> Text(
                    stringResource(R.string.settings_connecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.focusIndicator(),
                ) {
                    Text(stringResource(R.string.settings_arr_test))
                }
                Spacer(Modifier.width(8.dp))
                if (server.isManual) {
                    IconButton(
                        onClick = { onRemove(server) },
                        modifier = Modifier.focusIndicator(CircleShape),
                    ) {
                        Icon(Tabler.Outline.Trash, contentDescription = stringResource(R.string.settings_arr_remove_manual_server_cd))
                    }
                }
            }
        }
    }
}

/**
 * Colored dot indicating per-server reachability. Green = reachable, amber =
 * probe in flight, red = last probe failed, gray = not yet probed. Kept tiny
 * (10.dp) so it reads as a status pip next to the server name.
 */
@Composable
private fun StatusDot(status: ArrSettingsViewModel.ServerConnectionStatus) {
    val color = when (status) {
        is ArrSettingsViewModel.ServerConnectionStatus.Connected -> StatusColors.available
        is ArrSettingsViewModel.ServerConnectionStatus.Testing -> StatusColors.pending
        is ArrSettingsViewModel.ServerConnectionStatus.Error -> MaterialTheme.colorScheme.error
        is ArrSettingsViewModel.ServerConnectionStatus.Idle -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddManualServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, baseUrl: String, apiKey: String, ArrServiceKind) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    // Radarr is the more common first entry; default to it.
    var kind by remember { mutableStateOf(ArrServiceKind.RADARR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_arr_add_manual_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ArrServiceKind.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = kind == value,
                            onClick = { kind = value },
                            shape = SegmentedButtonDefaults.itemShape(index, ArrServiceKind.entries.size),
                        ) {
                            Text(stringResource(if (value == ArrServiceKind.RADARR) R.string.settings_radarr else R.string.settings_sonarr))
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.settings_arr_base_url)) },
                    placeholder = { Text(stringResource(R.string.settings_arr_base_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_api_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, baseUrl, apiKey, kind) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Icon(Tabler.Outline.DeviceFloppy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
