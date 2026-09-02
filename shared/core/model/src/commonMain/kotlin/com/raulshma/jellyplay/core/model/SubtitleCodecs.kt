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
    val normalized = normalizeCodec(codec)
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
    val normalized = normalizeCodec(codec) ?: return false
    return normalized in IMAGE_SUBTITLE_CODECS
}

/** Image codecs that travel as VobSub `.sub` (paired with an `.idx` palette).
 *  Everything else in [IMAGE_SUBTITLE_CODECS] is served verbatim as `.sup`.
 *  Must stay a subset of [IMAGE_SUBTITLE_CODECS] — pinned by test. */
internal val VOBSUB_FAMILY_CODECS = setOf("dvd_subtitle", "vobsub")

/** Case/whitespace normalization shared by every codec predicate below. */
private fun normalizeCodec(codec: String?): String? = codec?.trim()?.lowercase()

/** Whether [codec] is a VobSub-family stream that only renders as an
 *  `.idx`+`.sub` pair (see [subtitleCompanionFileName]). */
fun isVobsubFamilyCodec(codec: String?): Boolean {
    val normalized = normalizeCodec(codec) ?: return false
    return normalized in VOBSUB_FAMILY_CODECS
}

/**
 * Sibling file name of a VobSub pair: `2.idx` ↔ `2.sub`. Both halves must be
 * bundled — the palette alone or the bitmap alone renders nothing — and both
 * must survive orphan pruning while the manifest entry lives. Returns `null`
 * for non-pair names (no companion concept).
 */
fun subtitleCompanionFileName(fileName: String): String? {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".idx") -> fileName.dropLast(4) + ".sub"
        lower.endsWith(".sub") -> fileName.dropLast(4) + ".idx"
        else -> null
    }
}

/**
 * On-disk file extension for a bundled offline subtitle sidecar of [codec].
 *
 * Image codecs must NOT default to `.srt`: the bytes are verbatim PGS/VOBSUB
 * (the Jellyfin endpoint cannot transcode image formats), and players that
 * probe by extension would try to parse bitmap data as text. A `.sup` name
 * also documents intent for resync reconciliation, which reads sidecar files
 * from disk without re-reading server metadata. New members of
 * [IMAGE_SUBTITLE_CODECS] land on `.sup` automatically — only add to
 * [VOBSUB_FAMILY_CODECS] when the server serves them as VobSub pairs.
 *
 * Note the mime layer does not depend on this mapping — manifests carry the
 * original [MediaStream.codec] and the engines derive mime from that. This
 * exists purely to write an honest file extension.
 */
fun subtitleSidecarExtension(codec: String?): String {
    val normalized = normalizeCodec(codec) ?: return "srt"
    if (normalized in IMAGE_SUBTITLE_CODECS) {
        // The download path bundles VobSub as a full .idx+.sub pair
        // ([isVobsubFamilyCodec] branch), so this arm only ever names the
        // bitmap half — the palette alone or the bitmap alone renders nothing.
        return if (normalized in VOBSUB_FAMILY_CODECS) "sub" else "sup"
    }
    return when (normalized) {
        "subrip", "srt" -> "srt"
        "ass", "ssa" -> "ass"
        "webvtt", "vtt" -> "vtt"
        "mov_text", "ttml", "dfxp", "tt" -> "ttml"
        "sub", "microdvd" -> "sub"
        else -> "srt"
    }
}
