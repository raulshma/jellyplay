package com.raulshma.jellyplay.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Android actual of the [EditorFilePicker] seam: the SAF open-document
 * launchers moved verbatim from the pre-migration ImagesTab/SubtitlesTab, and
 * the deferred contentResolver byte read moved verbatim from the
 * pre-migration EditorViewModel.uploadImageFromUri/uploadSubtitleFromUri
 * (including the distinct "Cannot open input stream…" error messages, which
 * surface through uiState.error at upload time exactly as before).
 * androidx.activity.compose rides navigation3-ui's transitive edge
 * (settings BackupFilePicker / BackHandler-seam precedent), so this source set
 * needs no extra dependency.
 */
@Composable
internal actual fun rememberImageFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = rememberEditorFilePicker(
    mimeTypes = arrayOf("image/*"),
    streamErrorMessage = "Cannot open input stream for selected image",
    onPicked = onPicked,
)

@Composable
internal actual fun rememberSubtitleFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = rememberEditorFilePicker(
    mimeTypes = arrayOf("*/*"),
    streamErrorMessage = "Cannot open input stream for selected subtitle",
    onPicked = onPicked,
)

@Composable
private fun rememberEditorFilePicker(
    mimeTypes: Array<String>,
    streamErrorMessage: String,
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            onPicked(
                EditorPickedFile(
                    fileName = it.lastPathSegment?.substringAfterLast('/').orEmpty(),
                    previewUrl = it.toString(),
                    readBytes = {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes()
                            } ?: throw IOException(streamErrorMessage)
                        }
                    },
                ),
            )
        }
    }
    return remember {
        object : EditorFilePicker {
            override fun launch() {
                launcher.launch(mimeTypes)
            }
        }
    }
}
