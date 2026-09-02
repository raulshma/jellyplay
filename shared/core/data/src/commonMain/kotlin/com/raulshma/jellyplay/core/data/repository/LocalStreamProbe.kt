package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType

/**
 * Probes a downloaded media file's container and returns the actual audio/video
 * [MediaStream] inventory — the same ground truth ExoPlayer derives at playback
 * (`ExoPlayerEngine.buildTracks`), but read metadata-only via a platform
 * extractor (no decoder init) so the detail screen can render quality/audio
 * badges.
 *
 * Why a probe instead of persisted server metadata: a transcoded download
 * (`maxBitrate != null`) bakes a single audio track and a lower resolution, so
 * the server's `MediaStream` list no longer matches the file. Only the file
 * itself is authoritative. See `TrackSelectionHelper` for the player-side path.
 *
 * Returns `emptyList()` on any failure (missing/corrupt file, I/O error) so the
 * detail screen degrades to "no badges" — the pre-feature behaviour — rather
 * than crashing. Subtitle tracks are intentionally skipped: the detail screen
 * surfaces local subtitles through the manifest-backed `LocalSubtitlePicker`.
 */
fun interface LocalStreamProbe {
    suspend fun probe(videoFilePath: String): List<MediaStream>
}

/**
 * Resolves the Dolby-Vision title and/or HDR range label for a video track.
 * `null` for both means SDR (the badge's default). Dolby Vision is detected by
 * codec name; HDR10 otherwise by the presence of static HDR metadata
 * (`MediaFormat.KEY_HDR_STATIC_INFO`, API 24+ — HLG carries none, so HLG falls
 * back to SDR, an acceptable miss for a compact badge). Extracted as a pure
 * function so the mapping is unit-testable without the Android framework.
 *
 * C4 part 2 note: `internal` in the legacy `:core:data`; promoted to `public`
 * because the legacy `LocalStreamProbeMappingTest` (still in `:core:data`
 * `src/test`) references it — `internal` is module-scoped and would not be
 * visible across the module boundary.
 */
fun resolveVideoRange(codec: String, hasHdrStaticInfo: Boolean): Pair<String?, String?> {
    if (codec.contains("dolby-vision", ignoreCase = true) ||
        codec.contains("dovi", ignoreCase = true)
    ) {
        return "Dolby Vision" to null
    }
    return null to if (hasHdrStaticInfo) "HDR" else null
}

/**
 * Pure mapping from a single probed track's primitive fields to a domain
 * [MediaStream], or `null` when the mime is not a VIDEO/AUDIO stream (e.g.
 * subtitle/data) or is missing. The video-range labels are pre-resolved by
 * the caller (via [resolveVideoRange]); this function only classifies the type
 * and assembles the model. Extracted out of the platform `MediaFormat` reader
 * so it is directly unit-testable without the Android framework.
 *
 * C4 part 2 note: `internal` → `public`, same reason as [resolveVideoRange].
 */
fun mediaStreamFromProbe(
    index: Int,
    mime: String,
    height: Int?,
    width: Int?,
    channels: Int?,
    sampleRate: Int?,
    language: String?,
    videoDoViTitle: String?,
    videoRangeType: String?,
): MediaStream? = when {
    mime.startsWith("video/") -> {
        val codec = mime.substringAfter('/')
        MediaStream(
            index = index,
            type = StreamType.VIDEO,
            codec = codec,
            width = width,
            height = height,
            videoDoViTitle = videoDoViTitle,
            videoRangeType = videoRangeType,
        )
    }
    mime.startsWith("audio/") -> {
        val codec = mime.substringAfter('/')
        MediaStream(
            index = index,
            type = StreamType.AUDIO,
            codec = codec,
            // "und" is the container's "undetermined" sentinel, not a real language.
            language = language?.takeIf { it != "und" },
            channels = channels,
            sampleRate = sampleRate,
        )
    }
    else -> null // subtitle / data / application — handled elsewhere or irrelevant.
}
