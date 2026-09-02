package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop actual of the [rememberSubtitleUploadPicker] seam (wave 20C): the
 * SAF open-document launcher becomes the shared AWT dialog ([pickAwtFile],
 * LOAD mode) shown straight from the select-file click
 * (rememberDocumentPicker precedent in VideoPlayerScreenSeams.jvm.kt —
 * Compose desktop's UI thread is the AWT EDT, so the modal dialog blocks the
 * click handler and resumes with the pick). A completed pick is delivered as
 * a `file:` URI plus the bare file name — the same shapes the Android
 * launcher's `uri.toString()` / `lastPathSegment` fallback deliver — and the
 * Upload tab's read of those bytes stays lazy: it happens at upload-button
 * time inside
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager.uploadSubtitle]'s
 * existing `Dispatchers.IO` + size-check + userMessageBus error path, via the
 * desktop [SubtitleContentGateway] actual that now resolves `file:` URIs.
 *
 * The subtitle extension list is an *advisory* filter only (see
 * [pickAwtFile]'s KDoc for the setFilenameFilter-doesn't-work-on-Windows
 * caveat); the Android launcher's any-type mime list was equally permissive
 * and the upload path's own checks (size cap, empty-file rejection) still
 * apply. Cancelling the dialog fires no callback, so the sheet keeps its
 * previously selected file.
 */
private val SUBTITLE_PICK_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "idx")

/** Pure name half of the advisory filter (see [subtitlePickFilenameFilter]); extracted for jvmTest. */
internal fun isAdvisorySubtitleFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in SUBTITLE_PICK_EXTENSIONS

/** Advisory-only filename filter (see [SUBTITLE_PICK_EXTENSIONS]); directories always pass so navigation works. */
// dir must be part of the directory check: `File(name).isDirectory` resolves
// against the process CWD, so on peers that honor setFilenameFilter (Windows
// does not) every directory would read false and the dialog would be
// unnavigable — DesktopEditorFilePicker.editorImageFilenameFilter twin.
internal fun subtitlePickFilenameFilter(): FilenameFilter = FilenameFilter { dir, name ->
    File(dir, name).isDirectory || isAdvisorySubtitleFileName(name)
}

@Composable
internal actual fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker? =
    remember(onPicked) {
        object : SubtitleUploadPicker {
            override fun launch() {
                val picked = pickAwtFile(
                    title = "Choose subtitle file",
                    filenameFilter = subtitlePickFilenameFilter(),
                ) ?: return // cancel: retain the prior pick.
                onPicked(picked.toURI().toString(), picked.name)
            }
        }
    }
