package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Book2
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Devices
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Upload
import com.composables.icons.tabler.outline.Users
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.clockTime
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.relativeDayLabel
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import com.raulshma.jellyplay.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The sync screen's tabs (the non-TV layout); each owns one slice of the content. */
private enum class JellyPlaySyncTab(val labelRes: StringResource) {
    Status(Res.string.settings_jellyplay_sync_tab_status),
    Devices(Res.string.settings_jellyplay_sync_devices_title),
    Activity(Res.string.settings_jellyplay_sync_tab_activity),
    Backup(Res.string.settings_jellyplay_sync_tab_backup),
}

/**
 * The JellyPlay companion-plugin's settings-sync screen. The non-TV layout is
 * tabbed: the sync opt-in toggle, the manual sync cycle and the error banner
 * stay pinned above the tab row (the master switch + the failure face are
 * visible from every tab), and a [PrimaryTabRow] splits the content:
 *
 *  - **Status** — the server-side usage face: the quota bars, the
 *    per-namespace selective-sync toggles with their pending counts, and the
 *    admin per-user overview. Degrades on the 404 to the quiet
 *    "unavailable" line, never an error.
 *  - **Devices** — the merged device list (rename this device, revoke
 *    others) and this device's resolved profile.
 *  - **Activity** — the last cycle's `stale-write` conflict rejects with
 *    their ours/theirs resolutions, and the sync history ledger with its
 *    retention footer (rows expand into their lazily-fetched per-key diff).
 *  - **Backup** — the server's restore points (each opening the unified
 *    restore wizard) and the namespace-reset / force-re-pull / export /
 *    restore-from-file actions (the file arm reroutes into the wizard; the
 *    namespace reset captures a safety restore point first).
 *
 * TV (`LocalTvMode`) keeps the untabbed d-pad essentials: the status +
 * sync-now, the error banner, and the namespace toggles only.
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated "Sync" entry (plugin probe AVAILABLE + the
 * `settings-sync` meta feature key); the ViewModel still re-checks the gate
 * before each api call. A server whose plugin does not serve the sync-status
 * reads 404s the usage/history/snapshot calls — those sections degrade to the
 * quiet "unavailable" line, never an error.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlaySyncScreen(
    onBack: () -> Unit,
    /** The actions group's import row: opens the unified restore wizard's file arm. */
    onRestoreWizard: () -> Unit = {},
    /** A tapped restore point: opens the wizard's snapshot arm (preview / degrade / confirm / apply). */
    onRestoreSnapshot: (String) -> Unit = {},
    viewModel: JellyPlaySyncViewModel = koinViewModel(),
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val platformIntents = rememberPlatformIntents()
    val bus = LocalUserMessageBus.current

    // Freshness on open: one pull per visit (the messages screen's idiom);
    // manual actions re-pull through the ViewModel.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // The pre-reset safety capture missed — warn once, never block.
    val safetyMissedText = stringResource(Res.string.wizard_safety_snapshot_failed)
    LaunchedEffect(uiState.safetySnapshotMissed) {
        if (uiState.safetySnapshotMissed) {
            bus.error(safetyMissedText)
            viewModel.clearSafetySnapshotMissed()
        }
    }

    val resetConfirm = rememberConfirmState()
    val revokeConfirm = rememberConfirmState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var expandedConflictKey by remember { mutableStateOf<String?>(null) }

    // The export share's pre-resolved texts (the share call fires from a
    // non-composable callback).
    val exportSubject = stringResource(Res.string.settings_jellyplay_sync_export_title)
    val exportChooser = stringResource(Res.string.settings_jellyplay_sync_export_subtitle)

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_jellyplay_sync),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { contentPadding ->
        if (isTv) {
            // TV: the untabbed d-pad essentials — status + sync-now, the
            // error banner, and the namespace toggles only.
            val tvListState = rememberLazyListState()
            LazyColumn(
                state = tvListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SettingsItemList(total = rowTotalFor(JellyPlaySyncGroup, RowAdmissionFlags())) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Devices,
                            title = stringResource(Res.string.settings_sync_across_devices),
                            subtitle = stringResource(Res.string.ss_jellyplay_sync_enabled_subtitle),
                            checked = syncState.enabled,
                            onCheckedChange = { enabled -> viewModel.setSyncEnabled(enabled) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Refresh,
                            title = stringResource(Res.string.settings_sync_now),
                            subtitle = syncNowSubtitle(syncState, uiState.thisDeviceId, uiState.status),
                            onClick = { viewModel.syncNow() },
                        )
                    }
                }
                if (syncState.lastError != null || uiState.actionError) {
                    item(key = "error_banner") { ErrorBanner(text = syncState.lastError) }
                }
                item(key = "usage_label") { SectionLabel(
                        stringResource(Res.string.settings_jellyplay_sync_usage_title),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    ) }
                val status = uiState.status
                if (status == null) {
                    item(key = "usage_unavailable") {
                        QuietLine(stringResource(Res.string.settings_jellyplay_sync_usage_unavailable))
                    }
                } else {
                    val namespaceRows = uiState.namespaceRows.filter { it.toggleable }
                    items(namespaceRows, key = { "ns_${it.ns}" }, contentType = { "namespaceToggle" }) { namespace ->
                        val presentation = syncNamespacePresentation(namespace.ns)
                        SettingToggleItem(
                            icon = presentation.icon,
                            title = presentation.title,
                            subtitle = namespaceSubtitle(namespace, presentation.description, syncState.enabled),
                            checked = namespace.enabled,
                            enabled = syncState.enabled,
                            onCheckedChange = { enabled -> viewModel.setNamespaceEnabled(namespace.ns, enabled) },
                        )
                    }
                }
            }
        } else {
            // Non-TV: compact pinned master controls and surface-backed tab row;
            // content sits inside a bounded weighted box with independent scroll states.
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
            val tab = JellyPlaySyncTab.entries.getOrElse(selectedTabIndex) { JellyPlaySyncTab.Status }
            val statusListState = rememberLazyListState()
            val devicesListState = rememberLazyListState()
            val activityListState = rememberLazyListState()
            val backupListState = rememberLazyListState()
            val bottomPadding = contentPadding.calculateBottomPadding() + 96.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding()),
            ) {
                Surface(
                    color = backgroundColorState.value,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            SettingsItemList(total = rowTotalFor(JellyPlaySyncGroup, RowAdmissionFlags())) {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Devices,
                                    title = stringResource(Res.string.settings_sync_across_devices),
                                    subtitle = if (syncState.enabled) {
                                        stringResource(Res.string.settings_jellyplay_sync_on)
                                    } else {
                                        stringResource(Res.string.settings_jellyplay_sync_off)
                                    },
                                    checked = syncState.enabled,
                                    onCheckedChange = { enabled -> viewModel.setSyncEnabled(enabled) },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.Refresh,
                                    title = stringResource(Res.string.settings_sync_now),
                                    subtitle = syncNowSubtitle(syncState, uiState.thisDeviceId, uiState.status),
                                    onClick = { viewModel.syncNow() },
                                )
                            }
                            if (syncState.lastError != null || uiState.actionError) {
                                ErrorBanner(text = syncState.lastError)
                            }
                        }
                        TabRow(
                            selectedTabIndex = tab.ordinal,
                            containerColor = backgroundColorState.value,
                            divider = {},
                        ) {
                            JellyPlaySyncTab.entries.forEach { entry ->
                                Tab(
                                    selected = tab == entry,
                                    onClick = { selectedTabIndex = entry.ordinal },
                                    text = {
                                        Text(
                                            text = stringResource(entry.labelRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (tab == entry) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (tab) {
                        JellyPlaySyncTab.Status -> SyncStatusTabContent(
                            uiState = uiState,
                            syncEnabled = syncState.enabled,
                            listState = statusListState,
                            bottomPadding = bottomPadding,
                            onNamespaceEnabled = { ns, enabled -> viewModel.setNamespaceEnabled(ns, enabled) },
                        )
                        JellyPlaySyncTab.Devices -> SyncDevicesTabContent(
                            uiState = uiState,
                            deviceProfile = viewModel.deviceProfile,
                            listState = devicesListState,
                            bottomPadding = bottomPadding,
                            onRenameThisDevice = { name ->
                                renameText = name
                                showRenameDialog = true
                            },
                            onRevokeDevice = { deviceId ->
                                revokeConfirm.request { viewModel.revokeDevice(deviceId) }
                            },
                        )
                        JellyPlaySyncTab.Activity -> SyncActivityTabContent(
                            uiState = uiState,
                            conflicts = syncState.conflicts,
                            expandedConflictKey = expandedConflictKey,
                            listState = activityListState,
                            bottomPadding = bottomPadding,
                            onConflictToggle = { key ->
                                expandedConflictKey = if (expandedConflictKey == key) null else key
                            },
                            onKeepMine = { ns, key -> viewModel.resolveConflictKeepMine(ns, key) },
                            onTakeTheirs = { ns, key -> viewModel.resolveConflictTakeTheirs(ns, key) },
                            onToggleHistory = { seq -> viewModel.toggleHistoryEntryExpanded(seq) },
                        )
                        JellyPlaySyncTab.Backup -> SyncBackupTabContent(
                            uiState = uiState,
                            listState = backupListState,
                            bottomPadding = bottomPadding,
                            onCreateSnapshot = { if (!uiState.actionInFlight) viewModel.createSnapshot() },
                            onRestoreSnapshot = onRestoreSnapshot,
                            onResetNamespace = { resetConfirm.request { viewModel.resetNamespace() } },
                            onForceRepull = { viewModel.forceRepull() },
                            onExport = {
                                if (!uiState.actionInFlight) {
                                    viewModel.exportSettings { json ->
                                        platformIntents.shareJson(exportSubject, exportChooser, json)
                                    }
                                }
                            },
                            onRestoreWizard = onRestoreWizard,
                        )
                    }
                }
            }
        }
    }

    resetConfirm.ConfirmDialog(
        title = stringResource(Res.string.settings_jellyplay_sync_reset_confirm_title),
        message = stringResource(Res.string.settings_jellyplay_sync_reset_confirm_message),
        confirmText = stringResource(Res.string.settings_reset),
        dismissText = stringResource(Res.string.settings_cancel),
        tone = ConfirmTone.DESTRUCTIVE,
        icon = Tabler.Outline.Trash,
    )

    revokeConfirm.ConfirmDialog(
        title = stringResource(Res.string.settings_jellyplay_sync_device_revoke_confirm_title),
        message = stringResource(Res.string.settings_jellyplay_sync_device_revoke_confirm_message),
        confirmText = stringResource(Res.string.settings_jellyplay_sync_device_revoke_title),
        dismissText = stringResource(Res.string.settings_cancel),
        tone = ConfirmTone.DESTRUCTIVE,
        icon = Tabler.Outline.Ban,
    )

    if (showRenameDialog) {
        RenameDeviceDialog(
            initialName = renameText,
            onRename = { name ->
                showRenameDialog = false
                viewModel.renameThisDevice(name)
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

/** The Status tab: the usage face — quota bars, per-namespace rows, the admin per-user overview. */
@Composable
private fun SyncStatusTabContent(
    uiState: JellyPlaySyncUiState,
    syncEnabled: Boolean,
    listState: LazyListState,
    bottomPadding: Dp,
    onNamespaceEnabled: (ns: String, enabled: Boolean) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "usage_label") { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_usage_title),
                Modifier.padding(top = 4.dp, start = 4.dp),
            ) }
        val status = uiState.status
        if (status == null) {
            // The 404 degrade — the quiet line, never an error.
            item(key = "usage_unavailable") {
                QuietLine(stringResource(Res.string.settings_jellyplay_sync_usage_unavailable))
            }
        } else {
            item(key = "quota_card") { QuotaCard(status) }
            items(uiState.namespaceRows, key = { "ns_${it.ns}" }, contentType = { row ->
                if (row.toggleable) "namespaceToggle" else "namespaceInfo"
            }) { namespace ->
                val presentation = syncNamespacePresentation(namespace.ns)
                if (namespace.toggleable) {
                    SettingToggleItem(
                        icon = presentation.icon,
                        title = presentation.title,
                        subtitle = namespaceSubtitle(namespace, presentation.description, syncEnabled),
                        checked = namespace.enabled,
                        enabled = syncEnabled,
                        onCheckedChange = { enabled -> onNamespaceEnabled(namespace.ns, enabled) },
                    )
                } else {
                    SettingInfoItem(
                        icon = presentation.icon,
                        title = presentation.title,
                        subtitle = namespaceSubtitle(namespace, presentation.description, syncEnabled),
                    )
                }
            }
            val adminUsers = uiState.adminUsers
            if (!adminUsers.isNullOrEmpty()) {
                item(key = "admin_label") { SectionLabel(
                    stringResource(Res.string.settings_jellyplay_sync_admin_section),
                    Modifier.padding(top = 8.dp, start = 4.dp),
                ) }
                items(adminUsers, key = { "admin_${it.userId}" }, contentType = { "adminUser" }) { user ->
                    val formattedUsage = if (user.keys == 1) {
                        stringResource(Res.string.settings_jellyplay_sync_key_singular, user.keys, user.bytes.formatBytes())
                    } else {
                        stringResource(Res.string.settings_jellyplay_sync_keys_plural, user.keys, user.bytes.formatBytes())
                    }
                    SettingInfoItem(
                        icon = Tabler.Outline.Users,
                        title = user.userName.ifBlank { user.userId },
                        subtitle = "$formattedUsage • ${user.deviceCount} devices",
                    )
                }
            }
        }
    }
}

/**
 * The Devices tab: the registry merged with the server's per-device ledger;
 * this device first (rename), the others revocable (the v7 revoke + wipe).
 * Revoked rows render destructive and inert. Below: the resolved profile this
 * device syncs under.
 */
@Composable
private fun SyncDevicesTabContent(
    uiState: JellyPlaySyncUiState,
    deviceProfile: String,
    listState: LazyListState,
    bottomPadding: Dp,
    onRenameThisDevice: (String) -> Unit,
    onRevokeDevice: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.devices, key = { it.deviceId }, contentType = { "deviceRow" }) { device ->
            val lastSync = device.lastSyncAt
                ?.let { stringResource(Res.string.settings_jellyplay_sync_device_last_sync, formatSyncTimestamp(it)) }
                ?: stringResource(Res.string.settings_sync_never)
            SettingListItem(
                icon = Tabler.Outline.Devices,
                title = buildList {
                    add(device.name)
                    if (device.revoked) add(stringResource(Res.string.settings_jellyplay_sync_device_revoked))
                }.joinToString(" • "),
                subtitle = buildList {
                    if (device.platform.isNotBlank()) add(device.platform)
                    device.model?.let { add(it) }
                    add(lastSync)
                    if (device.isThisDevice) add(stringResource(Res.string.settings_jellyplay_sync_this_device))
                }.joinToString(" • "),
                isDestructive = device.revoked,
                onClick = {
                    when {
                        device.isThisDevice -> onRenameThisDevice(device.name)
                        !device.revoked -> onRevokeDevice(device.deviceId)
                    }
                },
            )
        }
        item(key = "profile_row") {
            SettingInfoItem(
                icon = Tabler.Outline.DeviceDesktop,
                title = stringResource(Res.string.settings_jellyplay_sync_profile_title),
                subtitle = stringResource(Res.string.settings_jellyplay_sync_profile_subtitle, deviceProfile),
            )
        }
    }
}

