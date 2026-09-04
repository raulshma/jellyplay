package com.raulshma.jellyplay.feature.player.video.subtitle

/**
 * The single vocabulary for side-loadable subtitle formats: file extension →
 * canonical codec, and codec (or server label) → MIME type. Every site that
 * meets a subtitle filename, stream codec, or MIME string reads it from here —
 * the former per-site `when` copies had already drifted (4 codecs vs 7,
 * silently mislabelling `.tt`/`.subrip`/`.webvtt` picks).
 *
 * Whether a given format is *parseable* by a specific consumer (e.g. Media3's
 * DefaultSubtitleParserFactory does not cover ASS/SSA) is that consumer's
 * policy, expressed as a gate over these mappings — not a second vocabulary.
 */
internal object SubtitleFormatCatalog {

    /** Codec (or server-reported label/variant) → MIME type. */
    fun mapCodecToMime(codecOrLabel: String?): String? {
        if (codecOrLabel == null) return null
        return when (codecOrLabel.lowercase()) {
            "srt", "subrip" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "vtt", "webvtt" -> "text/vtt"
            "ttml", "dfxp", "tt" -> "application/ttml+xml"
            "pgs", "pgssub", "hdmv_pgs_subtitle" -> "application/pgs"
            "mov_text" -> "application/x-quicktime-tx3g"
            else -> null
        }
    }

    /**
     * File extension (or provider-reported format string) → canonical codec.
     * Returns the canonical name (`srt`/`ass`/`vtt`/`ttml`) so downstream
     * [mapCodecToMime] and engine lookups see one spelling per format.
     */
    fun codecForExtension(extensionOrFormat: String?): String? {
        if (extensionOrFormat == null) return null
        return when (extensionOrFormat.lowercase()) {
            "srt", "subrip" -> "srt"
            "ass", "ssa" -> "ass"
            "vtt", "webvtt" -> "vtt"
            "ttml", "dfxp", "tt" -> "ttml"
            else -> null
        }
    }

    /**
     * MIME types offered to the local-subtitle document picker. `text/plain`
     * stays listed deliberately: several file managers report bare `.srt`
     * files with the generic type, and the fallback file-name decode +
     * [codecForExtension] recovers the real format.
     */
    val pickerMimeTypes: Array<String> = arrayOf(
        "application/x-subrip",
        "text/vtt",
        "text/plain",
        "text/x-ssa",
        "application/ttml+xml",
    )
}
