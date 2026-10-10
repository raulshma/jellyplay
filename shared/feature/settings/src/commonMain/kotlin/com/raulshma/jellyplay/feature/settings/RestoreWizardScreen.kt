package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.FileImport
import com.composables.icons.tabler.outline.Lock
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.settings.components.IntegrationsNote
import com.raulshma.jellyplay.feature.settings.components.PreferenceDiffSummaryCard
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_app_state
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_changed_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_up_to_date
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_error
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_security_option
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_summary_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_version_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_apply
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_counts_arr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_counts_seerr_no
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_counts_seerr_yes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_counts_servers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_counts_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_unlock_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_wrong_passphrase
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unknown
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_confirm_cross_account
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_confirm_full_message
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_confirm_message
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_confirm_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_diff_nothing_to_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_diff_parent_preferences
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_diff_parent_slices
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_diff_restore_selected
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_full_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_group_counts
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_load_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_loading
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_preview_unavailable_message
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_preview_unavailable_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_row_kind_added
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_row_kind_changed
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_row_kind_removed
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_snapshot_entry_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_snapshot_preview_short
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_file_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_file_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_server_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_server_unavailable_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_source_server_unavailable_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_title
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_value_absent
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_value_removed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The unified restore wizard (Wave 5): ONE screen, two internal steps. SOURCE
 * picks the payload — the server's restore points (gated by the sync probe,
 * an explanatory state when sync is off/unavailable) or a backup file (the
 * platform picker seam). DIFF renders the source's diff as two-tier selectable
 * cards — Preferences (parent) → per-domain category cards for the file arm;
 * whole namespaces → prefs domains (with the counted "Other" bucket) for the
 * snapshot arm — behind a destructive confirm carrying the cross-account
 * warning line, then the pre-apply safety capture (Wave 6 hook #1) and the
 * apply run inside the ViewModel; success posts the summary to the bus and
 * pops the wizard.
 *
 * The old-plugin degrade face (`getSnapshotContent` 404) renders the
 * "preview unavailable" card and offers the full server-orchestrated restore
 * instead. The Wave-3 secrets card rides at the bottom of the file diff
 * exactly as the retired import preview staged it: locked → unlock → counts +
 * its own explicit apply confirm.
 */
@Composable
fun RestoreWizardScreen(
    onBack: () -> Unit,
    uri: String? = null,
    snapshotId: String? = null,
    viewModel: RestoreWizardViewModel = koinViewModel(),
) {
    val uiState = viewModel.uiState
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val importPicker = rememberBackupFilePicker(
        onExportUriSelected = {},
        onImportUriSelected = { picked -> viewModel.loadBackupFile(picked) },
    )

    LaunchedEffect(Unit) { viewModel.start(uri, snapshotId) }

    // Success: the VM already posted the summary to the bus — pop the wizard.
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) {
            viewModel.consumeCompleted()
            onBack()
        }
    }

    // Wave-3 unlock: drop the passphrase dialog once the counts land (a wrong
    // passphrase keeps it up for the inline retry).
    var showUnlockDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.file?.secretsUnlocked) {
        if (uiState.file?.secretsUnlocked != null) showUnlockDialog = false
    }
    var showSecretsApplyConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val callbacks = WizardCallbacks(
        onRestoreConfirm = { showRestoreConfirm = true },
        onUnlock = { showUnlockDialog = true },
        onSecretsApply = { showSecretsApplyConfirm = true },
    )

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "restore_wizard_init")

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.wizard_title),
        // Back is dead while the confirmed apply runs: an apply abandoned
        // mid-batch leaves the source half-restored. The dialog already
        // refuses dismissal; the scaffold's back affordance must follow.
        onBack = { if (!uiState.applying) onBack() },
        backgroundColorState = backgroundColorState,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (uiState.step) {
                WizardStep.SOURCE -> sourceStep(
                    uiState,
                    importPicker != null,
                    viewModel,
                    onPickFile = { importPicker?.launchOpenImport() },
                )

                WizardStep.DIFF -> when {
                    uiState.file != null -> fileDiffStep(viewModel, callbacks)
                    uiState.snapshot != null -> snapshotDiffStep(viewModel, callbacks)
                    uiState.fullRestore != null -> fullRestoreStep(callbacks)
                }
            }

            if (uiState.step == WizardStep.DIFF && uiState.applyError != null) {
                item(key = "apply_error") {
                    WizardWarningCard(
                        icon = Tabler.Outline.AlertTriangle,
                        title = uiState.applyError.orEmpty(),
                        message = null,
                    )
                }
            }
        }
    }

    // ── The destructive confirm: tone + message + the cross-account line ──
    if (showRestoreConfirm) {
        val isFullRestore = uiState.fullRestore != null
        ConfirmDialog(
            title = stringResource(Res.string.wizard_confirm_title),
            message = stringResource(
                if (isFullRestore) Res.string.wizard_confirm_full_message
                else Res.string.wizard_confirm_message,
                viewModel.selectedGroupCount(),
            ),
            confirmText = stringResource(
                if (isFullRestore) Res.string.wizard_full_restore
                else Res.string.wizard_diff_restore_selected,
            ),
            dismissText = stringResource(Res.string.settings_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            icon = Tabler.Outline.AlertTriangle,
            confirmLoading = uiState.applying,
            // The dialog stays up (loading) until the apply settles: `completed`
            // pops the wizard; a failure drops `applying` so the dialog becomes
            // dismissible and the inline apply-error card takes over.
            onConfirm = { viewModel.restoreSelected() },
            onDismiss = { if (!uiState.applying) showRestoreConfirm = false },
            content = if (uiState.file?.crossAccount == true) {
                {
                    Text(
                        text = stringResource(Res.string.wizard_confirm_cross_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                null
            },
        )
    }

    // ── Wave-3 secrets: the passphrase unlock + the explicit apply confirm ──
    if (showUnlockDialog) {
        UnlockPassphraseDialog(
            isBusy = uiState.file?.secretsUnlocking == true,
            errorText = if (uiState.file?.secretsError == true) {
                stringResource(Res.string.settings_import_secrets_wrong_passphrase)
            } else {
                null
            },
            onDismiss = {
                showUnlockDialog = false
                viewModel.clearSecretsError()
            },
            onConfirm = { passphrase -> viewModel.unlockSecrets(passphrase) },
        )
    }
    if (showSecretsApplyConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_import_secrets_confirm_title),
            message = stringResource(Res.string.settings_import_secrets_confirm_message),
            confirmText = stringResource(Res.string.settings_import_secrets_apply),
            tone = ConfirmTone.PRIMARY,
            icon = Tabler.Outline.Lock,
            onConfirm = {
                showSecretsApplyConfirm = false
                viewModel.applySecrets()
            },
            onDismiss = { showSecretsApplyConfirm = false },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }
}

/** The screen-scope dialog triggers the LazyListScope step builders reach. */
private class WizardCallbacks(
    val onRestoreConfirm: () -> Unit,
    val onUnlock: () -> Unit,
    val onSecretsApply: () -> Unit,
)

// ---------------------------------------------------------------------------
// SOURCE step
// ---------------------------------------------------------------------------

private fun LazyListScope.sourceStep(
    uiState: RestoreWizardUiState,
    hasPicker: Boolean,
    viewModel: RestoreWizardViewModel,
    onPickFile: () -> Unit,
) {
    if (uiState.serverAvailable) {
        item(key = "server_title") {
            WizardSectionLabel(stringResource(Res.string.wizard_source_server_title))
        }
        when {
            uiState.snapshotsUnavailable -> item(key = "server_unavailable") {
                WizardWarningCard(
                    icon = Tabler.Outline.CloudDownload,
                    title = stringResource(Res.string.wizard_source_server_unavailable_title),
                    message = stringResource(Res.string.wizard_source_server_unavailable_subtitle),
                )
            }

            uiState.snapshotsLoading -> item(key = "server_loading") { WizardLoadingLine() }

            uiState.snapshots.isEmpty() -> item(key = "server_empty") {
                WizardWarningCard(
                    icon = Tabler.Outline.CloudDownload,
                    title = stringResource(Res.string.wizard_source_server_unavailable_title),
                    message = stringResource(Res.string.wizard_source_server_unavailable_subtitle),
                )
            }

            else -> items(uiState.snapshots, key = { "snapshot_${it.id}" }) { snapshot ->
                SnapshotSourceRow(snapshot = snapshot, onClick = { viewModel.openSnapshot(snapshot.id.toString()) })
            }
        }
    }

    item(key = "file_title") {
        WizardSectionLabel(stringResource(Res.string.wizard_source_file_title))
    }
    item(key = "file_row") {
        if (hasPicker) {
            WizardSourceRow(
                icon = Tabler.Outline.FileImport,
                title = stringResource(Res.string.wizard_source_file_title),
                subtitle = stringResource(Res.string.wizard_source_file_subtitle),
                trailingText = null,
                onClick = onPickFile,
            )
        } else {
            WizardWarningCard(
                icon = Tabler.Outline.FileImport,
                title = stringResource(Res.string.wizard_source_file_title),
                message = stringResource(Res.string.wizard_source_file_subtitle),
            )
        }
    }
    if (uiState.loadError) {
        item(key = "load_error") {
            WizardWarningCard(
                icon = Tabler.Outline.AlertTriangle,
                title = stringResource(
                    if (viewModel.lastLoadErrorIsServer) Res.string.wizard_load_failed
                    else Res.string.settings_import_preview_error,
                ),
                message = viewModel.lastLoadErrorMessage,
            )
        }
    }
}

/** One restore-point entry: origin + timestamp face, "Preview & restore" affordance. */
@Composable
private fun SnapshotSourceRow(snapshot: JellyPlaySnapshot, onClick: () -> Unit) {
    WizardSourceRow(
        icon = Tabler.Outline.Clock,
        title = snapshot.origin.ifBlank { stringResource(Res.string.settings_unknown) },
        subtitle = snapshotSubtitle(snapshot),
        trailingText = stringResource(Res.string.wizard_snapshot_preview_short),
        onClick = onClick,
    )
}

@Composable
private fun snapshotSubtitle(snapshot: JellyPlaySnapshot): String = buildString {
    if (snapshot.createdAt > 0) append(formatWizardTimestamp(snapshot.createdAt))
    if (snapshot.keys > 0) {
        if (isNotEmpty()) append(" • ")
        append(stringResource(Res.string.wizard_snapshot_entry_subtitle, snapshot.keys))
    }
}

/**
 * The entry timestamp's compact face ("MMM d, HH:mm") — informational only;
 * a clock failure falls back to the raw millis (the sync screen's idiom).
 */
@Composable
private fun formatWizardTimestamp(epochMillis: Long): String =
    runCatching {
        val dateTime = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm").format(dateTime)
    }.getOrDefault(epochMillis.toString())

// ---------------------------------------------------------------------------
// DIFF step — BACKUP-FILE flavor (the retired import preview's faces, ported)
// ---------------------------------------------------------------------------

private fun LazyListScope.fileDiffStep(viewModel: RestoreWizardViewModel, callbacks: WizardCallbacks) {
    val file = viewModel.uiState.file ?: return
    val selection = file.selection

    val totalChanged = file.categories.sumOf { it.changed.size } +
        (file.extras?.changed?.size ?: 0) +
        file.externalSlices.sumOf { it.changed.size }
    val totalFields = file.categories.sumOf { it.total } +
        (file.extras?.total ?: 0) +
        file.externalSlices.sumOf { it.total }

    item(key = "summary") {
        PreferenceDiffSummaryCard(
            totalChanged = totalChanged,
            totalFields = totalFields,
            titleRes = Res.string.wizard_title,
            summaryRes = Res.string.settings_import_preview_summary_card,
            primaryLabelRes = Res.string.wizard_diff_restore_selected,
            onPrimary = callbacks.onRestoreConfirm,
            primaryEnabled = viewModel.selectedGroupCount() > 0,
            isErrorContainer = false,
        )
    }

    if (file.versionMismatch) {
        item(key = "version_warn") {
            WizardWarningCard(
                icon = Tabler.Outline.AlertTriangle,
                title = stringResource(Res.string.settings_import_preview_version_warning, file.schemaVersion),
                message = null,
            )
        }
    }

    if (file.hasSecuritySensitive) {
        item(key = "security_opt") {
            WizardCheckboxRow(
                title = stringResource(Res.string.settings_import_preview_security_option),
                checked = file.restoreSecuritySensitive,
                onToggle = { viewModel.toggleRestoreSecuritySensitive(!file.restoreSecuritySensitive) },
            )
        }
    }

    item(key = "integrations_note") { IntegrationsNote() }

    // ── The Preferences parent tier + the per-domain category children ──
    item(key = "prefs_parent") {
        WizardGroupCard(
            icon = Tabler.Outline.Database,
            title = stringResource(Res.string.wizard_diff_parent_preferences),
            subtitle = GroupSubtitle.FileStyle(
                changed = file.categories.sumOf { it.changed.size },
                total = file.categories.sumOf { it.total },
            ),
            checked = toggleState(
                allSelected = selection.allCategoriesSelected(file.categories.map { it.category }),
                noneSelected = selection.categories.isEmpty(),
            ),
            onToggle = { viewModel.toggleAllCategories() },
        )
    }
    file.categories.forEach { group ->
        item(key = "cat_${group.category.key}") {
            WizardGroupCard(
                icon = iconForCategory(group.category),
                title = group.title,
                subtitle = GroupSubtitle.FileStyle(group.changed.size, group.total),
                checked = group.category.toToggleableState(selection.categories),
                onToggle = { viewModel.toggleCategory(group.category) },
                indent = true,
                rows = group.changed.map { WizardDiffRow(it.label, it.currentValue, it.factoryValue) },
            )
        }
    }

    // ── Extras (App State) ──
    file.extras?.let { extras ->
        item(key = "extras") {
            WizardGroupCard(
                icon = Tabler.Outline.Database,
                title = stringResource(Res.string.factory_reset_cat_app_state),
                subtitle = GroupSubtitle.FileStyle(extras.changed.size, extras.total),
                checked = if (selection.extras) ToggleableState.On else ToggleableState.Off,
                onToggle = { viewModel.toggleExtras() },
                rows = extras.changed.map { WizardDiffRow(it.label, it.currentValue, it.factoryValue) },
            )
        }
    }

    // ── Wave-2 external slices: an "All" parent tier + the per-slice cards ──
    if (file.externalSlices.isNotEmpty()) {
        item(key = "slices_parent") {
            WizardGroupCard(
                icon = Tabler.Outline.Database,
                title = stringResource(Res.string.wizard_diff_parent_slices),
                subtitle = GroupSubtitle.FileStyle(
                    changed = file.externalSlices.sumOf { it.changed.size },
                    total = file.externalSlices.sumOf { it.total },
                ),
                checked = toggleState(
                    allSelected = selection.allSlicesSelected(file.externalSlices.map { it.key }),
                    noneSelected = selection.slices.isEmpty(),
                ),
                onToggle = { viewModel.toggleAllSlices() },
            )
        }
    }
    file.externalSlices.forEach { slice ->
        item(key = "slice_${slice.key}") {
            WizardGroupCard(
                icon = iconForSlice(slice.key),
                title = slice.title,
                subtitle = GroupSubtitle.FileStyle(slice.changed.size, slice.total),
                checked = slice.key.toToggleableState(selection.slices),
                onToggle = { viewModel.toggleSlice(slice.key) },
                indent = true,
                rows = slice.changed.map { WizardDiffRow(it.label, it.currentValue, it.factoryValue) },
            )
        }
    }

    // ── Wave-3 secrets card (always last) ──
    if (file.hasSecrets) {
        item(key = "secrets") {
            SecretsCard(
                summary = file.secretsUnlocked,
                unlocking = file.secretsUnlocking,
                onUnlock = callbacks.onUnlock,
                onApply = callbacks.onSecretsApply,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DIFF step — SNAPSHOT flavor
// ---------------------------------------------------------------------------

private fun LazyListScope.snapshotDiffStep(viewModel: RestoreWizardViewModel, callbacks: WizardCallbacks) {
    val state = viewModel.uiState.snapshot ?: return

    val totalRows = state.groups.namespaces.sumOf { it.rows.size } +
        state.groups.prefDomains.sumOf { it.rows.size }
    item(key = "summary") {
        PreferenceDiffSummaryCard(
            totalChanged = totalRows,
            totalFields = totalRows,
            titleRes = Res.string.wizard_title,
            summaryRes = Res.string.settings_import_preview_summary_card,
            primaryLabelRes = Res.string.wizard_diff_restore_selected,
            onPrimary = callbacks.onRestoreConfirm,
            primaryEnabled = viewModel.selectedGroupCount() > 0 && totalRows > 0,
            isErrorContainer = false,
        )
    }

    if (totalRows == 0) {
        item(key = "empty") {
            WizardWarningCard(
                icon = Tabler.Outline.Database,
                title = stringResource(Res.string.wizard_diff_nothing_to_restore),
                message = null,
            )
        }
        return
    }

    // Whole-namespace groups.
    state.groups.namespaces.forEach { group ->
        item(key = "ns_${group.id}") {
            WizardGroupCard(
                icon = Tabler.Outline.Database,
                title = group.title,
                subtitle = GroupSubtitle.Counts(group.changed, group.added, group.removed),
                checked = group.id.toToggleableState(state.selection.namespaces),
                onToggle = { viewModel.toggleNamespace(group.id) },
                rows = group.rows.map { it.toWizardRow() },
            )
        }
    }

    // The prefs parent + its domain children (the counted "Other" bucket last).
    if (state.groups.prefDomains.isNotEmpty()) {
        item(key = "prefs_parent") {
            WizardGroupCard(
                icon = Tabler.Outline.Database,
                title = stringResource(Res.string.wizard_diff_parent_preferences),
                subtitle = GroupSubtitle.Counts(
                    changed = state.groups.prefDomains.sumOf { it.changed },
                    added = state.groups.prefDomains.sumOf { it.added },
                    removed = state.groups.prefDomains.sumOf { it.removed },
                ),
                checked = toggleState(
                    allSelected = state.selection.allDomainsSelected(state.groups.prefDomains),
                    noneSelected = state.selection.domains.isEmpty(),
                ),
                onToggle = { viewModel.togglePrefs() },
            )
        }
        state.groups.prefDomains.forEach { domain ->
            item(key = "domain_${domain.id}") {
                WizardGroupCard(
                    icon = iconForDomain(domain.id),
                    title = domain.title,
                    subtitle = GroupSubtitle.Counts(domain.changed, domain.added, domain.removed),
                    checked = domain.id.toToggleableState(state.selection.domains),
                    onToggle = { viewModel.togglePrefDomain(domain.id) },
                    indent = true,
                    rows = domain.rows.map { it.toWizardRow() },
                )
            }
        }
    }
}

/** The parent checkbox's tri-state face over a two-tier selection. */
private fun toggleState(allSelected: Boolean, noneSelected: Boolean): ToggleableState = when {
    allSelected -> ToggleableState.On
    noneSelected -> ToggleableState.Off
    else -> ToggleableState.Indeterminate
}

/** A child's plain On/Off checkbox state. */
private fun <T> T.toToggleableState(selected: Collection<T>): ToggleableState =
    if (this in selected) ToggleableState.On else ToggleableState.Off

/**
 * One diff row's rendered face. The label shows the canonical key — the
 * `u_<userId>::` user namespace stripped on prefs rows, the user never sees
 * their id in the diff — plus a subtle origin-profile tag when the row does
 * not belong to the base profile (base and overlay rows sharing an ns/key
 * are distinct diff rows).
 */
private fun SnapshotRowDiff.toWizardRow(): WizardDiffRow {
    val displayKey = if (ns == PrefsDomainCatalog.PREFS_NS) {
        PrefsDomainCatalog.stripUserNamespace(key)
    } else {
        key
    }
    val profileTag = profile.takeIf { it.isNotBlank() }?.let { "  ·  $it" }.orEmpty()
    return WizardDiffRow(
        label = "$ns/$displayKey$profileTag",
        from = when (kind) {
            SnapshotRowKind.ADDED -> null
            else -> renderJson(liveValue)
        },
        to = when (kind) {
            SnapshotRowKind.REMOVED -> null
            else -> renderJson(snapshotValue)
        },
        kind = kind,
    )
}

/** Entry text: primitives verbatim, non-primitives as compact JSON, truncated. */
private fun renderJson(value: kotlinx.serialization.json.JsonElement?): String = when (value) {
    null -> ""
    is kotlinx.serialization.json.JsonPrimitive -> value.content
    else -> value.toString()
}.take(60)

/** One snapshot row's diff line: the ns/key + the live → snapshot pair (+ kind badge). */
private data class WizardDiffRow(
    val label: String,
    val from: String?,
    val to: String?,
    val kind: SnapshotRowKind = SnapshotRowKind.CHANGED,
)

// ---------------------------------------------------------------------------
// DIFF step — old-plugin FULL-RESTORE degrade
// ---------------------------------------------------------------------------

private fun LazyListScope.fullRestoreStep(callbacks: WizardCallbacks) {
    item(key = "preview_unavailable") {
        WizardWarningCard(
            icon = Tabler.Outline.AlertTriangle,
            title = stringResource(Res.string.wizard_preview_unavailable_title),
            message = stringResource(Res.string.wizard_preview_unavailable_message),
        )
    }
    item(key = "full_restore") {
        WizardSourceRow(
            icon = Tabler.Outline.Clock,
            title = stringResource(Res.string.wizard_full_restore),
            subtitle = null,
            trailingText = null,
            destructive = true,
            onClick = callbacks.onRestoreConfirm,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** A group card's subtitle face, resolved in composition. */
private sealed interface GroupSubtitle {
    /** The snapshot flavor's changed/added/removed counts. */
    data class Counts(val changed: Int, val added: Int, val removed: Int) : GroupSubtitle

    /** The file flavor's "N of M changed" face (0 changed renders "up to date"). */
    data class FileStyle(val changed: Int, val total: Int) : GroupSubtitle
}

@Composable
private fun WizardSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun WizardLoadingLine() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(Res.string.wizard_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardWarningCard(icon: ImageVector, title: String, message: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The lock-config opt-in row (the import preview's checkbox card, same register). */
@Composable
private fun WizardCheckboxRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One tappable source entry (a restore point / the file row / the full restore). */
@Composable
private fun WizardSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailingText: String?,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val headlineColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = headlineColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * One selectable diff group: a checkbox header (the selection — parents
 * render [ToggleableState.Indeterminate] on partial child selection) plus a
 * chevron cell (the expandable diff rows). TV navigable: the checkbox + header
 * row are one focusable toggle target; the trailing chevron is its OWN
 * clickable cell so expansion (review-only) never steals the primary toggle.
 */
@Composable
private fun WizardGroupCard(
    icon: ImageVector,
    title: String,
    subtitle: GroupSubtitle,
    checked: ToggleableState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
    rows: List<WizardDiffRow> = emptyList(),
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "wizardChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 20.dp) else Modifier)
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(state = checked, onClick = { onToggle() })
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (subtitle) {
                        is GroupSubtitle.Counts -> stringResource(
                            Res.string.wizard_group_counts,
                            subtitle.changed,
                            subtitle.added,
                            subtitle.removed,
                        )
                        is GroupSubtitle.FileStyle -> if (subtitle.changed == 0) {
                            stringResource(Res.string.settings_factory_reset_up_to_date)
                        } else {
                            stringResource(
                                Res.string.settings_factory_reset_changed_count,
                                subtitle.changed,
                                subtitle.total,
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (rows.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .clickable { expanded = !expanded }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && rows.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                rows.forEach { row -> WizardDiffRowLine(row) }
            }
        }
    }
}

/** One diff line: the field/key + (a kind badge for added/removed) + the from → to pair. */
@Composable
private fun WizardDiffRowLine(row: WizardDiffRow) {
    val absentText = stringResource(Res.string.wizard_value_absent)
    val removedText = stringResource(Res.string.wizard_value_removed)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true),
            )
            when (row.kind) {
                SnapshotRowKind.REMOVED -> KindBadge(stringResource(Res.string.wizard_row_kind_removed), true)
                SnapshotRowKind.ADDED -> KindBadge(stringResource(Res.string.wizard_row_kind_added), false)
                SnapshotRowKind.CHANGED -> KindBadge(stringResource(Res.string.wizard_row_kind_changed), false)
            }
        }
        Text(
            text = "${row.from ?: absentText}  →  ${row.to ?: removedText}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KindBadge(text: String, isError: Boolean) {
    Spacer(Modifier.width(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

private fun iconForCategory(category: PreferenceResetCategory): ImageVector =
    PreferenceCategoryViews.firstOrNull { it.category == category }?.icon ?: Tabler.Outline.Database

private fun iconForSlice(key: String): ImageVector =
    ExternalSliceCardViews.firstOrNull { it.key == key }?.icon ?: Tabler.Outline.Database

private fun iconForDomain(domain: String): ImageVector = when (domain) {
    PrefsDomainCatalog.OTHER_DOMAIN -> Tabler.Outline.Database
    else -> Tabler.Outline.Clock
}

/**
 * The Wave-3 secrets card: locked state offers the passphrase unlock; the
 * unlocked state lists WHAT will be restored behind the explicit apply
 * button (never automatic). Ported from the retired import preview.
 */
@Composable
private fun SecretsCard(
    summary: SecretsRestoreSummary?,
    unlocking: Boolean,
    onUnlock: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Tabler.Outline.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_import_secrets_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.settings_import_secrets_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (summary == null) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onUnlock,
                enabled = !unlocking,
                shape = ShapeCache.smoothPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.align(Alignment.End),
            ) {
                if (unlocking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.settings_import_secrets_unlock_button))
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            SecretCountLine(stringResource(Res.string.settings_import_secrets_counts_servers, summary.servers))
            SecretCountLine(stringResource(Res.string.settings_import_secrets_counts_arr, summary.arrServers))
            SecretCountLine(
                stringResource(Res.string.settings_import_secrets_counts_subtitle, summary.subtitleProviders),
            )
            SecretCountLine(
                stringResource(
                    if (summary.hasSeerr) Res.string.settings_import_secrets_counts_seerr_yes
                    else Res.string.settings_import_secrets_counts_seerr_no,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onApply,
                shape = ShapeCache.smoothPill,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.settings_import_secrets_apply))
            }
        }
    }
}

@Composable
private fun SecretCountLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
