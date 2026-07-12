package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubtitleStyleSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun subtitleStyleSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Settings").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysFontSize() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(fontSize = 24),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Size: 24sp").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysFontColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysBackgroundColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Background Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysBackgroundOpacity() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(backgroundOpacity = 0.6f),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Background Opacity: 60%").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysEdgeTypeSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(edgeType = SubtitleEdgeType.NONE),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Type").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysAllEdgeTypes() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("None").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shadow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Raised").assertIsDisplayed()
        composeTestRule.onNodeWithText("Depressed").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withEdgeType_showsEdgeColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(edgeType = SubtitleEdgeType.OUTLINE),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_noneEdgeType_hidesEdgeColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(edgeType = SubtitleEdgeType.NONE),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Color").assertDoesNotExist()
    }

    @Test
    fun subtitleStyleSheet_displaysSubtitleOffset() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = 0L),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: 0.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_positiveOffset_displaysPlusSign() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = 2000L),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: +2.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_negativeOffset_displaysMinusSign() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = -2000L),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: -2.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysVerticalPosition() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(verticalPosition = 0.05f),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Vertical Position: 5%").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysResetButton() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_reset_callsOnStyleChangeWithDefaults() {
        var receivedStyle: SubtitleStyle? = null
        val modifiedStyle = SubtitleStyle(
            fontSize = 36,
            offsetMs = 5000L,
            edgeType = SubtitleEdgeType.OUTLINE,
        )
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = modifiedStyle,
                    onStyleChange = { receivedStyle = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset").performClick()
        // Reset re-enables applyCustomStyle (the button is only enabled when it is on),
        // so the emitted style is the full-default with applyCustomStyle = true.
        assertEquals(SubtitleStyle(applyCustomStyle = true), receivedStyle)
    }

    @Test
    fun subtitleStyleSheet_customFontSize_showsCorrectValue() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(fontSize = 36),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Size: 36sp").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withAssOverrideCapability_showsAssOverrideControl() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsAssOverride = true,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("ASS Styling").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withoutAssOverrideCapability_hidesAssOverrideControl() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsAssOverride = false,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("ASS Styling").assertDoesNotExist()
    }

    @Test
    fun subtitleStyleSheet_withBorderStylesCapability_showsBorderSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsBorderStyles = true,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Border Style").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withoutBorderStylesCapability_hidesBorderSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsBorderStyles = false,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Border Style").assertDoesNotExist()
    }
}
