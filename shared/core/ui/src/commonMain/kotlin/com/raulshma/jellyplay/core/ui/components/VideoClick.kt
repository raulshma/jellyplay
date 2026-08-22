package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.UriHandler
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo

/**
 * Builds a click handler for a [SeerrRelatedVideo] that plays YouTube videos
 * inline via [onPlayYouTube] (typically opening an [InlineTrailerPlayer] dialog
 * keyed on the video id), and falls back to opening the YouTube watch URL in
 * an external app for any other site.
 *
 * Deduplicates the `site == "youtube" && key != null` branching that previously
 * lived in both `MediaDetailScreen` and `SeerrDetailScreen`.
 */
@Composable
fun rememberVideoClickHandler(
    uriHandler: UriHandler,
    onPlayYouTube: (key: String) -> Unit,
): (SeerrRelatedVideo) -> Unit = remember(uriHandler, onPlayYouTube) {
    { video ->
        val key = video.key
        if (key != null && video.site?.equals("youtube", ignoreCase = true) == true) {
            onPlayYouTube(key)
        } else if (key != null) {
            // Only YouTube has a resolvable watch URL here — non-YouTube sites
            // without an inline player are ignored rather than launched blind.
            video.site?.lowercase()?.let { site ->
                if (site == "youtube") {
                    uriHandler.openUri("https://www.youtube.com/watch?v=$key")
                }
            }
        }
    }
}
