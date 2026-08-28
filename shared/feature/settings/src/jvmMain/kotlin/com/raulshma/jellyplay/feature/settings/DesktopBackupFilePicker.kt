package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop actual of the [BackupFilePicker] seam (wave 20C): the SAF
 * create/open-document pair becomes native AWT [FileDialog]s — SAVE for the
 * export target (pre-filled with the suggested file name, so the common
 * `jellyplay-settings.json` default shows up in the name box exactly like
 * SAF's CreateDocument suggestion), LOAD for the import source. Showing the
 * modal dialog straight from the row's click callback follows the
 * rememberDocumentPicker precedent (VideoPlayerScreenSeams.jvm.kt): Compose
 * desktop's UI thread is the AWT EDT, so `isVisible = true` blocks the click
 * handler while the native dialog is up and resumes with the pick.
 *
 * A completed pick is delivered as a `file:` URI string — the same opaque
 * serialisation [SettingsBackupIo] consumes; [DesktopSettingsBackupIo] maps
 * it back to [File] streams. Cancelling the dialog fires no callback, so the
 * screen's rows and the VM's backup-restore status keep their prior state
 * (the SAF launchers behave the same way on Android).
 */
@Composable
internal actual fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker? =
    remember(onExportUriSelected, onImportUriSelected) {
        object : BackupFilePicker {
            override fun launchCreateExport(defaultFileName: String) {
                val dialog = FileDialog(null as Frame?, "Export settings", FileDialog.SAVE)
                // SAF's CreateDocument pre-fills the suggested document name;
                // AWT's SAVE-mode equivalent is the `file` field.
                dialog.file = defaultFileName
                dialog.isVisible = true
                val picked = dialog.pickedFile() ?: return
                onExportUriSelected(picked.toURI().toString())
            }

            override fun launchOpenImport() {
                val dialog = FileDialog(null as Frame?, "Import settings", FileDialog.LOAD)
                dialog.isVisible = true
                val picked = dialog.pickedFile() ?: return
                onImportUriSelected(picked.toURI().toString())
            }
        }
    }

/**
 * The picked file, or null when the dialog was dismissed without a selection
 * (AWT reports a cancel as a null `directory`/`file` pair).
 */
private fun FileDialog.pickedFile(): File? {
    val dir = directory ?: return null
    val file = file ?: return null
    return File(dir, file)
}