/**
 * The Activity tab: the last cycle's conflict rejects (each expanding into
 * the ours/theirs pair + the two resolutions) above the server's history
 * ledger with its retention footer. The history rides the status payload — a
 * plugin old enough to 404 the status 404s the history too; with no
 * conflicts either, the tab degrades to the quiet "unavailable" line.
 */
@Composable
private fun SyncActivityTabContent(
    uiState: JellyPlaySyncUiState,
    conflicts: List<ProfileSyncRepository.SyncConflict>,
    expandedConflictKey: String?,
    listState: LazyListState,
    bottomPadding: Dp,
    onConflictToggle: (String) -> Unit,
    onKeepMine: (ns: String, key: String) -> Unit,
    onTakeTheirs: (ns: String, key: String) -> Unit,
    onToggleHistory: (Long) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (conflicts.isNotEmpty()) {
            item(key = "conflicts_label") { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_conflicts_title),
                Modifier.padding(top = 4.dp, start = 4.dp),
            ) }
            items(conflicts, key = { "conflict_${it.ns}/${it.key}" }, contentType = { "conflictRow" }) { conflict ->
                val key = "${conflict.ns}/${conflict.key}"
                ConflictRow(
                    conflict = conflict,
                    expanded = expandedConflictKey == key,
                    onToggle = { onConflictToggle(key) },
                    onKeepMine = {
                        onConflictToggle(key)
                        onKeepMine(conflict.ns, conflict.key)
                    },
                    onTakeTheirs = {
                        onConflictToggle(key)
                        onTakeTheirs(conflict.ns, conflict.key)
                    },
                )
            }
        }
        val status = uiState.status
        if (status != null) {
            item(key = "history_label") { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_history_title),
                Modifier.padding(top = 4.dp, start = 4.dp),
            ) }
            if (uiState.history.isEmpty()) {
                item(key = "history_empty") { QuietLine(stringResource(Res.string.settings_jellyplay_sync_history_empty)) }
            } else {
                items(uiState.history, key = { it.seq }, contentType = { "historyRow" }) { entry ->
                    HistoryRow(
                        entry = entry,
                        expanded = uiState.expandedHistorySeq == entry.seq,
                        detail = uiState.historyKeyDetails[entry.seq],
                        onToggle = { onToggleHistory(entry.seq) },
                    )
                }
                item(key = "history_retention") {
                    QuietLine(
                        stringResource(Res.string.settings_jellyplay_sync_history_retention, status.historyRetentionDays),
                    )
                }
            }
        } else if (conflicts.isEmpty()) {
            item(key = "activity_unavailable") {
                QuietLine(stringResource(Res.string.settings_jellyplay_sync_activity_unavailable))
            }
        }
    }
}

