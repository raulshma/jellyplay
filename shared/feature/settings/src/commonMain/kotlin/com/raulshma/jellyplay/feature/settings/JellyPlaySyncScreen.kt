package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Devices
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.Refresh
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
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_action_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_actions_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_admin_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_admin_user_usage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_deleted_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_keep_mine
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_ours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_row
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_take_theirs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflict_theirs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_conflicts_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_last_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_rename_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_rename_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_rename_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_revoke_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_revoke_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_revoke_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_revoke_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_revoked
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_devices_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_error_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_export_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_export_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_force_repull_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_force_repull_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_entry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_keys_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_keys_loading
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_retention
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_rejects
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_history_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_import_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_import_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_import_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_import_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_in_flight
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_last_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_toggle_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_usage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_pull
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_push
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_unknown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_pending
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_profile_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_profile_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_quota_bytes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_quota_keys
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_relative_days
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_relative_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_relative_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_relative_now
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_reset_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_reset_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_reset_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshot_entry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshot_restore_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshot_restore_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshot_restore_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshots_create_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshots_create_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshots_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_snapshots_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_this_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_usage_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_usage_unavailable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_across_devices
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_never
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_now
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_enabled_subtitle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The JellyPlay companion-plugin's settings-sync screen (ADR 0010) — the sync
 * engine's whole UI under one roof, top to bottom: the opt-in toggle + sync-now
 * (the old settings-root rows' mechanism, untouched), the error banner (the
 * engine's `lastError`/`rejectedKeys` + the snapshot/export/import action
 * failures), the server-side usage (quota bars + the per-namespace
 * selective-sync toggles with their pending counts), the conflict list (the
 * last cycle's `stale-write` rejects with ours/theirs previews), the merged
 * device list (rename this device, revoke others), this device's resolved
 * profile, the sync history ledger with its retention footer, the server's
 * restore points, and the namespace-reset / force-re-pull / export-import
 * actions.
 *
 * TV (`LocalTvMode`) collapses the screen to its d-pad essentials: the status
 * + sync-now, the error banner, and the namespace toggles only.
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated "Sync" entry (plugin probe AVAILABLE + the
 * `settings-sync` meta feature key); the ViewModel still re-checks the gate
 * before each api call. A server whose plugin predates the sync-status wave
 * 404s the usage/history/snapshot reads — those sections degrade to the quiet
 * "unavailable" line, never an error.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlaySyncScreen(
    onBack: () -> Unit,
    viewModel: JellyPlaySyncViewModel = koinViewModel(),
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val platformIntents = rememberPlatformIntents()

    // Freshness on open: one pull per visit (the messages screen's idiom);
    // manual actions re-pull through the ViewModel.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val resetConfirm = rememberConfirmState()
    val revokeConfirm = rememberConfirmState()
    val restoreConfirm = rememberConfirmState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var expandedConflictKey by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }
    val importPicker = rememberBackupFilePicker(
        onExportUriSelected = {},
        onImportUriSelected = { uri -> pendingImportUri = uri },
    )

    // The export share's pre-resolved texts (the share call fires from a
    // non-composable callback).
    val exportSubject = stringResource(Res.string.settings_jellyplay_sync_export_title)
    val exportChooser = stringResource(Res.string.settings_jellyplay_sync_export_subtitle)

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_jellyplay_sync),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── a. Status: the opt-in toggle + the manual sync cycle (the
            //      declared JellyPlaySyncGroup — ADR 0009 rule 2, one
            //      projection for the admission total) ──
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

            // The error banner: the engine's failure face (fatal rejects,
            // transport errors) beside the snapshot/export/import action
            // failures — one banner, never two.
            if (syncState.lastError != null || uiState.actionError) {
                item(key = "error_banner") {
                    ErrorBanner(text = syncState.lastError)
                }
            }

            // ── b. What's synced: quota bars + per-namespace rows (usage +
            //      pending + the selective-sync toggle per active namespace;
            //      TV keeps ONLY the toggle rows — the d-pad essentials). The
            //      section degrades on the pre-wave 404 — the quiet line,
            //      never an error. ──
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
                if (!isTv) {
                    item(key = "quota_card") { QuotaCard(status) }
                }
                val namespaceRows = if (isTv) uiState.namespaceRows.filter { it.toggleable } else uiState.namespaceRows
                items(namespaceRows, key = { "ns_${it.ns}" }) { namespace ->
                    if (namespace.toggleable) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Database,
                            title = namespace.ns,
                            subtitle = namespaceSubtitle(namespace),
                            checked = namespace.enabled,
                            onCheckedChange = { enabled -> viewModel.setNamespaceEnabled(namespace.ns, enabled) },
                        )
                    } else {
                        SettingInfoItem(
                            icon = Tabler.Outline.Database,
                            title = namespace.ns,
                            subtitle = namespaceSubtitle(namespace),
                        )
                    }
                }
                val adminUsers = uiState.adminUsers
                if (!isTv && !adminUsers.isNullOrEmpty()) {
                    item(key = "admin_label") { SectionLabel(
                        stringResource(Res.string.settings_jellyplay_sync_admin_section),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    ) }
                    items(adminUsers, key = { "admin_${it.userId}" }) { user ->
                        SettingInfoItem(
                            icon = Tabler.Outline.Users,
                            title = user.userName.ifBlank { user.userId },
                            subtitle = stringResource(
                                Res.string.settings_jellyplay_sync_admin_user_usage,
                                user.keys,
                                user.bytes.formatBytes(),
                                user.deviceCount,
                            ),
                        )
                    }
                }
            }

            if (!isTv) {
                // ── c. Conflicts: the last cycle's `stale-write` rejects with
                //      both sides previewed; a row expands into the ours /
                //      theirs pair + the two resolutions. ──
                if (syncState.conflicts.isNotEmpty()) {
                    item(key = "conflicts_label") { SectionLabel(
                        stringResource(Res.string.settings_jellyplay_sync_conflicts_title),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    ) }
                    items(syncState.conflicts, key = { "conflict_${it.ns}/${it.key}" }) { conflict ->
                        val key = "${conflict.ns}/${conflict.key}"
                        ConflictRow(
                            conflict = conflict,
                            expanded = expandedConflictKey == key,
                            onToggle = { expandedConflictKey = if (expandedConflictKey == key) null else key },
                            onKeepMine = { viewModel.resolveConflictKeepMine(conflict.ns, conflict.key) },
                            onTakeTheirs = { viewModel.resolveConflictTakeTheirs(conflict.ns, conflict.key) },
                        )
                    }
                }

                // ── d. Devices: the registry merged with the server's
                //      per-device ledger; this device first (rename), the
                //      others revocable (the v7 revoke + wipe). Revoked rows
                //      render destructive and inert. ──
                item(key = "devices_label") { SectionLabel(
                    stringResource(Res.string.settings_jellyplay_sync_devices_title),
                    Modifier.padding(top = 8.dp, start = 4.dp),
                ) }
                items(uiState.devices, key = { it.deviceId }) { device ->
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
                                device.isThisDevice -> {
                                    renameText = device.name
                                    showRenameDialog = true
                                }
                                !device.revoked -> revokeConfirm.request { viewModel.revokeDevice(device.deviceId) }
                            }
                        },
                    )
                }

                // ── e. Device profile: the resolved profile this device syncs under ──
                item(key = "profile_row") {
                    SettingInfoItem(
                        icon = Tabler.Outline.DeviceDesktop,
                        title = stringResource(Res.string.settings_jellyplay_sync_profile_title),
                        subtitle = stringResource(Res.string.settings_jellyplay_sync_profile_subtitle, viewModel.deviceProfile),
                    )
                }

                // ── f. History: the server's ledger, newest first. The
                //      section rides the status payload — a plugin old enough
                //      to 404 the status 404s the history too, so it hides
                //      with it. Tapping a row expands its per-key diff
                //      (fetched lazily per seq). ──
                if (status != null) {
                    item(key = "history_label") { SectionLabel(
                        stringResource(Res.string.settings_jellyplay_sync_history_title),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    ) }
                    if (uiState.history.isEmpty()) {
                        item(key = "history_empty") { QuietLine(stringResource(Res.string.settings_jellyplay_sync_history_empty)) }
                    } else {
                        items(uiState.history, key = { it.seq }) { entry ->
                            HistoryRow(
                                entry = entry,
                                expanded = uiState.expandedHistorySeq == entry.seq,
                                detail = uiState.historyKeyDetails[entry.seq],
                                onToggle = { viewModel.toggleHistoryEntryExpanded(entry.seq) },
                            )
                        }
                        item(key = "history_retention") {
                            QuietLine(
                                stringResource(Res.string.settings_jellyplay_sync_history_retention, status.historyRetentionDays),
                            )
                        }
                    }
                }

                // ── g. Restore points: the server's rolling snapshots —
                //      create one, restore one (confirmed). null (old plugin /
                //      failed read) hides the section quietly. ──
                val snapshots = uiState.snapshots
                if (snapshots != null) {
                    item(key = "snapshots_label") { SectionLabel(
                        stringResource(Res.string.settings_jellyplay_sync_snapshots_title),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    ) }
                    item(key = "snapshot_create") {
                        SettingListItem(
                            icon = Tabler.Outline.DeviceFloppy,
                            title = stringResource(Res.string.settings_jellyplay_sync_snapshots_create_title),
                            subtitle = stringResource(Res.string.settings_jellyplay_sync_snapshots_create_subtitle),
                            onClick = { if (!uiState.actionInFlight) viewModel.createSnapshot() },
                        )
                    }
                    if (snapshots.isEmpty()) {
                        item(key = "snapshots_empty") { QuietLine(stringResource(Res.string.settings_jellyplay_sync_snapshots_empty)) }
                    } else {
                        items(snapshots, key = { "snapshot_${it.id}" }) { snapshot ->
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = stringResource(
                                    Res.string.settings_jellyplay_sync_snapshot_entry,
                                    snapshot.origin.ifBlank { stringResource(Res.string.settings_jellyplay_sync_op_unknown) },
                                    snapshot.keys,
                                    snapshot.bytes.formatBytes(),
                                ),
                                subtitle = formatSyncTimestamp(snapshot.createdAt),
                                onClick = { restoreConfirm.request { viewModel.restoreSnapshot(snapshot.id) } },
                            )
                        }
                    }
                }

                // ── h. Actions: the destructive namespace reset (confirmed),
                //      the pull-dominant recovery cycle, and the JSON
                //      export/import pair (import rides the platform file
                //      picker + a confirm; hidden when the platform offers no
                //      picker). Screen-local rows with no declaration (the
                //      recovery actions live only here — the documented
                //      non-derivable total, ADR 0009 rule 2). ──
                item(key = "actions_label") { SectionLabel(
                    stringResource(Res.string.settings_jellyplay_sync_actions_title),
                    Modifier.padding(top = 8.dp, start = 4.dp),
                ) }
                item(key = "actions_group") {
                    // reset + force-re-pull + export (+ import when the
                    // platform offers a picker).
                    SettingsItemList(total = if (importPicker != null) 4 else 3) {
                        SettingListItem(
                            icon = Tabler.Outline.Trash,
                            title = stringResource(Res.string.settings_jellyplay_sync_reset_title),
                            subtitle = stringResource(Res.string.settings_jellyplay_sync_reset_subtitle),
                            isDestructive = true,
                            onClick = {
                                resetConfirm.request { viewModel.resetNamespace() }
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.History,
                            title = stringResource(Res.string.settings_jellyplay_sync_force_repull_title),
                            subtitle = stringResource(Res.string.settings_jellyplay_sync_force_repull_subtitle),
                            onClick = { viewModel.forceRepull() },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Share,
                            title = stringResource(Res.string.settings_jellyplay_sync_export_title),
                            subtitle = stringResource(Res.string.settings_jellyplay_sync_export_subtitle),
                            onClick = {
                                if (!uiState.actionInFlight) {
                                    viewModel.exportSettings { json ->
                                        platformIntents.shareJson(exportSubject, exportChooser, json)
                                    }
                                }
                            },
                        )
                        if (importPicker != null) {
                            SettingListItem(
                                icon = Tabler.Outline.Download,
                                title = stringResource(Res.string.settings_jellyplay_sync_import_title),
                                subtitle = stringResource(Res.string.settings_jellyplay_sync_import_subtitle),
                                onClick = {
                                    if (!uiState.actionInFlight) importPicker.launchOpenImport()
                                },
                            )
                        }
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

    restoreConfirm.ConfirmDialog(
        title = stringResource(Res.string.settings_jellyplay_sync_snapshot_restore_title),
        message = stringResource(Res.string.settings_jellyplay_sync_snapshot_restore_message),
        confirmText = stringResource(Res.string.settings_jellyplay_sync_snapshot_restore_confirm),
        dismissText = stringResource(Res.string.settings_cancel),
        tone = ConfirmTone.WARNING,
        icon = Tabler.Outline.Clock,
    )

    // The picked import file's confirm: the bundle applies server-now-stamped,
    // so the user should know what "import" overwrites before it does.
    pendingImportUri?.let { uri ->
        ConfirmDialog(
            title = stringResource(Res.string.settings_jellyplay_sync_import_confirm_title),
            message = stringResource(Res.string.settings_jellyplay_sync_import_confirm_message),
            confirmText = stringResource(Res.string.settings_jellyplay_sync_import_title),
            dismissText = stringResource(Res.string.settings_cancel),
            tone = ConfirmTone.WARNING,
            icon = Tabler.Outline.Download,
            confirmLoading = uiState.actionInFlight,
            onConfirm = {
                pendingImportUri = null
                viewModel.importFromUri(uri)
            },
            onDismiss = { pendingImportUri = null },
        )
    }

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

/** One namespace row's factual line: usage (when the server reported it), pending, or the sync-off note. */
@Composable
private fun namespaceSubtitle(namespace: JellyPlaySyncNamespaceRow): String {
    val usage = stringResource(
        Res.string.settings_jellyplay_sync_ns_toggle_subtitle,
        namespace.ns,
        namespace.keys,
        namespace.bytes.formatBytes(),
    )
    return buildList {
        if (namespace.toggleable && !namespace.enabled) {
            add(stringResource(Res.string.settings_jellyplay_sync_ns_off))
        }
        if (namespace.pending > 0) {
            add(stringResource(Res.string.settings_jellyplay_sync_pending, namespace.pending))
        }
        if (namespace.enabled) add(usage)
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
        JellyPlayLinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One history row: the op + timestamp/count face, tappable to expand the
 * entry's per-key diff below (the VM fetches it lazily on first expand and
 * caches per seq). The expanded block reads:
 *  - the changed keys as `ns/key` lines with a relative `updatedAt`,
 *  - "No key changes" when the server holds no diff (reset rows),
 *  - a quiet "—" when the detail never landed (the pre-wave 404, a failed
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

/** The wire ops' declared faces: icon + label resource keyed by the op string (ADR-0009's rows-own-faces, per wire op). */
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
