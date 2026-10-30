package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The full-screen embedded-trailer dialog — extracted from the verbatim twin
 * blocks the media detail and Seerr detail screens each carried inline
 * (a `Dialog` with platform-default width off, a black full-size `Box`, an
 * autoplaying unmuted [InlineTrailerPlayerHost] with controls, and an
 * embed-failure fallback that opens the YouTube watch URL). One composable so
 * the two surfaces cannot drift; the embed-failure arm routes through
 * [onDismiss] first — the same "clear activeTrailerKey" both screens performed
 * inline — before opening the URL, and the same lambda backs
 * `onDismissRequest`, so a dismiss during playback and a failed embed clear
 * the key through one path.
 */
@Composable
internal fun InlineTrailerDialog(
    videoKey: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            InlineTrailerPlayerHost(
                videoKey = videoKey,
                modifier = Modifier.fillMaxSize(),
                muted = false,
                showControls = true,
                autoplay = true,
                onEmbedFailed = {
                    onDismiss()
                    uriHandler.openUri("https://www.youtube.com/watch?v=$videoKey")
                },
            )
        }
    }
}
