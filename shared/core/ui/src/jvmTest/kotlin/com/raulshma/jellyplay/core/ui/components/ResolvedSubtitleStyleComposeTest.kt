package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import com.raulshma.jellyplay.core.model.ResolvedSubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the Compose mapping helpers shared by the player subtitle overlay and
 * the onboarding preview (the composables that consume them are skipped):
 *
 *  - [resolvedColor] is a plain ARGB-int passthrough;
 *  - [resolvedFontWeight] is Bold only when `bold`, otherwise the SemiBold
 *    legibility default (never Normal);
 *  - [resolvedShadow] is null for [SubtitleEdgeType.NONE] and, for the three
 *    directional edges, uses the exact legacy offsets/blur constants and a
 *    dimmed edge color — OUTLINE keeps full alpha;
 *  - [subtitleColorToCompose] maps every [SubtitleColor] enum constant to its
 *    canonical Compose color.
 */
class ResolvedSubtitleStyleComposeTest {

    private fun style(
        edgeType: SubtitleEdgeType,
        edgeColorArgb: Int = 0xFF000000.toInt(),
    ) = ResolvedSubtitleStyle(
        fontColorArgb = 0xFFFFFFFF.toInt(),
        backgroundColorArgb = 0xFF000000.toInt(),
        backgroundAlpha = 0.5f,
        edgeColorArgb = edgeColorArgb,
        edgeType = edgeType,
        borderWidth = 1f,
        shadowOffset = 2f,
        fontSizeSp = 24,
        bold = false,
        italic = false,
        verticalPosition = 0.9f,
        offsetMs = 0L,
    )

    @Test
    fun resolvedColor_isArgbPassthrough() {
        assertEquals(Color(0xFF3366CC.toInt()), resolvedColor(0xFF3366CC.toInt()))
        assertEquals(Color.Transparent, resolvedColor(0))
    }

    @Test
    fun resolvedFontWeight_boldIsBold() {
        assertEquals(FontWeight.Bold, resolvedFontWeight(true))
    }

    @Test
    fun resolvedFontWeight_notBoldIsSemiBoldLegibilityDefault() {
        assertEquals(FontWeight.SemiBold, resolvedFontWeight(false))
    }

    @Test
    fun resolvedShadow_noneIsNull() {
        assertNull(resolvedShadow(style(SubtitleEdgeType.NONE)))
    }

    @Test
    fun resolvedShadow_outlineUsesFullAlphaEdgeColor() {
        val argb = 0xFF101010.toInt()
        val shadow = resolvedShadow(style(SubtitleEdgeType.OUTLINE, argb))

        assertEquals(Shadow(color = Color(argb), blurRadius = 4f), shadow)
    }

    @Test
    fun resolvedShadow_dropShadowDimsColorAndOffsetsDiagonally() {
        val argb = 0xFFFFFFFF.toInt()
        val shadow = resolvedShadow(style(SubtitleEdgeType.DROP_SHADOW, argb))

        assertEquals(
            Shadow(
                color = Color(argb).copy(alpha = 0.8f),
                blurRadius = 8f,
                offset = Offset(2f, 2f),
            ),
            shadow,
        )
    }

    @Test
    fun resolvedShadow_raisedAndDepressed_useOppositeDiagonals() {
        val argb = 0xFF808080.toInt()

        val raised = resolvedShadow(style(SubtitleEdgeType.RAISED, argb))
        assertEquals(Offset(1.5f, 1.5f), raised?.offset)
        assertEquals(Color(argb).copy(alpha = 0.7f), raised?.color)
        assertEquals(2f, raised?.blurRadius)

        val depressed = resolvedShadow(style(SubtitleEdgeType.DEPRESSED, argb))
        assertEquals(Offset(-1.5f, -1.5f), depressed?.offset)
    }

    @Test
    fun subtitleColorToCompose_coversEveryEnumConstant() {
        assertEquals(Color.White, subtitleColorToCompose(SubtitleColor.WHITE))
        assertEquals(Color.Yellow, subtitleColorToCompose(SubtitleColor.YELLOW))
        assertEquals(Color.Green, subtitleColorToCompose(SubtitleColor.GREEN))
        assertEquals(Color.Cyan, subtitleColorToCompose(SubtitleColor.CYAN))
        assertEquals(Color.Red, subtitleColorToCompose(SubtitleColor.RED))
        assertEquals(Color.Black, subtitleColorToCompose(SubtitleColor.BLACK))
        assertEquals(Color.Blue, subtitleColorToCompose(SubtitleColor.BLUE))
    }
}
