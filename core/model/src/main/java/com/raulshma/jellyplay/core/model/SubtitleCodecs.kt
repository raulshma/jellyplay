package com.raulshma.jellyplay.core.model

/**
 * Subtitle codec taxonomy shared across the subtitle-delivery pipeline
 * (device-profile advertisement, side-load decisions, URL building).
 *
 * The Jellyfin `/Videos/{id}/{sourceId}/Subtitles/{index}/Stream.{format}`
 * endpoint serves **text** formats (SRT/ASS/VTT/TTML) but cannot synthesize or
 * re-serve **image** formats (PGS/VOBSUB/DVB). Image subs are either burned
 * into the video by the transcoder, delivered verbatim via a server-issued
 * `deliveryUrl`, or read from the container by the player's own demuxer (MPV).
 */

/** Text subtitle codecs the Jellyfin subtitle endpoint can serve as an
 *  external side-load (`Stream.{format}`). */
internal val TEXT_SUBTITLE_CODECS = setOf(
    "srt", "subrip", "ass", "ssa", "vtt", "webvtt",
    "ttml", "dfxp", "tt", "mov_text",
)

/** Image / bitmap subtitle codecs the endpoint cannot serve — they must be
 *  burned in, delivered verbatim, or demuxed from the container. */
internal val IMAGE_SUBTITLE_CODECS = setOf(
    "pgs", "pgssub", "hdmv_pgs_subtitle",
    "dvd_subtitle", "vobsub", "dvb_subtitle",
)

/**
 * Whether an embedded subtitle stream can be side-loaded via the Jellyfin
 * subtitle endpoint (text) rather than needing container demux / burn-in (image).
 *
 * Returns `true` for text codecs and for a `null`/blank/unknown codec (the
 * dominant case for an embedded sub with an unset codec is text; permissive
 * matches the transcoded path's previous unconditional side-load, and a
 * failed endpoint fetch falls back harmlessly to container demux). Returns
 * `false` for known image codecs.
 *
 * This is the asymmetry behind the movie-vs-anime subtitle bug: direct-played
 * movies (embedded text subs dropped for MPV) showed no subs while anime
 * (external/transcoded subs) worked.
 */
fun isSideLoadableEmbeddedSubtitle(codec: String?): Boolean {
    val normalized = codec?.trim()?.lowercase()
    if (normalized.isNullOrBlank()) return true
    return when (normalized) {
        in TEXT_SUBTITLE_CODECS -> true
        in IMAGE_SUBTITLE_CODECS -> false
        // Unknown codec: be permissive — the endpoint fails harmlessly if it
        // can't serve it, and mpv falls back to demuxing the container.
        else -> true
    }
}

/**
 * Whether [codec] is a known **image** subtitle codec (PGS/VOBSUB/DVB family).
 * Used to decide device-profile advertisement and endpoint refusal.
 */
fun isImageSubtitleCodec(codec: String?): Boolean {
    val normalized = codec?.trim()?.lowercase() ?: return false
    return normalized in IMAGE_SUBTITLE_CODECS
}
