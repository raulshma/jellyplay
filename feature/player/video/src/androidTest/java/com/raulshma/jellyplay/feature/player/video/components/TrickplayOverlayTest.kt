package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class TrickplayOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trickplayOverlay_displaysTimeForPosition() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    imageUrl = null,
                    positionMs = 90_000L,
                )
            }
        }
        composeTestRule.onNodeWithText("01:30").assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_displaysTimeWithHours() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    imageUrl = null,
                    positionMs = 3_600_000L,
                )
            }
        }
        composeTestRule.onNodeWithText("1:00:00").assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_zeroPosition_displaysZeroTime() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    imageUrl = null,
                    positionMs = 0L,
                )
            }
        }
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_withImageUrl_displaysTime() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    imageUrl = "https://example.com/thumb.jpg",
                    positionMs = 45_000L,
                )
            }
        }
        composeTestRule.onNodeWithText("00:45").assertIsDisplayed()
    }
}
