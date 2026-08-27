package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.MimeTypes

internal object SubtitleMimeMapper {
    fun mapCodecToMime(codecOrLabel: String?): String? {
        if (codecOrLabel == null) return null
        return when (codecOrLabel.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp", "tt" -> MimeTypes.APPLICATION_TTML
            "pgs", "pgssub", "hdmv_pgs_subtitle" -> MimeTypes.APPLICATION_PGS
            "mov_text" -> MimeTypes.APPLICATION_TX3G
            else -> null
        }
    }
}
