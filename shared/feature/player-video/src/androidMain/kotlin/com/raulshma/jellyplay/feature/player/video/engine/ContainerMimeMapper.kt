package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.MimeTypes

/**
 * Maps a container format string (as reported by the Jellyfin MediaSource or
 * sniffed from file magic bytes) to a Media3 MIME type.
 *
 * Why: ExoPlayer's [androidx.media3.common.MediaItem] selects its extractor
 * from the URI extension by default. Downloaded files historically carry a
 * hardcoded `.mp4` extension regardless of the real container, so MKV/TS/AVI
 * bytes get fed to the MP4 extractor and ExoPlayer hangs silently in
 * `STATE_BUFFERING`. Attaching the correct MIME type via
 * [androidx.media3.common.MediaItem.Builder.setMimeType] forces the right
 * extractor and unblocks playback.
 *
 * Mirrors the [com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleFormatCatalog]
 * pattern: lowercase-normalizes input, returns `null` for unknown containers
 * so the caller can fall back to extension-based inference.
 */
internal object ContainerMimeMapper {
    fun mapToMime(container: String?): String? {
        if (container.isNullOrBlank()) return null
        return when (container.lowercase().trim()) {
            "mp4", "m4v", "m4a", "mov", "ismv", "isma" -> MimeTypes.APPLICATION_MP4
            "mkv", "webm", "mka" -> MimeTypes.APPLICATION_MATROSKA
            "ts", "m2ts", "mts", "tsa", "tsv" -> MimeTypes.VIDEO_MP2T
            "flac" -> MimeTypes.AUDIO_FLAC
            "mp3" -> MimeTypes.AUDIO_MPEG
            "aac", "adts" -> MimeTypes.AUDIO_AAC
            "ogg", "oga", "opus" -> MimeTypes.AUDIO_OGG
            "wav" -> MimeTypes.AUDIO_WAV
            "flv" -> MimeTypes.VIDEO_FLV
            // AVI: Media3 has no dedicated AVI MIME constant and no first-class
            // AVI extractor on all versions. Return null so ExoPlayer falls back
            // to content sniffing; if it still cannot decode, the buffering
            // watchdog surfaces the retry-with-engine dialog.
            else -> null
        }
    }
}