/**
 * The Backup tab: the server's rolling restore points — create one, restore
 * one (routed into the unified wizard) — above the actions group: the
 * destructive namespace reset (confirmed; safety capture inside the VM), the
 * pull-dominant recovery cycle, the JSON export, and the restore-from-file
 * entry. null snapshots (old plugin / failed read) hides that section
 * quietly.
 */
@Composable
private fun SyncBackupTabContent(
    uiState: JellyPlaySyncUiState,
    listState: LazyListState,
    bottomPadding: Dp,
    onCreateSnapshot: () -> Unit,
    onRestoreSnapshot: (String) -> Unit,
    onResetNamespace: () -> Unit,
    onForceRepull: () -> Unit,
    onExport: () -> Unit,
    onRestoreWizard: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val snapshots = uiState.snapshots
        if (snapshots != null) {
            item(key = "snapshots_label") { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_snapshots_title),
                Modifier.padding(top = 4.dp, start = 4.dp),
            ) }
            item(key = "snapshot_create") {
                SettingListItem(
                    icon = Tabler.Outline.DeviceFloppy,
                    title = stringResource(Res.string.settings_jellyplay_sync_snapshots_create_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_snapshots_create_subtitle),
                    onClick = onCreateSnapshot,
                )
            }
            if (snapshots.isEmpty()) {
                item(key = "snapshots_empty") { QuietLine(stringResource(Res.string.settings_jellyplay_sync_snapshots_empty)) }
            } else {
                items(snapshots, key = { "snapshot_${it.id}" }, contentType = { "snapshotRow" }) { snapshot ->
                    val originDisplay = when (snapshot.origin.lowercase()) {
                        "manual" -> stringResource(Res.string.settings_jellyplay_sync_origin_manual)
                        "safety" -> stringResource(Res.string.settings_jellyplay_sync_origin_safety)
                        "auto" -> stringResource(Res.string.settings_jellyplay_sync_origin_auto)
                        else -> snapshot.origin.ifBlank { stringResource(Res.string.settings_jellyplay_sync_op_unknown) }
                    }
                    val formattedKeys = if (snapshot.keys == 1) {
                        stringResource(Res.string.settings_jellyplay_sync_key_singular, snapshot.keys, snapshot.bytes.formatBytes())
                    } else {
                        stringResource(Res.string.settings_jellyplay_sync_keys_plural, snapshot.keys, snapshot.bytes.formatBytes())
                    }
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = formatSyncTimestamp(snapshot.createdAt),
                        subtitle = "$originDisplay • $formattedKeys",
                        trailingText = null,
                        onClick = { onRestoreSnapshot(snapshot.id.toString()) },
                    )
                }
            }
        }
        item(key = "actions_label") { SectionLabel(
            stringResource(Res.string.settings_jellyplay_sync_actions_title),
            Modifier.padding(top = 8.dp, start = 4.dp),
        ) }
        item(key = "actions_group") {
            SettingsItemList(total = 4) {
                SettingListItem(
                    icon = Tabler.Outline.Trash,
                    title = stringResource(Res.string.settings_jellyplay_sync_reset_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_reset_subtitle),
                    isDestructive = true,
                    onClick = onResetNamespace,
                )
                SettingListItem(
                    icon = Tabler.Outline.History,
                    title = stringResource(Res.string.settings_jellyplay_sync_force_repull_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_force_repull_subtitle),
                    onClick = onForceRepull,
                )
                SettingListItem(
                    icon = Tabler.Outline.Share,
                    title = stringResource(Res.string.settings_jellyplay_sync_export_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_export_subtitle),
                    onClick = onExport,
                )
                SettingListItem(
                    icon = Tabler.Outline.Download,
                    title = stringResource(Res.string.settings_jellyplay_sync_import_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_import_subtitle),
                    onClick = onRestoreWizard,
                )
            }
        }
    }
}

