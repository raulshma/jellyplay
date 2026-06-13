package com.raulshma.jellyplay.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val context = LocalContext.current

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
    LaunchedEffect(Unit) {
        if (isTv) {
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                if (focusRequester.tryRequestFocus("backup_init")) break
            }
        }
    }

    JellyPlayScreenScaffold(
        title = "Backup & Restore",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
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
                    title = "Backup & Restore",
                    summary = { "Export or import app settings" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.FileExport,
                        title = "Export Settings",
                        subtitle = "Save current settings to a JSON file",
                        index = 0, count = 2,
                        highlighted = highlightSettingId == "backup_export",
                        onClick = {
                            settingsLauncher.launch("jellyplay-settings.json")
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.FileImport,
                        title = "Import Settings",
                        subtitle = "Restore settings from a backup file",
                        index = 1, count = 2,
                        highlighted = highlightSettingId == "backup_import",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                    )
                }

                LaunchedEffect(viewModel.backupRestoreStatus) {
                    viewModel.backupRestoreStatus?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearBackupRestoreStatus()
                    }
                }
            }
        }
    }
}
