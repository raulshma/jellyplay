package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.StreamType

/**
 * Media-content metadata slice of [com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState]:
 * what's playing (overview, cast, artwork, lyrics), how (play method, direct-play flag), and the
 * raw media-source/stream model for the detail/track sheets.
 */
@Immutable
data class MediaContentState(
    val overview: String = "",
    val people: List<PersonInfo> = emptyList(),
    val artworkUrl: String? = null,
    val lyricsLines: List<LyricsLine> = emptyList(),
    val streamUrl: String? = null,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    /**
     * Every media source (version) of the playing item — the Version sheet's
     * rows. Empty for offline sessions (their detail carries no sources) so
     * the sheet entry never offers a version switch there.
     */
    val mediaSources: List<MediaSource> = emptyList(),
    /** Display name of the active play method; empty until the load resolves it. */
    val playMethod: String = "",
    val isDirectPlayForced: Boolean = false,
    /**
     * True when the session plays a locally downloaded file (a
     * [com.raulshma.jellyplay.feature.player.video.PlaybackSource.Offline]
     * resolution) — the sole condition of the player's "Downloaded" badge.
     */
    val isOfflineSource: Boolean = false,
    val seriesId: String? = null,
    /** Raw server transcode reasons for the current stream; empty when
     *  direct playing. Formatted for display at the call site via
     *  [com.raulshma.jellyplay.core.ui.player.TranscodeReasonsFormatter]. */
    val transcodeReasons: List<String> = emptyList(),
) {
    /** HDR type from the first video stream, or null for SDR/unknown. */
    val hdrType: String?
        get() {
            val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
            val range = videoStream.videoRange ?: return null
            return if (range.equals("SDR", ignoreCase = true)) null else range
        }

    /** Real frame rate from the first video stream. */
    val videoFrameRate: Float?
        get() = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }?.realFrameRate

    /**
     * The player "Downloaded" badge derivation: shown only while the current
     * session serves the stored download, rendered beside the play-method
     * row in the stats overlay and the playback-info sheet.
     */
    val showDownloadedBadge: Boolean
        get() = isOfflineSource
}
