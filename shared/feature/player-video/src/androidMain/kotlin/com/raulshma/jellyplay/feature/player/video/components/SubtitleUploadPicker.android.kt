package com.raulshma.jellyplay.feature.player.video.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android actual of the [rememberSubtitleUploadPicker] seam: the SAF launcher
 * moved verbatim from the pre-migration UploadTab (same any-type mime filter
 * and same `lastPathSegment`-derived display name with the "subtitle.srt"
 * fallback). androidx.activity.compose rides media3-ui's transitive
 * androidx.activity edge (EditorFilePicker / BackHandler-seam precedent), so
 * this source set needs no extra dependency.
 */
@Composable
internal actual fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker? {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            onPicked(
                it.toString(),
                it.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt",
            )
        }
    }
    return remember {
        object : SubtitleUploadPicker {
            override fun launch() {
                launcher.launch(arrayOf("*/*"))
            }
        }
    }
}
