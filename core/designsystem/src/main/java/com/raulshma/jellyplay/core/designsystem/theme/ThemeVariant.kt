package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeVariant(val displayName: String) {
    STANDARD("Standard"),
    SYNTHWAVE("Synthwave"),
    SOOTHING("Soothing"),
    MONOCHROME("Monochrome"),
    VIVID("Vivid"),
    AURORA("Aurora"),
    SAKURA("Sakura"),
    VECTOR_POP("Vector Pop");

    companion object {
        /** Parses a persisted variant string ("standard", "vector_pop", …), defaulting to [STANDARD]. */
        fun fromId(id: String?): ThemeVariant =
            entries.find { it.name.lowercase() == id?.lowercase() } ?: STANDARD
    }

    /**
     * Variants whose palettes and gradient backgrounds only read against dark
     * tones. They force the dark theme, making the light/dark picker inert —
     * every "is this variant dark no matter what" check should read this
     * instead of re-listing the members.
     */
    val isDarkLocked: Boolean get() = this == SYNTHWAVE || this == AURORA

    /**
     * Whether the OLED pure-black surface treatment may apply on top of this
     * variant. Suppressed for the dark-locked gradients (their backgrounds
     * aren't pure black) and for Soothing (own tints) / Monochrome (already
     * implies OLED by itself).
     */
    val allowsOled: Boolean get() = this == STANDARD || this == VIVID || this == SAKURA || this == VECTOR_POP
}

/**
 * One selectable accent for a themed variant. Persisted as [id]; [lightColor]/
 * [darkColor] are swatch previews (and the seed roles the scheme builder uses).
 */
data class VariantAccent(
    val id: String,
    val label: String,
    val lightColor: Color,
    val darkColor: Color,
)

/**
 * The accent swatches selectable for this variant, or null when the variant has
 * no accent picker (Standard uses the global accent swatch; Monochrome is
 * fixed). Synthwave/Soothing keep their historical accent palettes; the four
 * new variants own theirs in their theme files.
 */
fun ThemeVariant.accentOptions(): List<VariantAccent>? = when (this) {
    ThemeVariant.SYNTHWAVE -> listOf(
        VariantAccent("magenta", "Magenta", Color(0xFFFF007F), Color(0xFFFF007F)),
        VariantAccent("cyan", "Cyan", Color(0xFF00F0FF), Color(0xFF00F0FF)),
        VariantAccent("violet", "Violet", Color(0xFF9D00FF), Color(0xFF9D00FF)),
        VariantAccent("orange", "Orange", Color(0xFFFF5E00), Color(0xFFFF5E00)),
    )
    ThemeVariant.SOOTHING -> listOf(
        VariantAccent("ocean", "Ocean", Color(0xFF1877F2), Color(0xFF6CACDE)),
        VariantAccent("lavender", "Lavender", Color(0xFF8B7FE8), Color(0xFFB4A7FF)),
        VariantAccent("sage", "Sage", Color(0xFF4CAF6E), Color(0xFF7ECFA0)),
        VariantAccent("coral", "Coral", Color(0xFFE85D5D), Color(0xFFFF8A80)),
        VariantAccent("amber", "Amber", Color(0xFFE8A43A), Color(0xFFFFD180)),
        VariantAccent("rose", "Rose", Color(0xFFE85A8A), Color(0xFFFF80AB)),
    )
    ThemeVariant.VIVID -> VividAccents
    ThemeVariant.AURORA -> AuroraAccents
    ThemeVariant.SAKURA -> SakuraAccents
    ThemeVariant.VECTOR_POP -> VectorPopAccents
    ThemeVariant.STANDARD, ThemeVariant.MONOCHROME -> null
}

