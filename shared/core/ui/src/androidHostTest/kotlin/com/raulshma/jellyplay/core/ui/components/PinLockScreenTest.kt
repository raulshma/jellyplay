package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pinLockScreen_displaysTitleAndKeys() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                PinLockScreen(
                    title = "Test PIN",
                    subtitle = "Enter test PIN",
                    onPinEntered = {},
                )
            }
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
            JellyPlayTheme(dynamicColor = false) {
                PinLockScreen(
                    onPinEntered = { enteredPin = it },
                )
            }
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
            JellyPlayTheme(dynamicColor = false) {
                PinLockScreen(
                    onPinEntered = { enteredPin = it },
                )
            }
        }

        // Contract: backspace removes the last digit mid-entry, and the code
        // is submitted exactly when the 4th digit lands (then the field
        // resets). Typing 1,2,3 + backspace must drop the "3" from the next
        // submission — without it the entered code would be "1234".
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithContentDescription("Backspace").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("5").performClick()

        assertEquals("1245", enteredPin)
    }

    @Test
    fun pinLockScreen_showsErrorMessage() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                PinLockScreen(
                    onPinEntered = {},
                    errorMessage = "Invalid PIN",
                )
            }
        }

        composeTestRule.onNodeWithText("Invalid PIN").assertIsDisplayed()
    }
}
