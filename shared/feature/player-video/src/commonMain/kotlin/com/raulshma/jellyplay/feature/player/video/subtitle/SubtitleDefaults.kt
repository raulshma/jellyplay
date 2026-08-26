package com.raulshma.jellyplay.feature.player.video.subtitle

/**
 * Shared, engine-agnostic subtitle rendering constants.
 *
 * These document the relationship between values that otherwise look like
 * unrelated magic numbers scattered across the engine and overlay code —
 * e.g. mpv's `sub-scale = fontSize / 24` and VLC's `:freetype-fontsize=24`
 * fallback both express "font size relative to a 24sp reference canvas."
 */
internal object SubtitleDefaults {
    /**
     * The reference subtitle font size (sp) every engine scales relative to.
     *
     * - mpv: `sub-font-size` is pinned to [MPV_LIBASS_REFERENCE_FONT_SIZE]
     *   (libass's 720p canvas default) and the user's size is applied as
     *   `sub-scale = fontSize / [REFERENCE_FONT_SIZE]`.
     * - VLC: used as the absolute `:freetype-fontsize` bundled-default fallback
     *   when `applyCustomStyle` is false.
     * - Compose overlay ([MpvSubtitleOverlay]): stroke width scales with
     *   `borderWidth * (fontSize / [REFERENCE_FONT_SIZE])` to match the mpv
     *   ratio, and the default fallback style uses this as its font size.
     */
    const val REFERENCE_FONT_SIZE: Int = 24

    /**
     * mpv's `sub-font-size` reference value — libass's default for a 720p
     * canvas. The user's size is applied multiplicatively via `sub-scale`
     * so libass layout matches across container sizes.
     */
    const val MPV_LIBASS_REFERENCE_FONT_SIZE: Int = 55
}
