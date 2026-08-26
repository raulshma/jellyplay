package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.MediaSegmentType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkipOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun segmentSkipOverlay_intro_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.INTRO,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
    }

    @Test
    fun segmentSkipOverlay_intro_hidden_doesNotShowText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = false,
                    segmentType = MediaSegmentType.INTRO,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }

    @Test
    fun segmentSkipOverlay_intro_click_callsOnSkip() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.INTRO,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Intro").performClick()
        assertTrue(skipped)
    }

    @Test
    fun segmentSkipOverlay_outro_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.OUTRO,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").assertIsDisplayed()
    }

    @Test
    fun segmentSkipOverlay_commercial_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.COMMERCIAL,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Commercial").assertIsDisplayed()
    }

    @Test
    fun segmentSkipOverlay_recap_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.RECAP,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Recap").assertIsDisplayed()
    }

    @Test
    fun segmentSkipOverlay_preview_visible_showsText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.PREVIEW,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Preview").assertIsDisplayed()
    }

    @Test
    fun segmentSkipOverlay_outro_hidden_doesNotShowText() {
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = false,
                    segmentType = MediaSegmentType.OUTRO,
                    onSkip = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").assertDoesNotExist()
    }

    @Test
    fun segmentSkipOverlay_outro_click_callsOnSkip() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = MediaSegmentType.OUTRO,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Skip Credits").performClick()
        assertTrue(skipped)
    }
}
