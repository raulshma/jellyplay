package com.raulshma.jellyplay.feature.admin.backups

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_component_database
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_component_metadata
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_component_subtitles
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_component_trickplay
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_create
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_create_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_empty
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_reconnecting
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restore
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restore_complete
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restore_confirm_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restore_confirm_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restore_server_unreachable
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_restoring
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_unsupported_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_backups_unsupported_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown_error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBackupsScreen(
    onBack: () -> Unit,
    viewModel: AdminBackupsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backgroundColorState = rememberScreenBackgroundColorState()

    var showCreateDialog by remember { mutableStateOf(false) }
    // The archive awaiting its restore confirmation; held in state so it
    // survives recomposition while the dialog is up (the dashboard's
    // pending-stop-session pattern).
    var pendingRestoreFile by remember { mutableStateOf<String?>(null) }

    if (showCreateDialog) {
        CreateBackupDialog(
            isCreating = state.isCreating,
            onConfirm = { options ->
                showCreateDialog = false
                viewModel.createBackup(options)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    pendingRestoreFile?.let { fileName ->
        ConfirmDialog(
            title = stringResource(Res.string.admin_backups_restore_confirm_title),
            message = stringResource(Res.string.admin_backups_restore_confirm_body, fileName),
            confirmText = stringResource(Res.string.admin_backups_restore),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                pendingRestoreFile = null
                viewModel.restoreBackup(fileName)
            },
            onDismiss = { pendingRestoreFile = null },
        )
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.admin_backups_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            if (state.isSupported) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { viewModel.refresh() }),
                ) {
                    Icon(
                        Tabler.Outline.Refresh,
                        contentDescription = stringResource(Res.string.admin_refresh),
                        modifier = Modifier.padding(12.dp).size(20.dp),
                    )
                }
            }
        },
    ) {
        when {
            // The restore ladder owns the whole screen: from the 204 until the
            // session is re-established there is nothing to interact with.
            state.restorePhase != RestorePhase.Idle -> RestoringState(
                phase = state.restorePhase,
                modifier = Modifier.fillMaxSize(),
            )

            state.isLoading -> ScreenLoadingState(modifier = Modifier.fillMaxSize())

            !state.isSupported -> ScreenEmptyState(
                icon = Tabler.Outline.Database,
                title = stringResource(Res.string.admin_backups_unsupported_title),
                description = stringResource(Res.string.admin_backups_unsupported_body),
                modifier = Modifier.fillMaxSize(),
            )

            state.error != null || state.restoreServerUnreachable -> ErrorScreen(
                message = state.error
                    ?: stringResource(Res.string.admin_backups_restore_server_unreachable),
                onRetry = { viewModel.loadBackups() },
                modifier = Modifier.fillMaxSize(),
            )

            else -> BackupsContent(
                state = state,
                onCreate = { showCreateDialog = true },
                onRestore = { pendingRestoreFile = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BackupsContent(
    state: AdminBackupsState,
    onCreate: () -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "create") {
            Column {
                if (state.restoreComplete) {
                    Text(
                        stringResource(Res.string.admin_backups_restore_complete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.admin_backups_create))
                }
            }
        }
        if (state.backups.isEmpty()) {
            item(key = "empty") {
                ScreenEmptyState(
                    icon = Tabler.Outline.Archive,
                    title = stringResource(Res.string.admin_backups_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(state.backups, key = { it.path }) { backup ->
                BackupRow(
                    backup = backup,
                    onRestore = { onRestore(backup.fileName()) },
                )
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: ServerBackup,
    onRestore: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                backup.fileName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // dateCreated is the server's raw ISO-8601 string; the slice
                // is the display form every list renderer here can afford.
                backup.dateCreated.take(19).replace('T', ' ') +
                    (if (backup.serverVersion.isNotBlank()) "  ·  v" + backup.serverVersion else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                backup.componentsLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(Res.string.admin_backups_restore))
                }
            }
        }
    }
}

/** The four component toggles of the create dialog. */
@Composable
private fun CreateBackupDialog(
    isCreating: Boolean,
    onConfirm: (BackupComponentOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var metadata by remember { mutableStateOf(true) }
    var trickplay by remember { mutableStateOf(true) }
    var subtitles by remember { mutableStateOf(true) }
    var database by remember { mutableStateOf(true) }

    ConfirmDialog(
        title = stringResource(Res.string.admin_backups_create_title),
        confirmText = stringResource(Res.string.admin_backups_create),
        dismissText = stringResource(Res.string.admin_cancel),
        tone = ConfirmTone.NEUTRAL,
        confirmEnabled = !isCreating,
        confirmLoading = isCreating,
        onConfirm = {
            onConfirm(
                BackupComponentOptions(
                    metadata = metadata,
                    trickplay = trickplay,
                    subtitles = subtitles,
                    database = database,
                ),
            )
        },
        onDismiss = onDismiss,
        content = {
            ComponentToggle(
                label = stringResource(Res.string.admin_backups_component_metadata),
                checked = metadata,
                onCheckedChange = { metadata = it },
            )
            ComponentToggle(
                label = stringResource(Res.string.admin_backups_component_trickplay),
                checked = trickplay,
                onCheckedChange = { trickplay = it },
            )
            ComponentToggle(
                label = stringResource(Res.string.admin_backups_component_subtitles),
                checked = subtitles,
                onCheckedChange = { subtitles = it },
            )
            ComponentToggle(
                label = stringResource(Res.string.admin_backups_component_database),
                checked = database,
                onCheckedChange = { database = it },
            )
        },
    )
}

@Composable
private fun ComponentToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestoringState(
    phase: RestorePhase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                if (phase == RestorePhase.Restoring) {
                    Res.string.admin_backups_restoring
                } else {
                    Res.string.admin_backups_reconnecting
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The archive's display name: the path's last segment ("" path → the raw value). */
private fun ServerBackup.fileName(): String = path.substringAfterLast('/').ifBlank { path }

/** The enabled components, dot-separated; "—" when the manifest names none. */
@Composable
private fun ServerBackup.componentsLabel(): String {
    val labels = buildList {
        if (options.metadata) add(stringResource(Res.string.admin_backups_component_metadata))
        if (options.trickplay) add(stringResource(Res.string.admin_backups_component_trickplay))
        if (options.subtitles) add(stringResource(Res.string.admin_backups_component_subtitles))
        if (options.database) add(stringResource(Res.string.admin_backups_component_database))
    }
    return labels.joinToString(" · ").ifBlank { "—" }
}
