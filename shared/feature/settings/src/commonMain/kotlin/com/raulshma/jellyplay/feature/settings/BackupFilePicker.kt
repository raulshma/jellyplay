package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Platform file-picker seam for [BackupSettingsScreen] (Wave 1b SAF move): the
 * create-document (export target) and open-document (import source) launchers
 * live behind one nullable handle. Android wires the verbatim SAF
 * `ActivityResultContracts` bodies; desktop (wave 20C) shows native AWT
 * `FileDialog`s — SAVE pre-filled with the suggested export name, LOAD for
 * the import source — and delivers `file:` URIs, so the export/import rows
 * work on every platform (the factory reset row is platform-independent and
 * stayed active throughout).
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
