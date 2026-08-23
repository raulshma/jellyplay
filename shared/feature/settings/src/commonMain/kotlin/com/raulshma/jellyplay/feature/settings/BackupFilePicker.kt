package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Platform file-picker seam for [BackupSettingsScreen] (Wave 1b SAF move): the
 * create-document (export target) and open-document (import source) launchers
 * live behind one nullable handle. Android wires the verbatim SAF
 * `ActivityResultContracts` bodies; desktop v1 has no file pickers, so the
 * actual returns null and the screen's export/import rows no-op (the factory
 * reset row is platform-independent and stays active).
 *
 * Uri handles are delivered as opaque [String]s — the same serialisation
 * [SettingsBackupIo] consumes.
 */
internal interface BackupFilePicker {
    /** SAF create-document for a `application/json` export target. */
    fun launchCreateExport(defaultFileName: String)

    /** SAF open-document for a `application/json` import source. */
    fun launchOpenImport()
}

@Composable
internal expect fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker?
