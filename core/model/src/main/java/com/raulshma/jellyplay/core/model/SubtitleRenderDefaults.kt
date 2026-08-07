package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The canonical "effective subtitle defaults" — a single immutable record of the
 * rendering defaults every engine / the Compose overlay / the onboarding preview
 * should consume, independent of any engine's native format.
 *
 * These values match [SubtitleStyle]'s own model defaults, making "defaults" mean
 * one thing. Where an engine historically diverged (mpv's libass border = 3.0 /
 * shadow = 0.0; ExoPlayer's 18sp + DROP_SHADOW), the engine keeps a **documented
 * override** — never a silent inline literal — so the divergence is a conscious
 * choice, not drift.
 *
 * Promoted to core/model (from feature/player/video's `SubtitleDefaults`, which
 * only held two size constants) so [ResolvedSubtitleStyle] and the Compose
 * `toCompose()` mapping in core/ui can live without depending on the feature
 * module. The feature module's `SubtitleDefaults` remains the source of the
 * mpv-specific [MPV_LIBASS_REFERENCE_FONT_SIZE] (a libass canvas concern).
 */
@Immutable
data class SubtitleRenderDefaults(
    val fontSizeSp: Int = REFERENCE_FONT_SIZE,   // 24
    val fontColor: Int = SubtitleColor.WHITE.value,
    val backgroundColor: Int = SubtitleColor.BLACK.value,
    val backgroundAlpha: Float = 0f,
    val edgeColor: Int = SubtitleColor.BLACK.value,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.OUTLINE,
    val borderWidth: Float = 2.0f,
    val shadowOffset: Float = 1.0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
) {
    companion object {
        /** The reference subtitle font size (sp) every engine scales relative to. */
        const val REFERENCE_FONT_SIZE: Int = 24

        /** Canonical defaults matching [SubtitleStyle]'s zero-arg constructor. */
        val DEFAULT: SubtitleRenderDefaults = SubtitleRenderDefaults()

        /**
         * ExoPlayer's **documented** divergence from [DEFAULT]: uncustomized
         * captions render at 18sp + DROP_SHADOW (vs the 24sp + OUTLINE every other
         * path uses). This is intentional — Media3's embedded-style path sizes
         * captions against the view height, and the smaller size keeps them stable
         * across orientation changes. Promoted from a silent inline literal in
         * `ExoPlayerEngine.applySubtitleStyleToView` to a named override so the
         * divergence is a conscious, discoverable choice rather than drift.
         *
         * To align ExoPlayer with the other engines, repoint the engine's default
         * branch at [DEFAULT] instead.
         */
        val EXOPLAYER_OVERRIDE: SubtitleRenderDefaults = SubtitleRenderDefaults(
            fontSizeSp = 18,
            edgeType = SubtitleEdgeType.DROP_SHADOW,
        )
    }
}

/**
 * Engine-neutral resolved subtitle magnitudes: ARGB ints + plain floats, no mpv
 * property strings, no Compose `Color`. Engines map from this record to their
 * native format; the Compose overlay and the onboarding preview map from this
 * record to Compose `Color`/`TextStyle` via a `toCompose()` extension in core/ui.
 *
 * Generalizes feature/player/video's mpv-named `ResolvedMpvStyle` — same shape,
 * no longer named after one engine.
 */
@Immutable
data class ResolvedSubtitleStyle(
    val fontColorArgb: Int,
    val backgroundColorArgb: Int,
    val backgroundAlpha: Float,
    val edgeColorArgb: Int,
    val edgeType: SubtitleEdgeType,
    val borderWidth: Float,
    val shadowOffset: Float,
    val fontSizeSp: Int,
    val bold: Boolean,
    val italic: Boolean,
    val verticalPosition: Float,
    val offsetMs: Long,
)

/**
 * Resolve a [SubtitleStyle] against [defaults] into an engine-neutral record.
 *
 * - When [SubtitleStyle.applyCustomStyle] is false, the [defaults] table wins
 *   (only `verticalPosition` / `offsetMs` carry over, since they're layout not
 *   styling).
 * - When true, the user's free-form ARGB fields win over the enum colors
 *   (`fontColorArgb ?: fontColor.value`), folding in what
 *   feature/player/video's `SubtitleColorResolver` used to do module-locally.
 *
 * This is the single resolution entry point; engines and the Compose overlay
 * read from the returned [ResolvedSubtitleStyle] instead of re-deriving.
 */
fun SubtitleStyle.resolveAgainst(
    defaults: SubtitleRenderDefaults = SubtitleRenderDefaults.DEFAULT,
): ResolvedSubtitleStyle =
    if (!applyCustomStyle) {
        ResolvedSubtitleStyle(
            fontColorArgb = defaults.fontColor,
            backgroundColorArgb = defaults.backgroundColor,
            backgroundAlpha = defaults.backgroundAlpha,
            edgeColorArgb = defaults.edgeColor,
            edgeType = defaults.edgeType,
            borderWidth = defaults.borderWidth,
            shadowOffset = defaults.shadowOffset,
            fontSizeSp = defaults.fontSizeSp,
            bold = defaults.bold,
            italic = defaults.italic,
            verticalPosition = verticalPosition,
            offsetMs = offsetMs,
        )
    } else {
        ResolvedSubtitleStyle(
            fontColorArgb = fontColorArgb ?: fontColor.value,
            backgroundColorArgb = backgroundColorArgb ?: backgroundColor.value,
            backgroundAlpha = backgroundOpacity,
            edgeColorArgb = edgeColorArgb ?: edgeColor.value,
            edgeType = edgeType,
            borderWidth = borderWidth,
            shadowOffset = shadowOffset,
            fontSizeSp = fontSize,
            bold = bold,
            italic = italic,
            verticalPosition = verticalPosition,
            offsetMs = offsetMs,
        )
    }
