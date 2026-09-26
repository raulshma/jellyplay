package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun errorScreen_displaysMessage() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                ErrorScreen(
                    message = "Network error",
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
    }

    @Test
    fun errorScreen_retryButtonClicked() {
        var retried = false

        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                ErrorScreen(
                    message = "Failed to load",
                    onRetry = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun errorScreen_noRetryButtonWhenNull() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                ErrorScreen(
                    message = "Fatal error",
                    onRetry = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Fatal error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }
}
