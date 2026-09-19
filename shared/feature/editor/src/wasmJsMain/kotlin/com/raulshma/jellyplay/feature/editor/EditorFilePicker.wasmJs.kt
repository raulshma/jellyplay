package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Composable

/**
 * The wasmJs actuals of the editor file-picker seam: null-degrade, the same
 * drop-on-web pattern the shared UserMessageBus default provides. A real
 * `<input type="file">` bridge is the natural follow-up; until it lands the
 * upload-from-file affordances launch nothing on web (the sheets'
 * `filePicker?.launch()` reads stay inert) and the upload-from-URL tabs
 * remain fully functional.
 */
@Composable
internal actual fun rememberImageFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = null

@Composable
internal actual fun rememberSubtitleFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = null
