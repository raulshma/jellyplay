package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AspectRatioSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aspectRatioSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Aspect Ratio").assertIsDisplayed()
    }

    @Test
    fun aspectRatioSheet_displaysAllRatios() {
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Auto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fill").assertIsDisplayed()
        composeTestRule.onNodeWithText("16:9").assertIsDisplayed()
        composeTestRule.onNodeWithText("4:3").assertIsDisplayed()
        composeTestRule.onNodeWithText("21:9").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crop").assertIsDisplayed()
    }

    @Test
    fun aspectRatioSheet_withDetectedRatio_showsDetectedLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = AspectRatio.RATIO_16_9,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Detected: 16:9").assertIsDisplayed()
    }

    @Test
    fun aspectRatioSheet_autoWithDetected_showsCombinedLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = AspectRatio.RATIO_21_9,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Auto (21:9)").assertIsDisplayed()
    }

    @Test
    fun aspectRatioSheet_selectRatio_callsOnSelect() {
        var selected: AspectRatio? = null
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = null,
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Fill").performClick()
        assertEquals(AspectRatio.FILL, selected)
    }

    @Test
    fun aspectRatioSheet_selectRatio_callsOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = null,
                    onSelect = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("16:9").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun aspectRatioSheet_detectedFit_hidesDetectedLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AspectRatioSheet(
                    currentRatio = AspectRatio.AUTO,
                    detectedRatio = AspectRatio.FIT,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Detected:").assertDoesNotExist()
    }
}
