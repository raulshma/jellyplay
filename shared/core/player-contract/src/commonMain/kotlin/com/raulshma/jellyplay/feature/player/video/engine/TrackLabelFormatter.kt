package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Small inline badge rendered next to a track label in the picker. Surfaced to
 * the UI via [MediaTrack.badges] / [TrackOption.badges].
 */
@Immutable
enum class TrackBadge {
    FORCED,
    DEFAULT,
    /** Hearing-impaired / SDH caption track. */
    SDH,
}

/**
 * Normalized inputs for track label + badge construction, independent of engine.
 * Every engine (ExoPlayer, mpv, libVLC) and the server stream path funnels its
 * raw fields through here so the picker shows identical formatting everywhere.
 *
 *  - [title]     embedded track title (e.g. "Signs & Songs"), not the language.
 *  - [language]  ISO code as exposed by the engine/server ("eng", "en", "en-US").
 *  - [codec]     raw mime/codec string (e.g. "text/x-ssa", "audio/mp4a-latm",
 *                "subrip"); [TrackLabelFormatter.mimeToCodec] converts it.
 */
@Immutable
data class TrackLabelInfo(
    val title: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val isForced: Boolean = false,
    val isDefault: Boolean = false,
    val isHearingImpaired: Boolean = false,
)

/**
 * Single source of truth for track display labels + badges across all engines
 * and the Jellyfin server stream path.
 *
 * The primary label format is `Title - Language - Codec - Channels` (joined by
 * `" - "`, matching the Jellyfin server's `displayTitle` convention) so that
 * existing label-based deduplication and selection-resolution in
 * [com.raulshma.jellyplay.feature.player.video.TrackSelectionHelper] keeps
 * working unchanged.
 */
object TrackLabelFormatter {

    /**
     * @return the human-readable single-line label. Falls back to "Unknown" when
     *         every component is missing.
     *
     * Parts are de-duplicated case-insensitively so a track whose title is the
     * bare language (e.g. `"English"`) plus a language code (`eng`) doesn't
     * render as `English - English`. Track titles that merely prefix the
     * language with a demuxer index (`"1 - English"`, `"2. Spanish"`) are
     * normalised back to the language so they dedup cleanly.
     */
    fun primary(info: TrackLabelInfo): String {
        val cleanTitle = info.title
            ?.trim()
            ?.replace(LEADING_INDEX, "")
            ?.replace(BADGE_MARKER, "")
            ?.replace(WHITESPACE, " ")
            ?.trim()
        val parts = buildList {
            cleanTitle?.takeIf { it.isNotBlank() }?.let(::add)
            info.language?.takeIf { it.isNotBlank() }?.let { displayLanguage(it) }?.let(::add)
            info.codec?.takeIf { it.isNotBlank() }?.let { mimeToCodec(it) }?.let(::add)
            info.channels?.takeIf { it > 0 }?.let { channelLabel(it) }?.let(::add)
        }
        // Drop parts that repeat an earlier part (case-insensitive). This also
        // collapses "1 - English" + lang once the index is stripped above.
        val seen = mutableSetOf<String>()
        val deduped = parts.filter { seen.add(it.trim().lowercase()) }
        return deduped.joinToString(" - ").ifBlank { "Unknown" }
    }

    /**
     * @return the ordered badges to render, or empty when none apply. Forced and
     *         SDH are mutually meaningful and both may show; Default is only
     *         surfaced when the track isn't also Forced/SDH (avoids noise).
     *
     * When the engine/server doesn't expose explicit flags (common for
     * side-loaded and container subs), the title text is inspected for the usual
     * markers (`forced`, `SDH`, `CC`, `HI`, `hearing`) so badges stay consistent
     * across sources instead of appearing on only some tracks.
     */
    fun badges(info: TrackLabelInfo): List<TrackBadge> {
        val title = info.title.orEmpty()
        val isForced = info.isForced || FORCED_MARKER.containsMatchIn(title)
        val isSdh = info.isHearingImpaired || SDH_MARKER.containsMatchIn(title)
        return buildList {
            if (isForced) add(TrackBadge.FORCED)
            if (isSdh) add(TrackBadge.SDH)
            if (info.isDefault && !isForced && !isSdh) add(TrackBadge.DEFAULT)
        }
    }

