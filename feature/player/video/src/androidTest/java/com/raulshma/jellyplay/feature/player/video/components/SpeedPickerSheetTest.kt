package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpeedPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun speedPickerSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                SpeedPickerSheet(
                    currentSpeed = 1.0f,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Playback Speed").assertIsDisplayed()
    }

    @Test
    fun speedPickerSheet_displaysAllSpeedOptions() {
        composeTestRule.setContent {
            MaterialTheme {
                SpeedPickerSheet(
                    currentSpeed = 1.0f,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("0.25x").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.5x").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.75x").assertIsDisplayed()
        composeTestRule.onNodeWithText("1x").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.25x").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.75x").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.0x").assertIsDisplayed()
    }

    @Test
    fun speedPickerSheet_selectSpeed_callsOnSelect() {
        var selectedSpeed: Float? = null
        composeTestRule.setContent {
            MaterialTheme {
                SpeedPickerSheet(
                    currentSpeed = 1.0f,
                    onSelect = { selectedSpeed = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("1.5x").performClick()
        assertEquals(1.5f, selectedSpeed!!)
    }

    @Test
    fun speedPickerSheet_selectSpeed_callsOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                SpeedPickerSheet(
                    currentSpeed = 1.0f,
                    onSelect = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("2.0x").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun speedPickerSheet_initialSpeed_1x() {
        composeTestRule.setContent {
            MaterialTheme {
                SpeedPickerSheet(
                    currentSpeed = 0.5f,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("0.5x").assertIsDisplayed()
        composeTestRule.onNodeWithText("1x").assertIsDisplayed()
    }
}
