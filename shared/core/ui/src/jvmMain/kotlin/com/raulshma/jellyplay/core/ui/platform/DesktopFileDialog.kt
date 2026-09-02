package com.raulshma.jellyplay.core.ui.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * The ONE desktop shape of the native AWT file dialog (wave 21D
 * de-triplication): the editor upload sheets, the player's document picker,
 * the player's subtitle-upload picker and the settings backup export/import
 * rows all show this same modal [FileDialog] with a null parent frame and
 * read the answers after `isVisible` returns. Showing it straight from a
 * click callback is safe on Compose desktop because its UI thread IS the
 * AWT EDT — the modal dialog blocks the handler and resumes with the pick.
 *
 * [filenameFilter] is advisory only: AWT's
 * `FileDialog.setFilenameFilter` is documented not to function on Windows,
 * so the dialog may offer every file there; even on peers that honor it, a
 * typed file name still goes through. Callers keep their own name/size
 * checks, and filters should pass directories through — rejecting them
 * would hide the folders themselves and make the dialog unnavigable.
 *
 * @param title the dialog's title bar text.
 * @param save `true` shows the SAVE mode (export target), `false` the LOAD
 *   mode (pick an existing file).
 * @param prefillFileName pre-fills the name box in SAVE mode — the AWT
 *   twin of SAF CreateDocument's suggested document name.
 * @param filenameFilter optional advisory [FilenameFilter] (see above).
 * @return the picked file, or null when the dialog was dismissed without a
 *   selection (see [pickedAwtFile] for the cancel shape).
 */
fun pickAwtFile(
    title: String,
    save: Boolean = false,
    prefillFileName: String? = null,
    filenameFilter: FilenameFilter? = null,
): File? {
    val dialog = FileDialog(
        null as Frame?,
        title,
        if (save) FileDialog.SAVE else FileDialog.LOAD,
    )
    if (prefillFileName != null) {
        dialog.file = prefillFileName
    }
    if (filenameFilter != null) {
        dialog.filenameFilter = filenameFilter
    }
    dialog.isVisible = true
    return pickedAwtFile(dialog.directory, dialog.file)
}

/**
 * The pure answer-mapping half of [pickAwtFile]: builds the picked [File]
 * from the dialog's post-modal `directory`/`file` fields, or null when the
 * dialog was cancelled — AWT reports a cancel as a null pair (Windows
 * clears both; other peers may clear one), so EITHER half null means "no
 * pick" and the caller simply fires no callback, retaining prior state
 * (SAF cancel semantics).
 */
fun pickedAwtFile(directory: String?, file: String?): File? {
    if (directory == null || file == null) return null
    return File(directory, file)
}
