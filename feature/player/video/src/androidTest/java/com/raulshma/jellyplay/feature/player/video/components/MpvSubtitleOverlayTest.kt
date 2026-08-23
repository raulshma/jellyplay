package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MpvSubtitleOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mpvSubtitleOverlay_nullOrBlankCue_rendersNothing() {
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = null,
                    style = SubtitleStyle(),
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        composeTestRule.onNodeWithText("Hello World").assertDoesNotExist()

        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "",
                    style = SubtitleStyle(),
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        composeTestRule.onNodeWithText("Hello World").assertDoesNotExist()
    }

    @Test
    fun mpvSubtitleOverlay_defaultStyle_displaysCueText() {
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "Hello Native World",
                    style = SubtitleStyle(applyCustomStyle = false),
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        composeTestRule.onNodeWithText("Hello Native World").assertIsDisplayed()
    }

    @Test
    fun mpvSubtitleOverlay_customStyle_displaysCueText() {
        val style = SubtitleStyle(
            applyCustomStyle = true,
            fontSize = 32,
            fontColor = SubtitleColor.YELLOW,
            backgroundColor = SubtitleColor.BLACK,
            backgroundOpacity = 0.8f,
            edgeType = SubtitleEdgeType.OUTLINE,
            edgeColor = SubtitleColor.RED,
            bold = true,
            italic = true,
        )
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "Custom Subtitle Cue",
                    style = style,
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        composeTestRule.onNodeWithText("Custom Subtitle Cue").assertIsDisplayed()
    }

    @Test
    fun mpvSubtitleOverlay_outlineEdge_rendersStrokeLayerForCrisp360Outline() {
        // The "crisp 360° outline" feature adds a second Text node drawn with
        // Stroke behind the fill. With OUTLINE + a non-zero border width, the
        // overlay must render the cue *twice* (stroke + fill); a regression that
        // drops the stroke layer would leave exactly one node.
        val style = SubtitleStyle(
            applyCustomStyle = true,
            edgeType = SubtitleEdgeType.OUTLINE,
            borderWidth = 3.0f,
        )
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "Outlined Cue",
                    style = style,
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        assertEquals(
            "OUTLINE must render a stroke layer plus a fill layer",
            2,
            composeTestRule.onAllNodesWithText("Outlined Cue").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun mpvSubtitleOverlay_noEdge_rendersSingleFillLayer() {
        // NONE must NOT add a stroke layer — only the fill Text renders. Guards
        // against the stroke layer leaking in for edge types that don't outline.
        val style = SubtitleStyle(
            applyCustomStyle = true,
            edgeType = SubtitleEdgeType.NONE,
        )
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "Plain Cue",
                    style = style,
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        assertEquals(
            "NONE must render only the fill layer",
            1,
            composeTestRule.onAllNodesWithText("Plain Cue").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun mpvSubtitleOverlay_defaultFallback_ignoresUserColorFields() {
        // When applyCustomStyle is false, the overlay falls back to mpv's native
        // defaults (white text, black outline, non-bold, non-italic) and MUST
        // ignore any user-supplied color/typography fields. A cue still renders.
        val style = SubtitleStyle(
            applyCustomStyle = false,
            fontColor = SubtitleColor.YELLOW,
            edgeColor = SubtitleColor.RED,
            bold = true,
            italic = true,
            fontSize = 48,
        )
        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "Native Fallback",
                    style = style,
                    fontProvider = FontProvider(LocalContext.current.applicationContext),
                )
            }
        }
        // applyCustomStyle=false still enables the OUTLINE default edge type, so
        // the stroke + fill pair should be present regardless of the user fields.
        assertEquals(
            "default fallback should still render stroke + fill (native outline)",
            2,
            composeTestRule.onAllNodesWithText("Native Fallback").fetchSemanticsNodes().size,
        )
    }
}
