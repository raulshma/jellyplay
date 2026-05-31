package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.R

internal val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

internal val bodyFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Roboto Flex"),
        fontProvider = fontProvider,
    ),
)

internal val displayFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Space Grotesk"),
        fontProvider = fontProvider,
    ),
)

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
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.05f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
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
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 0.95.em,
        letterSpacing = (-0.02).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.1f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 1.0.em,
        letterSpacing = (-0.01).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.05f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
)
