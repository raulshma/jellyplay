package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class GestureOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun gestureOverlay_noGestures_noSeekOverlay() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 0,
                    seekOffsetMs = 0L,
                    brightnessValue = -1f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                    showControls = true,
                    onEdgeSwipe = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-").assertDoesNotExist()
        composeTestRule.onNodeWithText("+").assertDoesNotExist()
    }

    @Test
    fun gestureOverlay_seekBack_showsSeekOverlay() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = -1,
                    seekOffsetMs = 10_000L,
                    brightnessValue = -1f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-10s").assertIsDisplayed()
    }

    @Test
    fun gestureOverlay_seekForward_showsSeekOverlay() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 1,
                    seekOffsetMs = 30_000L,
                    brightnessValue = -1f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+30s").assertIsDisplayed()
    }

    @Test
    fun gestureOverlay_brightnessShown_displaysPercentage() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 0,
                    seekOffsetMs = 0L,
                    brightnessValue = 0.75f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                )
            }
        }
        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
    }

    @Test
    fun gestureOverlay_volumeShown_displaysPercentage() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 0,
                    seekOffsetMs = 0L,
                    brightnessValue = -1f,
                    volumeValue = 0.5f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                )
            }
        }
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun gestureOverlay_gesturesDisabled_noOverlays() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 1,
                    seekOffsetMs = 10_000L,
                    brightnessValue = 0.5f,
                    volumeValue = 0.5f,
                    gesturesEnabled = false,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+10s").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun gestureOverlay_zeroOffset_noSeekText() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 1,
                    seekOffsetMs = 0L,
                    brightnessValue = -1f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                    showControls = true,
                    onEdgeSwipe = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+0s").assertDoesNotExist()
    }

    @Test
    fun gestureOverlay_negativeBrightness_noOverlay() {
        composeTestRule.setContent {
            MaterialTheme {
                GestureOverlay(
                    seekDirection = 0,
                    seekOffsetMs = 0L,
                    brightnessValue = -1f,
                    volumeValue = -1f,
                    gesturesEnabled = true,
                    swipeSeekMaxMs = 120_000L,
                    onSeekGesture = {},
                    onBrightnessGesture = {},
                    onVolumeGesture = {},
                    onClearOverlays = {},
                    showControls = true,
                    onEdgeSwipe = {},
                )
            }
        }
        composeTestRule.onNodeWithText("-100%").assertDoesNotExist()
    }
}
