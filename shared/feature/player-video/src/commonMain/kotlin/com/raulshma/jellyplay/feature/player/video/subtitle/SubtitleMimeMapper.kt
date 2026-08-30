package com.raulshma.jellyplay.feature.player.video.subtitle

internal object SubtitleMimeMapper {
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
}
