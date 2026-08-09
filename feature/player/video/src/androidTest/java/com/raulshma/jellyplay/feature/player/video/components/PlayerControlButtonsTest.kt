package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

class PlayerControlButtonsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerIconButton_displaysIcon() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerIconButton(
                    onClick = {},
                    icon = Tabler.Outline.Music,
                    contentDescription = "Audio",
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Audio").assertIsDisplayed()
    }

    @Test
    fun playerIconButton_click_callsOnClick() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                PlayerIconButton(
                    onClick = { clicked = true },
                    icon = Tabler.Outline.Music,
                    contentDescription = "Audio",
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Audio").performClick()
        assert(clicked)
    }

    @Test
    fun playerIconButton_disabled_clickDoesNotFire() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                PlayerIconButton(
                    onClick = { clicked = true },
                    icon = Tabler.Outline.Music,
                    contentDescription = "Audio",
                    enabled = false,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Audio").performClick()
        assert(!clicked)
    }

    @Test
    fun playerSpeedButton_showsSpeedText() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerSpeedButton(
                    onClick = {},
                    speed = 1.5f,
                )
            }
        }
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()
    }

    @Test
    fun playerSpeedButton_speedOne_showsSimplifiedFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerSpeedButton(
                    onClick = {},
                    speed = 1.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("1x").assertIsDisplayed()
    }

    @Test
    fun playerSpeedButton_speedZeroPoint25_showsCorrectFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerSpeedButton(
                    onClick = {},
                    speed = 0.25f,
                )
            }
        }
        composeTestRule.onNodeWithText("0.25x").assertIsDisplayed()
    }

    @Test
    fun playerSpeedButton_click_callsOnClick() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                PlayerSpeedButton(
                    onClick = { clicked = true },
                    speed = 1.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("1x").performClick()
        assert(clicked)
    }

    @Test
    fun playerSpeedButton_speedTwo_showsCorrectFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerSpeedButton(
                    onClick = {},
                    speed = 2.0f,
                )
            }
        }
        composeTestRule.onNodeWithText("2.0x").assertIsDisplayed()
    }
}
