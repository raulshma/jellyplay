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
    ThemeVariant.STANDARD -> null
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
