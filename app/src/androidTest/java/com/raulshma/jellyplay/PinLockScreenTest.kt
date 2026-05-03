package com.raulshma.jellyplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.ui.components.PinLockScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pinLockScreen_displaysTitleAndKeys() {
        composeTestRule.setContent {
            PinLockScreen(
                title = "Test PIN",
                subtitle = "Enter test PIN",
                onPinEntered = {},
            )
        }

        composeTestRule.onNodeWithText("Test PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter test PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Backspace").assertIsDisplayed()
    }

    @Test
    fun pinLockScreen_entersPin() {
        var enteredPin = ""

        composeTestRule.setContent {
            PinLockScreen(
                onPinEntered = { enteredPin = it },
            )
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        assertEquals("1234", enteredPin)
    }

    @Test
    fun pinLockScreen_backspaceClearsDigit() {
        var enteredPin = ""

        composeTestRule.setContent {
            PinLockScreen(
                onPinEntered = { enteredPin = it },
            )
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithContentDescription("Backspace").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        assertEquals("134", enteredPin)
    }

    @Test
    fun pinLockScreen_showsErrorMessage() {
        composeTestRule.setContent {
            PinLockScreen(
                onPinEntered = {},
                errorMessage = "Invalid PIN",
            )
        }

        composeTestRule.onNodeWithText("Invalid PIN").assertIsDisplayed()
    }
}