    /**
     * Maps a Media3 sample mime, Matroska/MPEG codec string, or side-loaded
     * codec name to the Jellyfin server-style codec string (e.g. `text/x-ssa`,
     * `subrip`, `aac`). Returns `null` for mimes that carry no useful format
     * info — most importantly Media3's synthetic renderer-output mime
     * `application/x-media3-cues`, which must never reach the UI. In that case
     * the caller should rely on server-stream enrichment to recover the codec.
     */
    fun mimeToCodec(mime: String?): String? {
        if (mime.isNullOrBlank()) return null
        val key = mime.trim().lowercase(Locale.ROOT)
        return when {
            // Media3 internal renderer-output mime — not a real container codec.
            key == "application/x-media3-cues" ||
                key == "application/x-media3-cues-text" -> null

            // Explicit subtitle mappings first (ASS normalizes to SSA, the same
            // family the Jellyfin server reports); then the generic text/x-
            // passthrough below only catches unmapped Jellyfin-style mimes.
            key == "application/x-subrip" || key == "application/mpsub" -> "subrip"
            key == "text/x-ssa" || key == "text/x-ass" -> "text/x-ssa"
            key == "text/vtt" || key == "application/x-webvtt" -> "vtt"
            key == "application/pgs" || key == "application/x-pgs" -> "hdmv_pgs_subtitle"
            key == "application/tx3g" -> "mov_text"
            key == "application/ttml+xml" || key == "application/x-ttml+xml" -> "ttml"

            // Already a Jellyfin-style subtitle mime with no explicit map — keep verbatim.
            key.startsWith("text/x-") -> mime

            // Audio.
            key == "audio/mp4a-latm" || key == "audio/mp4a" -> "aac"
            key == "audio/e-ac-3" || key == "audio/mha1" -> "eac3"
            key == "audio/ac-3" -> "ac3"
            key == "audio/true-hd" -> "truehd"
            key == "audio/vnd.dts" || key == "audio/dts" -> "dts"
            key == "audio/vnd.dts.hd" || key == "audio/dtshd" -> "dtshd"
            key == "audio/mpeg" -> "mp3"
            key == "audio/opus" -> "opus"
            key == "audio/flac" -> "flac"
            key == "audio/mp4" || key == "audio/3gpp" -> "aac"
            key == "audio/pcm" || key == "audio/raw" -> "pcm"

            // Video (rarely surfaced, but keep symmetric).
            key == "video/hevc" -> "hevc"
            key == "video/av01" || key == "video/av1" -> "av1"
            key == "video/avc" || key == "video/h264" -> "h264"
            key == "video/vp9" -> "vp9"
            key == "video/mpeg" -> "mpeg"

            // Matroska / FFmpeg codec strings passed through verbatim.
            key in RAW_CODEC_PASSTHROUGH -> mime

            // Strip any "audio/" / "video/" / "application/" prefix and keep the tail.
            key.startsWith("audio/") || key.startsWith("video/") ||
                key.startsWith("application/") -> mime.substringAfter('/')
            else -> mime
        }
    }

    private fun displayLanguage(lang: String): String? = try {
        Locale.forLanguageTag(lang.replace('_', '-')).displayLanguage
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        // Two/three-letter codes that aren't a valid BCP-47 tag.
        lang.takeIf { it.isNotBlank() }
    }

    private fun channelLabel(channels: Int): String? = when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }

    private val RAW_CODEC_PASSTHROUGH = setOf(
        "aac", "eac3", "ac3", "truehd", "dts", "dtshd", "mp3", "opus", "flac", "pcm",
        "subrip", "ass", "ssa", "hdmv_pgs_subtitle", "mov_text", "dvd_subtitle",
        "hevc", "av1", "h264", "vp9", "mpeg",
    )

    /** Strips a leading demuxer index from a track title: `"1 - English"`, `"2. Spanish"`. */
    private val LEADING_INDEX = Regex("""^\s*\d+\s*[-.:)\]]+\s*""")

    /**
     * Strips bracketed or dash-separated forced/SDH markers from the displayed
     * title so they render as badges instead of duplicated text — e.g.
     * `"(SDH)"`, `" - Forced"`, `"[CC]"`. Bare marker words (e.g. a title that
     * literally is `"Forced Narrative"`) are left intact to avoid mangling real
     * titles; those are still surfaced as badges via [FORCED_MARKER] /
     * [SDH_MARKER] for badge detection only.
     */
    private val BADGE_MARKER = Regex(
        """(?i)(\s*[-]\s*(forced|sdh|cc|hi|hearing(?:[- ]?impaired)?)\s*)|(\s*[(\[{]\s*(forced|sdh|cc|hi|hearing(?:[- ]?impaired)?)\s*[)\]}]\s*)"""
    )

    /** Collapses runs of whitespace left after stripping markers. */
    private val WHITESPACE = Regex("""\s+""")

    /** Title markers that imply a forced-narrative subtitle, case-insensitive. */
    private val FORCED_MARKER = Regex("""(?i)\bforced\b""")

    /**
     * Title markers that imply an SDH / hearing-impaired track. `sdh` and
     * `hearing(-impaired)` are matched as words; bare `cc`/`hi` are only
     * recognised when bracketed (`(CC)`, `[HI]`) to avoid false hits in titles
     * like "Chapter" or "This is Spinal Tap".
     */
    private val SDH_MARKER = Regex(
        """(?i)(\bsdh\b|hearing(?:[- ]?impaired)?|\(cc\)|\[cc\]|\(hi\)|\[hi\])"""
    )
}
