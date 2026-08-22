package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font families are a platform seam: Android resolves Google Fonts through the
 * GMS fonts provider (`FontFamilies.android.kt`); desktop/web fall back to
 * system families until the fonts ship as bundled composeResources (follow-up,
 * identical typography on every target).
 */
internal expect val displayFontFamily: FontFamily
internal expect val bodyFontFamily: FontFamily
internal expect val synthwaveDisplayFontFamily: FontFamily
internal expect val synthwaveBodyFontFamily: FontFamily
internal expect val soothingFontFamily: FontFamily
internal expect val monochromeDisplayFontFamily: FontFamily
internal expect val monochromeBodyFontFamily: FontFamily



/**
 * Expressive Material Design 3 Typography for JellyPlay.
 *
 * Key expressive changes from standard MD3:
 * - Larger display sizes with tighter line height for impact
 * - Bolder weights for display/headline (SemiBold → Bold)
 * - Negative letter spacing for large text (crisper look)
 * - Wider letter spacing for body text (better readability)
 * - [textGeometricTransform] on display styles for a wider, expressive feel
 */
val JellyPlayTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.08f),
        platformStyle = noFontPaddingStyle,
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.05f),
        platformStyle = noFontPaddingStyle,
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
        platformStyle = noFontPaddingStyle,
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Expressive title typography for special screens (onboarding, splash, etc.).
 * Uses oversized display text with geometric transforms for maximum impact.
 */
val JellyPlayExpressiveTitles = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 0.95.em,
        letterSpacing = (-0.02).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.15f),
        platformStyle = noFontPaddingStyle,
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 0.95.em,
        letterSpacing = (-0.02).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.1f),
        platformStyle = noFontPaddingStyle,
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 1.0.em,
        letterSpacing = (-0.01).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.05f),
        platformStyle = noFontPaddingStyle,
    ),
)

val SynthwaveTypography = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = synthwaveDisplayFontFamily),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = synthwaveDisplayFontFamily),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = synthwaveDisplayFontFamily),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = synthwaveDisplayFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = synthwaveDisplayFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = synthwaveDisplayFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = synthwaveDisplayFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = synthwaveDisplayFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = synthwaveDisplayFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = synthwaveBodyFontFamily),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = synthwaveBodyFontFamily),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = synthwaveBodyFontFamily),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = synthwaveBodyFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = synthwaveBodyFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = synthwaveBodyFontFamily),
)

val SoothingTypography = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = soothingFontFamily, letterSpacing = (-0.01).em),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = soothingFontFamily, letterSpacing = (-0.01).em),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = soothingFontFamily, letterSpacing = (-0.01).em),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.005).em),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.em),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = soothingFontFamily, letterSpacing = 0.1.sp),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = soothingFontFamily, letterSpacing = 0.1.sp),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = soothingFontFamily, letterSpacing = 0.1.sp),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = soothingFontFamily, fontWeight = FontWeight.Medium),
)

val MonochromeTypography = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = monochromeDisplayFontFamily),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = monochromeDisplayFontFamily),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = monochromeDisplayFontFamily),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = monochromeDisplayFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = monochromeDisplayFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = monochromeDisplayFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = monochromeDisplayFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = monochromeDisplayFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = monochromeDisplayFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = monochromeBodyFontFamily),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = monochromeBodyFontFamily),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = monochromeBodyFontFamily),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = monochromeBodyFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = monochromeBodyFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = monochromeBodyFontFamily),
)
