package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop (jvmMain) actual of the [EditorFilePicker] seam (wave 20A): the
 * upload sheets' file-source rows open the native AWT FileDialog via the
 * shared [pickAwtFile] helper (:shared:core:ui jvmMain — the same modal
 * LOAD pattern every desktop picker seam uses) instead of no-op'ing.
 * [EditorPickedFile.readBytes] defers the disk read to upload time on
 * `Dispatchers.IO`, mirroring the Android actual's contentResolver timing; a
 * file that vanished between pick and upload throws from readBytes (the JDK
 * FileNotFoundException carries the path), which the ViewModel's
 * platform-neutral `runCatching` routes into `uiState.error` — no
 * desktop-specific error string needed (the "Cannot open input stream…"
 * messages pinned by EditorViewModelUploadTest are the Android actual's).
 *
 * [EditorPickedFile.previewUrl] carries `File.toURI().toString()` (the
 * `file:/…` single-slash form): coil3 registers `StringMapper` +
 * `FileUriFetcher.Factory` in `addCommonComponents` for every target, so the
 * Images tab's MediaImage preview decodes local files with the default
 * desktop ImageLoader (apps/desktop registers no custom fetcher). Runtime
 * preview is manually-verified-only — the jvmTest suite covers the pure
 * helpers below (a native modal dialog cannot run headless).
 */
@Composable
internal actual fun rememberImageFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = rememberEditorFilePicker(
    title = "Choose image",
    filenameFilter = editorImageFilenameFilter(),
    onPicked = onPicked,
)

@Composable
internal actual fun rememberSubtitleFilePicker(
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = rememberEditorFilePicker(
    // Unfiltered — matches the Android actual's any-type ("*/*") SAF launch;
    // SubtitlesTab's own name handling applies (blank name → subtitle.srt).
    title = "Choose subtitle",
    filenameFilter = null,
    onPicked = onPicked,
)

@Composable
private fun rememberEditorFilePicker(
    title: String,
    filenameFilter: FilenameFilter?,
    onPicked: (EditorPickedFile) -> Unit,
): EditorFilePicker? = remember(onPicked) {
    object : EditorFilePicker {
        override fun launch() {
            // The shared AWT dialog (modal; blocks until dismissed; filter
            // advisory — see pickAwtFile's KDoc). Cancel → null → onPicked
            // never fires.
            editorPickedFile(pickAwtFile(title = title, filenameFilter = filenameFilter))
                ?.let(onPicked)
        }
    }
}

// ── Pure helpers (jvmTest-covered; the dialog itself is manual-only) ────────

/** Image extensions the image dialog's advisory filter offers. */
internal val editorImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

internal fun isEditorImageFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in editorImageExtensions

/**
 * Advisory [FilenameFilter] for the image dialog: accept the common image
 * extensions (case-insensitive) AND every directory — rejecting directories
 * would hide the folders themselves and make the dialog unnavigable.
 */
internal fun editorImageFilenameFilter(): FilenameFilter = FilenameFilter { dir, name ->
    File(dir, name).isDirectory || isEditorImageFileName(name)
}

/**
 * Builds the seam's picked file from the dialog's answer ([pickAwtFile]
 * output — null on cancel), or null when the dialog was cancelled — the
 * caller then simply doesn't fire onPicked, so the sheet's prior pick is
 * retained (SAF cancel semantics: the Android actual's `uri?.let` never
 * fires on a null uri).
 */
internal fun editorPickedFile(picked: File?): EditorPickedFile? {
    if (picked == null) return null
    return EditorPickedFile(
        fileName = picked.name,
        previewUrl = picked.toURI().toString(),
        readBytes = {
            withContext(Dispatchers.IO) { picked.readBytes() }
        },
    )
}
