package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest

/**
 * Detects whether a playback session includes any ASS/SSA subtitle sources,
 * which is the signal that the ExoPlayer engine must activate the libass
 * (ass-media) rendering path for that session.
 */
internal object AssSupport {
    fun hasAssSubtitles(request: PlaybackRequest): Boolean =
        request.externalSubtitles.any { source ->
            val c = source.codec?.lowercase()
            c == "ass" || c == "ssa"
        }
}
