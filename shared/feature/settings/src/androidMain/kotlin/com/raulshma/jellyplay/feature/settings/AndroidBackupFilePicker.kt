package com.raulshma.jellyplay.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android actual of the [BackupFilePicker] seam: the two SAF launchers moved
 * verbatim from the pre-migration BackupSettingsScreen. androidx.activity.compose
 * rides navigation3-ui's transitive edge (AndroidVoiceSearch / BackHandler-seam
 * precedent), so androidMain needs no new dependency.
 */
@Composable
internal actual fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker? {
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { onExportUriSelected(it.toString()) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportUriSelected(it.toString()) }
    }
    return remember {
        object : BackupFilePicker {
            override fun launchCreateExport(defaultFileName: String) {
                settingsLauncher.launch(defaultFileName)
            }

            override fun launchOpenImport() {
                importLauncher.launch(arrayOf("application/json"))
            }
        }
    }
}