/** The sync-now row's factual line: the in-flight flag, then the error, then this device's last sync + pending count. */
@Composable
private fun syncNowSubtitle(
    syncState: ProfileSyncRepository.SyncState,
    thisDeviceId: String?,
    status: JellyPlaySyncStatus?,
): String {
    val lastSyncAt = status?.perDevice
        ?.firstOrNull { it.deviceId == thisDeviceId && it.lastSyncAt > 0 }
        ?.lastSyncAt
        ?: syncState.lastSyncAt
    val base = when {
        syncState.inFlight -> stringResource(Res.string.settings_jellyplay_sync_in_flight)
        syncState.lastError != null -> stringResource(Res.string.settings_jellyplay_sync_failed)
        lastSyncAt != null -> stringResource(
            Res.string.settings_jellyplay_sync_last_sync,
            formatSyncTimestamp(lastSyncAt),
        )
        else -> stringResource(Res.string.settings_sync_never)
    }
    val pending = syncState.pendingByNamespace.values.sum()
    return if (pending > 0 && !syncState.inFlight) {
        "$base • ${stringResource(Res.string.settings_jellyplay_sync_pending, pending)}"
    } else {
        base
    }
}

private data class SyncNamespacePresentation(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
private fun syncNamespacePresentation(ns: String): SyncNamespacePresentation {
    return when (ns.lowercase()) {
        "prefs" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_prefs_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_prefs_desc),
            icon = Tabler.Outline.AdjustmentsHorizontal,
        )
        "books" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_books_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_books_desc),
            icon = Tabler.Outline.Book,
        )
        "search" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_search_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_search_desc),
            icon = Tabler.Outline.Search,
        )
        "cw" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_cw_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_cw_desc),
            icon = Tabler.Outline.PlayerPlay,
        )
        "reader" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_reader_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_reader_desc),
            icon = Tabler.Outline.Book2,
        )
        "homelayout" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_homelayout_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_homelayout_desc),
            icon = Tabler.Outline.LayoutGrid,
        )
        "integrations" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_integrations_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_integrations_desc),
            icon = Tabler.Outline.Apps,
        )
        "itemprefs" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_itemprefs_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_itemprefs_desc),
            icon = Tabler.Outline.Movie,
        )
        "playlists" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_playlists_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_playlists_desc),
            icon = Tabler.Outline.Playlist,
        )
        "ui" -> SyncNamespacePresentation(
            title = stringResource(Res.string.settings_jellyplay_sync_ns_ui_title),
            description = stringResource(Res.string.settings_jellyplay_sync_ns_ui_desc),
            icon = Tabler.Outline.Palette,
        )
        else -> SyncNamespacePresentation(
            title = ns.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            description = "",
            icon = Tabler.Outline.Database,
        )
    }
}

