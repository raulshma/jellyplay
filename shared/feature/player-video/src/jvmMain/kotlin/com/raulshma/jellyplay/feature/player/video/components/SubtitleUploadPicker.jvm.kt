package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop actual of the [rememberSubtitleUploadPicker] seam (wave 20C): the
 * SAF open-document launcher becomes a native AWT [FileDialog] in LOAD mode,
 * shown straight from the select-file click (rememberDocumentPicker precedent
 * in VideoPlayerScreenSeams.jvm.kt — Compose desktop's UI thread is the AWT
 * EDT, so the modal dialog blocks the click handler and resumes with the
 * pick). A completed pick is delivered as a `file:` URI plus the bare file
 * name — the same shapes the Android launcher's `uri.toString()` /
 * `lastPathSegment` fallback deliver — and the Upload tab's read of those
 * bytes stays lazy: it happens at upload-button time inside
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager.uploadSubtitle]'s
 * existing `Dispatchers.IO` + size-check + userMessageBus error path, via the
 * desktop [SubtitleContentGateway] actual that now resolves `file:` URIs.
 *
 * The subtitle extension list is an *advisory* filter only — AWT's
 * [FileDialog.setFilenameFilter] is documented not to function on Windows, so
 * the dialog may offer every file there; the Android launcher's any-type
 * mime list was equally permissive and the upload path's own checks (size
 * cap, empty-file rejection) still apply. Cancelling the dialog fires no
 * callback, so the sheet keeps its previously selected file.
 */
private val SUBTITLE_PICK_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "idx")

/** Pure name half of the advisory filter (see [subtitlePickFilenameFilter]); extracted for jvmTest. */
internal fun isAdvisorySubtitleFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in SUBTITLE_PICK_EXTENSIONS

/** Advisory-only filename filter (see [SUBTITLE_PICK_EXTENSIONS]); directories always pass so navigation works. */
internal fun subtitlePickFilenameFilter(): FilenameFilter = FilenameFilter { _, name ->
    File(name).isDirectory || isAdvisorySubtitleFileName(name)
}

@Composable
internal actual fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker? =
    remember(onPicked) {
        object : SubtitleUploadPicker {
            override fun launch() {
                val dialog = FileDialog(null as Frame?, "Choose subtitle file", FileDialog.LOAD)
                dialog.filenameFilter = subtitlePickFilenameFilter()
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir == null || file == null) return // cancel: retain the prior pick.
                onPicked(File(dir, file).toURI().toString(), file)
            }
        }
    }
