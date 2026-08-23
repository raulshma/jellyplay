package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberBackupFilePicker(
    onExportUriSelected: (String) -> Unit,
    onImportUriSelected: (String) -> Unit,
): BackupFilePicker? = null
