package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkipOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun introSkipOverlay_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                IntroSkipOverlay(
                    isVisible = true,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
    }

    @Test
    fun introSkipOverlay_hidden_doesNotShowText() {
        composeTestRule.setContent {
            MaterialTheme {
                IntroSkipOverlay(
                    isVisible = false,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }

    @Test
    fun introSkipOverlay_click_callsOnSkip() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                IntroSkipOverlay(
                    isVisible = true,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").performClick()
        assertTrue(skipped)
    }

    @Test
    fun creditsSkipOverlay_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                CreditsSkipOverlay(
                    isVisible = true,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").assertIsDisplayed()
    }

    @Test
    fun creditsSkipOverlay_hidden_doesNotShowText() {
        composeTestRule.setContent {
            MaterialTheme {
                CreditsSkipOverlay(
                    isVisible = false,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").assertDoesNotExist()
    }

    @Test
    fun creditsSkipOverlay_click_callsOnSkip() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                CreditsSkipOverlay(
                    isVisible = true,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").performClick()
        assertTrue(skipped)
    }
}