/** One namespace row's factual line: description, usage with pluralization, or sync-off note. */
@Composable
private fun namespaceSubtitle(
    namespace: JellyPlaySyncNamespaceRow,
    description: String,
    masterSyncEnabled: Boolean,
): String {
    val formattedUsage = if (namespace.keys == 1) {
        stringResource(Res.string.settings_jellyplay_sync_key_singular, namespace.keys, namespace.bytes.formatBytes())
    } else {
        stringResource(Res.string.settings_jellyplay_sync_keys_plural, namespace.keys, namespace.bytes.formatBytes())
    }
    return buildList {
        if (!masterSyncEnabled) {
            add(stringResource(Res.string.settings_jellyplay_sync_master_off_hint))
        } else if (namespace.toggleable && !namespace.enabled) {
            add(stringResource(Res.string.settings_jellyplay_sync_ns_off))
        }
        if (namespace.pending > 0 && masterSyncEnabled) {
            add(stringResource(Res.string.settings_jellyplay_sync_pending, namespace.pending))
        }
        if (description.isNotBlank()) {
            add(description)
        }
        if (namespace.keys > 0 || namespace.bytes > 0) {
            add(formattedUsage)
        }
    }.joinToString(" • ")
}

/** The sync failures' banner: the engine error verbatim, else the generic action-failed note. */
@Composable
private fun ErrorBanner(text: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "⚠",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column {
            Text(
                text = stringResource(Res.string.settings_jellyplay_sync_error_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = text ?: stringResource(Res.string.settings_jellyplay_sync_action_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One conflict row: the `ns/key` + reason face, tappable to expand the
 * ours/theirs previews and the two resolutions ("keep mine" re-pushes with a
 * fresh stamp; "take theirs" adopts the server's current value).
 */
@Composable
private fun ConflictRow(
    conflict: ProfileSyncRepository.SyncConflict,
    expanded: Boolean,
    onToggle: () -> Unit,
    onKeepMine: () -> Unit,
    onTakeTheirs: () -> Unit,
) {
    Column {
        SettingListItem(
            icon = Tabler.Outline.AlertTriangle,
            title = stringResource(
                Res.string.settings_jellyplay_sync_conflict_row,
                "${conflict.ns}/${conflict.key}",
                conflict.reason,
            ),
            subtitle = null,
            onClick = onToggle,
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ConflictValueLine(
                    label = stringResource(Res.string.settings_jellyplay_sync_conflict_ours),
                    value = conflict.ours,
                )
                ConflictValueLine(
                    label = stringResource(Res.string.settings_jellyplay_sync_conflict_theirs),
                    value = conflict.theirs,
                )
                SettingListItem(
                    icon = Tabler.Outline.Upload,
                    title = stringResource(Res.string.settings_jellyplay_sync_conflict_keep_mine),
                    subtitle = null,
                    onClick = {
                        onToggle()
                        onKeepMine()
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.CloudDownload,
                    title = stringResource(Res.string.settings_jellyplay_sync_conflict_take_theirs),
                    subtitle = null,
                    onClick = {
                        onToggle()
                        onTakeTheirs()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConflictValueLine(label: String, value: kotlinx.serialization.json.JsonElement?) {
    Text(
        text = buildString {
            append(label)
            append(": ")
            append(
                when (value) {
                    null -> stringResource(Res.string.settings_jellyplay_sync_conflict_deleted_value)
                    else -> value.toString().take(120)
                },
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun QuotaCard(status: JellyPlaySyncStatus) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (status.quotaBytes > 0) {
            QuotaBar(
                label = stringResource(
                    Res.string.settings_jellyplay_sync_quota_bytes,
                    status.bytes.formatBytes(),
                    status.quotaBytes.formatBytes(),
                ),
                ratio = (status.bytes.toFloat() / status.quotaBytes.toFloat()).coerceIn(0f, 1f),
            )
        }
        if (status.quotaKeys > 0) {
            Spacer(Modifier.height(8.dp))
            QuotaBar(
                label = stringResource(
                    Res.string.settings_jellyplay_sync_quota_keys,
                    status.keys,
                    status.quotaKeys,
                ),
                ratio = (status.keys.toFloat() / status.quotaKeys.toFloat()).coerceIn(0f, 1f),
            )
        }
    }
}

@Composable
private fun QuotaBar(label: String, ratio: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * One history row: the op + timestamp/count face, tappable to expand the
 * entry's per-key diff below (the VM fetches it lazily on first expand and
 * caches per seq). The expanded block reads:
 *  - the changed keys as `ns/key` lines with a relative `updatedAt`,
 *  - "No key changes" when the server holds no diff (reset rows),
 *  - a quiet "—" when the detail never landed (a 404, a failed
 *    read — degrade, never an error), a "Loading changes…" line in flight.
 */
@Composable
private fun HistoryRow(
    entry: JellyPlaySyncHistoryEntry,
    expanded: Boolean,
    detail: JellyPlaySyncHistoryKeyDetail?,
    onToggle: () -> Unit,
) {
    val rejects = entry.rejects.orEmpty()
    val (opIcon, opLabelRes) = syncOpFace(entry.op)
    Column {
        SettingInfoItem(
            icon = opIcon,
            title = stringResource(opLabelRes),
            subtitle = buildList {
                add(
                    stringResource(
                        Res.string.settings_jellyplay_sync_history_entry,
                        formatSyncTimestamp(entry.ts),
                        entry.keysApplied,
                        entry.keysRejected,
                    ),
                )
                if (rejects.isNotEmpty()) {
                    add(
                        stringResource(
                            Res.string.settings_jellyplay_sync_history_rejects,
                            rejects.take(3).joinToString(", ") { reject -> "${reject.ns}/${reject.key}: ${reject.reason}" },
                        ),
                    )
                }
            }.joinToString("\n"),
            modifier = Modifier.clickable(onClick = onToggle),
        )
        if (expanded) {
            HistoryKeyDiff(detail)
        }
    }
}

/** The expanded per-key diff block under one history row. */
@Composable
private fun HistoryKeyDiff(detail: JellyPlaySyncHistoryKeyDetail?) {
    when {
        detail == null || detail.isLoading -> QuietLine(stringResource(Res.string.settings_jellyplay_sync_history_keys_loading))
        detail.keys == null -> QuietLine("—")
        detail.keys.isEmpty() -> QuietLine(stringResource(Res.string.settings_jellyplay_sync_history_keys_empty))
        else -> Column(
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            detail.keys.forEach { key ->
                Text(
                    text = buildList {
                        add("${key.ns}/${key.key}")
                        if (key.updatedAt > 0) add(relativeSyncTime(key.updatedAt))
                    }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The wire ops' declared faces: icon + label resource keyed by the op string (rows own their faces, per wire op). */
private val SyncOpFaces = mapOf(
    "push" to (Tabler.Outline.Upload to Res.string.settings_jellyplay_sync_op_push),
    "pull" to (Tabler.Outline.ArrowDown to Res.string.settings_jellyplay_sync_op_pull),
    "reset" to (Tabler.Outline.Refresh to Res.string.settings_jellyplay_sync_op_reset),
)

/** The op's face — unknown ops get the generic arrow + "unknown" label. */
private fun syncOpFace(op: String) =
    SyncOpFaces[op.lowercase()] ?: (Tabler.Outline.ArrowUp to Res.string.settings_jellyplay_sync_op_unknown)

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/**
 * The device rename dialog: the ActiveDevicesRow message dialog's idiom — an
 * [ImeAlertDialog] over one single-line field, confirm disabled while blank.
 */
@Composable
private fun RenameDeviceDialog(
    initialName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.settings_jellyplay_sync_device_rename_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.settings_jellyplay_sync_device_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.settings_jellyplay_sync_device_rename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) }
        },
    )
}

/**
 * The sync timestamps' shape: "HH:mm" today, "Yesterday, HH:mm" the day
 * before, "MMM d, HH:mm" older. The DATE half rides the core:ui seam —
 * [relativeDayLabel] (Today / Yesterday / shortMonthDay) — the clock fragment
 * rides [clockTime]; the feature owns only the composite. Fallback keeps the
 * raw millis honest.
 */
@Composable
private fun formatSyncTimestamp(epochMillis: Long): String {
    val parts = runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val dateTime = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        Triple(
            dateTime.toLocalDate().toKotlinLocalDate(),
            java.time.LocalDate.now(zone).toKotlinLocalDate(),
            dateTime.toLocalTime().toKotlinLocalTime(),
        )
    }.getOrNull() ?: return epochMillis.toString()
    val (date, today, time) = parts
    return if (date == today) {
        clockTime(time)
    } else {
        "${relativeDayLabel(date, today)}, ${clockTime(time)}"
    }
}

/**
 * The diff keys' relative stamp: "just now" / "5m ago" / "3h ago" / "2d ago"
 * — the integer-slot patterns render through core:ui's [formatIntPattern]
 * (the ONE renderer for "N days / N minutes" translation patterns), the
 * epoch-millis delta computed against [wallNowMillis], never a raw
 * System.currentTimeMillis in commonMain. A server clock ahead of this
 * device reads as "just now".
 */
@Composable
private fun relativeSyncTime(epochMillis: Long): String {
    val deltaMinutes = (wallNowMillis() - epochMillis) / 60_000
    return when {
        deltaMinutes < 1 -> stringResource(Res.string.settings_jellyplay_sync_relative_now)
        deltaMinutes < 60 -> formatIntPattern(
            stringResource(Res.string.settings_jellyplay_sync_relative_minutes),
            deltaMinutes.toInt(),
        )
        deltaMinutes < 24 * 60 -> formatIntPattern(
            stringResource(Res.string.settings_jellyplay_sync_relative_hours),
            (deltaMinutes / 60).toInt(),
        )
        else -> formatIntPattern(
            stringResource(Res.string.settings_jellyplay_sync_relative_days),
            (deltaMinutes / (24 * 60)).toInt(),
        )
    }
}
