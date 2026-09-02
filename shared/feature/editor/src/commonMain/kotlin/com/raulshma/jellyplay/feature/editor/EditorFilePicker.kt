package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/**
 * Platform file-picker seam for the editor's two file-source uploads (ninth V3
 * conveyor feature): an image-type document for the Images tab's upload sheet
 * and an any-type document for the Subtitles tab's upload sheet. The Android
 * actual wires the verbatim `ActivityResultContracts.OpenDocument` launchers
 * plus the contentResolver byte read that used to live in
 * EditorViewModel.uploadImageFromUri/uploadSubtitleFromUri (the
 * `@ApplicationContext Context` ctor param died with the move); the desktop
 * actual (wave 20A) opens the native AWT FileDialog the player's document
 * picker already uses, advisory-filtered to image extensions for the Images
 * tab and unfiltered for the Subtitles tab (Android's any-type launch).
 *
 * [readBytes] defers the stream read to upload time — the same timing the
 * legacy ViewModel used (`withContext(Dispatchers.IO)` around
 * `openInputStream().use { readBytes() }`, now inside the platform actuals) —
 * so a failed read still surfaces through `uiState.error` exactly as before.
 * [previewUrl] keeps the Images tab's Coil preview of the picked `content://`
 * URI byte-identical on Android; desktop passes the picked file's `file:/`
 * URI, which coil3's common FileUriFetcher decodes.
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
