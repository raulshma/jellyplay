package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Maps a container format string (as reported by the Jellyfin MediaSource or
 * sniffed from file magic bytes) to a Media3 MIME type.
 *
 * Why: ExoPlayer's `MediaItem` selects its extractor from the URI extension
 * by default. Downloaded files historically carry a hardcoded `.mp4`
 * extension regardless of the real container, so MKV/TS/AVI bytes get fed to
 * the MP4 extractor and ExoPlayer hangs silently in `STATE_BUFFERING`.
 * Attaching the correct MIME type via `MediaItem.Builder.setMimeType` forces
 * the right extractor and unblocks playback.
 *
 * The table lives in commonMain (the `MpvErrorTaxonomy` pattern) so it is
 * jvmTest-pinnable; media3's `MimeTypes` object stays OUT of commonMain — its
 * constants are plain strings, mirrored verbatim below (verified against the
 * media3 line the app builds against via `javap -constants`). The androidMain
 * `ContainerMimeMapper` adapter is a thin delegate and keeps its name for the
 * platform call sites.
 *
 * Mirrors the [com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleFormatCatalog]
 * pattern: lowercase-normalizes input, returns `null` for unknown containers
 * so the caller can fall back to extension-based inference.
 */
internal object ContainerMimeTable {

    // Media3 MimeTypes constants as plain strings (commonMain cannot see the
    // media3 dependency).
    const val MIME_APPLICATION_MP4 = "application/mp4" // MimeTypes.APPLICATION_MP4
    const val MIME_APPLICATION_MATROSKA = "application/x-matroska" // MimeTypes.APPLICATION_MATROSKA
    const val MIME_VIDEO_MP2T = "video/mp2t" // MimeTypes.VIDEO_MP2T
    const val MIME_VIDEO_FLV = "video/x-flv" // MimeTypes.VIDEO_FLV
    const val MIME_AUDIO_FLAC = "audio/flac" // MimeTypes.AUDIO_FLAC
    const val MIME_AUDIO_MPEG = "audio/mpeg" // MimeTypes.AUDIO_MPEG
    const val MIME_AUDIO_AAC = "audio/mp4a-latm" // MimeTypes.AUDIO_AAC
    const val MIME_AUDIO_OGG = "audio/ogg" // MimeTypes.AUDIO_OGG
    const val MIME_AUDIO_WAV = "audio/wav" // MimeTypes.AUDIO_WAV

    fun mapToMime(container: String?): String? {
        if (container.isNullOrBlank()) return null
        return when (container.lowercase().trim()) {
            "mp4", "m4v", "m4a", "mov", "ismv", "isma" -> MIME_APPLICATION_MP4
            "mkv", "webm", "mka" -> MIME_APPLICATION_MATROSKA
            "ts", "m2ts", "mts", "tsa", "tsv" -> MIME_VIDEO_MP2T
            "flac" -> MIME_AUDIO_FLAC
            "mp3" -> MIME_AUDIO_MPEG
            "aac", "adts" -> MIME_AUDIO_AAC
            "ogg", "oga", "opus" -> MIME_AUDIO_OGG
            "wav" -> MIME_AUDIO_WAV
            "flv" -> MIME_VIDEO_FLV
            // AVI: Media3 has no dedicated AVI MIME constant and no first-class
            // AVI extractor on all versions. Return null so ExoPlayer falls back
            // to content sniffing; if it still cannot decode, the buffering
            // watchdog surfaces the retry-with-engine dialog.
            else -> null
        }
    }
}
