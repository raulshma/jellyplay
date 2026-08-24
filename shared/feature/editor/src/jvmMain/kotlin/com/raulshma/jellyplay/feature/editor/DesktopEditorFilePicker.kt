package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Composable

// Desktop v1 has no SAF/file pickers (settings BackupFilePicker precedent):
// both actuals return null, so the upload sheets' file-source buttons no-op
// and the upload buttons (gated on a picked file existing) can never enable.
// The URL image upload and remote subtitle search paths are unaffected.
@Composable
internal actual fun rememberImageFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = null

@Composable
internal actual fun rememberSubtitleFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = null
