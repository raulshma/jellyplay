package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onFactoryReset: () -> Unit,
    onImportPreview: (String) -> Unit = {},
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val userMessageBus = LocalUserMessageBus.current

    val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportSettings(it) }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importSettings(it) }
    }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "backup_init",
    )

    // When a backup is picked and decoded, navigate to the full-screen diff.
    // The ViewModel stages `pendingImport` without writing; the preview screen
    // performs the actual restore (all or per-category). The pending state is
    // consumed atomically: navigation is attempted first and the pending is
    // cleared only after the navigate call returns, so a failed navigation
    // does not drop the staged file (user can retry). A duplicate launch for
    // the same uri is suppressed via the `lastNavigatedUri` guard — without it
    // returning to this screen would re-trigger the pending that was never
    // cleared on failure.
    // Use a one-shot event id to allow re-picking the *same* file.
    // The previous `lastNavigatedUri == raw` guard permanently suppressed the
    // same uri after first navigation (review finding). Instead, clear the guard
    // when pending becomes null so a new PendingImport with the same uri can
    // re-fire after `cancelImport()` + re-pick.
    val lastNavigatedUri = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel.pendingImport) {
        val pending = viewModel.pendingImport
        if (pending == null) {
            lastNavigatedUri.value = null
            return@LaunchedEffect
        }
        val raw = pending.uri.toString()
        if (raw == lastNavigatedUri.value) return@LaunchedEffect
        lastNavigatedUri.value = raw
        try {
            onImportPreview(raw)
        } finally {
            viewModel.cancelImport()
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_backup_restore),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.DatabaseExport,
                    title = stringResource(R.string.settings_backup_restore),
                    summary = { stringResource(R.string.settings_backup_restore_subtitle) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.FileExport,
                        title = stringResource(R.string.settings_export_settings),
                        subtitle = stringResource(R.string.settings_export_settings_subtitle),
                        index = 0, count = 3,
                        highlighted = highlightSettingId == "backup_export",
                        onClick = {
                            settingsLauncher.launch("jellyplay-settings.json")
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.FileImport,
                        title = stringResource(R.string.settings_import_settings),
                        subtitle = stringResource(R.string.settings_import_settings_subtitle),
                        index = 1, count = 3,
                        highlighted = highlightSettingId == "backup_import",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = stringResource(R.string.settings_factory_reset),
                        subtitle = stringResource(R.string.settings_factory_reset_subtitle),
                        index = 2, count = 3,
                        isDestructive = true,
                        highlighted = highlightSettingId == "factory_reset",
                        onClick = onFactoryReset,
                    )
                }

                LaunchedEffect(viewModel.backupRestoreStatus) {
                    viewModel.backupRestoreStatus?.let { msg ->
                        userMessageBus.info(msg)
                        viewModel.clearBackupRestoreStatus()
                    }
                }
            }
        }
        }
    }
}
