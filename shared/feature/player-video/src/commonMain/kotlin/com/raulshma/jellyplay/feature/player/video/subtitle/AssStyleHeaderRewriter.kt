package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleRenderDefaults
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.math.roundToInt

/**
 * Rewrites the `Style: Default` line of an ASS/SSA header so libass renders the
 * user's [SubtitleStyle] on ExoPlayer.
 *
 * This exists because ass-media 0.5.0 binds no style-override JNI
 * (`ass_track_set_style` / `ass_set_style_overrides` are not reachable), so the
 * only non-fork seam is the header text itself: `AssHandler.createTrack` feeds
 * `Format.initializationData[1]` (embedded Matroska CodecPrivate) or the parsed
 * side-car file body straight into the track, and libass applies whatever the
 * `Style:` lines say. Rewriting the header before track creation is therefore
 * equivalent to mpv's `--ass-override=force`, with one caveat: it is
 * apply-at-load — the track (and its header) is built when the subtitle source
 * is parsed, so a style edit mid-playback takes effect on the next track load,
 * not live.
 *
 * Pure text-in/text-out (no Media3, no Android types) so it stays unit-testable
 * on the JVM. Total function: any malformed or unexpected header passes through
 * unchanged, and non-FORCE styles are never rewritten (SCALE mode keeps
 * today's `AssRender.setFontScale` behavior).
 *
 * Scope (the C3 spec asked about "font/color/position" overrides): this
 * rewrite deliberately covers font/colors/border/size but NOT authored ASS
 * positioning (alignment/margins) — rewriting those would break
 * signage/karaoke event positioning; the user's vertical position keeps
 * applying via the overlay host as before. Only the `Style: Default` line is
 * rewritten (first style line as fallback); authored sign/song styles keep
 * their design.
 */
internal object AssStyleHeaderRewriter {

    /**
     * True when the resolved style requests the full user-style override of
     * ASS tracks (the ExoPlayer analogue of mpv's `sub-ass-override=force`).
     * Mirrors the mpv engine's gating so the two engines agree on when FORCE
     * applies.
     */
    fun isForceOverride(style: SubtitleStyle): Boolean =
        style.applyCustomStyle && style.assOverride == AssOverrideMode.FORCE

    /**
     * Returns [header] with the target style line rewritten to [style]'s
     * colors, borders, typeface flags, and (scaled) font size. Returns [header]
     * unchanged when [style] does not request FORCE, or when the header has no
     * parseable styles section (total-function passthrough).
     *
     * Only the `Style: Default` line is rewritten (first style line when the
     * author named it differently); authored sign/song styles keep their
     * design, matching the SCALE mode's spirit of respecting the author.
     */
    fun rewrite(header: String, style: SubtitleStyle): String {
        if (!isForceOverride(style)) return header

        // A NUL-terminated CodecPrivate header (the jellyfin-ffmpeg#506 shape
        // AssHeaderParser fixes up downstream) keeps its terminator through the
        // rewrite, so the parser's trailing-NUL check sees the same shape with
        // or without the override applied.
        val nulTerminated = header.endsWith('\u0000')
        val body = if (nulTerminated) header.dropLast(1) else header

        // Normalize line endings to "\n" — libass accepts it, and
        // AssHeaderParser already rejoins with "\n" on the CUES path.
        val lines = body.split("\r\n", "\n", "\r").toMutableList()

        // Locate the styles section, its Format field list, and the style
        // lines. Field INDICES always come from the header's own Format line —
        // V4 (SSA) and V4+ (ASS) have different field orders/names, and some
        // authors reorder fields.
        var inStylesSection = false
        var formatFields: List<String>? = null
        val styleLines = mutableListOf<Pair<Int, String>>() // line index to style name
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val sectionMatch = SECTION_REGEX.matchEntire(trimmed)
            if (sectionMatch != null) {
                inStylesSection = sectionMatch.groupValues[1]
                    .contains("style", ignoreCase = true)
                continue
            }
            if (!inStylesSection) continue
            val lower = trimmed.lowercase()
            when {
                lower.startsWith(FORMAT_PREFIX) ->
                    formatFields = trimmed.substringAfter(':')
                        .split(',')
                        .map { it.trim().lowercase() }

                lower.startsWith(STYLE_PREFIX) && formatFields != null ->
                    styleLines += index to trimmed.substringAfter(':')
                        .substringBefore(',')
                        .trim()
            }
        }
        val fields = formatFields ?: return header
        val targetIndex = styleLines.firstOrNull { it.second.equals("default", ignoreCase = true) }?.first
            ?: styleLines.firstOrNull()?.first
            ?: return header

