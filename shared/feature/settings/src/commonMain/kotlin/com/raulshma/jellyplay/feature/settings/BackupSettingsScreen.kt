package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_settings

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onFactoryReset: () -> Unit,
    /** The import row's reroute: the picked file opens the unified restore wizard (Wave 5). */
    onRestoreWizard: (String) -> Unit = {},
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val bus = LocalUserMessageBus.current

    /**
     * Wave-3 "Export with secrets" staging: clicking the secrets row arms the
     * flag, the picker delivers the export uri, and the passphrase dialog
     * opens for that uri; confirming encrypts + writes through
     * [SettingsViewModel.exportSettings], dismissing simply drops it. The
     * plain export row keeps its old write-immediately path.
     */
    var secretsExportArmed by remember { mutableStateOf(false) }
    var stagedSecretsExportUri by remember { mutableStateOf<String?>(null) }

    // SAF/native pickers behind the platform seam: Android returns the SAF
    // launcher facade, desktop an AWT FileDialog facade — both
    // deliver opaque uri strings straight into the ViewModel below.
    val backupPicker = rememberBackupFilePicker(
        onExportUriSelected = { uri ->
            if (secretsExportArmed) {
                secretsExportArmed = false
                stagedSecretsExportUri = uri
            } else {
                viewModel.exportSettings(uri)
            }
        },
        // The import row stages NOTHING here anymore: the picked file rides
        // straight into the restore wizard, which owns the read/diff/confirm.
        onImportUriSelected = { uri -> onRestoreWizard(uri) },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_backup_restore),
        onBack = onBack,
        focusTag = "backup_init",
    ) { _ ->
            item {
                SettingsGroup(
                    icon = Tabler.Outline.DatabaseExport,
                    title = stringResource(Res.string.settings_backup_restore),
                    summary = { stringResource(Res.string.settings_backup_restore_subtitle) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.FileExport,
                        title = rowTitle(BackupRows.BackupExport),
                        subtitle = rowSubtitle(BackupRows.BackupExport),
                        index = 0, count = 4,
                        highlighted = highlightSettingId == BackupRows.BackupExport.id,
                        onClick = {
                            // Disarm unconditionally: a cancelled secrets pick
                            // must not hijack the next plain export.
                            secretsExportArmed = false
                            backupPicker?.launchCreateExport("jellyplay-settings.json")
                        },
                    )
                    SettingListItem(
                        icon = BackupRows.BackupExportSecrets.icon,
                        title = rowTitle(BackupRows.BackupExportSecrets),
                        subtitle = rowSubtitle(BackupRows.BackupExportSecrets),
                        index = 1, count = 4,
                        highlighted = highlightSettingId == BackupRows.BackupExportSecrets.id,
                        onClick = {
                            secretsExportArmed = true
                            backupPicker?.launchCreateExport("jellyplay-settings.json")
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.FileImport,
                        title = rowTitle(BackupRows.BackupImport),
                        subtitle = rowSubtitle(BackupRows.BackupImport),
                        index = 2, count = 4,
                        highlighted = highlightSettingId == BackupRows.BackupImport.id,
                        onClick = {
                            backupPicker?.launchOpenImport()
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = rowTitle(BackupRows.FactoryReset),
                        subtitle = rowSubtitle(BackupRows.FactoryReset),
                        index = 3, count = 4,
                        isDestructive = true,
                        highlighted = highlightSettingId == BackupRows.FactoryReset.id,
                        onClick = onFactoryReset,
                    )
                }

                LaunchedEffect(viewModel.backupRestoreStatus) {
                    viewModel.backupRestoreStatus?.let { msg ->
                        bus.info(msg)
                        viewModel.clearBackupRestoreStatus()
                    }
                }
            }
    }

    stagedSecretsExportUri?.let { uri ->
        ExportPassphraseDialog(
            onDismiss = { stagedSecretsExportUri = null },
            onConfirm = { passphrase ->
                stagedSecretsExportUri = null
                viewModel.exportSettings(uri, includeSecrets = true, passphrase = passphrase)
            },
        )
    }
}
