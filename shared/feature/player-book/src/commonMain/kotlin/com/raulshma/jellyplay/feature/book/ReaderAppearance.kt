package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import kotlin.math.roundToInt

/**
 * The theme + font size the reader should actually render with: each axis
 * comes from the per-book override when it carries one, else the global
 * [ReaderSlice] value. Pure — pinned by ReaderAppearanceTest (override /
 * global / cleared); the ViewModel derives its effective flows and routes
 * writes through it (see [BookReaderViewModel.setUsePerBookAppearance]).
 */
internal data class EffectiveAppearance(
    val theme: ReaderTheme,
    val fontSizePx: Int,
)

internal fun effectiveAppearance(global: ReaderSlice, perBook: PerBookAppearance?): EffectiveAppearance =
    EffectiveAppearance(
        theme = perBook?.theme ?: global.readerTheme,
        fontSizePx = perBook?.fontSizePx ?: global.readerFontSizePx,
    )

/**
 * [ReaderFontFamily] → the CSS stack reader.js applies via
 * `rendition.themes.font`. SYSTEM maps to `null`: the reader default, and a
 * live push of the empty stack must CLEAR whatever family was applied before
 * (see `pushAppearanceScripts`).
 */
internal fun ReaderFontFamily.epubCssStack(): String? = when (this) {
    ReaderFontFamily.SYSTEM -> null
    ReaderFontFamily.SERIF -> "Georgia, 'Times New Roman', serif"
    ReaderFontFamily.SANS -> "Verdana, Arial, sans-serif"
    ReaderFontFamily.MONO -> "'Courier New', monospace"
}

/**
 * Margin percent (0..100) → the px padding handed to reader.js. There is no
 * shared percent-of-viewport contract across the platform WebViews, so the
 * band maps linearly onto 0..[MAX_EPUB_MARGINS_PX] (64 px ≈ a comfortable
 * maximal gutter at phone page width) — the slider only needs to feel
 * proportional. Defaults: 8 % → 5 px, 100 % → 64 px.
 */
internal fun epubMarginsPx(marginPct: Int): Int =
    (marginPct.coerceIn(0, 100) / 100.0 * MAX_EPUB_MARGINS_PX).roundToInt()

internal const val MAX_EPUB_MARGINS_PX = 64

/** Line-height percent (100..200) → the multiplier reader.js applies (1.6× default). */
internal fun epubLineHeight(lineHeightPct: Int): Double =
    lineHeightPct.coerceIn(100, 200) / 100.0

/**
 * The dim-veil alpha for a brightness percent (0..100): 100 → fully clear,
 * 0 → [MAX_BRIGHTNESS_DIM] (75 % black). A veil never goes full black — the
 * page stays readable with the chrome at full brightness above it.
 */
internal fun brightnessDimAlpha(brightnessPct: Int): Float =
    (100 - brightnessPct.coerceIn(0, 100)) / 100f * MAX_BRIGHTNESS_DIM

internal const val MAX_BRIGHTNESS_DIM = 0.75f
