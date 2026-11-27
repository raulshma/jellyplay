package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest

/**
 * Detects whether a playback session includes any ASS/SSA subtitle sources,
 * which is the signal that the ExoPlayer engine must activate the libass
 * (ass-media) rendering path for that session.
 */
internal object AssSupport {
    fun isAssCodec(codec: String?): Boolean {
        val c = codec?.lowercase()
        return c == "ass" || c == "ssa"
    }

    fun hasAssSubtitles(request: PlaybackRequest): Boolean =
        request.requestSpecific?.hasEmbeddedAssSubtitles == true ||
            request.externalSubtitles.any {
                isAssCodec(it.codec) || it.mimeType.equals("text/x-ssa", ignoreCase = true)
            }
}
