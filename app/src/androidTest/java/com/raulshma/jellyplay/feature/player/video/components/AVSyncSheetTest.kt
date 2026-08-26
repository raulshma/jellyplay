package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.util.UnstableApi
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
class AVSyncSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun avSyncSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("A/V Sync").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_showsAudioDelayLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio Delay").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_zeroDelay_showsNeutralLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onAllNodesWithText("0.0s")[0].assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_positiveAudioDelay_showsPlusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 1000L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+1.0s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_negativeAudioDelay_showsMinusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = -1500L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-1.5s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_showsResetAudioButtonWhenNonZero() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 500L,
                    onAudioDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset audio").assertIsDisplayed()
    }
}
