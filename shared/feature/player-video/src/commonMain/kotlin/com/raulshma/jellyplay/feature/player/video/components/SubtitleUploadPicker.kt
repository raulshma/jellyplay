package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.runtime.Composable

/**
 * SAF/native open-document seam for the subtitle manager sheet's Upload tab
 * (wave 7C KMP move; EditorFilePicker precedent): the Android actual wires
 * the verbatim `ActivityResultContracts.OpenDocument` launcher that used to
 * live in UploadTab — including the `lastPathSegment` display-name fallback —
 * while the desktop actual (wave 20C) shows an AWT FileDialog and delivers a
 * `file:` URI plus the bare name, since the player surface is live on
 * desktop (Route.VideoPlayer unguarded on Windows since wave 9A). The actual
 * byte read stays at upload time through the platform SubtitleContentGateway
 * (VideoPlayerPlatform.kt), so both platforms share the size-cap /
 * empty-file checks and error surfacing.
 */
internal interface SubtitleUploadPicker {
    fun launch()
}

@Composable
internal expect fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker?
