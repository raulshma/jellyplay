package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeVariant {
    STANDARD, SYNTHWAVE, SOOTHING, MONOCHROME
}

object ThemeVariantColors {
    val SYNTHWAVE_BACKGROUND = Color(0xFF0D061A)
    val SYNTHWAVE_BACKGROUND_END = Color(0xFF1B0B3A)
    val SYNTHWAVE_TINT = Color(0xFF160C2D).copy(alpha = 0.82f)
    val SYNTHWAVE_NAV_TINT = Color(0xFF160C2D).copy(alpha = 0.92f)
    val SYNTHWAVE_DETAIL_BG = Color(0xFF0C061A)

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
fun synthwaveBackgroundBrush(): Brush = Brush.verticalGradient(
    colors = listOf(ThemeVariantColors.SYNTHWAVE_BACKGROUND, ThemeVariantColors.SYNTHWAVE_BACKGROUND_END),
)

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

@Composable
fun ThemeVariant.cardBorder(
    primary: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    outline: Color = Color.Unspecified,
): BorderStroke? = when (this) {
    ThemeVariant.SYNTHWAVE -> BorderStroke(
        width = 1.5.dp,
        brush = Brush.linearGradient(colors = listOf(primary, secondary))
    )
    ThemeVariant.SOOTHING -> BorderStroke(
        width = 0.8.dp,
        color = outline.copy(alpha = 0.35f)
    )
    ThemeVariant.MONOCHROME -> BorderStroke(
        width = 1.dp,
        color = outline.copy(alpha = 0.45f)
    )
    ThemeVariant.STANDARD -> BorderStroke(
        width = 1.dp,
        color = outline.copy(alpha = 0.3f)
    )
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.containerTint(defaultTint: Color): Color = when (this) {
    ThemeVariant.SYNTHWAVE -> ThemeVariantColors.SYNTHWAVE_TINT
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
    ThemeVariant.STANDARD -> defaultTint
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.scaffoldContainerColor(defaultColor: Color): Color = when (this) {
    ThemeVariant.SYNTHWAVE -> Color.Transparent
    else -> defaultColor
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.shadowElevation(defaultElevation: Dp = 8.dp): Dp = when (this) {
    ThemeVariant.SOOTHING -> 4.dp
    ThemeVariant.SYNTHWAVE -> 0.dp
    else -> defaultElevation
}

@Composable
@ReadOnlyComposable
fun ThemeVariant.tonalElevation(defaultElevation: Dp = 4.dp): Dp = when (this) {
    ThemeVariant.SOOTHING -> 0.dp
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
    this == ThemeVariant.SOOTHING -> 1.5.dp
    else -> 4.dp
}
// NOTE: per-card drop shadows were removed from Home section cards for GPU
// performance; cards now rely on a 1px border (see cardBorder) instead. This
// helper is kept only for non-home callers that still want an elevation value.
