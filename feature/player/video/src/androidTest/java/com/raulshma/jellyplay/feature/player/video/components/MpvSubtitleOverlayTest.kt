package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
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
                )
            }
        }
        composeTestRule.onNodeWithText("Hello World").assertDoesNotExist()

        composeTestRule.setContent {
            MaterialTheme {
                MpvSubtitleOverlay(
                    cue = "",
                    style = SubtitleStyle(),
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
                )
            }
        }
        composeTestRule.onNodeWithText("Custom Subtitle Cue").assertIsDisplayed()
    }
}
