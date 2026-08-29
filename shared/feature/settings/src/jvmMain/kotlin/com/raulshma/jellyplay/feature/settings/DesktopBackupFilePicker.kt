package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile

/**
 * Desktop actual of the [BackupFilePicker] seam (wave 20C): the SAF
 * create/open-document pair becomes the shared AWT dialog ([pickAwtFile]) —
 * SAVE for the export target (pre-filled with the suggested file name, so
 * the common `jellyplay-settings.json` default shows up in the name box
 * exactly like SAF's CreateDocument suggestion), LOAD for the import source.
 * Showing the modal dialog straight from the row's click callback follows
 * the rememberDocumentPicker precedent (VideoPlayerScreenSeams.jvm.kt):
 * Compose desktop's UI thread is the AWT EDT, so the dialog blocks the click
 * handler while it is up and resumes with the pick.
 *
 * A completed pick is delivered as a `file:` URI string — the same opaque
 * serialisation [SettingsBackupIo] consumes; [DesktopSettingsBackupIo] maps
 * it back to [java.io.File] streams. Cancelling the dialog fires no
 * callback, so the screen's rows and the VM's backup-restore status keep
 * their prior state (the SAF launchers behave the same way on Android).
 */
@Composable
internal actual fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker? =
    remember(onExportUriSelected, onImportUriSelected) {
        object : BackupFilePicker {
            override fun launchCreateExport(defaultFileName: String) {
                // SAF's CreateDocument pre-fills the suggested document name;
                // the shared helper's SAVE mode does the same via prefill.
                val picked = pickAwtFile(
                    title = "Export settings",
                    save = true,
                    prefillFileName = defaultFileName,
                ) ?: return
                onExportUriSelected(picked.toURI().toString())
            }

            override fun launchOpenImport() {
                val picked = pickAwtFile(title = "Import settings") ?: return
                onImportUriSelected(picked.toURI().toString())
            }
        }
    }