object ThemeVariantColors {
    val SYNTHWAVE_BACKGROUND = Color(0xFF0D061A)
    val SYNTHWAVE_BACKGROUND_END = Color(0xFF1B0B3A)
    val SYNTHWAVE_TINT = Color(0xFF160C2D).copy(alpha = 0.82f)
    val SYNTHWAVE_NAV_TINT = Color(0xFF160C2D).copy(alpha = 0.92f)
    val SYNTHWAVE_DETAIL_BG = Color(0xFF0C061A)

    val AURORA_BACKGROUND = Color(0xFF040A18)
    val AURORA_BACKGROUND_END = Color(0xFF0A2A33)
    val AURORA_TINT = Color(0xFF0C1B2E).copy(alpha = 0.82f)
    val AURORA_NAV_TINT = Color(0xFF0C1B2E).copy(alpha = 0.92f)
    val AURORA_DETAIL_BG = Color(0xFF050D1C)

    val SOOTHING_DARK_TINT = Color(0xFF161B22).copy(alpha = 0.88f)
    val SOOTHING_LIGHT_TINT = Color(0xFFFFFFFF).copy(alpha = 0.88f)
}

/**
 * The vertical gradient used as the synthwave app/detail background. Exposed
 * here so every caller (JellyPlayApp, MediaDetailScreen, SeerrDetailScreen,
 * ...) paints the same gradient without re-typing the hex tokens. Callers
 * should gate this on `ThemeVariant.SYNTHWAVE` and fall back to the M3
 * background colour otherwise.
 */
private val SynthwaveBackgroundBrush = Brush.verticalGradient(
    colors = listOf(ThemeVariantColors.SYNTHWAVE_BACKGROUND, ThemeVariantColors.SYNTHWAVE_BACKGROUND_END),
)

fun synthwaveBackgroundBrush(): Brush = SynthwaveBackgroundBrush

/**
 * Aurora's soft accent-glow border — one definition shared by
 * [ThemeVariant.cardBorder] and the detail screens that hand-roll borders for
 * a subset of variants (those screens keep plain/no borders for the rest, so
 * they can't call [ThemeVariant.cardBorder] directly).
 */
fun auroraCardBorder(primary: Color): BorderStroke =
    BorderStroke(width = 1.dp, color = primary.copy(alpha = 0.25f))

/**
 * The vertical gradient used as the Aurora app/detail background — a calm
 * night-sky blend from deep indigo to teal. Pair with
 * [ThemeVariant.backgroundBrush] so callers don't special-case variants by
 * name.
 */
private val AuroraBackgroundBrush = Brush.verticalGradient(
    colors = listOf(ThemeVariantColors.AURORA_BACKGROUND, ThemeVariantColors.AURORA_BACKGROUND_END),
)

fun auroraBackgroundBrush(): Brush = AuroraBackgroundBrush

/**
 * The full-bleed background brush for gradient variants (Synthwave, Aurora),
 * or null when the variant paints a plain M3 background colour. Callers should
 * paint this when non-null and fall back to `colorScheme.background` otherwise.
 */
fun ThemeVariant.backgroundBrush(): Brush? = when (this) {
    ThemeVariant.SYNTHWAVE -> synthwaveBackgroundBrush()
    ThemeVariant.AURORA -> auroraBackgroundBrush()
    else -> null
}

/**
 * GitHub-style five-step activity palette for the watch-progress heatmap.
 * Index 0 is the empty/lowest cell; index 4 is the most-active cell. The dark
 * and light variants mirror GitHub's own dark/light heatmap colours so the
 * heatmap stays legible in either theme.
 */
object HeatmapPalette {
    val dark: Array<Color> = arrayOf(
        Color(0xFF161B22),
        Color(0xFF0E4429),
        Color(0xFF006D32),
        Color(0xFF26A641),
        Color(0xFF39D353),
    )
    val light: Array<Color> = arrayOf(
        Color(0xFFEBEDF0),
        Color(0xFF9BE9A8),
        Color(0xFF40C463),
        Color(0xFF30A14E),
        Color(0xFF216E39),
    )
}

