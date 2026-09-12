package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Web actual of the [BackupFilePicker] seam: null — the browser shell has no
 * SAF/AWT file picker wired yet, so the backup export/import rows take the
 * null-picker unavailable path (hidden), which also keeps the
 * [SettingsBackupIo] payload calls unreachable on web. The wiring gate lives
 * HERE, not in a broken picker attempt.
 */
@Composable
internal actual fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker? = null
