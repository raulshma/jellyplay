package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable

/**
 * Document-picker seam for the player's SAF flows (local subtitle side-load,
 * custom subtitle-font install). The androidMain actual wraps
 * `ActivityResultContracts.OpenDocument` and hands the picked document back
 * as its string form; the jvmMain actual opens a native AWT file dialog and
 * hands back an absolute `file:/` URI string — both flow into the same
 * string-typed SubtitleManager/FontProvider members ([uri] is stringified at
 * this boundary). The returned launcher is invoke-once; `null` in the result
 * means the user dismissed the picker.
 *
 * The jvmMain dialog is a modal AWT `FileDialog` shown synchronously on the
 * caller thread — modal dialogs pump their own event loop, so this blocks
 * composition but not the UI; move it behind a dispatcher if it ever stops
 * being modal.
 */
@Composable
internal expect fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onResult: (uriString: String?) -> Unit,
): () -> Unit

/**
 * Platform display name for a document picked through
 * [rememberDocumentPicker]: the decoded file-name portion of the URI, or null
 * when nothing derivable. Android decodes the SAF `content://` URI segment
 * (`…%3AMovies%2Fsub.srt` → `sub.srt`) — string surgery on the raw form would
 * surface percent-encoded names in the subtitle track label. JVM callers get
 * the last path segment of the `file:/` URI.
 */
internal expect fun pickedDocumentDisplayName(uriString: String): String?
