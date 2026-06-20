package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AVSyncSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun avSyncSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("A/V Sync").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_showsBothSectionLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio Delay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subtitle Delay").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_zeroDelay_showsNeutralLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("0.0s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_positiveAudioDelay_showsPlusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 1000L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+1.0s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_negativeSubtitleDelay_showsMinusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = -1500L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-1.5s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_showsResetBothButton() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 500L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset both").assertIsDisplayed()
    }
}