fun ThemeVariant.cardBorder(
    primary: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    outline: Color = Color.Unspecified,
): BorderStroke? = when (this) {
    ThemeVariant.SYNTHWAVE -> BorderStroke(
        width = 1.5.dp,
        brush = Brush.linearGradient(colors = listOf(primary, secondary))
    )
    ThemeVariant.AURORA -> auroraCardBorder(primary)
    ThemeVariant.SOOTHING -> BorderStroke(
        width = 0.8.dp,
        color = outline.copy(alpha = 0.35f)
    )
    ThemeVariant.SAKURA -> BorderStroke(
        width = 0.8.dp,
        color = outline.copy(alpha = 0.4f)
    )
    ThemeVariant.MONOCHROME -> BorderStroke(
        width = 1.dp,
        color = outline.copy(alpha = 0.45f)
    )
    // Vector Pop's signature: a thick flat outline. The variant's scheme sets
    // `outline` to ink (light) / near-white (dark) so no dark-theme branch here.
    ThemeVariant.VECTOR_POP -> BorderStroke(
        width = 2.dp,
        color = outline
    )
    ThemeVariant.VIVID -> BorderStroke(
        width = 1.25.dp,
        color = primary.copy(alpha = 0.4f)
    )
    ThemeVariant.STANDARD -> BorderStroke(
        width = 1.dp,
        color = outline.copy(alpha = 0.3f)
    )
}

/**
 * [ThemeVariant.cardBorder] resolved against the current scheme colors and
 * memoized — the standard way a composable obtains the variant border without
 * rebuilding the [BorderStroke] on every recomposition.
 */
@Composable
fun rememberThemeCardBorder(themeVariant: ThemeVariant): BorderStroke? {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    return remember(themeVariant, primary, secondary, outline) {
        themeVariant.cardBorder(primary, secondary, outline)
    }
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.containerTint(defaultTint: Color): Color = when (this) {
    ThemeVariant.SYNTHWAVE -> ThemeVariantColors.SYNTHWAVE_TINT
    ThemeVariant.AURORA -> ThemeVariantColors.AURORA_TINT
    ThemeVariant.SOOTHING -> if (isSystemInDarkTheme()) {
        ThemeVariantColors.SOOTHING_DARK_TINT
    } else {
        ThemeVariantColors.SOOTHING_LIGHT_TINT
    }
    ThemeVariant.MONOCHROME -> if (isSystemInDarkTheme()) {
        Color.Black.copy(alpha = 0.95f)
    } else {
        Color.White.copy(alpha = 0.95f)
    }
    else -> defaultTint
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.scaffoldContainerColor(defaultColor: Color): Color = when (this) {
    ThemeVariant.SYNTHWAVE, ThemeVariant.AURORA -> Color.Transparent
    else -> defaultColor
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.shadowElevation(defaultElevation: Dp = 8.dp): Dp = when (this) {
    ThemeVariant.SOOTHING, ThemeVariant.SAKURA -> 4.dp
    ThemeVariant.SYNTHWAVE, ThemeVariant.AURORA, ThemeVariant.VECTOR_POP -> 0.dp
    else -> defaultElevation
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.tonalElevation(defaultElevation: Dp = 4.dp): Dp = when (this) {
    ThemeVariant.SOOTHING, ThemeVariant.VECTOR_POP -> 0.dp
    else -> defaultElevation
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.cardElevation(
    isPressed: Boolean = false,
    isTvFocused: Boolean = false,
    isTv: Boolean = false,
): Dp = when {
    isPressed -> 12.dp
    isTvFocused -> 16.dp
    isTv -> 12.dp
    this == ThemeVariant.SOOTHING || this == ThemeVariant.SAKURA -> 1.5.dp
    this == ThemeVariant.VECTOR_POP -> 0.dp
    else -> 4.dp
}
// NOTE: per-card drop shadows were removed from Home section cards for GPU
// performance; cards now rely on a 1px border (see cardBorder) instead. This
// helper is kept only for non-home callers that still want an elevation value.
