package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AudioDelaySheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun audioDelaySheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                AudioDelaySheet(
                    currentDelayMs = 0L,
                    onDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio Delay").assertIsDisplayed()
    }

    @Test
    fun audioDelaySheet_zeroDelay_showsNeutralLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AudioDelaySheet(
                    currentDelayMs = 0L,
                    onDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("0.0s").assertIsDisplayed()
    }

    @Test
    fun audioDelaySheet_positiveDelay_showsAudioLate() {
        composeTestRule.setContent {
            MaterialTheme {
                AudioDelaySheet(
                    currentDelayMs = 1000L,
                    onDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+1.0s (audio late)").assertIsDisplayed()
    }

    @Test
    fun audioDelaySheet_negativeDelay_showsAudioEarly() {
        composeTestRule.setContent {
            MaterialTheme {
                AudioDelaySheet(
                    currentDelayMs = -1500L,
                    onDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-1.5s (audio early)").assertIsDisplayed()
    }

    @Test
    fun audioDelaySheet_showsHelpText() {
        composeTestRule.setContent {
            MaterialTheme {
                AudioDelaySheet(
                    currentDelayMs = 0L,
                    onDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Negative values make audio play earlier.").assertIsDisplayed()
    }
}
