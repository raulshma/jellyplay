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
 */
@Composable
internal expect fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onResult: (uriString: String?) -> Unit,
): () -> Unit
