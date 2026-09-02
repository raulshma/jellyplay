package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.settings.components.CategoryDiff
import com.raulshma.jellyplay.feature.settings.components.IntegrationsNote
import com.raulshma.jellyplay.feature.settings.components.PreferenceDiffCategoryItem
import com.raulshma.jellyplay.feature.settings.components.PreferenceDiffSummaryCard
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_app_state
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_all_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_all_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_category_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_category_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_extras_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_confirm_extras_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_error
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_import_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_import_category
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_legacy_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_loading
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_security_option
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_summary_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_preview_version_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    onBack: () -> Unit,
    uri: String = "",
    viewModel: ImportPreviewViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val messenger = rememberSettingsMessenger()

    val current = viewModel.currentPrefs
    val incoming = viewModel.incomingPrefs
    val currentExtras = viewModel.currentExtras
    val incomingExtras = viewModel.incomingExtras

    var pendingAction by remember { mutableStateOf<PendingImportAction?>(null) }
    var restoreSecuritySensitive by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri.isNotBlank()) viewModel.loadBackup(uri)
    }
    // Blank uri (e.g. direct deep-link without file) should not spin forever.
    val isBlankUri = uri.isBlank()

    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "import_preview_init")

    // Toast feedback from VM — typed event avoids string-matching.
    LaunchedEffect(viewModel.importEvent) {
        viewModel.importEvent?.let { event ->
            val msg = when (event) {
                is ImportPreviewViewModel.ImportEvent.AllImported -> "All settings imported"
                is ImportPreviewViewModel.ImportEvent.CategoryImported -> "Category imported"
                is ImportPreviewViewModel.ImportEvent.ExtrasImported -> "App state imported"
                is ImportPreviewViewModel.ImportEvent.Failed -> "Import failed: ${event.message}"
            }
            messenger?.info(msg)
            viewModel.clearImportEvent()
            if (event is ImportPreviewViewModel.ImportEvent.AllImported) onBack()
        }
    }
    // Keep legacy String path for any external callers that still observe it.
    LaunchedEffect(viewModel.importStatus) {
        viewModel.importStatus?.let { viewModel.clearImportStatus() }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_import_preview_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { innerPadding ->
        if (isBlankUri) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                WarningCard(
                    icon = Tabler.Outline.AlertTriangle,
                    text = stringResource(Res.string.settings_import_preview_error),
                )
            }
            return@JellyPlayScreenScaffold
        }
        if (viewModel.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.settings_import_preview_loading), style = MaterialTheme.typography.bodyMedium)
            }
            return@JellyPlayScreenScaffold
        }
        if (viewModel.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                WarningCard(
                    icon = Tabler.Outline.AlertTriangle,
                    text = viewModel.error!!,
                )
            }
            return@JellyPlayScreenScaffold
        }
        if (incoming == null || incomingExtras == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp),
            ) {
                Text(stringResource(Res.string.settings_import_preview_error), color = MaterialTheme.colorScheme.error)
            }
            return@JellyPlayScreenScaffold
        }

        val noneLabel = stringResource(Res.string.settings_unknown)
        val categoryDiffs: List<CategoryDiff> = remember(current, incoming) {
            PreferenceCategoryViews.map { view ->
                val changed = view.changedFields(current, incoming)
                CategoryDiff(view, changed, view.totalFields(current, incoming))
            }
        }
        val extrasFields = remember(currentExtras, incomingExtras, noneLabel) {
            appRuntimeFields(currentExtras, incomingExtras, noneLabel)
        }
        val extrasChanged = extrasFields.filter { it.changed }
        val totalChanged = categoryDiffs.sumOf { it.changed.size } + extrasChanged.size
        val totalFields = categoryDiffs.sumOf { it.total } + extrasFields.size

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
                top = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                PreferenceDiffSummaryCard(
                    totalChanged = totalChanged,
                    totalFields = totalFields,
                    titleRes = Res.string.settings_import_preview_title,
                    summaryRes = Res.string.settings_import_preview_summary_card,
                    primaryLabelRes = Res.string.settings_import_preview_import_all,
                    onPrimary = { pendingAction = PendingImportAction.All },
                    primaryEnabled = totalChanged > 0,
                    isErrorContainer = false,
                )
            }

            // Warnings
            if (viewModel.isLegacy) {
                item(key = "legacy_warn") {
                    WarningCard(
                        icon = Tabler.Outline.InfoCircle,
                        text = stringResource(Res.string.settings_import_preview_legacy_warning),
                    )
                }
            }
            if (viewModel.versionMismatch && !viewModel.isLegacy) {
                item(key = "version_warn") {
                    WarningCard(
                        icon = Tabler.Outline.AlertTriangle,
                        text = stringResource(Res.string.settings_import_preview_version_warning, viewModel.schemaVersion ?: 0),
                    )
                }
            }

            // Security opt-in
            if (viewModel.hasSecuritySensitive) {
                item(key = "security_opt") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth20)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = restoreSecuritySensitive,
                            onCheckedChange = { restoreSecuritySensitive = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.settings_import_preview_security_option),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "integrations_note") {
                IntegrationsNote()
            }

            // Category list (15)
            categoryDiffs.forEach { diff ->
                item(key = diff.view.category.key) {
                    PreferenceDiffCategoryItem(
                        icon = diff.view.icon,
                        nameRes = diff.view.displayNameRes,
                        changedCount = diff.changed.size,
                        totalInCategory = diff.total,
                        fields = diff.changed,
                        onAction = { pendingAction = PendingImportAction.Category(diff.view.category) },
                        actionLabelRes = Res.string.settings_import_preview_import_category,
                        isErrorAction = false,
                    )
                }
            }

            // Extras card (App State) — "everything"
            item(key = "app_state") {
                PreferenceDiffCategoryItem(
                    icon = Tabler.Outline.Database,
                    nameRes = Res.string.factory_reset_cat_app_state,
                    changedCount = extrasChanged.size,
                    totalInCategory = extrasFields.size,
                    fields = extrasChanged,
                    onAction = { pendingAction = PendingImportAction.Extras },
                    actionLabelRes = Res.string.settings_import_preview_import_category,
                    isErrorAction = false,
                )
            }
        }
    }

    pendingAction?.let { action ->
        val (titleRes, messageRes) = when (action) {
            is PendingImportAction.All -> Res.string.settings_import_preview_confirm_all_title to Res.string.settings_import_preview_confirm_all_message
            is PendingImportAction.Category -> Res.string.settings_import_preview_confirm_category_title to Res.string.settings_import_preview_confirm_category_message
            is PendingImportAction.Extras -> Res.string.settings_import_preview_confirm_extras_title to Res.string.settings_import_preview_confirm_extras_message
        }
        ConfirmDialog(
            title = stringResource(titleRes),
            message = stringResource(messageRes),
            confirmText = stringResource(Res.string.settings_import_preview_confirm),
            onConfirm = {
                when (action) {
                    is PendingImportAction.All -> viewModel.importAll(restoreSecuritySensitive) { /* pop handled via status effect */ }
                    is PendingImportAction.Category -> viewModel.importCategory(action.category, restoreSecuritySensitive) { pendingAction = null }
                    is PendingImportAction.Extras -> viewModel.importExtras { pendingAction = null }
                }
                if (action is PendingImportAction.All) {
                    // Keep pendingAction to show loading; will pop on success via LaunchedEffect
                } else {
                    pendingAction = null
                }
            },
            onDismiss = { pendingAction = null },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }
}

private sealed interface PendingImportAction {
    data object All : PendingImportAction
    data class Category(val category: PreferenceResetCategory) : PendingImportAction
    data object Extras : PendingImportAction
}

@Composable
private fun WarningCard(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
