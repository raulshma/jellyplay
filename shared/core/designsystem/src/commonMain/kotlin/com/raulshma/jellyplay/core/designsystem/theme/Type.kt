package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font families are a platform seam: Android resolves Google Fonts through the
 * GMS fonts provider (`FontFamilies.android.kt`); desktop loads the
 * bundled compose-resources statics (`FontFamilies.jvm.kt`)
 * so every target renders identical brand type.
 *
 * The resolution is a `@Composable` read because CMP 1.11.1 only publishes a
 * composable `Font(resource, weight, style)` loader for font resources (there
 * is no non-composable or single-shot suspend overload); that is why these
 * declarations — and the Typography values below that read them — are
 * composable properties rather than plain vals.
 */
internal expect val displayFontFamily: FontFamily
    @Composable get
internal expect val bodyFontFamily: FontFamily
    @Composable get
internal expect val synthwaveDisplayFontFamily: FontFamily
    @Composable get
internal expect val synthwaveBodyFontFamily: FontFamily
    @Composable get
internal expect val soothingFontFamily: FontFamily
    @Composable get
internal expect val monochromeDisplayFontFamily: FontFamily
    @Composable get
internal expect val monochromeBodyFontFamily: FontFamily
    @Composable get

// v0.10.6 variant families (Aurora/Sakura/VectorPop/Vivid). Android resolves
// the real Google Fonts faces; the desktop actuals map to the closest bundled
// faces (the four statics are not bundled — follow-up, see FontFamilies.jvm.kt).
internal expect val auroraFontFamily: FontFamily
    @Composable get
internal expect val sakuraFontFamily: FontFamily
    @Composable get
internal expect val vectorPopFontFamily: FontFamily
    @Composable get
internal expect val vividFontFamily: FontFamily
    @Composable get



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
val JellyPlayTypography: Typography
    @Composable get() = Typography(
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
val JellyPlayExpressiveTitles: Typography
    @Composable get() = Typography(
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

/**
 * Shared scaffold for the seven variant typographies: the per-role family
 * substitution across all 15 MD3 slots lives here once — display/headline/
 * title take [display], body/label take [body] — and [tweak] lets each
 * variant declare only the slots it actually deviates on, applied after the
 * family substitution as a [Typography.copy]. Slots the tweak doesn't name
 * keep the family-substituted base style verbatim.
 *
 * [base] is an explicit parameter (not a defaulted [JellyPlayTypography]
 * read) so the builder stays a pure function the commonTest suite can pin;
 * the composable family reads happen at the variant declarations.
 */
internal fun variantTypography(
    base: Typography,
    display: FontFamily,
    body: FontFamily = display,
    tweak: Typography.() -> Typography = { this },
): Typography {
    val withFamilies = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = base.headlineSmall.copy(fontFamily = display),
        titleLarge = base.titleLarge.copy(fontFamily = display),
        titleMedium = base.titleMedium.copy(fontFamily = display),
        titleSmall = base.titleSmall.copy(fontFamily = display),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
    return withFamilies.tweak()
}

val SynthwaveTypography: Typography
    @Composable get() = variantTypography(
        JellyPlayTypography,
        display = synthwaveDisplayFontFamily,
        body = synthwaveBodyFontFamily,
    )

// The old hand-copied block also restated base weights on six more slots
// (headlineLarge/headlineSmall/titleLarge/titleMedium/labelLarge/labelSmall);
// those were no-ops against JellyPlayTypography's defaults, so only the real
// deviations are declared here.
val SoothingTypography: Typography
    @Composable get() = variantTypography(JellyPlayTypography, soothingFontFamily) {
        copy(
            displayLarge = displayLarge.copy(letterSpacing = (-0.01).em),
            displayMedium = displayMedium.copy(letterSpacing = (-0.01).em),
            displaySmall = displaySmall.copy(letterSpacing = (-0.01).em),
            headlineLarge = headlineLarge.copy(letterSpacing = (-0.005).em),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em),
            headlineSmall = headlineSmall.copy(letterSpacing = (-0.005).em),
            titleLarge = titleLarge.copy(letterSpacing = 0.em),
            titleMedium = titleMedium.copy(letterSpacing = 0.em),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
            bodyLarge = bodyLarge.copy(letterSpacing = 0.1.sp),
            bodyMedium = bodyMedium.copy(letterSpacing = 0.1.sp),
            bodySmall = bodySmall.copy(letterSpacing = 0.1.sp),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }

val MonochromeTypography: Typography
    @Composable get() = variantTypography(
        JellyPlayTypography,
        display = monochromeDisplayFontFamily,
        body = monochromeBodyFontFamily,
    )
