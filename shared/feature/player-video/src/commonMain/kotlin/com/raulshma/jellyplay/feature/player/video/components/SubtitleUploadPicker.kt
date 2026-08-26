package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.runtime.Composable

/**
 * SAF open-document seam for the subtitle manager sheet's Upload tab (wave 7C
 * KMP move; EditorFilePicker precedent): the Android actual wires the verbatim
 * `ActivityResultContracts.OpenDocument` launcher that used to live in
 * UploadTab — including the `lastPathSegment` display-name fallback — while
 * the desktop actual returns null so the file-select row no-ops there (the
 * whole player surface stays latent on desktop: no nav route, PlayerActivity
 * is the sole entry point).
 */
internal interface SubtitleUploadPicker {
    fun launch()
}

@Composable
internal expect fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker?
