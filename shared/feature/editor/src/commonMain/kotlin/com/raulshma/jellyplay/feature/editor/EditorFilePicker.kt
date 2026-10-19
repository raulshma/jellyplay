package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/**
 * Platform file-picker seam for the editor's two file-source uploads: an
 * image-type document for the Images tab's upload sheet and an any-type
 * document for the Subtitles tab's upload sheet. The Android actual wires the
 * `ActivityResultContracts.OpenDocument` launchers plus the contentResolver
 * byte read; the desktop actual opens the native AWT FileDialog the player's
 * document picker already uses, advisory-filtered to image extensions for the
 * Images tab and unfiltered for the Subtitles tab (Android's any-type launch).
 *
 * [readBytes] defers the stream read to upload time — `withContext(Dispatchers.IO)`
 * around `openInputStream().use { readBytes() }` inside the platform actuals —
 * so a failed read still surfaces through `uiState.error`.
 * [previewUrl] keeps the Images tab's Coil preview of the picked `content://`
 * URI unchanged on Android; desktop passes the picked file's `file:/` URI,
 * which coil3's common FileUriFetcher decodes.
 */
public class EditorPickedFile(
    val fileName: String,
    val previewUrl: String?,
    val readBytes: suspend () -> ByteArray,
)

public interface EditorFilePicker {
    fun launch()
}

/** SAF open-document for an image-type source (Images tab upload sheet). */
@Composable
internal expect fun rememberImageFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker?

/** SAF open-document for an any-type source (Subtitles tab upload sheet). */
@Composable
internal expect fun rememberSubtitleFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker?
