package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.Devices
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Upload
import com.composables.icons.tabler.outline.Users
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_actions_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_admin_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_admin_user_usage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_device_last_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_devices_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_in_flight
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_last_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_usage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_pull
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_push
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_unknown
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_this_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_time_yesterday
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
 * (the old settings-root rows' mechanism, untouched), the server-side usage
 * (namespaces + quotas), the merged device list, this device's resolved
 * profile, the sync history ledger with its retention footer, and the
 * namespace-reset / force-re-pull recovery actions.
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated "Sync" entry (plugin probe AVAILABLE + the
 * `settings-sync` meta feature key); the ViewModel still re-checks the gate
 * before each api call. A server whose plugin predates the sync-status wave
 * 404s the usage/history reads — those sections degrade to the quiet
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

    // Freshness on open: one pull per visit (the messages screen's idiom);
    // manual actions re-pull through the ViewModel.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val resetConfirm = rememberConfirmState()

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

            // ── b. What's synced: namespaces + quotas (degrades on the
            //      pre-wave 404 — the quiet line, never an error) ──
            item { SectionLabel(
                    stringResource(Res.string.settings_jellyplay_sync_usage_title),
                    Modifier.padding(top = 8.dp, start = 4.dp),
                ) }
            val status = uiState.status
            if (status == null) {
                item {
                    QuietLine(stringResource(Res.string.settings_jellyplay_sync_usage_unavailable))
                }
            } else {
                item { QuotaCard(status) }
                items(status.namespaces, key = { "ns_${it.ns}" }) { namespace ->
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = namespace.ns,
                        subtitle = stringResource(
                            Res.string.settings_jellyplay_sync_ns_usage,
                            namespace.keys,
                            namespace.bytes.formatBytes(),
                        ),
                    )
                }
                val adminUsers = uiState.adminUsers
                if (!adminUsers.isNullOrEmpty()) {
                    item { SectionLabel(
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

            // ── c. Devices: the registry merged with the server's per-device
            //      ledger; this device first ──
            item { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_devices_title),
                Modifier.padding(top = 8.dp, start = 4.dp),
            ) }
            items(uiState.devices, key = { it.deviceId }) { device ->
                val lastSync = device.lastSyncAt
                    ?.let { stringResource(Res.string.settings_jellyplay_sync_device_last_sync, formatSyncTimestamp(it)) }
                    ?: stringResource(Res.string.settings_sync_never)
                SettingListItem(
                    icon = Tabler.Outline.Devices,
                    title = device.name,
                    subtitle = buildList {
                        if (device.platform.isNotBlank()) add(device.platform)
                        add(lastSync)
                        if (device.isThisDevice) add(stringResource(Res.string.settings_jellyplay_sync_this_device))
                    }.joinToString(" • "),
                    onClick = {},
                )
            }

            // ── d. Device profile: the resolved profile this device syncs under ──
            item {
                SettingInfoItem(
                    icon = Tabler.Outline.DeviceDesktop,
                    title = stringResource(Res.string.settings_jellyplay_sync_profile_title),
                    subtitle = stringResource(Res.string.settings_jellyplay_sync_profile_subtitle, viewModel.deviceProfile),
                )
            }

            // ── e. History: the server's ledger, newest first. The section
            //      rides the status payload — a plugin old enough to 404 the
            //      status 404s the history too, so it hides with it. Tapping a
            //      row expands its per-key diff (fetched lazily per seq). ──
            if (status != null) {
                item { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_history_title),
                Modifier.padding(top = 8.dp, start = 4.dp),
            ) }
                if (uiState.history.isEmpty()) {
                    item { QuietLine(stringResource(Res.string.settings_jellyplay_sync_history_empty)) }
                } else {
                    items(uiState.history, key = { it.seq }) { entry ->
                        HistoryRow(
                            entry = entry,
                            expanded = uiState.expandedHistorySeq == entry.seq,
                            detail = uiState.historyKeyDetails[entry.seq],
                            onToggle = { viewModel.toggleHistoryEntryExpanded(entry.seq) },
                        )
                    }
                    item {
                        QuietLine(
                            stringResource(Res.string.settings_jellyplay_sync_history_retention, status.historyRetentionDays),
                        )
                    }
                }
            }

            // ── f. Actions: the destructive namespace reset (confirmed) and
            //      the pull-dominant recovery cycle. Screen-local rows with no
            //      declaration (the recovery actions live only here — the
            //      documented non-derivable total, ADR 0009 rule 2) ──
            item { SectionLabel(
                stringResource(Res.string.settings_jellyplay_sync_actions_title),
                Modifier.padding(top = 8.dp, start = 4.dp),
            ) }
            item {
                // 2 = the reset + force-re-pull rows, both undeclared above.
                SettingsItemList(total = 2) {
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
}

/** The sync-now row's factual line: the in-flight flag, then the error, then this device's last sync + op. */
@Composable
private fun syncNowSubtitle(
    syncState: com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository.SyncState,
    thisDeviceId: String?,
    status: JellyPlaySyncStatus?,
): String {
    val lastSyncAt = status?.perDevice
        ?.firstOrNull { it.deviceId == thisDeviceId && it.lastSyncAt > 0 }
        ?.lastSyncAt
        ?: syncState.lastSyncAt
    return when {
        syncState.inFlight -> stringResource(Res.string.settings_jellyplay_sync_in_flight)
        syncState.lastError != null -> stringResource(Res.string.settings_jellyplay_sync_failed)
        lastSyncAt != null -> stringResource(
            Res.string.settings_jellyplay_sync_last_sync,
            formatSyncTimestamp(lastSyncAt),
        )
        else -> stringResource(Res.string.settings_sync_never)
    }
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
 * The sync timestamps' shape: "HH:mm" today, "Yesterday, HH:mm" the day
 * before, "MMM d, HH:mm" older (the old settings-root row's locale-formatted
 * HH:mm precedent, lifted to a date ladder); fallback keeps the raw millis
 * honest. java.time rides the module's commonMain precedent (SettingsViewModel).
 */
@Composable
private fun formatSyncTimestamp(epochMillis: Long): String {
    val parts = runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val dateTime = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        Triple(dateTime.toLocalDate(), java.time.LocalDate.now(zone), dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
    }.getOrNull() ?: return epochMillis.toString()
    val (date, today, time) = parts
    return when (date) {
        today -> time
        today.minusDays(1) -> stringResource(Res.string.settings_jellyplay_sync_time_yesterday, time)
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + ", $time"
    }
}

/**
 * The diff keys' relative stamp: "just now" / "5m ago" / "3h ago" / "2d ago"
 * (the core:ui relative-time ladder, epoch-millis-based — the wire stamps are
 * epochs, not ISO; [wallNowMillis] is the portable now, never a raw
 * System.currentTimeMillis in commonMain). A server clock ahead of this
 * device reads as "just now".
 */
@Composable
private fun relativeSyncTime(epochMillis: Long): String {
    val deltaMinutes = (wallNowMillis() - epochMillis) / 60_000
    return when {
        deltaMinutes < 1 -> stringResource(Res.string.settings_jellyplay_sync_relative_now)
        deltaMinutes < 60 -> stringResource(Res.string.settings_jellyplay_sync_relative_minutes, deltaMinutes)
        deltaMinutes < 24 * 60 -> stringResource(Res.string.settings_jellyplay_sync_relative_hours, deltaMinutes / 60)
        else -> stringResource(Res.string.settings_jellyplay_sync_relative_days, deltaMinutes / (24 * 60))
    }
}
