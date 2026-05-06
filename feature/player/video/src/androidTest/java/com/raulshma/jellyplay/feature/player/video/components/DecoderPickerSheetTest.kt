package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DecoderPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun decoderPickerSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                DecoderPickerSheet(
                    currentMode = DecoderMode.HW_PREFERRED,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Decoder Mode").assertIsDisplayed()
    }

    @Test
    fun decoderPickerSheet_displaysAllModes() {
        composeTestRule.setContent {
            MaterialTheme {
                DecoderPickerSheet(
                    currentMode = DecoderMode.HW_PREFERRED,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Hardware (Preferred)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hardware Only").assertIsDisplayed()
        composeTestRule.onNodeWithText("Software Only").assertIsDisplayed()
    }

    @Test
    fun decoderPickerSheet_displaysHelpText() {
        composeTestRule.setContent {
            MaterialTheme {
                DecoderPickerSheet(
                    currentMode = DecoderMode.HW_PREFERRED,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Changes take effect on next video playback.").assertIsDisplayed()
    }

    @Test
    fun decoderPickerSheet_selectMode_callsOnSelect() {
        var selected: DecoderMode? = null
        composeTestRule.setContent {
            MaterialTheme {
                DecoderPickerSheet(
                    currentMode = DecoderMode.HW_PREFERRED,
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Software Only").performClick()
        assertEquals(DecoderMode.SW_ONLY, selected)
    }

    @Test
    fun decoderPickerSheet_selectMode_callsOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                DecoderPickerSheet(
                    currentMode = DecoderMode.HW_PREFERRED,
                    onSelect = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Hardware Only").performClick()
        assertTrue(dismissed)
    }
}
