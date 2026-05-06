package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerControlButtonsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun labeledControlButton_displaysIconAndLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledControlButton(
                    onClick = {},
                    icon = Icons.Default.Audiotrack,
                    label = "Audio",
                )
            }
        }
        composeTestRule.onNodeWithText("Audio").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Audio").assertIsDisplayed()
    }

    @Test
    fun labeledControlButton_click_callsOnClick() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LabeledControlButton(
                    onClick = { clicked = true },
                    icon = Icons.Default.Audiotrack,
                    label = "Audio",
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Audio").performClick()
        assert(clicked)
    }

    @Test
    fun labeledControlButton_disabled_clickDoesNotFire() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LabeledControlButton(
                    onClick = { clicked = true },
                    icon = Icons.Default.Audiotrack,
                    label = "Audio",
                    enabled = false,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Audio").performClick()
        assert(!clicked)
    }

    @Test
    fun labeledControlButton_displaysCustomLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledControlButton(
                    onClick = {},
                    icon = Icons.Default.Audiotrack,
                    label = "Subs",
                )
            }
        }
        composeTestRule.onNodeWithText("Subs").assertIsDisplayed()
    }

    @Test
    fun labeledSpeedButton_showsSpeedText() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledSpeedButton(
                    onClick = {},
                    speed = 1.5f,
                )
            }
        }
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speed").assertIsDisplayed()
    }

    @Test
    fun labeledSpeedButton_speedOne_showsSimplifiedFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledSpeedButton(
                    onClick = {},
                    speed = 1.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("1x").assertIsDisplayed()
    }

    @Test
    fun labeledSpeedButton_speedZeroPoint25_showsCorrectFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledSpeedButton(
                    onClick = {},
                    speed = 0.25f,
                )
            }
        }
        composeTestRule.onNodeWithText("0.25x").assertIsDisplayed()
    }

    @Test
    fun labeledSpeedButton_click_callsOnClick() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LabeledSpeedButton(
                    onClick = { clicked = true },
                    speed = 1.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("1x").performClick()
        assert(clicked)
    }

    @Test
    fun labeledSpeedButton_speedTwo_showsCorrectFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                LabeledSpeedButton(
                    onClick = {},
                    speed = 2.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("2.0x").assertIsDisplayed()
    }
}
