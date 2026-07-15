package com.raulshma.jellyplay.feature.subtitle.tester.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.feature.subtitle.tester.preview.PreviewEngineHost

@Composable
fun PreviewTile(
    host: PreviewEngineHost,
    isApplying: Boolean,
    applyingLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { host.container },
            // fillMaxSize, not fillMaxWidth: libVLC's VLCVideoLayout hosts a
            // separate Subtitles SurfaceView alongside the video surface, and
            // both surface holders only fire surfaceCreated once the layout
            // measures to a real height. With fillMaxWidth the container
            // height wraps to 0, so the surfaces are never created and VLC
            // logs "can't get Subtitles Surface" + "video output creation
            // failed" — resulting in no subtitles and a tiny/blank video.
            // ExoPlayer/mpv surface views set their own MATCH_PARENT so they
            // survive a wrap parent, but libVLC does not.
            modifier = Modifier.fillMaxSize(),
        )
        if (isApplying) {
            Text(applyingLabel, modifier = Modifier.padding(8.dp))
        }
    }
}
