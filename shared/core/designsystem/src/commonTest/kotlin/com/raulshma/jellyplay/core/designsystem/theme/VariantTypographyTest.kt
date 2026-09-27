package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [variantTypography], the shared scaffold behind the seven variant
 * typographies (Synthwave/Soothing/Monochrome in Type.kt; Aurora/Vivid/
 * Sakura/VectorPop in their theme files):
 *
 *  - family substitution is per role — display/headline/title take the
 *    display family, body/label take the body family, which defaults to the
 *    display family (the single-family variants);
 *  - every other TextStyle attribute passes through the base untouched;
 *  - the tweak runs after family substitution and only the slots it names
 *    deviate from the family-substituted defaults — the same expression shape
 *    the variant declarations use, including Soothing's absolute em/sp
 *    tracking replacements, weight overrides, and VectorPop's cleared
 *    geometric transform.
 *
 * The variant vals themselves are @Composable (font family resolution reads
 * resources — see Type.kt's header), so this pins the pure builder against a
 * synthetic base; the variant declarations are thin re-applications of it.
 */
class VariantTypographyTest {

    // A distinct size per slot (60..11 sp) so a cross-slot mixup inside the
    // builder cannot mask as correct output.
    private val base = Typography(
        displayLarge = TextStyle(fontSize = 60.sp),
        displayMedium = TextStyle(fontSize = 48.sp),
        displaySmall = TextStyle(fontSize = 40.sp),
        headlineLarge = TextStyle(fontSize = 34.sp),
        headlineMedium = TextStyle(fontSize = 30.sp),
        headlineSmall = TextStyle(fontSize = 26.sp),
        titleLarge = TextStyle(fontSize = 24.sp),
        titleMedium = TextStyle(fontSize = 18.sp),
        titleSmall = TextStyle(fontSize = 15.sp),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 14.sp),
        bodySmall = TextStyle(fontSize = 12.sp),
        labelLarge = TextStyle(fontSize = 13.sp),
        labelMedium = TextStyle(fontSize = 12.5.sp),
        labelSmall = TextStyle(fontSize = 11.sp),
    )

    // ── role substitution ────────────────────────────────────────────────────

    @Test
    fun `display family lands on display headline title and body family on body label`() {
        val result = variantTypography(base, FontFamily.Serif, FontFamily.Monospace)
        assertEquals(base.displayLarge.copy(fontFamily = FontFamily.Serif), result.displayLarge)
        assertEquals(base.displayMedium.copy(fontFamily = FontFamily.Serif), result.displayMedium)
        assertEquals(base.displaySmall.copy(fontFamily = FontFamily.Serif), result.displaySmall)
        assertEquals(base.headlineLarge.copy(fontFamily = FontFamily.Serif), result.headlineLarge)
        assertEquals(base.headlineMedium.copy(fontFamily = FontFamily.Serif), result.headlineMedium)
        assertEquals(base.headlineSmall.copy(fontFamily = FontFamily.Serif), result.headlineSmall)
        assertEquals(base.titleLarge.copy(fontFamily = FontFamily.Serif), result.titleLarge)
        assertEquals(base.titleMedium.copy(fontFamily = FontFamily.Serif), result.titleMedium)
        assertEquals(base.titleSmall.copy(fontFamily = FontFamily.Serif), result.titleSmall)
        assertEquals(base.bodyLarge.copy(fontFamily = FontFamily.Monospace), result.bodyLarge)
        assertEquals(base.bodyMedium.copy(fontFamily = FontFamily.Monospace), result.bodyMedium)
        assertEquals(base.bodySmall.copy(fontFamily = FontFamily.Monospace), result.bodySmall)
        assertEquals(base.labelLarge.copy(fontFamily = FontFamily.Monospace), result.labelLarge)
        assertEquals(base.labelMedium.copy(fontFamily = FontFamily.Monospace), result.labelMedium)
        assertEquals(base.labelSmall.copy(fontFamily = FontFamily.Monospace), result.labelSmall)
    }

    @Test
    fun `body family defaults to the display family`() {
        val result = variantTypography(base, FontFamily.Serif)
        assertEquals(base.displayLarge.copy(fontFamily = FontFamily.Serif), result.displayLarge)
        assertEquals(base.bodyLarge.copy(fontFamily = FontFamily.Serif), result.bodyLarge)
        assertEquals(base.labelSmall.copy(fontFamily = FontFamily.Serif), result.labelSmall)
    }

    // ── tweak seam ───────────────────────────────────────────────────────────

    @Test
    fun `tweak runs after substitution and only names its slots`() {
        // Soothing-shaped: absolute em/sp tracking replacements plus weight
        // overrides on top of the family-substituted defaults.
        val result = variantTypography(base, FontFamily.Serif) {
            copy(
                displayLarge = displayLarge.copy(letterSpacing = (-0.01).em),
                headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em),
                bodySmall = bodySmall.copy(letterSpacing = 0.1.sp),
                labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        assertEquals(
            base.displayLarge.copy(fontFamily = FontFamily.Serif, letterSpacing = (-0.01).em),
            result.displayLarge,
        )
        assertEquals(
            base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em),
            result.headlineMedium,
        )
        assertEquals(
            base.bodySmall.copy(fontFamily = FontFamily.Serif, letterSpacing = 0.1.sp),
            result.bodySmall,
        )
        assertEquals(
            base.labelMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            result.labelMedium,
        )
        // Untweaked slots keep the plain family-substituted style.
        assertEquals(base.displayMedium.copy(fontFamily = FontFamily.Serif), result.displayMedium)
        assertEquals(base.titleSmall.copy(fontFamily = FontFamily.Serif), result.titleSmall)
        assertEquals(base.labelLarge.copy(fontFamily = FontFamily.Serif), result.labelLarge)
    }

    @Test
    fun `tweak can clear base attributes vectorpop style`() {
        val withTransform = base.copy(
            displayLarge = base.displayLarge.copy(textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)),
            displayMedium = base.displayMedium.copy(textGeometricTransform = TextGeometricTransform(scaleX = 1.05f)),
        )
        val result = variantTypography(withTransform, FontFamily.Serif) {
            copy(
                displayLarge = displayLarge.copy(textGeometricTransform = null),
                displayMedium = displayMedium.copy(textGeometricTransform = null),
            )
        }
        assertNull(result.displayLarge.textGeometricTransform)
        assertNull(result.displayMedium.textGeometricTransform)
        assertEquals(FontFamily.Serif, result.displayLarge.fontFamily)
        assertEquals(base.displaySmall.fontSize, result.displaySmall.fontSize)
    }
}