        val parts = lines[targetIndex].split(',')
        if (parts.size < fields.size) return header // truncated style line — passthrough

        val replacements = mutableMapOf<Int, String>()

        // Rewrites the first field matching any of [names] (case already
        // normalized). "outlinecolour" is V4+; SSA V4 keeps the edge color in
        // "tertiarycolour" — mapped to whichever the header declares.
        fun field(vararg names: String, transform: (current: String) -> String?) {
            val index = fields.indexOfFirst { it in names }
            if (index < 0 || index >= parts.size) return
            transform(parts[index])?.let { replacements[index] = it }
        }

        // User font family only when one is actually installed — otherwise the
        // author's font choice stands (libass falls back per its font config).
        field("fontname") {
            if (style.fontFamilyPath != null && style.fontFamilyName != null) {
                style.fontFamilyName
            } else {
                null
            }
        }
        // Scaled relative to the header's own PlayRes-relative size so
        // PlayResY semantics survive (same ratio SCALE mode feeds
        // setFontScale); never an absolute pixel size.
        field("fontsize") { current ->
            current.trim().toFloatOrNull()?.let { original ->
                (original * style.fontSize.toFloat() /
                    SubtitleRenderDefaults.REFERENCE_FONT_SIZE).roundToInt().toString()
            }
        }
        field("primarycolour") { argbToAssColor(style.fontColorArgb ?: style.fontColor.value) }
        field("outlinecolour", "tertiarycolour") {
            argbToAssColor(style.edgeColorArgb ?: style.edgeColor.value)
        }
        // BackColour carries the background box AND the drop-shadow color;
        // backgroundOpacity drives its ASS alpha (00 = opaque, FF = clear).
        field("backcolour") {
            argbToAssColor(
                style.backgroundColorArgb ?: style.backgroundColor.value,
                opacity = style.backgroundOpacity,
            )
        }
        field("bold") { if (style.bold) "-1" else "0" }
        field("italic") { if (style.italic) "-1" else "0" }
        // 1 = outline + shadow, 3 = opaque box, 4 = background box (libass).
        field("borderstyle") {
            when (style.borderStyle) {
                SubtitleBorderStyle.OUTLINE_AND_SHADOW -> "1"
                SubtitleBorderStyle.OPAQUE_BOX -> "3"
                SubtitleBorderStyle.BACKGROUND_BOX -> "4"
            }
        }
        field("outline") { style.borderWidth.toString() }
        field("shadow") { style.shadowOffset.toString() }

        if (replacements.isEmpty()) return header

        // parts[0] is "Style: <Name>" — kept verbatim (name spelling, prefix
        // spacing); only the mapped fields change.
        lines[targetIndex] = parts.mapIndexed { i, part -> replacements[i] ?: part }
            .joinToString(",")
        val rewritten = lines.joinToString("\n")
        return if (nulTerminated) "$rewritten\u0000" else rewritten
    }

    /**
     * ARGB int → ASS `&HAABBGGRR`: BGR byte order, and alpha INVERTED
     * (ASS 00 = opaque, FF = transparent). [opacity] replaces the ARGB alpha
     * the way mpv's mapping folds [SubtitleStyle.backgroundOpacity] into
     * back-color; text and edge colors pass 1f (fully opaque).
     */
    private fun argbToAssColor(argb: Int, opacity: Float = 1f): String {
        val alpha = 255 - (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        fun hex(value: Int) = value.toString(16).padStart(2, '0').uppercase()
        // ARGB byte order: A(24-31) R(16-23) G(8-15) B(0-7).
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        // ASS order: &H AA BB GG RR.
        return "&H" + hex(alpha) + hex(blue) + hex(green) + hex(red)
    }

    /** `[V4+ Styles]`, `[V4 Styles]`, `[v4+ styles]`, etc. */
    private val SECTION_REGEX = Regex("""\[(.+)]""")

    private const val FORMAT_PREFIX = "format:"
    private const val STYLE_PREFIX = "style:"
}
