package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.runtime.Composable

/**
 * Desktop actual of the [rememberSubtitleUploadPicker] seam: no SAF picker on
 * JVM, so the Upload tab's file-select row no-ops (EditorFilePicker desktop
 * precedent). The player-video module is deliberately latent on desktop — no
 * nav route, no engine-stack Koin defs — so this half only exists to satisfy
 * the expect.
 */
@Composable
internal actual fun rememberSubtitleUploadPicker(
    onPicked: (uri: String, fileName: String) -> Unit,
): SubtitleUploadPicker? = null
